package com.nhs.customkeyboard.theme;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.nhs.customkeyboard.R;

public class CustomThemeBackgroundDrawable extends Drawable
{
  private final Bitmap _bitmap;
  private final float _darkness;
  private final View _containerView;
  private final Paint _paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Paint _darknessPaint = new Paint();
  private final Rect _srcRect = new Rect();
  private final Rect _dstRect = new Rect();

  public CustomThemeBackgroundDrawable(Bitmap bitmap, float darkness, View containerView)
  {
    _bitmap = bitmap;
    _darkness = darkness;
    _containerView = containerView;
    _darknessPaint.setColor(Color.BLACK);
    _darknessPaint.setStyle(Paint.Style.FILL);
    _darknessPaint.setAlpha((int) (Math.min(0.9f, Math.max(0f, darkness)) * 255));
  }

  public CustomThemeBackgroundDrawable(Bitmap bitmap, float darkness)
  {
    this(bitmap, darkness, null);
  }

  @Override
  public void draw(@NonNull Canvas canvas)
  {
    Rect bounds = getBounds();
    if (bounds.isEmpty() || _bitmap == null || _bitmap.isRecycled()) return;

    int viewW = bounds.width();
    int viewH = bounds.height();
    int bmpW = _bitmap.getWidth();
    int bmpH = _bitmap.getHeight();

    float ySuggestionTop = 0f;
    if (_containerView instanceof ViewGroup)
    {
      View candView = _containerView.findViewById(R.id.candidates_view);
      if (candView != null && candView.getVisibility() == View.VISIBLE)
      {
        ySuggestionTop = candView.getTop();
      }
    }

    float totalRatio = 1.0f + CropImageView.EXTRA_TOP_RATIO + CropImageView.EXTRA_BOTTOM_RATIO;
    float topHeadroomRatio = CropImageView.EXTRA_TOP_RATIO / totalRatio;
    float mainAndBottomRatio = 1.0f - topHeadroomRatio;

    // Ensure scale covers both width and full container height down to the bottom margin/nav bar
    float requiredBottomHeight = viewH - ySuggestionTop;
    float minScaleForBottom = requiredBottomHeight / (bmpH * mainAndBottomRatio);
    float scale = Math.max((float) viewW / bmpW, minScaleForBottom);

    float scaledW = bmpW * scale;
    float scaledH = bmpH * scale;
    float scaledSuggestionTop = (bmpH * topHeadroomRatio) * scale;

    float left = bounds.left + (viewW - scaledW) / 2f;
    float top = bounds.top + ySuggestionTop - scaledSuggestionTop;

    _srcRect.set(0, 0, bmpW, bmpH);
    _dstRect.set((int) left, (int) top, (int) (left + scaledW), (int) (top + scaledH));

    canvas.save();
    canvas.clipRect(bounds);
    canvas.drawBitmap(_bitmap, _srcRect, _dstRect, _paint);
    if (_darkness > 0f)
    {
      canvas.drawRect(bounds, _darknessPaint);
    }
    canvas.restore();
  }

  @Override
  public void setAlpha(int alpha)
  {
    _paint.setAlpha(alpha);
  }

  @Override
  public void setColorFilter(@Nullable ColorFilter colorFilter)
  {
    _paint.setColorFilter(colorFilter);
  }

  @Override
  public int getOpacity()
  {
    return PixelFormat.TRANSLUCENT;
  }
}
