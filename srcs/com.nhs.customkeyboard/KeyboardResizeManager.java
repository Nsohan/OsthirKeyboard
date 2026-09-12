package com.nhs.customkeyboard;

import android.content.SharedPreferences;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.nhs.customkeyboard.suggestions.CandidatesView;

public class KeyboardResizeManager
{
  private final Keyboard2 _keyboardService;
  private ViewGroup _containerView;
  private Keyboard2View _keyboardLayoutView;
  private CandidatesView _candidatesView;
  private View _resizeOverlay;
  private FrameLayout _resizeBoundingBox;

  private boolean _isResizeActive = false;
  private int _initialHeightPercent = 35;
  private int _currentHeightPercent = 35;
  private int _initialMarginBottomDp = 7;
  private int _currentMarginBottomDp = 7;

  private int _defaultHeightPercent = 35;
  private int _defaultMarginBottomDp = 7;

  private String _heightPrefKey = "keyboard_height";
  private String _marginPrefKey = "margin_bottom_portrait";

  public KeyboardResizeManager(Keyboard2 service)
  {
    _keyboardService = service;
  }

  public void setupViews(ViewGroup containerView, Keyboard2View keyboardLayoutView,
                         CandidatesView candidatesView)
  {
    _containerView = containerView;
    _keyboardLayoutView = keyboardLayoutView;
    _candidatesView = candidatesView;

    if (_containerView == null)
      return;

    _resizeOverlay = _containerView.findViewById(R.id.resize_overlay_panel);
    if (_resizeOverlay == null)
      return;

    if (_resizeOverlay instanceof KeyboardResizeOverlay)
    {
      ((KeyboardResizeOverlay) _resizeOverlay).setKeyboardView(_keyboardLayoutView);
    }

    _resizeBoundingBox = _resizeOverlay.findViewById(R.id.resize_bounding_box);

    View handleTop = _resizeOverlay.findViewById(R.id.resize_handle_top);
    View handleBottom = _resizeOverlay.findViewById(R.id.resize_handle_bottom);
    View btnMove = _resizeOverlay.findViewById(R.id.resize_btn_move);
    View btnReset = _resizeOverlay.findViewById(R.id.resize_btn_reset);
    View btnDone = _resizeOverlay.findViewById(R.id.resize_btn_done);

    if (handleTop != null)
    {
      handleTop.setOnTouchListener(new View.OnTouchListener()
      {
        private float _startY = 0f;
        private int _startPercent = 35;

        @Override
        public boolean onTouch(View v, MotionEvent event)
        {
          switch (event.getActionMasked())
          {
            case MotionEvent.ACTION_DOWN:
              _startY = event.getRawY();
              _startPercent = _currentHeightPercent;
              return true;

            case MotionEvent.ACTION_MOVE:
              float dy = event.getRawY() - _startY;
              DisplayMetrics dm = _keyboardService.getResources().getDisplayMetrics();
              float baseHeight = Math.min(dm.heightPixels, dm.widthPixels * 16.f / 9.f);
              float pixelsPerPercent = baseHeight / 100.f;
              int deltaPercent = -Math.round(dy / pixelsPerPercent);
              int minH = 15;
              int maxH = 75;
              int newPercent = Math.max(minH, Math.min(maxH, _startPercent + deltaPercent));
              if (newPercent != _currentHeightPercent)
              {
                _currentHeightPercent = newPercent;
                applyCurrentHeight();
              }
              return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
              return true;
          }
          return false;
        }
      });
    }

    if (handleBottom != null)
    {
      handleBottom.setOnTouchListener(new View.OnTouchListener()
      {
        private float _startY = 0f;
        private int _startMarginDp = 7;

        @Override
        public boolean onTouch(View v, MotionEvent event)
        {
          switch (event.getActionMasked())
          {
            case MotionEvent.ACTION_DOWN:
              _startY = event.getRawY();
              _startMarginDp = _currentMarginBottomDp;
              return true;

            case MotionEvent.ACTION_MOVE:
              float dy = event.getRawY() - _startY;
              float density = _keyboardService.getResources().getDisplayMetrics().density;
              int deltaDp = -Math.round(dy / density);
              int newMarginDp = Math.max(0, Math.min(120, _startMarginDp + deltaDp));
              if (newMarginDp != _currentMarginBottomDp)
              {
                _currentMarginBottomDp = newMarginDp;
                applyCurrentMargin();
              }
              return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
              return true;
          }
          return false;
        }
      });
    }

    if (btnMove != null)
    {
      btnMove.setOnTouchListener(new View.OnTouchListener()
      {
        private float _startY = 0f;
        private int _startMarginDp = 7;

        @Override
        public boolean onTouch(View v, MotionEvent event)
        {
          switch (event.getActionMasked())
          {
            case MotionEvent.ACTION_DOWN:
              _startY = event.getRawY();
              _startMarginDp = _currentMarginBottomDp;
              return true;

            case MotionEvent.ACTION_MOVE:
              float dy = event.getRawY() - _startY;
              float density = _keyboardService.getResources().getDisplayMetrics().density;
              int deltaDp = -Math.round(dy / density);
              int newMarginDp = Math.max(0, Math.min(120, _startMarginDp + deltaDp));
              if (newMarginDp != _currentMarginBottomDp)
              {
                _currentMarginBottomDp = newMarginDp;
                applyCurrentMargin();
              }
              return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
              return true;
          }
          return false;
        }
      });
    }

    if (btnReset != null)
    {
      btnReset.setOnClickListener(v -> resetToDefaults());
    }

    if (btnDone != null)
    {
      btnDone.setOnClickListener(v -> saveAndClose());
    }
  }

