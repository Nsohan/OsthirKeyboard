package com.nhs.customkeyboard;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

public class TextEditManagerView extends LinearLayout
{
  private Keyboard2 _keyboardService;
  private boolean _isSelectMode = false;
  private int _targetHeight = 0;

  private ImageButton _btnBack;
  private View _btnLeft;
  private View _btnRight;
  private View _btnUp;
  private View _btnDown;
  private FrameLayout _btnSelect;
  private TextView _tvSelect;
  private View _btnSelectAll;
  private View _btnCopy;
  private View _btnCut;
  private View _btnPaste;
  private View _btnStart;
  private View _btnEnd;
  private View _btnBackspace;
  private View _contentContainer;

  private final Handler _repeatHandler = new Handler(Looper.getMainLooper());
  private Runnable _repeatRunnable = null;

  public TextEditManagerView(Context context)
  {
    super(context);
  }

  public TextEditManagerView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
  }

  public TextEditManagerView(Context context, AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
  }

  public void setKeyboardService(Keyboard2 service)
  {
    _keyboardService = service;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
    int targetH = _targetHeight;
    if (targetH <= 0 && _keyboardService != null)
    {
      targetH = _keyboardService.get_keyboard_height();
    }
    if (targetH <= 0)
    {
      android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
      targetH = (int) (290 * dm.density);
    }
    int exactHeightSpec = MeasureSpec.makeMeasureSpec(targetH, MeasureSpec.EXACTLY);
    super.onMeasure(widthMeasureSpec, exactHeightSpec);
  }

  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();

    _btnBack = findViewById(R.id.text_edit_btn_back);
    _btnLeft = findViewById(R.id.text_edit_btn_left);
    _btnRight = findViewById(R.id.text_edit_btn_right);
    _btnUp = findViewById(R.id.text_edit_btn_up);
    _btnDown = findViewById(R.id.text_edit_btn_down);
    _btnSelect = findViewById(R.id.text_edit_btn_select);
    _tvSelect = findViewById(R.id.text_edit_tv_select);
    _btnSelectAll = findViewById(R.id.text_edit_btn_select_all);
    _btnCopy = findViewById(R.id.text_edit_btn_copy);
    _btnCut = findViewById(R.id.text_edit_btn_cut);
    _btnPaste = findViewById(R.id.text_edit_btn_paste);
    _btnStart = findViewById(R.id.text_edit_btn_start);
    _btnEnd = findViewById(R.id.text_edit_btn_end);
    _btnBackspace = findViewById(R.id.text_edit_btn_backspace);
    _contentContainer = findViewById(R.id.text_edit_content_container);

    if (_btnBack != null)
    {
      _btnBack.setOnClickListener(v -> {
        vibrate();
        if (_keyboardService != null)
        {
          _keyboardService.close_text_edit();
        }
      });
    }

    setupRepeatingKey(_btnLeft, () -> sendDpadKey(KeyEvent.KEYCODE_DPAD_LEFT));
    setupRepeatingKey(_btnRight, () -> sendDpadKey(KeyEvent.KEYCODE_DPAD_RIGHT));
    setupRepeatingKey(_btnUp, () -> sendDpadKey(KeyEvent.KEYCODE_DPAD_UP));
    setupRepeatingKey(_btnDown, () -> sendDpadKey(KeyEvent.KEYCODE_DPAD_DOWN));
    setupRepeatingKey(_btnBackspace, this::sendBackspace);

    if (_btnSelect != null)
    {
      _btnSelect.setOnClickListener(v -> toggleSelectMode());
    }

    if (_btnSelectAll != null)
    {
      _btnSelectAll.setOnClickListener(v -> {
        vibrate();
        performContextMenuAction(android.R.id.selectAll);
      });
    }

    if (_btnCopy != null)
    {
      _btnCopy.setOnClickListener(v -> {
        vibrate();
        performContextMenuAction(android.R.id.copy);
      });
    }

    if (_btnCut != null)
    {
      _btnCut.setOnClickListener(v -> {
        vibrate();
        performContextMenuAction(android.R.id.cut);
      });
    }

    if (_btnPaste != null)
    {
      _btnPaste.setOnClickListener(v -> {
        vibrate();
        performContextMenuAction(android.R.id.paste);
      });
    }

    if (_btnStart != null)
    {
      _btnStart.setOnClickListener(v -> {
        vibrate();
        sendDpadKey(KeyEvent.KEYCODE_MOVE_HOME);
      });
    }

    if (_btnEnd != null)
    {
      _btnEnd.setOnClickListener(v -> {
        vibrate();
        sendDpadKey(KeyEvent.KEYCODE_MOVE_END);
      });
    }
  }

  public void onShow(int targetHeight)
  {
    if (targetHeight <= 0 && _keyboardService != null)
    {
      targetHeight = _keyboardService.get_keyboard_height();
    }
    if (targetHeight > 0)
    {
      _targetHeight = targetHeight;
      ViewGroup.LayoutParams lp = getLayoutParams();
      if (lp != null && lp.height != targetHeight)
      {
        lp.height = targetHeight;
        setLayoutParams(lp);
      }
    }
    _isSelectMode = false;
    updateSelectUi();
    update_bottom_margin(0);
    requestLayout();
  }

  private void update_bottom_margin(int insetBottom)
  {
    int basePad = 0;
    if (Config.globalConfig() != null && Config.globalConfig().margin_bottom > 0)
    {
      basePad = Math.max(basePad, (int) Config.globalConfig().margin_bottom);
    }
    int totalBottom = Math.max(basePad, insetBottom);
    if (_contentContainer != null)
    {
      _contentContainer.setPadding(
          _contentContainer.getPaddingLeft(),
          _contentContainer.getPaddingTop(),
          _contentContainer.getPaddingRight(),
          totalBottom + (int)(6 * getResources().getDisplayMetrics().density));
    }
  }

  @Override
  public WindowInsets onApplyWindowInsets(WindowInsets insets)
  {
    if (Build.VERSION.SDK_INT >= 30)
    {
      android.graphics.Insets navInsets = insets.getInsets(
          WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
      update_bottom_margin(navInsets.bottom);
    }
    return super.onApplyWindowInsets(insets);
  }

  private void toggleSelectMode()
  {
    vibrate();
    _isSelectMode = !_isSelectMode;
    updateSelectUi();
  }

  private void updateSelectUi()
  {
    if (_btnSelect == null)
      return;

    if (_isSelectMode)
    {
      _btnSelect.setBackgroundResource(R.drawable.bg_text_edit_select_active);
      if (_tvSelect != null)
      {
        TypedValue tv = new TypedValue();
        if (getContext().getTheme().resolveAttribute(R.attr.colorLabelActivated, tv, true))
        {
          _tvSelect.setTextColor(tv.data);
        }
        else if (getContext().getTheme().resolveAttribute(R.attr.colorLabelAction, tv, true))
        {
          _tvSelect.setTextColor(tv.data);
        }
        else
        {
          _tvSelect.setTextColor(0xFF1E1A19);
        }
      }
    }
    else
    {
      _btnSelect.setBackgroundResource(R.drawable.bg_text_edit_nav_key);
      if (_tvSelect != null)
      {
        TypedValue tv = new TypedValue();
        if (getContext().getTheme().resolveAttribute(R.attr.colorLabel, tv, true))
        {
          _tvSelect.setTextColor(tv.data);
        }
        else
        {
          _tvSelect.setTextColor(0xFFFFFFFF);
        }
      }
    }
  }

  private void setupRepeatingKey(View view, Runnable action)
  {
    if (view == null)
      return;

    view.setOnTouchListener((v, event) -> {
      switch (event.getActionMasked())
      {
        case MotionEvent.ACTION_DOWN:
          vibrate();
          action.run();
          _repeatHandler.removeCallbacksAndMessages(null);
          _repeatRunnable = new Runnable()
          {
            @Override
            public void run()
            {
              vibrate();
              action.run();
              _repeatHandler.postDelayed(this, 50);
            }
          };
          _repeatHandler.postDelayed(_repeatRunnable, 400);
          return true;

        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          _repeatHandler.removeCallbacksAndMessages(null);
          _repeatRunnable = null;
          return true;
      }
      return false;
    });
  }

  private InputConnection getInputConnection()
  {
    if (_keyboardService != null)
    {
      return _keyboardService.getCurrentInputConnection();
    }
    return null;
  }

  private void sendDpadKey(int keyCode)
  {
    InputConnection conn = getInputConnection();
    if (conn == null)
      return;

    int meta = _isSelectMode ? (KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON) : 0;
    long now = SystemClock.uptimeMillis();

    if (_isSelectMode)
    {
      conn.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0,
          meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
          KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
    }

    conn.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0,
        meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
        KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
    conn.sendKeyEvent(new KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0,
        meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
        KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));

    if (_isSelectMode)
    {
      conn.sendKeyEvent(new KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0,
          0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
          KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
    }
  }

  private void sendBackspace()
  {
    InputConnection conn = getInputConnection();
    if (conn == null)
      return;

    long now = SystemClock.uptimeMillis();
    conn.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, 0,
        0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
        KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
    conn.sendKeyEvent(new KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL, 0,
        0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
        KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
  }

  private void performContextMenuAction(int actionId)
  {
    InputConnection conn = getInputConnection();
    if (conn != null)
    {
      conn.performContextMenuAction(actionId);
    }
  }

  private void vibrate()
  {
    Config cfg = Config.globalConfig();
    if (cfg != null)
    {
      VibratorCompat.vibrate(this, cfg);
    }
  }
}
