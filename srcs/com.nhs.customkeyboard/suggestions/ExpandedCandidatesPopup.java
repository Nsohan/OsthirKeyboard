package com.nhs.customkeyboard.suggestions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.nhs.customkeyboard.Config;
import com.nhs.customkeyboard.R;
import com.nhs.customkeyboard.VibratorCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Floating Expanded Suggestions Popup dynamically styled according to the active
 * keyboard theme (colorKeyboard, colorKey, colorKeyActivated, colorLabel, keyBorderRadius).
 *
 * Provides a compact 3-column scrollable grid for candidates and Drag-to-Delete Trash Bin
 * for removing user-learned words, transitions, and emails.
 */
public final class ExpandedCandidatesPopup
{
  private final Context _context;
  private final CandidatesView _candidatesView;
  private final UserLearningEngine _engine;

  private PopupWindow _popup;
  private FrameLayout _rootLayout;
  private FrameLayout _binZone;
  private FrameLayout _binContainer;
  private ImageView _binIcon;
  private LinearLayout _cardLayout;
  private ScrollView _scrollView;
  private LinearLayout _gridContainer;
  private TextView _floatingChip;

  private final GradientDrawable _normalBinBg;
  private final GradientDrawable _hoveredBinBg;

  private int _themeColorKeyboard;
  private int _themeColorKey;
  private int _themeColorKeyActivated;
  private int _themeColorLabel;
  private int _themeColorSubLabel;
  private float _themeBorderRadius;
  private boolean _isDarkTheme = true;

  private final List<String> _currentWords = new ArrayList<>();
  private boolean _isDragging = false;
  private boolean _isBinHovered = false;
  private String _draggedWord = null;
  private View _draggedSourceView = null;
  private float _dragStartX = 0f;
  private float _dragStartY = 0f;

  public ExpandedCandidatesPopup(Context context, CandidatesView candidatesView)
  {
    _context = context;
    _candidatesView = candidatesView;
    _engine = UserLearningEngine.getInstance(context);

    DisplayMetrics dm = _context.getResources().getDisplayMetrics();
    float density = dm.density;

    // Normal bin background: Prominent Red circle matching Image 2
    _normalBinBg = new GradientDrawable();
    _normalBinBg.setShape(GradientDrawable.OVAL);
    _normalBinBg.setColor(Color.parseColor("#E53935"));
    _normalBinBg.setStroke((int)(2.0f * density), Color.WHITE);

    // Hovered bin background: Deeper crimson red on hover
    _hoveredBinBg = new GradientDrawable();
    _hoveredBinBg.setShape(GradientDrawable.OVAL);
    _hoveredBinBg.setColor(Color.parseColor("#B71C1C"));
    _hoveredBinBg.setStroke((int)(2.5f * density), Color.parseColor("#FFCDD2"));

    buildViewHierarchy();
  }

