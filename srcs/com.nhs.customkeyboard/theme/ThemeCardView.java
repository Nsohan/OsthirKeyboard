package com.nhs.customkeyboard.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import androidx.core.content.ContextCompat;
import com.nhs.customkeyboard.R;

public class ThemeCardView extends View
{
  private ThemeModel _model;
  private boolean _isSelected = false;
  private boolean _isAddButton = false;

  private final RectF _cardRect = new RectF();
  private final RectF _tmpRect = new RectF();
  private final Path _clipPath = new Path();

  private Paint _bgPaint;
  private Paint _keyPillPaint;
  private Paint _accentDotPaint;
  private Paint _borderPaint;
  private Paint _selectedBadgePaint;
  private Paint _addBorderPaint;

  private Drawable _checkDrawable;
  private Drawable _addDrawable;
  private Bitmap _customPhotoBitmap = null;

  private float _cornerRadius;
  private int _primaryColor;

  public ThemeCardView(Context context)
  {
    this(context, null);
  }

  public ThemeCardView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    init(context);
  }

  private void init(Context context)
  {
    _cornerRadius = dp(16);

    TypedValue tv = new TypedValue();
    if (context.getTheme().resolveAttribute(android.R.attr.colorAccent, tv, true))
    {
      _primaryColor = tv.data;
    }
    else
    {
      _primaryColor = 0xFF8D4F38;
    }

    _bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    _bgPaint.setStyle(Paint.Style.FILL);

    _keyPillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    _keyPillPaint.setStyle(Paint.Style.FILL);

    _accentDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    _accentDotPaint.setStyle(Paint.Style.FILL);

    _borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    _borderPaint.setStyle(Paint.Style.STROKE);
    _borderPaint.setStrokeWidth(dp(2.5f));
    _borderPaint.setColor(_primaryColor);

    _selectedBadgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    _selectedBadgePaint.setStyle(Paint.Style.FILL);
    _selectedBadgePaint.setColor(_primaryColor);

    _addBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    _addBorderPaint.setStyle(Paint.Style.STROKE);
    _addBorderPaint.setStrokeWidth(dp(2f));
    _addBorderPaint.setColor(_primaryColor);

    _checkDrawable = ContextCompat.getDrawable(context, R.drawable.ic_check);
    if (_checkDrawable != null)
    {
      _checkDrawable.setTint(Color.WHITE);
    }

    _addDrawable = ContextCompat.getDrawable(context, R.drawable.ic_add);
    if (_addDrawable != null)
    {
      _addDrawable.setTint(_primaryColor);
    }
  }

  public void setThemeModel(ThemeModel model, boolean selected)
  {
    _model = model;
    _isSelected = selected;
    _isAddButton = false;
    _customPhotoBitmap = null;

    if (model != null && model.isCustomPhoto && model.imagePath != null)
    {
      try
      {
        _customPhotoBitmap = BitmapFactory.decodeFile(model.imagePath);
      }
      catch (Throwable ignored) {}
    }
    invalidate();
  }

  public void setAddButton()
  {
    _model = null;
    _isSelected = false;
    _isAddButton = true;
    _customPhotoBitmap = null;
    invalidate();
  }

  public void setSelectedState(boolean selected)
  {
    _isSelected = selected;
    invalidate();
  }

  public ThemeModel getThemeModel()
  {
    return _model;
  }

  public boolean isAddButton()
  {
    return _isAddButton;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    // Maintain approx 1.25 : 1 ratio for cards
    int height = (int) (width / 1.28f);
    setMeasuredDimension(width, height);
  }

  @Override
  protected void onDraw(Canvas canvas)
  {
    super.onDraw(canvas);

    float w = getWidth();
    float h = getHeight();
    if (w <= 0 || h <= 0) return;

    float padding = dp(3);
    _cardRect.set(padding, padding, w - padding, h - padding);

    if (_isAddButton)
    {
      drawAddCard(canvas, _cardRect);
      return;
    }

    if (_model == null) return;

    // Save layer & clip to rounded rect
    int save = canvas.save();
    _clipPath.reset();
    _clipPath.addRoundRect(_cardRect, _cornerRadius, _cornerRadius, Path.Direction.CW);
    canvas.clipPath(_clipPath);

    if (_model.isCustomPhoto && _customPhotoBitmap != null && !_customPhotoBitmap.isRecycled())
    {
      canvas.drawBitmap(_customPhotoBitmap, null, _cardRect, null);
      if (_model.darknessOverlay > 0f)
      {
        _bgPaint.setColor(Color.BLACK);
        _bgPaint.setAlpha((int) (_model.darknessOverlay * 255));
        canvas.drawRect(_cardRect, _bgPaint);
      }
      int keyAlpha = Math.max(0, Math.min(255, (int) (_model.keyOpacity * 0x55)));
      int keyColor = (keyAlpha << 24) | 0x00FFFFFF;
      drawKeyIndicators(canvas, _cardRect, keyColor, 0xFF4285F4);
    }
    else if (_model.isSystemAuto)
    {
      // Split card vertically
      float halfW = _cardRect.left + _cardRect.width() / 2f;

      // Left half: Light
      _bgPaint.setColor(0xFFE2E7EC);
      _tmpRect.set(_cardRect.left, _cardRect.top, halfW, _cardRect.bottom);
      canvas.drawRect(_tmpRect, _bgPaint);

      // Right half: Dark
      _bgPaint.setColor(0xFF1E1F22);
      _tmpRect.set(halfW, _cardRect.top, _cardRect.right, _cardRect.bottom);
      canvas.drawRect(_tmpRect, _bgPaint);

      // Bottom bar split
      drawSplitKeyIndicators(canvas, _cardRect, halfW);
    }
    else
    {
      _bgPaint.setColor(_model.previewBgColor);
      canvas.drawRect(_cardRect, _bgPaint);
      drawKeyIndicators(canvas, _cardRect, _model.previewKeyColor, _model.previewAccentColor);
    }

    canvas.restoreToCount(save);

    // Selected border & badge
    if (_isSelected)
    {
      canvas.drawRoundRect(_cardRect, _cornerRadius, _cornerRadius, _borderPaint);

      // Center Checkmark Badge
      float badgeRadius = dp(17);
      float cx = _cardRect.centerX();
      float cy = _cardRect.centerY();
      canvas.drawCircle(cx, cy, badgeRadius, _selectedBadgePaint);

      if (_checkDrawable != null)
      {
        int checkSize = (int) dp(20);
        _checkDrawable.setBounds((int) (cx - checkSize / 2f), (int) (cy - checkSize / 2f),
            (int) (cx + checkSize / 2f), (int) (cy + checkSize / 2f));
        _checkDrawable.draw(canvas);
      }
    }
    else
    {
      // Subtle inactive border
      _borderPaint.setColor(0x1A000000);
      _borderPaint.setStrokeWidth(dp(1));
      canvas.drawRoundRect(_cardRect, _cornerRadius, _cornerRadius, _borderPaint);
      _borderPaint.setColor(_primaryColor);
      _borderPaint.setStrokeWidth(dp(2.5f));
    }
  }

  private void drawAddCard(Canvas canvas, RectF rect)
  {
    canvas.drawRoundRect(rect, _cornerRadius, _cornerRadius, _addBorderPaint);
    if (_addDrawable != null)
    {
      int iconSize = (int) dp(34);
      int cx = (int) rect.centerX();
      int cy = (int) rect.centerY();
      _addDrawable.setBounds(cx - iconSize / 2, cy - iconSize / 2, cx + iconSize / 2, cy + iconSize / 2);
      _addDrawable.draw(canvas);
    }
  }

  private void drawKeyIndicators(Canvas canvas, RectF rect, int keyColor, int accentColor)
  {
    // Spacebar pill
    float pillW = rect.width() * 0.44f;
    float pillH = dp(7.5f);
    float pillY = rect.bottom - dp(14);
    float pillX = rect.left + dp(12);

    _keyPillPaint.setColor(keyColor);
    _tmpRect.set(pillX, pillY, pillX + pillW, pillY + pillH);
    canvas.drawRoundRect(_tmpRect, dp(4), dp(4), _keyPillPaint);

    // Accent dot
    float dotRadius = dp(5.5f);
    float dotX = rect.right - dp(18);
    float dotY = pillY + pillH / 2f;

    _accentDotPaint.setColor(accentColor);
    canvas.drawCircle(dotX, dotY, dotRadius, _accentDotPaint);
  }

  private void drawSplitKeyIndicators(Canvas canvas, RectF rect, float splitX)
  {
    // Spacebar pill centered on split
    float pillW = rect.width() * 0.46f;
    float pillH = dp(7.5f);
    float pillY = rect.bottom - dp(14);
    float pillLeft = splitX - pillW / 2f;

    // Left portion of pill (light)
    _keyPillPaint.setColor(0xFFFFFFFF);
    _tmpRect.set(pillLeft, pillY, splitX, pillY + pillH);
    canvas.drawRoundRect(_tmpRect, dp(4), dp(4), _keyPillPaint);

    // Right portion of pill (dark)
    _keyPillPaint.setColor(0xFF383838);
    _tmpRect.set(splitX, pillY, pillLeft + pillW, pillY + pillH);
    canvas.drawRoundRect(_tmpRect, dp(4), dp(4), _keyPillPaint);

    // Accent dot on dark side
    float dotRadius = dp(5.5f);
    float dotX = rect.right - dp(18);
    float dotY = pillY + pillH / 2f;
    _accentDotPaint.setColor(0xFF8AB4F8);
    canvas.drawCircle(dotX, dotY, dotRadius, _accentDotPaint);
  }

  private float dp(float v)
  {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
  }
}