  public boolean isResizeActive()
  {
    return _isResizeActive;
  }

  public void startResize()
  {
    if (_resizeOverlay == null)
      return;

    Config config = Config.globalConfig();
    if (config == null)
      return;

    SharedPreferences prefs = DirectBootAwarePreferences.get_shared_preferences(_keyboardService);
    boolean landscape = config.orientation_landscape;
    boolean unfolded = config.foldable_unfolded;

    if (landscape)
    {
      _heightPrefKey = unfolded ? "keyboard_height_landscape_unfolded" : "keyboard_height_landscape";
      _marginPrefKey = unfolded ? "margin_bottom_landscape_unfolded" : "margin_bottom_landscape";
      _defaultHeightPercent = 50;
      _defaultMarginBottomDp = 3;
    }
    else
    {
      _heightPrefKey = unfolded ? "keyboard_height_unfolded" : "keyboard_height";
      _marginPrefKey = unfolded ? "margin_bottom_portrait_unfolded" : "margin_bottom_portrait";
      _defaultHeightPercent = 35;
      _defaultMarginBottomDp = 7;
    }

    _initialHeightPercent = prefs.getInt(_heightPrefKey, _defaultHeightPercent);
    _currentHeightPercent = _initialHeightPercent;

    _initialMarginBottomDp = prefs.getInt(_marginPrefKey, _defaultMarginBottomDp);
    _currentMarginBottomDp = _initialMarginBottomDp;

    _isResizeActive = true;
    _resizeOverlay.setVisibility(View.VISIBLE);

    applyCurrentHeight();
    applyCurrentMargin();
  }

  public void stopResize(boolean saveChanges)
  {
    if (!_isResizeActive)
      return;

    _isResizeActive = false;
    if (_resizeOverlay != null)
    {
      _resizeOverlay.setVisibility(View.GONE);
    }

    if (saveChanges)
    {
      SharedPreferences prefs = DirectBootAwarePreferences.get_shared_preferences(_keyboardService);
      prefs.edit()
          .putInt(_heightPrefKey, _currentHeightPercent)
          .putInt(_marginPrefKey, _currentMarginBottomDp)
          .apply();

      try
      {
        SharedPreferences defaultPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(_keyboardService);
        defaultPrefs.edit()
            .putInt(_heightPrefKey, _currentHeightPercent)
            .putInt(_marginPrefKey, _currentMarginBottomDp)
            .apply();
      }
      catch (Exception ignored) {}

      Toast.makeText(_keyboardService, R.string.resize_done, Toast.LENGTH_SHORT).show();
    }
    else
    {
      // Revert to initial values
      _currentHeightPercent = _initialHeightPercent;
      _currentMarginBottomDp = _initialMarginBottomDp;
      applyCurrentHeight();
      applyCurrentMargin();
    }

    _keyboardService.refresh_config_from_resize();
  }

  private void resetToDefaults()
  {
    _currentHeightPercent = _defaultHeightPercent;
    _currentMarginBottomDp = _defaultMarginBottomDp;
    applyCurrentHeight();
    applyCurrentMargin();
  }

  private void saveAndClose()
  {
    stopResize(true);
  }

  private void applyCurrentHeight()
  {
    Config config = Config.globalConfig();
    if (config == null || _keyboardLayoutView == null)
      return;

    DisplayMetrics dm = _keyboardService.getResources().getDisplayMetrics();
    float baseHeight = Math.min(dm.heightPixels, dm.widthPixels * 16.f / 9.f);
    config.keyboard_rows_height_pixels = (int)(baseHeight * _currentHeightPercent / 395);

    _keyboardLayoutView.reset();
    _keyboardLayoutView.requestLayout();

    if (_candidatesView != null)
    {
      _candidatesView.refresh_config(config);
    }
  }

  private void applyCurrentMargin()
  {
    Config config = Config.globalConfig();
    if (config == null || _keyboardLayoutView == null)
      return;

    DisplayMetrics dm = _keyboardService.getResources().getDisplayMetrics();
    config.margin_bottom = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, _currentMarginBottomDp, dm);

    _keyboardLayoutView.requestLayout();

    if (_resizeBoundingBox != null)
    {
      ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) _resizeBoundingBox.getLayoutParams();
      if (lp != null)
      {
        lp.bottomMargin = (int) config.margin_bottom;
        _resizeBoundingBox.setLayoutParams(lp);
      }
    }
  }
}
