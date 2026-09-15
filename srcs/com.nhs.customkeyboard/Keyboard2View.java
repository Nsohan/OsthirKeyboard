package com.nhs.customkeyboard;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.inputmethodservice.InputMethodService;
import android.os.Build.VERSION;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.ViewConfiguration;
import java.util.Arrays;
import java.util.List;

public class Keyboard2View extends View
        implements View.OnTouchListener, Pointers.IPointerEventHandler
{
  public interface OnKeyClickListener
  {
    void onKeyClick(KeyboardData.Key key);
  }

  private KeyboardData _keyboard;

  /** The key holding the shift key is used to set shift state from
   autocapitalisation. */
  private KeyboardData.Key _shift_key;

  /** Used to add fake pointers. */
  private KeyboardData.Key _compose_key;

  private Pointers _pointers;

  private Pointers.Modifiers _mods;

  private static int _currentWhat = 0;

  private Config _config;

  private float _keyWidth;
  private float _mainLabelSize;
  private float _subLabelSize;
  private float _marginRight;
  private float _marginLeft;
  private float _marginBottom;
  private int _insets_left = 0;
  private int _insets_right = 0;
  private int _insets_bottom = 0;

  private Theme _theme;
  private Theme.Computed _tc;

  private static RectF _tmpRect = new RectF();
  private final Path _shiftArrowPath = new Path();
  private final RectF _shiftBarRect = new RectF();
  private boolean _previewMode = false;
  private Paint _previewBorderPaint = null;
  private Paint _previewFallbackBgPaint = null;
  private Paint _previewHighlightPaint = null;
  private Paint _previewHighlightBorderPaint = null;
  private OnKeyClickListener _keyClickListener = null;
  private KeyboardData.Key _highlightedKey = null;
  private float _previewDownX = 0f;
  private float _previewDownY = 0f;
  private boolean _previewIsVerticalScroll = false;
  private int _touchSlop = 0;

  private boolean _themePreviewMode = false;
  private android.graphics.Bitmap _customBgBitmap = null;
  private float _customBgDarkness = 0.3f;
  private Paint _customBgDarknessPaint = null;
  private final RectF _tmpShadowRect = new RectF();
  private final RectF _tmpShadowAmbientRect = new RectF();
  private Paint _keyShadowPaint = null;
  private Paint _keyShadowAmbientPaint = null;

  public void setPreviewMode(boolean preview)
  {
    _previewMode = preview;
    requestLayout();
    invalidate();
  }

  public void setThemePreviewMode(boolean themePreview)
  {
    _themePreviewMode = themePreview;
    _previewMode = themePreview;
    if (themePreview)
    {
      _highlightedKey = null;
      setOnTouchListener(null);
      setClickable(false);
      setFocusable(false);
    }
    else
    {
      setOnTouchListener(this);
    }
    requestLayout();
    invalidate();
  }

  public void setCustomBackgroundBitmap(android.graphics.Bitmap bmp, float darkness)
  {
    _customBgBitmap = bmp;
    _customBgDarkness = darkness;
    invalidate();
  }

  public void setOnKeyClickListener(OnKeyClickListener listener)
  {
    _keyClickListener = listener;
  }

  public void setHighlightedKey(KeyboardData.Key key)
  {
    if (_themePreviewMode) return;
    if (_highlightedKey != key)
    {
      _highlightedKey = key;
      invalidate();
    }
  }

  public KeyboardData.Key getHighlightedKey()
  {
    return _highlightedKey;
  }

  public KeyboardData getKeyboard()
  {
    return _keyboard;
  }

  public KeyboardData.Key findKeyByLine(int line)
  {
    if (_keyboard == null || _keyboard.rows == null || line < 0) return null;
    for (KeyboardData.Row r : _keyboard.rows)
      for (KeyboardData.Key k : r.keys)
        if (k.sourceLineNumber == line)
          return k;
    return null;
  }

  enum Vertical
  {
    TOP,
    CENTER,
    BOTTOM
  }

  public Keyboard2View(Context context)
  {
    this(context, null);
  }

  public Keyboard2View(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    _theme = new Theme(getContext(), attrs);
    _config = Config.globalConfig();
    _pointers = new Pointers(this, _config);
    _touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    refresh_navigation_bar(context);
    setOnTouchListener(this);
    int layout_id = (attrs == null) ? 0 :
            attrs.getAttributeResourceValue(null, "layout", 0);
    if (layout_id == 0)
      reset();
    else
      setKeyboard(KeyboardData.load(getResources(), layout_id));
  }

  private Window getParentWindow(Context context)
  {
    if (context instanceof InputMethodService)
      return ((InputMethodService)context).getWindow().getWindow();
    if (context instanceof ContextWrapper)
      return getParentWindow(((ContextWrapper)context).getBaseContext());
    return null;
  }

  public void refresh_navigation_bar(Context context)
  {
    if (VERSION.SDK_INT < 21)
      return;
    // The intermediate Window is a [Dialog].
    Window w = getParentWindow(context);
    if (w != null)
    {
      w.setNavigationBarColor(_theme.colorNavBar);
      if (VERSION.SDK_INT >= 26)
      {
        int uiFlags = getSystemUiVisibility();
        if (_theme.isLightNavBar)
          uiFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        else
          uiFlags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        setSystemUiVisibility(uiFlags);
      }
    }
  }

  public void setKeyboard(KeyboardData kw)
  {
    int prevLine = (_highlightedKey != null) ? _highlightedKey.sourceLineNumber : -1;
    _keyboard = kw;
    _shift_key = _keyboard.findKeyWithValue(KeyValue.SHIFT);
    _compose_key = _keyboard.findKeyWithValue(KeyValue.COMPOSE);
    KeyModifier.set_modmap(_keyboard.modmap);
    reset();
    if (!_themePreviewMode && prevLine >= 0)
      _highlightedKey = findKeyByLine(prevLine);
  }

  public void reset()
  {
    _mods = Pointers.Modifiers.EMPTY;
    _pointers.clear();
    requestLayout();
    invalidate();
  }

  void set_fake_ptr_latched(KeyboardData.Key key, KeyValue kv, boolean latched,
                            boolean lock)
  {
    if (_keyboard == null || key == null)
      return;
    _pointers.set_fake_pointer_state(key, kv, latched, lock);
  }

  /** Called by auto-capitalisation. */
  public void set_shift_state(boolean latched, boolean lock)
  {
    set_fake_ptr_latched(_shift_key, KeyValue.SHIFT, latched, lock);
  }

  /** Called from [KeyEventHandler]. */
  public void set_compose_pending(boolean pending)
  {
    set_fake_ptr_latched(_compose_key, KeyValue.COMPOSE, pending, false);
  }

  /** Called from [Keybard2.onUpdateSelection].  */
  public void set_selection_state(boolean selection_state)
  {
    if (_config.editor_config.selection_mode_enabled)
      set_fake_ptr_latched(KeyboardData.Key.EMPTY,
              KeyValue.SELECTION_MODE, selection_state, true);
  }

  public KeyValue modifyKey(KeyValue k, Pointers.Modifiers mods)
  {
    return KeyModifier.modify(k, mods);
  }

  public void onPointerDown(KeyValue k, boolean isSwipe)
  {
    updateFlags();
    if (_config != null && _config.handler != null)
      _config.handler.key_down(k, isSwipe);
    invalidate();
    vibrate();
  }

  public void onPointerUp(KeyValue k, Pointers.Modifiers mods)
  {
    // [key_up] must be called before [updateFlags]. The latter might disable
    // flags.
    if (_config != null && _config.handler != null)
      _config.handler.key_up(k, mods);
    updateFlags();
    invalidate();
  }

  public void onPointerHold(KeyValue k, Pointers.Modifiers mods)
  {
    if (_config != null && _config.handler != null)
      _config.handler.key_up(k, mods);
    updateFlags();
  }

  public void onPointerFlagsChanged(boolean shouldVibrate)
  {
    updateFlags();
    invalidate();
    if (shouldVibrate)
      vibrate();
  }

  private void updateFlags()
  {
    _mods = _pointers.getModifiers();
    if (_config != null && _config.handler != null)
      _config.handler.mods_changed(_mods);
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent event)
  {
    if (_themePreviewMode)
      return false;
    return super.dispatchTouchEvent(event);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event)
  {
    if (_themePreviewMode)
      return false;
    return super.onTouchEvent(event);
  }

  @Override
  public boolean onTouch(View v, MotionEvent event)
  {
    if (_themePreviewMode)
      return false;
    if (_previewMode)
    {
      switch (event.getActionMasked())
      {
        case MotionEvent.ACTION_DOWN:
        {
          _previewDownX = event.getX();
          _previewDownY = event.getY();
          _previewIsVerticalScroll = false;
          KeyboardData.Key key = getKeyAtPosition(_previewDownX, _previewDownY);
          if (key != null)
          {
            setHighlightedKey(key);
            if (_keyClickListener != null)
              _keyClickListener.onKeyClick(key);
            if (getParent() != null)
              getParent().requestDisallowInterceptTouchEvent(true);
          }
          return true;
        }
        case MotionEvent.ACTION_MOVE:
        {
          if (_previewIsVerticalScroll)
            return false;
          float tx = event.getX();
          float ty = event.getY();
          float dx = tx - _previewDownX;
          float dy = ty - _previewDownY;
          if (Math.abs(dy) > _touchSlop && Math.abs(dy) > Math.abs(dx) * 1.3f)
          {
            _previewIsVerticalScroll = true;
            if (getParent() != null)
              getParent().requestDisallowInterceptTouchEvent(false);
            return false;
          }
          KeyboardData.Key key = getKeyAtPosition(tx, ty);
          if (key != null && key != _highlightedKey)
          {
            setHighlightedKey(key);
            if (_keyClickListener != null)
              _keyClickListener.onKeyClick(key);
          }
          return true;
        }
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
        {
          if (getParent() != null)
            getParent().requestDisallowInterceptTouchEvent(false);
          return true;
        }
      }
      return true;
    }

    int p;
    switch (event.getActionMasked())
    {
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_POINTER_UP:
        _pointers.onTouchUp(event.getPointerId(event.getActionIndex()));
        break;
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_POINTER_DOWN:
        p = event.getActionIndex();
        float tx = event.getX(p);
        float ty = event.getY(p);
        KeyboardData.Key key = getKeyAtPosition(tx, ty);
        if (key != null)
          _pointers.onTouchDown(tx, ty, event.getPointerId(p), key);
        break;
      case MotionEvent.ACTION_MOVE:
        for (p = 0; p < event.getPointerCount(); p++)
          _pointers.onTouchMove(event.getX(p), event.getY(p), event.getPointerId(p));
        break;
      case MotionEvent.ACTION_CANCEL:
        _pointers.onTouchCancel();
        break;
      default:
        return (false);
    }
    return (true);
  }

  private KeyboardData.Row getRowAtPosition(float ty)
  {
    float y = _config.marginTop;
    if (ty < y)
      return null;
    for (KeyboardData.Row row : _keyboard.rows)
    {
      y += (row.shift + row.height) * _tc.row_height;
      if (ty < y)
        return row;
    }
    return null;
  }

  private KeyboardData.Key getKeyAtPosition(float tx, float ty)
  {
    KeyboardData.Row row = getRowAtPosition(ty);
    float x = _marginLeft;
    if (row == null || tx < x)
      return null;
    for (KeyboardData.Key key : row.keys)
    {
      float xLeft = x + key.shift * _keyWidth;
      float xRight = xLeft + key.width * _keyWidth;
      if (tx < xLeft)
        return null;
      if (tx < xRight)
        return key;
      x = xRight;
    }
    return null;
  }

  private void vibrate()
  {
    VibratorCompat.vibrate(this, _config);
  }

  @Override
  public void onMeasure(int wSpec, int hSpec)
  {
    if (_keyboard == null)
    {
      setMeasuredDimension(0, 0);
      return;
    }
    int width = MeasureSpec.getSize(wSpec);
    if (width <= 0)
    {
      DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
      width = dm.widthPixels;
    }
    if (_themePreviewMode)
    {
      _marginLeft = dp(2f);
      _marginRight = dp(2f);
      _marginBottom = 0f;
      _keyWidth = (width - _marginLeft - _marginRight) / _keyboard.keysWidth;
      _tc = new Theme.Computed(_theme, _config, _keyWidth, _keyboard);
      _tc.horizontal_margin = dp(3.5f);
      _tc.vertical_margin = dp(3.5f);
      _tc.margin_left = dp(1f);
      _tc.margin_top = dp(2f);
      _tc.row_height = Math.round(_keyWidth * 1.25f);
      float labelBaseSize = Math.min(
              _tc.row_height - _tc.vertical_margin,
              (width / 10 - _tc.horizontal_margin) * 3/2
      ) * _config.characterSize;
      _mainLabelSize = labelBaseSize * _config.labelTextSize;
      _subLabelSize = labelBaseSize * _config.sublabelTextSize * _config.secondaryCharacterSize;
      int height = (int)(_tc.row_height * _keyboard.keysHeight + _tc.margin_top + dp(2f));
      setMeasuredDimension(width, height);
      return;
    }

    _marginLeft = Math.max(_config.horizontal_margin, _insets_left);
    _marginRight = Math.max(_config.horizontal_margin, _insets_right);
    _marginBottom = _config.margin_bottom + _insets_bottom;
    _keyWidth = (width - _marginLeft - _marginRight) / _keyboard.keysWidth;
    _tc = new Theme.Computed(_theme, _config, _keyWidth, _keyboard);

    if (_previewMode)
    {
      _tc.horizontal_margin = Math.max(dp(3.5f), _tc.horizontal_margin);
      _tc.vertical_margin = Math.max(dp(4f), _tc.vertical_margin);
      _tc.margin_left = _tc.horizontal_margin / 2;
      _tc.margin_top = _config.marginTop + _tc.vertical_margin / 2;
    }
    // Compute the size of labels based on the width or the height of keys. The
    // margin around keys is taken into account. Keys normal aspect ratio is
    // assumed to be 3/2 for a 10 columns layout. It's generally more, the
    // width computation is useful when the keyboard is unusually high.
    float labelBaseSize = Math.min(
            _tc.row_height - _tc.vertical_margin,
            (width / 10 - _tc.horizontal_margin) * 3/2
    ) * _config.characterSize;
    _mainLabelSize = labelBaseSize * _config.labelTextSize;
    _subLabelSize = labelBaseSize * _config.sublabelTextSize * _config.secondaryCharacterSize;
    int height =
            (int)(_tc.row_height * _keyboard.keysHeight
                    + _config.marginTop + _marginBottom);
    setMeasuredDimension(width, height);
  }

  public float getMainLabelSize()
  {
    return _mainLabelSize;
  }

  public float getMarginBottom()
  {
    return _marginBottom;
  }

  public int getKeyboardLayoutHeight()
  {
    if (getHeight() > 0)
    {
      return getHeight();
    }
    if (getMeasuredHeight() > 0)
    {
      return getMeasuredHeight();
    }
    if (_tc != null && _keyboard != null && _config != null)
    {
      return (int)(_tc.row_height * _keyboard.keysHeight + _config.marginTop + _marginBottom);
    }
    return 0;
  }

  Rect _cached_exclusion_rect = new Rect();
  List<Rect> _cached_exclusion_rects = Arrays.asList(_cached_exclusion_rect);
  @Override
  public void onLayout(boolean changed, int left, int top, int right, int bottom)
  {
    if (!changed)
      return;
    // Since SDK 30, this is done automatically:
    // https://android.googlesource.com/platform/frameworks/base/+/android11-release/core/java/android/inputmethodservice/InputMethodService.java#852
    if (VERSION.SDK_INT == 29)
    {
      // Disable the back-gesture on the keyboard area
      _cached_exclusion_rect.set(
              left + (int)_marginLeft,
              top + (int)_config.marginTop,
              right - (int)_marginRight,
              bottom - (int)_marginBottom);
      setSystemGestureExclusionRects(_cached_exclusion_rects);
    }
  }

  @Override
  public WindowInsets onApplyWindowInsets(WindowInsets wi)
  {
    // LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS is set in [Keyboard2#updateSoftInputWindowLayoutParams] for SDK_INT >= 35.
    if (VERSION.SDK_INT < 35)
      return wi;
    int insets_types =
            WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout();
    Insets insets = wi.getInsets(insets_types);
    _insets_left = insets.left;
    _insets_right = insets.right;
    _insets_bottom = insets.bottom;
    return WindowInsets.CONSUMED;
  }

  /** Horizontal and vertical position of the 9 indexes. */
  static final Paint.Align[] LABEL_POSITION_H = new Paint.Align[]{
          Paint.Align.CENTER, Paint.Align.LEFT, Paint.Align.RIGHT, Paint.Align.LEFT,
          Paint.Align.RIGHT, Paint.Align.LEFT, Paint.Align.RIGHT,
          Paint.Align.CENTER, Paint.Align.CENTER
  };

  static final Vertical[] LABEL_POSITION_V = new Vertical[]{
          Vertical.CENTER, Vertical.TOP, Vertical.TOP, Vertical.BOTTOM,
          Vertical.BOTTOM, Vertical.CENTER, Vertical.CENTER, Vertical.TOP,
          Vertical.BOTTOM
  };

  @Override
  protected void onDraw(Canvas canvas)
  {
    if (_keyboard == null || _tc == null)
      return;
    if (_customBgBitmap != null && !_customBgBitmap.isRecycled())
    {
      _tmpRect.set(0, 0, getWidth(), getHeight());
      canvas.drawBitmap(_customBgBitmap, null, _tmpRect, null);
      if (_customBgDarkness > 0f)
      {
        if (_customBgDarknessPaint == null)
        {
          _customBgDarknessPaint = new Paint();
          _customBgDarknessPaint.setStyle(Paint.Style.FILL);
          _customBgDarknessPaint.setColor(Color.BLACK);
        }
        _customBgDarknessPaint.setAlpha((int)(Math.min(0.9f, Math.max(0f, _customBgDarkness)) * 255));
        canvas.drawRect(0, 0, getWidth(), getHeight(), _customBgDarknessPaint);
      }
    }
    float y = _tc.margin_top;
    for (KeyboardData.Row row : _keyboard.rows)
    {
      y += row.shift * _tc.row_height;
      float x = _marginLeft + _tc.margin_left;
      float keyH = row.height * _tc.row_height - _tc.vertical_margin;
      for (KeyboardData.Key k : row.keys)
      {
        x += k.shift * _keyWidth;
        float keyW = _keyWidth * k.width - _tc.horizontal_margin;
        boolean isKeyDown = _pointers.isKeyDown(k);
        boolean isHighlighted = (!_themePreviewMode && _previewMode && _highlightedKey != null &&
            (k == _highlightedKey || (k.sourceLineNumber >= 0 && k.sourceLineNumber == _highlightedKey.sourceLineNumber)));
        Theme.Computed.Key tc_key;
        boolean isAction = isActionKey(k) || (k != null && k.role == KeyboardData.Key.Role.Action);
        if (isKeyDown)
          tc_key = isAction ? _tc.key_action_activated : _tc.key_activated;
        else if (isAction)
          tc_key = _tc.key_action;
        else
          switch (k.role)
          {
            case Space_bar: tc_key = _tc.key_space_bar; break;
            case Suggestion: tc_key = _tc.key_suggestion; break;
            default:
            case Normal: tc_key = _tc.key; break;
          }
        drawKeyFrame(canvas, x, y, keyW, keyH, tc_key, isHighlighted, isKeyDown);
        if (k.keys[0] != null)
          drawLabel(canvas, k, k.keys[0], keyW / 2f + x, y, keyH, isKeyDown, tc_key);
        for (int i = 1; i < 9; i++)
        {
          if (k.keys[i] != null)
            drawSubLabel(canvas, k, k.keys[i], x, y, keyW, keyH, i, isKeyDown, tc_key);
        }
        if (k.role == KeyboardData.Key.Role.Normal && k.longPressKey != null && k.keys[7] == null && k.keys[1] == null && k.keys[2] == null)
        {
          drawSubLabel(canvas, k, k.longPressKey, x, y, keyW, keyH, 7, isKeyDown, tc_key);
        }
        drawIndication(canvas, k, x, y, keyW, keyH, _tc);
        x += _keyWidth * k.width;
      }
      y += row.height * _tc.row_height;
    }
  }

  @Override
  public void onDetachedFromWindow()
  {
    super.onDetachedFromWindow();
  }

  /** Draw bottom drop shadow and background of the key button. */
  void drawKeyFrame(Canvas canvas, float x, float y, float keyW, float keyH,
                    Theme.Computed.Key tc, boolean isHighlighted, boolean isKeyDown)
  {
    float r = tc.border_radius > 0 ? tc.border_radius : (_previewMode ? dp(6) : 0);
    _tmpRect.set(x, y, x + keyW, y + keyH);

    boolean hasBg = (tc.bg_paint.getColor() != 0 && Color.alpha(tc.bg_paint.getColor()) > 0);
    float shadow = (_config != null) ? _config.keyShadow : dp(3.0f);

    // Render soft drop shadow elevation under key buttons matching exact Figma spec:
    // Layer 1: X: 0, Y: 1px, Blur: 1px, Color: #46301E (12%)
    // Layer 2: X: 0, Y: 1.5px, Blur: 2px, Color: #46301E (8%)
    if (hasBg && shadow > 0f)
    {
      float factor = (shadow / dp(3.0f)) * (isKeyDown ? 0.3f : 1.0f);
      if (factor > 0.05f)
      {
        boolean isDark = (getContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int sr = isDark ? 0 : 70;
        int sg = isDark ? 0 : 48;
        int sb = isDark ? 0 : 30;

        if (_keyShadowPaint == null)
        {
          _keyShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
          _keyShadowPaint.setStyle(Paint.Style.FILL);
        }

        // Layer 2: Y: 1.5px, Blur: 2px, rgba(70, 48, 30, 0.08)
        float dy2 = dp(1.5f) * factor;
        float blur2 = Math.max(0.1f, dp(2.0f) * factor);
        int a2 = Math.max(1, (int)(255 * (isDark ? 0.16f : 0.08f) * Math.min(1.5f, factor)));
        int c2 = Color.argb(a2, sr, sg, sb);
        _keyShadowPaint.setColor(c2);
        _keyShadowPaint.setShadowLayer(blur2, 0f, dy2, c2);
        _tmpShadowRect.set(x, y + dy2, x + keyW, y + keyH + dy2);
        canvas.drawRoundRect(_tmpShadowRect, r, r, _keyShadowPaint);

        // Layer 1: Y: 1px, Blur: 1px, rgba(70, 48, 30, 0.12)
        float dy1 = dp(1.0f) * factor;
        float blur1 = Math.max(0.1f, dp(1.0f) * factor);
        int a1 = Math.max(1, (int)(255 * (isDark ? 0.22f : 0.12f) * Math.min(1.5f, factor)));
        int c1 = Color.argb(a1, sr, sg, sb);
        _keyShadowPaint.setColor(c1);
        _keyShadowPaint.setShadowLayer(blur1, 0f, dy1, c1);
        _tmpShadowRect.set(x, y + dy1, x + keyW, y + keyH + dy1);
        canvas.drawRoundRect(_tmpShadowRect, r, r, _keyShadowPaint);
      }
    }

    if (_previewMode && !_themePreviewMode)
    {
      if (_previewFallbackBgPaint == null)
      {
        _previewFallbackBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        _previewFallbackBgPaint.setStyle(Paint.Style.FILL);
        boolean isDark = (getContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        _previewFallbackBgPaint.setColor(isDark ? Color.argb(45, 255, 255, 255) : Color.argb(20, 0, 0, 0));
      }
      if (tc.bg_paint.getColor() == 0 || Color.alpha(tc.bg_paint.getColor()) < 15)
        canvas.drawRoundRect(_tmpRect, r, r, _previewFallbackBgPaint);
      else
        canvas.drawRoundRect(_tmpRect, r, r, tc.bg_paint);
    }
    else
    {
      if (hasBg)
        canvas.drawRoundRect(_tmpRect, r, r, tc.bg_paint);
    }

    if (!_themePreviewMode && isHighlighted)
    {
      if (_previewHighlightPaint == null)
      {
        _previewHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        _previewHighlightPaint.setStyle(Paint.Style.FILL);
        _previewHighlightPaint.setColor(Color.argb(85, 56, 189, 248));
      }
      if (_previewHighlightBorderPaint == null)
      {
        _previewHighlightBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        _previewHighlightBorderPaint.setStyle(Paint.Style.STROKE);
        _previewHighlightBorderPaint.setStrokeWidth(dp(2.2f));
        _previewHighlightBorderPaint.setColor(Color.rgb(56, 189, 248));
      }
      canvas.drawRoundRect(_tmpRect, r, r, _previewHighlightPaint);
      canvas.drawRoundRect(_tmpRect, r, r, _previewHighlightBorderPaint);
    }
  }

  private float dp(float val)
  {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics());
  }

  public boolean isActionKey(KeyboardData.Key k)
  {
    if (k == null)
      return false;
    if (k.role == KeyboardData.Key.Role.Action)
      return true;
    if (k.role == KeyboardData.Key.Role.Space_bar)
      return false;
    if (k.keys != null && k.keys.length > 0 && k.keys[0] != null)
    {
      KeyValue kv = k.keys[0];
      if (kv == KeyValue.SHIFT || kv == KeyValue.ENTER)
        return true;
      KeyValue.Kind kind = kv.getKind();
      if (kind == KeyValue.Kind.Modifier || kind == KeyValue.Kind.Event)
        return true;
      if (kind == KeyValue.Kind.Editing)
      {
        KeyValue.Editing ed = kv.getEditing();
        if (ed == KeyValue.Editing.BACKSPACE || ed == KeyValue.Editing.DELETE_WORD || ed == KeyValue.Editing.FORWARD_DELETE_WORD)
          return true;
      }
    }
    return false;
  }

  private int labelColor(KeyboardData.Key key, KeyValue k, boolean isKeyDown, boolean sublabel)
  {
    boolean isAction = (key != null && (key.role == KeyboardData.Key.Role.Action || isActionKey(key)));
    if (isKeyDown)
    {
      int flags = _pointers.getKeyFlags(k);
      if (flags != -1)
      {
        if ((flags & Pointers.FLAG_P_LOCKED) != 0)
          return (isAction && _theme.lockedColor == 0) ? _theme.colorLabelAction : _theme.lockedColor;
        return (isAction && _theme.activatedColor == 0) ? _theme.colorLabelAction : _theme.activatedColor;
      }
      return isAction ? _theme.colorLabelAction : _theme.pressedColor;
    }
    if (k.hasFlagsAny(KeyValue.FLAG_GREYED))
    {
      return isAction ? _theme.greyedLabelActionColor : _theme.greyedLabelColor;
    }
    if (isAction)
    {
      if (sublabel)
        return _theme.subLabelActionColor;
      return _theme.colorLabelAction;
    }
    if (k.hasFlagsAny(KeyValue.FLAG_SECONDARY))
    {
      return _theme.secondaryLabelColor;
    }
    return sublabel ? _theme.subLabelColor : _theme.labelColor;
  }

  private boolean isShiftKey(KeyboardData.Key key, KeyValue kv)
  {
    if (key != null && key == _shift_key)
      return true;
    if (kv != null)
    {
      if (kv == KeyValue.SHIFT)
        return true;
      if (kv.getKind() == KeyValue.Kind.Modifier && kv.getModifier() == KeyValue.Modifier.SHIFT)
        return true;
    }
    if (key != null && key.keys != null && key.keys.length > 0 && key.keys[0] != null)
    {
      KeyValue k0 = key.keys[0];
      if (k0 == KeyValue.SHIFT || (k0.getKind() == KeyValue.Kind.Modifier && k0.getModifier() == KeyValue.Modifier.SHIFT))
        return true;
    }
    return false;
  }

  private void drawShiftIcon(Canvas canvas,
                             float cx,
                             float cy,
                             float keyH,
                             int state,
                             Paint paint)
  {
    float maxIconH = keyH * 0.44f;
    float scale = Math.min(1.0f, maxIconH / dp(16f));
    float w = dp(17f) * scale;
    float h = dp(15.5f) * scale;
    float headH = dp(8.5f) * scale;
    float stemW = dp(7.2f) * scale;
    float strokeW = Math.max(dp(1.5f), dp(1.8f) * scale);

    float barH = Math.max(dp(1.8f), dp(2.2f) * scale);
    float barW = dp(16f) * scale;
    float barGap = Math.max(dp(2.0f), dp(2.5f) * scale);

    float arrowCenterY = cy;
    if (state == 2)
    {
      arrowCenterY -= (barGap + barH) / 2f;
    }

    float top = arrowCenterY - h / 2f;
    float bottom = arrowCenterY + h / 2f;
    float wingY = top + headH;
    float leftWingX = cx - w / 2f;
    float rightWingX = cx + w / 2f;
    float leftStemX = cx - stemW / 2f;
    float rightStemX = cx + stemW / 2f;

    _shiftArrowPath.reset();
    _shiftArrowPath.moveTo(cx, top);
    _shiftArrowPath.lineTo(rightWingX, wingY);
    _shiftArrowPath.lineTo(rightStemX, wingY);
    _shiftArrowPath.lineTo(rightStemX, bottom);
    _shiftArrowPath.lineTo(leftStemX, bottom);
    _shiftArrowPath.lineTo(leftStemX, wingY);
    _shiftArrowPath.lineTo(leftWingX, wingY);
    _shiftArrowPath.close();

    Paint.Style origStyle = paint.getStyle();
    float origStrokeWidth = paint.getStrokeWidth();
    Paint.Join origJoin = paint.getStrokeJoin();
    Paint.Cap origCap = paint.getStrokeCap();

    try
    {
      paint.setStrokeJoin(Paint.Join.ROUND);
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setStrokeWidth(strokeW);

      if (state == 0)
      {
        // Shift OFF: hollow / outline arrow
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(_shiftArrowPath, paint);
      }
      else
      {
        // Shift ON (state == 1) or Caps Lock (state == 2): solid filled arrow
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        canvas.drawPath(_shiftArrowPath, paint);

        if (state == 2)
        {
          // Caps Lock: underline bar below arrow
          paint.setStyle(Paint.Style.FILL);
          float barTop = bottom + barGap;
          float barBottom = barTop + barH;
          float barLeft = cx - barW / 2f;
          float barRight = cx + barW / 2f;
          float barR = barH / 2f;
          _shiftBarRect.set(barLeft, barTop, barRight, barBottom);
          canvas.drawRoundRect(_shiftBarRect, barR, barR, paint);
        }
      }
    }
    finally
    {
      paint.setStyle(origStyle);
      paint.setStrokeWidth(origStrokeWidth);
      paint.setStrokeJoin(origJoin);
      paint.setStrokeCap(origCap);
    }
  }

  private void drawLabel(Canvas canvas,
                         KeyboardData.Key key,
                         KeyValue kv,
                         float x,
                         float y,
                         float keyH,
                         boolean isKeyDown,
                         Theme.Computed.Key tc)
  {
    kv = modifyKey(kv, _mods);
    if (kv == null)
      return;

    boolean is_space_bar =
            key.role == KeyboardData.Key.Role.Space_bar
                    || (kv.getKind() == KeyValue.Kind.Editing
                    && kv.getEditing() == KeyValue.Editing.SPACE_BAR);

    float textSize = scaleTextSize(kv, true);
    if (is_space_bar)
      textSize /= 1.3f;

    Paint p = tc.label_paint(
            kv.hasFlagsAny(KeyValue.FLAG_KEY_FONT),
            labelColor(key, kv, isKeyDown, false),
            textSize);

    boolean hasCustomLabel = (key.keyLabels != null
            && key.keyLabels[_mods.has(KeyValue.Modifier.SHIFT) ? 9 : 0] != null
            && !key.keyLabels[_mods.has(KeyValue.Modifier.SHIFT) ? 9 : 0].isEmpty());

    if (isShiftKey(key, kv) && !hasCustomLabel)
    {
      int shiftState = 0;
      if (_pointers.isShiftLocked(key))
        shiftState = 2;
      else if (_pointers.isShiftLatched(key) || _mods.has(KeyValue.Modifier.SHIFT) || isKeyDown)
        shiftState = 1;

      drawShiftIcon(canvas, x, (keyH / 2f) + y, keyH, shiftState, p);
      return;
    }

    String text;

    if (is_space_bar
            && _keyboard.name != null
            && !_keyboard.name.isEmpty())
    {
      text = _keyboard.name;
    }
    else
    {
      int labelIndex = _mods.has(KeyValue.Modifier.SHIFT) ? 9 : 0;

      if (key.keyLabels != null
              && key.keyLabels[labelIndex] != null
              && !key.keyLabels[labelIndex].isEmpty())
      {
        text = key.keyLabels[labelIndex];
      }
      else
      {
        KeyValue shown = key.getOutputValue(0, _mods.has(KeyValue.Modifier.SHIFT));
        if (shown != null)
        {
          if (_mods.has(KeyValue.Modifier.SHIFT)
                  && (key.keyOutputs == null || key.keyOutputs[0] == null)
                  && shown.getKind() == KeyValue.Kind.Char)
          {
            text = String.valueOf(Character.toUpperCase(shown.getChar()));
          }
          else
          {
            text = shown.getString();
          }
        }
        else
        {
          text = kv.getString();
        }
      }
    }

    canvas.drawText(
            text,
            x,
            (keyH - p.ascent() - p.descent()) / 2f + y,
            p);
  }

  private void drawSubLabel(Canvas canvas,
                            KeyboardData.Key key,
                            KeyValue kv,
                            float x,
                            float y,
                            float keyW,
                            float keyH,
                            int sub_index,
                            boolean isKeyDown,
                            Theme.Computed.Key tc)
  {
    Paint.Align a = LABEL_POSITION_H[sub_index];
    Vertical v = LABEL_POSITION_V[sub_index];
    kv = modifyKey(kv, _mods);
    if (kv == null)
      return;
    float textSize = scaleTextSize(kv, false);
    Paint p = tc.sublabel_paint(kv.hasFlagsAny(KeyValue.FLAG_KEY_FONT), labelColor(key, kv, isKeyDown, true), textSize, a);
    float r = (tc != null && tc.border_radius > 0) ? tc.border_radius : (_previewMode ? dp(6) : 0f);
    float basePad = Math.max(dp(2.8f), _config.keyPadding * 1.4f);
    boolean isCorner = (a != Paint.Align.CENTER && v != Vertical.CENTER);
    float padH = basePad + (isCorner ? r * 0.12f : 0f);
    float padV = basePad + (isCorner ? r * 0.12f : (v != Vertical.CENTER ? r * 0.05f : 0f));

    if (v == Vertical.CENTER)
      y += (keyH - p.ascent() - p.descent()) / 2f;
    else
      y += (v == Vertical.TOP) ? padV - p.ascent() : keyH - padV - p.descent();

    if (a == Paint.Align.CENTER)
      x += keyW / 2f;
    else
      x += (a == Paint.Align.LEFT) ? padH : keyW - padH;


    String label;
    int labelIndex = sub_index + (_mods.has(KeyValue.Modifier.SHIFT) ? 9 : 0);

    if (key.keyLabels != null
            && key.keyLabels[labelIndex] != null
            && !key.keyLabels[labelIndex].isEmpty())
    {
      label = key.keyLabels[labelIndex];
    }
    else
    {
      KeyValue shown = key.getOutputValue(sub_index, _mods.has(KeyValue.Modifier.SHIFT));
      if (shown != null)
      {
        if (_mods.has(KeyValue.Modifier.SHIFT)
                && (key.keyOutputs == null || key.keyOutputs[sub_index] == null)
                && shown.getKind() == KeyValue.Kind.Char)
        {
          label = String.valueOf(Character.toUpperCase(shown.getChar()));
        }
        else
        {
          label = shown.getString();
        }
      }
      else
      {
        label = kv.getString();
      }
    }




    int label_len = label.length();
    // Limit the label of string keys to 3 characters
    if (label_len > 3 && kv.getKind() == KeyValue.Kind.String)
      label_len = 3;
    canvas.drawText(label, 0, label_len, x, y, p);
  }

  private void drawIndication(Canvas canvas, KeyboardData.Key k, float x,
                              float y, float keyW, float keyH, Theme.Computed tc)
  {
    if (k.indication == null || k.indication.equals(""))
      return;
    Paint p = tc.indication_paint;
    p.setTextSize(_subLabelSize);
    boolean isAction = isActionKey(k) || (k != null && k.role == KeyboardData.Key.Role.Action);
    p.setColor(isAction ? _theme.subLabelActionColor : _theme.subLabelColor);
    canvas.drawText(k.indication, 0, k.indication.length(),
            x + keyW / 2f, (keyH - p.ascent() - p.descent()) * 4/5 + y, p);
  }

  private float scaleTextSize(KeyValue k, boolean main_label)
  {
    float smaller_font = k.hasFlagsAny(KeyValue.FLAG_SMALLER_FONT) ? 0.75f : 1.f;
    float label_size = main_label ? _mainLabelSize : _subLabelSize;
    return label_size * smaller_font;
  }
}