  private void buildViewHierarchy()
  {
    DisplayMetrics dm = _context.getResources().getDisplayMetrics();
    float density = dm.density;

    _rootLayout = new FrameLayout(_context);
    _rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT));
    _rootLayout.setClipChildren(false);
    _rootLayout.setClipToPadding(false);
    _rootLayout.setPadding(0, (int)(16 * density), 0, (int)(4 * density));

    // 1. Vertical main container (Trash Zone on top + Card Container below)
    LinearLayout mainContainer = new LinearLayout(_context);
    mainContainer.setOrientation(LinearLayout.VERTICAL);
    mainContainer.setClipChildren(false);
    mainContainer.setClipToPadding(false);
    FrameLayout.LayoutParams mcp = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT);
    mainContainer.setLayoutParams(mcp);

    // 2. Top Trash Zone (Image 2)
    _binZone = new FrameLayout(_context);
    LinearLayout.LayoutParams bzp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        (int)(48 * density));
    bzp.bottomMargin = (int)(6 * density);
    _binZone.setLayoutParams(bzp);
    _binZone.setClipChildren(false);
    _binZone.setClipToPadding(false);
    _binZone.setVisibility(View.INVISIBLE);
    _binZone.setAlpha(0f);
    _binZone.setScaleX(0.8f);
    _binZone.setScaleY(0.8f);

    _binContainer = new FrameLayout(_context);
    int binSize = (int)(40 * density);
    FrameLayout.LayoutParams bcp = new FrameLayout.LayoutParams(binSize, binSize);
    bcp.gravity = Gravity.CENTER;
    _binContainer.setLayoutParams(bcp);
    _binContainer.setBackground(_normalBinBg);

    _binIcon = new ImageView(_context);
    int iconSize = (int)(22 * density);
    FrameLayout.LayoutParams bip = new FrameLayout.LayoutParams(iconSize, iconSize);
    bip.gravity = Gravity.CENTER;
    _binIcon.setLayoutParams(bip);
    _binIcon.setImageResource(R.drawable.ic_delete);
    _binIcon.setColorFilter(Color.WHITE);
    _binContainer.addView(_binIcon);
    _binZone.addView(_binContainer);
    mainContainer.addView(_binZone);

    // 3. Card Container
    _cardLayout = new LinearLayout(_context);
    _cardLayout.setOrientation(LinearLayout.VERTICAL);
    LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT);
    _cardLayout.setLayoutParams(clp);
    _cardLayout.setPadding(
        (int)(6 * density),
        (int)(5 * density),
        (int)(6 * density),
        (int)(5 * density));
    if (android.os.Build.VERSION.SDK_INT >= 21)
    {
      _cardLayout.setElevation(8 * density);
    }

    _scrollView = new ScrollView(_context);
    _scrollView.setLayoutParams(new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT));
    _scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
    _scrollView.setVerticalScrollBarEnabled(true);
    _scrollView.setScrollbarFadingEnabled(true);

    _gridContainer = new LinearLayout(_context);
    _gridContainer.setOrientation(LinearLayout.VERTICAL);
    _gridContainer.setLayoutParams(new ScrollView.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT));
    _scrollView.addView(_gridContainer);
    _cardLayout.addView(_scrollView);
    mainContainer.addView(_cardLayout);
    _rootLayout.addView(mainContainer);

    // 4. Floating Drag Chip (Compact word chip matching touched cell)
    _floatingChip = new TextView(_context);
    _floatingChip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
    _floatingChip.setGravity(Gravity.CENTER);
    _floatingChip.setMaxLines(1);
    _floatingChip.setVisibility(View.GONE);
    FrameLayout.LayoutParams fcp = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT);
    _floatingChip.setLayoutParams(fcp);
    if (android.os.Build.VERSION.SDK_INT >= 21)
    {
      _floatingChip.setElevation(14 * density);
    }
    _rootLayout.addView(_floatingChip);

    // 5. Initialize PopupWindow
    _popup = new PopupWindow(_rootLayout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
    _popup.setFocusable(false);
    _popup.setTouchable(true);
    _popup.setOutsideTouchable(true);
    _popup.setClippingEnabled(false);
    _popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    if (android.os.Build.VERSION.SDK_INT >= 21)
    {
      _popup.setElevation(16 * density);
    }
    _popup.setOnDismissListener(() -> {
      resetDragState();
      if (_binZone != null)
      {
        _binZone.animate().cancel();
        _binZone.animate().setListener(null);
        _binZone.setVisibility(View.INVISIBLE);
        _binZone.setAlpha(0f);
      }
      hideFloatingChip();
    });
  }

  /**
   * Resolves active keyboard theme colors and styles the popup card, text, ripples, and bin.
   */
  private void resolveAndApplyTheme()
  {
    Context themeCtx = _candidatesView != null ? _candidatesView.getContext() : _context;
    Config config = Config.globalConfig();
    if (config != null && config.theme != 0)
    {
      themeCtx = new ContextThemeWrapper(themeCtx, config.theme);
    }

    TypedValue out = new TypedValue();
    DisplayMetrics dm = themeCtx.getResources().getDisplayMetrics();
    float density = dm.density;

    if (themeCtx.getTheme().resolveAttribute(R.attr.colorKeyboard, out, true))
    {
      _themeColorKeyboard = out.data;
    }
    else
    {
      _themeColorKeyboard = Color.parseColor("#121212");
    }

    if (themeCtx.getTheme().resolveAttribute(R.attr.colorKey, out, true))
    {
      _themeColorKey = out.data;
    }
    else
    {
      _themeColorKey = _themeColorKeyboard;
    }

    if (themeCtx.getTheme().resolveAttribute(R.attr.colorKeyActivated, out, true))
    {
      _themeColorKeyActivated = out.data;
    }
    else
    {
      _themeColorKeyActivated = Color.parseColor("#444444");
    }

    if (themeCtx.getTheme().resolveAttribute(R.attr.colorLabel, out, true))
    {
      _themeColorLabel = out.data;
    }
    else
    {
      _themeColorLabel = Color.WHITE;
    }

    if (themeCtx.getTheme().resolveAttribute(R.attr.colorSubLabel, out, true))
    {
      _themeColorSubLabel = out.data;
    }
    else
    {
      _themeColorSubLabel = Color.LTGRAY;
    }

    if (themeCtx.getTheme().resolveAttribute(R.attr.keyBorderRadius, out, true))
    {
      _themeBorderRadius = out.getDimension(dm);
    }
    else
    {
      _themeBorderRadius = 12 * density;
    }

    boolean isNight = (themeCtx.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    boolean isCustomTheme = (config != null && config.themeName != null && config.themeName.startsWith("custom_"))
        || (config != null && config.theme == R.style.CustomImageTheme)
        || (_themeColorKeyboard == Color.TRANSPARENT && Color.alpha(_themeColorKey) < 255);

    // Compute elevated surface color for popup card
    int cardBgColor;
    if (isCustomTheme)
    {
      if (!isNight)
      {
        cardBgColor = ContextCompat.getColor(themeCtx, R.color.settings_card_bg);
        _themeColorLabel = ContextCompat.getColor(themeCtx, R.color.settings_on_surface);
        _themeColorSubLabel = ContextCompat.getColor(themeCtx, R.color.settings_on_surface_variant);
        _themeColorKeyActivated = Color.parseColor("#1F000000");
        _isDarkTheme = false;
      }
      else
      {
        cardBgColor = ContextCompat.getColor(themeCtx, R.color.settings_card_bg);
        _themeColorLabel = ContextCompat.getColor(themeCtx, R.color.settings_on_surface);
        _themeColorSubLabel = ContextCompat.getColor(themeCtx, R.color.settings_on_surface_variant);
        _themeColorKeyActivated = Color.parseColor("#26FFFFFF");
        _isDarkTheme = true;
      }
    }
    else
    {
      _isDarkTheme = isDarkColor(_themeColorKeyboard);
      if (_isDarkTheme)
      {
        if (_themeColorKey != 0 && _themeColorKey != _themeColorKeyboard && _themeColorKey != Color.TRANSPARENT)
        {
          cardBgColor = _themeColorKey;
        }
        else
        {
          cardBgColor = Color.parseColor("#1E2024");
        }
      }
      else
      {
        if (_themeColorKey != 0 && _themeColorKey != _themeColorKeyboard && _themeColorKey != Color.TRANSPARENT)
        {
          cardBgColor = _themeColorKey;
        }
        else
        {
          cardBgColor = Color.parseColor("#F5F5F7");
        }
      }

      // Ensure popup card background is completely opaque
      cardBgColor = Color.rgb(Color.red(cardBgColor), Color.green(cardBgColor), Color.blue(cardBgColor));

      // Guarantee high-contrast text against the solid card background
      boolean cardIsDark = isDarkColor(cardBgColor);
      boolean labelIsDark = isDarkColor(_themeColorLabel);
      if (cardIsDark && labelIsDark)
      {
        _themeColorLabel = Color.WHITE;
      }
      else if (!cardIsDark && !labelIsDark)
      {
        _themeColorLabel = Color.parseColor("#1F1F1F");
      }
    }

    float cornerRadius = Math.max(12 * density, _themeBorderRadius);
    GradientDrawable cardBg = new GradientDrawable();
    cardBg.setShape(GradientDrawable.RECTANGLE);
    cardBg.setCornerRadius(cornerRadius);
    cardBg.setColor(cardBgColor);
    int strokeColor = _isDarkTheme ? Color.parseColor("#2BFFFFFF") : Color.parseColor("#1A000000");
    cardBg.setStroke((int)(1 * density), strokeColor);
    _cardLayout.setBackground(cardBg);

    // Style Floating Chip
    GradientDrawable chipBg = new GradientDrawable();
    chipBg.setShape(GradientDrawable.RECTANGLE);
    chipBg.setCornerRadius(8 * density);
    chipBg.setColor(cardBgColor);
    int chipStrokeColor = _isDarkTheme ? Color.parseColor("#33FFFFFF") : Color.parseColor("#33000000");
    chipBg.setStroke((int)(1.5f * density), chipStrokeColor);
    _floatingChip.setBackground(chipBg);
    _floatingChip.setTextColor(_themeColorLabel);
  }

  private static boolean isDarkColor(int color)
  {
    double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
    return luminance < 0.5;
  }

  public boolean isShowing()
  {
    return _popup != null && _popup.isShowing();
  }

  public void dismiss()
  {
    if (isShowing())
    {
      try
      {
        _popup.dismiss();
      }
      catch (Exception ignored) {}
    }
    resetDragState();
    if (_binZone != null)
    {
      _binZone.animate().cancel();
      _binZone.animate().setListener(null);
      _binZone.setVisibility(View.INVISIBLE);
      _binZone.setAlpha(0f);
    }
    hideFloatingChip();
  }

  public void show(Suggestions s, View anchorView)
  {
    if (s == null || s.count == 0 || anchorView == null || !anchorView.isAttachedToWindow())
    {
      return;
    }

    _currentWords.clear();
    for (int i = 0; i < s.count; i++)
    {
      String w = s.suggestions[i];
      if (w != null && !w.trim().isEmpty())
      {
        _currentWords.add(w.trim());
      }
    }

    if (_currentWords.isEmpty())
    {
      return;
    }

    if (_binZone != null)
    {
      _binZone.animate().cancel();
      _binZone.animate().setListener(null);
      _binZone.setVisibility(View.INVISIBLE);
      _binZone.setAlpha(0f);
      _binZone.setScaleX(0.8f);
      _binZone.setScaleY(0.8f);
    }

    resolveAndApplyTheme();
    rebuildGrid();

    DisplayMetrics dm = _context.getResources().getDisplayMetrics();
    float density = dm.density;

    int screenWidth = dm.widthPixels;
    int popupWidth = screenWidth - (int)(16 * density);
    if (anchorView.getWidth() > 0)
    {
      popupWidth = Math.min(popupWidth, anchorView.getWidth() - (int)(8 * density));
    }

    int count = _currentWords.size();
    int rows = (int) Math.ceil(count / 3.0);
    int maxScrollHeight = (int)(155 * density); // Display ~3.8 rows, compact & scrollable
    LinearLayout.LayoutParams slp = (LinearLayout.LayoutParams) _scrollView.getLayoutParams();
    if (rows > 4)
    {
      slp.height = maxScrollHeight;
    }
    else
    {
      slp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
    }
    _scrollView.setLayoutParams(slp);

    _rootLayout.measure(
        View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

    int measuredHeight = _rootLayout.getMeasuredHeight();

    _popup.setWidth(popupWidth);
    _popup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);

    int[] anchorLocation = new int[2];
    anchorView.getLocationOnScreen(anchorLocation);

    int x = anchorLocation[0] + (anchorView.getWidth() - popupWidth) / 2;
    int y = anchorLocation[1] - measuredHeight - (int)(6 * density);
    int topSafeMargin = (int)(28 * density);
    if (y < topSafeMargin)
    {
      y = topSafeMargin;
    }

    if (_popup.isShowing())
    {
      _popup.update(x, y, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    else
    {
      _popup.showAtLocation(anchorView, Gravity.NO_GRAVITY, x, y);
    }
  }

  private void rebuildGrid()
  {
    _gridContainer.removeAllViews();
    DisplayMetrics dm = _context.getResources().getDisplayMetrics();
    float density = dm.density;
    int rowHeight = (int)(38 * density);

    int count = _currentWords.size();
    int rows = (int) Math.ceil(count / 3.0);
    int maxScrollHeight = (int)(155 * density);
    LinearLayout.LayoutParams slp = (LinearLayout.LayoutParams) _scrollView.getLayoutParams();
    if (slp != null)
    {
      if (rows > 4)
      {
        slp.height = maxScrollHeight;
      }
      else
      {
        slp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
      }
      _scrollView.setLayoutParams(slp);
    }

    for (int r = 0; r < rows; r++)
    {
      LinearLayout rowLayout = new LinearLayout(_context);
      rowLayout.setOrientation(LinearLayout.HORIZONTAL);
      LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, rowHeight);
      rowLayout.setLayoutParams(rlp);

      for (int c = 0; c < 3; c++)
      {
        int index = r * 3 + c;
        if (index < count)
        {
          final String word = _currentWords.get(index);
          TextView cell = new TextView(_context);
          LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
          cell.setLayoutParams(clp);
          cell.setGravity(Gravity.CENTER);
          cell.setTextColor(_themeColorLabel);
          cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
          cell.setMaxLines(1);
          cell.setText(word);

          // Use theme's colorKeyActivated for cell touch ripple
          GradientDrawable mask = new GradientDrawable();
          mask.setShape(GradientDrawable.RECTANGLE);
          mask.setCornerRadius(6 * density);
          mask.setColor(Color.WHITE);
          int rippleAlpha = (_themeColorKeyActivated >>> 24);
          int rippleColor = (rippleAlpha == 0 || rippleAlpha == 255)
              ? (_isDarkTheme ? Color.parseColor("#26FFFFFF") : Color.parseColor("#1F000000"))
              : _themeColorKeyActivated;
          RippleDrawable ripple = new RippleDrawable(
              ColorStateList.valueOf(rippleColor),
              null,
              mask);
          cell.setBackground(ripple);

          attachTouchListener(cell, word);
          rowLayout.addView(cell);
        }
        else
        {
          View empty = new View(_context);
          empty.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
          rowLayout.addView(empty);
        }
      }

      _gridContainer.addView(rowLayout);
    }

    if (_popup != null && _popup.isShowing())
    {
      _popup.update();
    }
  }

  private void attachTouchListener(final TextView cell, final String word)
  {
    final Handler handler = new Handler(Looper.getMainLooper());
    final int touchSlop = ViewConfiguration.get(_context).getScaledTouchSlop();
    final float[] downCoords = new float[2];
    final boolean[] isLongPressActive = new boolean[]{false};

    final Runnable longPressRunnable = new Runnable()
    {
      @Override
      public void run()
      {
        isLongPressActive[0] = true;
        cell.setPressed(false);

        boolean isLearned = _engine.is_learned_word(word);
        if (!isLearned)
        {
          try
          {
            VibratorCompat.vibrate(cell, Config.globalConfig());
          }
          catch (Exception ignored) {}
          Toast.makeText(_context, "Built-in dictionary words cannot be deleted", Toast.LENGTH_SHORT).show();
          return;
        }

        // Start Drag-to-Delete mode
        _isDragging = true;
        _draggedWord = word;
        _draggedSourceView = cell;

        try
        {
          VibratorCompat.vibrate(cell, Config.globalConfig());
        }
        catch (Exception ignored) {}

        if (_scrollView != null)
        {
          _scrollView.requestDisallowInterceptTouchEvent(true);
        }

        cell.setVisibility(View.INVISIBLE);

        // Reveal the Red Trash Bin at top (Image 2)
        _binZone.setVisibility(View.VISIBLE);
        _binZone.animate().cancel();
        _binZone.animate()
            .setListener(null)
            .alpha(1f)
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(160)
            .setInterpolator(new DecelerateInterpolator())
            .start();

        // Exact 1:1 dimension matching of touched cell
        int cellW = cell.getWidth();
        int cellH = cell.getHeight();
        FrameLayout.LayoutParams chipLp = new FrameLayout.LayoutParams(cellW, cellH);
        _floatingChip.setLayoutParams(chipLp);
        _floatingChip.setText(word);
        _floatingChip.setVisibility(View.VISIBLE);
        _floatingChip.setAlpha(0.95f);
        _floatingChip.setScaleX(1.06f);
        _floatingChip.setScaleY(1.06f);

        int[] cellLoc = new int[2];
        cell.getLocationOnScreen(cellLoc);
        int[] rootLoc = new int[2];
        _rootLayout.getLocationOnScreen(rootLoc);
        _dragStartX = cellLoc[0] - rootLoc[0];
        _dragStartY = cellLoc[1] - rootLoc[1];

        _floatingChip.setTranslationX(_dragStartX);
        _floatingChip.setTranslationY(_dragStartY);
      }
    };

    cell.setOnTouchListener(new View.OnTouchListener()
    {
      @Override
      public boolean onTouch(View v, MotionEvent event)
      {
        float rawX = event.getRawX();
        float rawY = event.getRawY();

        switch (event.getActionMasked())
        {
          case MotionEvent.ACTION_DOWN:
            isLongPressActive[0] = false;
            downCoords[0] = rawX;
            downCoords[1] = rawY;
            cell.setPressed(true);
            handler.postDelayed(longPressRunnable, 240);
            return true;

          case MotionEvent.ACTION_MOVE:
            if (!isLongPressActive[0])
            {
              float dx = Math.abs(rawX - downCoords[0]);
              float dy = Math.abs(rawY - downCoords[1]);
              if (dx > touchSlop || dy > touchSlop)
              {
                handler.removeCallbacks(longPressRunnable);
                cell.setPressed(false);
                if (_scrollView != null)
                {
                  _scrollView.onTouchEvent(event);
                }
              }
            }
            else if (_isDragging)
            {
              float deltaX = rawX - downCoords[0];
              float deltaY = rawY - downCoords[1];
              _floatingChip.setTranslationX(_dragStartX + deltaX);
              _floatingChip.setTranslationY(_dragStartY + deltaY);
              checkBinHover(rawX, rawY);
            }
            return true;

          case MotionEvent.ACTION_UP:
            handler.removeCallbacks(longPressRunnable);
            cell.setPressed(false);

            if (_isDragging)
            {
              if (_isBinHovered && _draggedWord != null)
              {
                executeDelete(_draggedWord);
              }
              else
              {
                cancelDrag();
              }
            }
            else if (!isLongPressActive[0])
            {
              onWordSelected(word);
            }
            return true;

          case MotionEvent.ACTION_CANCEL:
            handler.removeCallbacks(longPressRunnable);
            cell.setPressed(false);
            if (_isDragging)
            {
              cancelDrag();
            }
            return true;
        }
        return false;
      }
    });
  }

  private void checkBinHover(float rawX, float rawY)
  {
    if (_binContainer == null || !_binContainer.isAttachedToWindow()) return;
    int[] binLoc = new int[2];
    _binContainer.getLocationOnScreen(binLoc);

    float density = _context.getResources().getDisplayMetrics().density;
    int slop = (int)(28 * density);
    Rect hitRect = new Rect(
        binLoc[0] - slop,
        binLoc[1] - slop,
        binLoc[0] + _binContainer.getWidth() + slop,
        binLoc[1] + _binContainer.getHeight() + slop);

    boolean hovered = hitRect.contains((int)rawX, (int)rawY);
    if (hovered != _isBinHovered)
    {
      _isBinHovered = hovered;
      if (_isBinHovered)
      {
        _binContainer.setBackground(_hoveredBinBg);
        _binContainer.animate().cancel();
        _binContainer.animate()
            .setListener(null)
            .scaleX(1.12f)
            .scaleY(1.12f)
            .setDuration(120)
            .setInterpolator(new DecelerateInterpolator())
            .start();
        try
        {
          VibratorCompat.vibrate(_binContainer, Config.globalConfig());
        }
        catch (Exception ignored) {}
      }
      else
      {
        _binContainer.setBackground(_normalBinBg);
        _binContainer.animate().cancel();
        _binContainer.animate()
            .setListener(null)
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(120)
            .setInterpolator(new DecelerateInterpolator())
            .start();
      }
    }
  }

  private void executeDelete(final String word)
  {
    try
    {
      VibratorCompat.vibrate(_rootLayout, Config.globalConfig());
    }
    catch (Exception ignored) {}

    _engine.delete_learned_word(word);
    Toast.makeText(_context, String.format(Locale.getDefault(), "\"%s\" removed from dictionary", word), Toast.LENGTH_SHORT).show();

    if (_candidatesView != null)
    {
      _candidatesView.on_learned_word_deleted(word);
    }

    _currentWords.remove(word);

    hideBinZone();
    hideFloatingChip();

    if (_currentWords.isEmpty())
    {
      dismiss();
    }
    else
    {
      rebuildGrid();
    }

    resetDragState();
  }

  private void cancelDrag()
  {
    hideBinZone();
    hideFloatingChip();
    if (_draggedSourceView != null)
    {
      _draggedSourceView.setVisibility(View.VISIBLE);
      _draggedSourceView.animate().cancel();
      _draggedSourceView.animate().setListener(null).alpha(1.0f).setDuration(150).start();
    }
    resetDragState();
  }

  private void hideBinZone()
  {
    if (_binZone != null)
    {
      _binZone.animate().cancel();
      _binZone.animate()
          .setListener(null)
          .alpha(0f)
          .scaleX(0.8f)
          .scaleY(0.8f)
          .setDuration(160)
          .setInterpolator(new DecelerateInterpolator())
          .setListener(new AnimatorListenerAdapter()
          {
            @Override
            public void onAnimationEnd(Animator animation)
            {
              if (_binZone != null)
              {
                _binZone.setVisibility(View.INVISIBLE);
                _binZone.animate().setListener(null);
              }
              if (_binContainer != null)
              {
                _binContainer.setBackground(_normalBinBg);
                _binContainer.setScaleX(1.0f);
                _binContainer.setScaleY(1.0f);
              }
            }
          })
          .start();
    }
  }

  private void hideFloatingChip()
  {
    if (_floatingChip != null)
    {
      _floatingChip.setVisibility(View.GONE);
    }
  }

  private void resetDragState()
  {
    _isDragging = false;
    _isBinHovered = false;
    _draggedWord = null;
    _draggedSourceView = null;
    if (_scrollView != null)
    {
      _scrollView.requestDisallowInterceptTouchEvent(false);
    }
    if (_binContainer != null)
    {
      _binContainer.animate().cancel();
      _binContainer.animate().setListener(null);
      _binContainer.setBackground(_normalBinBg);
      _binContainer.setScaleX(1.0f);
      _binContainer.setScaleY(1.0f);
    }
  }

  private void onWordSelected(String word)
  {
    boolean isEmailField = false;
    Config conf = Config.globalConfig();
    if (conf != null && conf.editor_config != null)
    {
      isEmailField = conf.editor_config.is_email_field;
    }
    boolean isEmail = CandidatesView.is_email_string(word);
    String insertion = (isEmail && isEmailField) ? word : (word + " ");

    if (Config.globalConfig() != null && Config.globalConfig().handler != null)
    {
      Config.globalConfig().handler.suggestion_entered(insertion);
    }
    dismiss();
  }
}
