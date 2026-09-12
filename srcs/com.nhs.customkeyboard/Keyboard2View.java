package com.nhs.customkeyboard;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Paint;
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

  public void setPreviewMode(boolean preview)
  {
    _previewMode = preview;
    requestLayout();
    invalidate();
  }

  public void setOnKeyClickListener(OnKeyClickListener listener)
  {
    _keyClickListener = listener;
  }

  public void setHighlightedKey(KeyboardData.Key key)
  {
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
    if (prevLine >= 0)
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
  public boolean onTouch(View v, MotionEvent event)
  {
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
    _subLabelSize = labelBaseSize * _config.sublabelTextSize;
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
        boolean isHighlighted = (_previewMode && _highlightedKey != null &&
            (k == _highlightedKey || (k.sourceLineNumber >= 0 && k.sourceLineNumber == _highlightedKey.sourceLineNumber)));
        Theme.Computed.Key tc_key;
        if (isKeyDown)
          tc_key = _tc.key_activated;
        else if (isActionKey(k))
          tc_key = _tc.key_action;
        else
          switch (k.role)
          {
            case Action: tc_key = _tc.key_action; break;
            case Space_bar: tc_key = _tc.key_space_bar; break;
            case Suggestion: tc_key = _tc.key_suggestion; break;
            default:
            case Normal: tc_key = _tc.key; break;
          }
        drawKeyFrame(canvas, x, y, keyW, keyH, tc_key, isHighlighted);
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

  /** Draw borders and background of the key. */
  void drawKeyFrame(Canvas canvas, float x, float y, float keyW, float keyH,
                    Theme.Computed.Key tc, boolean isHighlighted)
  {
    float r = tc.border_radius > 0 ? tc.border_radius : (_previewMode ? dp(6) : 0);
    float w = tc.border_width;
    float padding = (w > 0 ? w : (_previewMode ? dp(0.5f) : 0)) / 2.f;
    _tmpRect.set(x + padding, y + padding, x + keyW - padding, y + keyH - padding);

    if (_previewMode)
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
      canvas.drawRoundRect(_tmpRect, r, r, tc.bg_paint);
    }

    if (isHighlighted)
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

    if (w > 0.f)
    {
      float overlap = r - r * 0.85f + w; // sin(45°)
      drawBorder(canvas, x, y, x + overlap, y + keyH, tc.border_left_paint, tc);
      drawBorder(canvas, x + keyW - overlap, y, x + keyW, y + keyH, tc.border_right_paint, tc);
      drawBorder(canvas, x, y, x + keyW, y + overlap, tc.border_top_paint, tc);
      drawBorder(canvas, x, y + keyH - overlap, x + keyW, y + keyH, tc.border_bottom_paint, tc);
    }
    else if (_previewMode && !isHighlighted)
    {
      if (_previewBorderPaint == null)
      {
        _previewBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        _previewBorderPaint.setStyle(Paint.Style.STROKE);
        _previewBorderPaint.setStrokeWidth(dp(1));
        boolean isDark = (getContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        _previewBorderPaint.setColor(isDark ? Color.argb(70, 255, 255, 255) : Color.argb(55, 0, 0, 0));
      }
      canvas.drawRoundRect(_tmpRect, r, r, _previewBorderPaint);
    }
  }

  private float dp(float val)
  {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics());
  }

  /** Clip to draw a border at a time. This allows to call [drawRoundRect]
   several time with the same parameters but a different Paint. */
  void drawBorder(Canvas canvas, float clipl, float clipt, float clipr,
                  float clipb, Paint paint, Theme.Computed.Key tc)
  {
    float r = tc.border_radius;
    canvas.save();
    canvas.clipRect(clipl, clipt, clipr, clipb);
    canvas.drawRoundRect(_tmpRect, r, r, paint);
    canvas.restore();
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
    if (isKeyDown)
    {
      int flags = _pointers.getKeyFlags(k);
      if (flags != -1)
      {
        if ((flags & Pointers.FLAG_P_LOCKED) != 0)
          return _theme.lockedColor;
        return _theme.activatedColor;
      }
      return _theme.pressedColor;
    }
    if (k.hasFlagsAny(KeyValue.FLAG_SECONDARY | KeyValue.FLAG_GREYED))
    {
      if (k.hasFlagsAny(KeyValue.FLAG_GREYED))
        return _theme.greyedLabelColor;
      return _theme.secondaryLabelColor;
    }
    if (!sublabel && (key != null && (key.role == KeyboardData.Key.Role.Action || isActionKey(key))))
    {
      return _theme.colorLabelAction;
    }
    return sublabel ? _theme.subLabelColor : _theme.labelColor;
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

    float textSize = scaleTextSize(kv, true);

    Paint p = tc.label_paint(
            kv.hasFlagsAny(KeyValue.FLAG_KEY_FONT),
            labelColor(key, kv, isKeyDown, false),
            textSize);

    String text;

    // Show the current layout's name on the space bar instead of the
    // space glyph. Detected either via the key's declared role
    // (role="space_bar" in XML) or via the resolved KeyValue itself
    // (c="space", i.e. Editing.SPACE_BAR) - the latter covers manual
    // bottom rows that never set role="space_bar" explicitly.
    boolean is_space_bar =
            key.role == KeyboardData.Key.Role.Space_bar
                    || (kv.getKind() == KeyValue.Kind.Editing
                    && kv.getEditing() == KeyValue.Editing.SPACE_BAR);

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
    float subPadding = _config.keyPadding;
    if (v == Vertical.CENTER)
      y += (keyH - p.ascent() - p.descent()) / 2f;
    else
      y += (v == Vertical.TOP) ? subPadding - p.ascent() : keyH - subPadding - p.descent();
    if (a == Paint.Align.CENTER)
      x += keyW / 2f;
    else
      x += (a == Paint.Align.LEFT) ? subPadding : keyW - subPadding;


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
