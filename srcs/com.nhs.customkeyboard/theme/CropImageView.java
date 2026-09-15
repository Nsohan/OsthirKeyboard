package com.nhs.customkeyboard.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import androidx.annotation.Nullable;

public class CropImageView extends View
{
  public interface OnCropRectChangedListener
  {
    void onCropRectChanged(RectF cropRect);
  }

  private Bitmap _bitmap;
  private Bitmap _blurredBitmap;
  private final Matrix _matrix = new Matrix();
  private final Matrix _inverseMatrix = new Matrix();
  private final RectF _cropRect = new RectF();
  private final RectF _bitmapRect = new RectF();
  private final RectF _mappedBitmapRect = new RectF();

  public static final float EXTRA_TOP_RATIO = 0.38f;
  public static final float EXTRA_BOTTOM_RATIO = 0.12f;

  private final Paint _maskPaint = new Paint();
  private final Paint _borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint _guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint _topSectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint _darknessOverlayPaint = new Paint();

  private float _darkness = 0.20f;
  private OnCropRectChangedListener _cropRectListener;

  private ScaleGestureDetector _scaleDetector;
  private float _lastX = 0f;
  private float _lastY = 0f;
  private int _activePointerId = MotionEvent.INVALID_POINTER_ID;
  private boolean _isInitialized = false;

  public CropImageView(Context context)
  {
    super(context);
    init(context);
  }

  public CropImageView(Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    init(context);
  }

  private void init(Context context)
  {
    _maskPaint.setColor(Color.argb(160, 0, 0, 0));
    _maskPaint.setStyle(Paint.Style.FILL);

    _topSectionPaint.setColor(Color.argb(40, 255, 255, 255));
    _topSectionPaint.setStyle(Paint.Style.FILL);

    _darknessOverlayPaint.setColor(Color.BLACK);
    _darknessOverlayPaint.setStyle(Paint.Style.FILL);

    _borderPaint.setColor(Color.WHITE);
    _borderPaint.setStyle(Paint.Style.STROKE);
    _borderPaint.setStrokeWidth(dp(1.5f));

    _guidePaint.setColor(0xAAFFFFFF);
    _guidePaint.setStyle(Paint.Style.STROKE);
    _guidePaint.setStrokeWidth(dp(1f));
    _guidePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(4), dp(4)}, 0));

    _scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener()
    {
      @Override
      public boolean onScale(ScaleGestureDetector detector)
      {
        Bitmap current = (_blurredBitmap != null) ? _blurredBitmap : _bitmap;
        if (current == null) return false;
        float factor = detector.getScaleFactor();
        _matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
        checkBounds();
        invalidate();
        return true;
      }
    });
  }

  public void setOnCropRectChangedListener(OnCropRectChangedListener listener)
  {
    _cropRectListener = listener;
    if (_cropRectListener != null && !_cropRect.isEmpty())
    {
      _cropRectListener.onCropRectChanged(new RectF(_cropRect));
    }
  }

  public RectF getCropRect()
  {
    return new RectF(_cropRect);
  }

  public void setDarkness(float darkness)
  {
    _darkness = Math.max(0f, Math.min(1f, darkness));
    invalidate();
  }

  public void setBlurredBitmap(@Nullable Bitmap blurred)
  {
    _blurredBitmap = blurred;
    invalidate();
  }

  public void setImageBitmap(Bitmap bitmap)
  {
    _bitmap = bitmap;
    _blurredBitmap = null;
    _isInitialized = false;
    if (_bitmap != null)
    {
      _bitmapRect.set(0, 0, _bitmap.getWidth(), _bitmap.getHeight());
    }
    requestLayout();
    invalidate();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh)
  {
    super.onSizeChanged(w, h, oldw, oldh);
    if (w <= 0 || h <= 0) return;

    float totalRatio = 1f + EXTRA_TOP_RATIO + EXTRA_BOTTOM_RATIO;
    // Calculate crop dimensions to fit comfortably inside view bounds
    float maxCropW = Math.min(w * 0.90f, dp(370));
    float maxCropHByHeight = (h - dp(16)) / totalRatio;
    float cropW = Math.min(maxCropW, maxCropHByHeight * 1.55f);
    float cropH = cropW / 1.55f;

    float topHeadroom = cropH * EXTRA_TOP_RATIO;
    float botFootroom = cropH * EXTRA_BOTTOM_RATIO;
    float totalExtH = cropH + topHeadroom + botFootroom;

    float left = (w - cropW) / 2f;
    // Vertically center the total extended crop frame in the view
    float extTop = (h - totalExtH) / 2f;
    float top = extTop + topHeadroom;

    _cropRect.set(left, top, left + cropW, top + cropH);

    setupInitialMatrix();

    if (_cropRectListener != null)
    {
      _cropRectListener.onCropRectChanged(new RectF(_cropRect));
    }
  }

  private RectF getExtendedCropRect()
  {
    if (_cropRect.isEmpty()) return new RectF();
    float topHeadroom = _cropRect.height() * EXTRA_TOP_RATIO;
    float botFootroom = _cropRect.height() * EXTRA_BOTTOM_RATIO;
    return new RectF(_cropRect.left, _cropRect.top - topHeadroom, _cropRect.right, _cropRect.bottom + botFootroom);
  }

  private void setupInitialMatrix()
  {
    Bitmap current = (_blurredBitmap != null) ? _blurredBitmap : _bitmap;
    if (current == null || _cropRect.isEmpty() || _isInitialized) return;

    RectF extRect = getExtendedCropRect();
    _matrix.reset();
    float scale = Math.max(extRect.width() / current.getWidth(), extRect.height() / current.getHeight());
    _matrix.setScale(scale, scale);

    float dx = extRect.centerX() - (current.getWidth() * scale) / 2f;
    float dy = extRect.centerY() - (current.getHeight() * scale) / 2f;
    _matrix.postTranslate(dx, dy);

    _isInitialized = true;
    checkBounds();
    invalidate();
  }

  private void checkBounds()
  {
    Bitmap current = (_blurredBitmap != null) ? _blurredBitmap : _bitmap;
    if (current == null || _cropRect.isEmpty()) return;

    RectF extRect = getExtendedCropRect();
    _matrix.mapRect(_mappedBitmapRect, _bitmapRect);

    // Ensure scale covers full extended crop rect (including top translation area)
    float scaleFix = 1f;
    if (_mappedBitmapRect.width() < extRect.width())
    {
      scaleFix = Math.max(scaleFix, extRect.width() / _mappedBitmapRect.width());
    }
    if (_mappedBitmapRect.height() < extRect.height())
    {
      scaleFix = Math.max(scaleFix, extRect.height() / _mappedBitmapRect.height());
    }
    if (scaleFix > 1f)
    {
      _matrix.postScale(scaleFix, scaleFix, extRect.centerX(), extRect.centerY());
      _matrix.mapRect(_mappedBitmapRect, _bitmapRect);
    }

    // Translate to keep crop window filled
    float dx = 0f;
    float dy = 0f;

    if (_mappedBitmapRect.left > extRect.left)
    {
      dx = extRect.left - _mappedBitmapRect.left;
    }
    else if (_mappedBitmapRect.right < extRect.right)
    {
      dx = extRect.right - _mappedBitmapRect.right;
    }

    if (_mappedBitmapRect.top > extRect.top)
    {
      dy = extRect.top - _mappedBitmapRect.top;
    }
    else if (_mappedBitmapRect.bottom < extRect.bottom)
    {
      dy = extRect.bottom - _mappedBitmapRect.bottom;
    }

    if (dx != 0f || dy != 0f)
    {
      _matrix.postTranslate(dx, dy);
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event)
  {
    Bitmap current = (_blurredBitmap != null) ? _blurredBitmap : _bitmap;
    if (current == null) return false;

    _scaleDetector.onTouchEvent(event);

    switch (event.getActionMasked())
    {
      case MotionEvent.ACTION_DOWN:
      {
        _lastX = event.getX();
        _lastY = event.getY();
        _activePointerId = event.getPointerId(0);
        break;
      }
      case MotionEvent.ACTION_MOVE:
      {
        int pointerIndex = event.findPointerIndex(_activePointerId);
        if (pointerIndex != -1 && !_scaleDetector.isInProgress())
        {
          float x = event.getX(pointerIndex);
          float y = event.getY(pointerIndex);
          float dx = x - _lastX;
          float dy = y - _lastY;

          _matrix.postTranslate(dx, dy);
          checkBounds();
          invalidate();

          _lastX = x;
          _lastY = y;
        }
        break;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
      {
        _activePointerId = MotionEvent.INVALID_POINTER_ID;
        break;
      }
      case MotionEvent.ACTION_POINTER_UP:
      {
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);
        if (pointerId == _activePointerId)
        {
          int newIndex = (pointerIndex == 0) ? 1 : 0;
          _lastX = event.getX(newIndex);
          _lastY = event.getY(newIndex);
          _activePointerId = event.getPointerId(newIndex);
        }
        break;
      }
    }
    return true;
  }

  @Override
  protected void onDraw(Canvas canvas)
  {
    super.onDraw(canvas);
    Bitmap current = (_blurredBitmap != null && !_blurredBitmap.isRecycled()) ? _blurredBitmap : _bitmap;
    if (current == null) return;

    canvas.drawBitmap(current, _matrix, null);

    float w = getWidth();
    float h = getHeight();

    RectF extRect = getExtendedCropRect();
    float extTop = extRect.top;
    float extBot = extRect.bottom;

    // Draw live darkness overlay over extended crop area
    if (_darkness > 0f)
    {
      _darknessOverlayPaint.setAlpha((int) (Math.min(0.9f, Math.max(0f, _darkness)) * 255));
      canvas.drawRect(extRect.left, extTop, extRect.right, extBot, _darknessOverlayPaint);
    }

    // Draw dark mask outside total extended crop area
    canvas.drawRect(0, 0, w, extTop, _maskPaint);
    canvas.drawRect(0, extBot, w, h, _maskPaint);
    canvas.drawRect(0, extTop, extRect.left, extBot, _maskPaint);
    canvas.drawRect(extRect.right, extTop, w, extBot, _maskPaint);

    // Light tint over top headroom box (Translation Section Area)
    canvas.drawRect(_cropRect.left, extTop, _cropRect.right, _cropRect.top, _topSectionPaint);

    // Dashed border around top Translation section box
    canvas.drawRect(_cropRect.left, extTop, _cropRect.right, _cropRect.top, _guidePaint);

    // Dashed line for bottom footroom
    if (extBot < h)
    {
      canvas.drawLine(_cropRect.left, extBot, _cropRect.right, extBot, _guidePaint);
    }

    // Draw main white crop border for keyboard layout (bottom key row to suggestion bar)
    canvas.drawRect(_cropRect, _borderPaint);
  }

  @Nullable
  public Bitmap getCroppedBitmap()
  {
    Bitmap current = (_blurredBitmap != null && !_blurredBitmap.isRecycled()) ? _blurredBitmap : _bitmap;
    if (current == null || _cropRect.isEmpty()) return null;

    try
    {
      _matrix.invert(_inverseMatrix);

      RectF extCropRect = getExtendedCropRect();

      RectF srcRect = new RectF();
      _inverseMatrix.mapRect(srcRect, extCropRect);

      int targetW = 1080;
      float totalRatio = 1f + EXTRA_TOP_RATIO + EXTRA_BOTTOM_RATIO;
      int targetH = Math.round((targetW / 1.55f) * totalRatio);

      Bitmap outBmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(outBmp);
      Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

      int bmpW = current.getWidth();
      int bmpH = current.getHeight();

      float srcL = Math.max(0, Math.min(bmpW, srcRect.left));
      float srcR = Math.max(0, Math.min(bmpW, srcRect.right));
      float srcT = Math.max(0, Math.min(bmpH, srcRect.top));
      float srcB = Math.max(0, Math.min(bmpH, srcRect.bottom));

      float srcW = srcRect.width();
      float srcHeight = srcRect.height();

      if (srcW > 0 && srcHeight > 0 && srcR > srcL && srcB > srcT)
      {
        float dstL = ((srcL - srcRect.left) / srcW) * targetW;
        float dstR = ((srcR - srcRect.left) / srcW) * targetW;
        float dstT = ((srcT - srcRect.top) / srcHeight) * targetH;
        float dstB = ((srcB - srcRect.top) / srcHeight) * targetH;

        android.graphics.Rect src = new android.graphics.Rect((int) srcL, (int) srcT, (int) srcR, (int) srcB);
        RectF dst = new RectF(dstL, dstT, dstR, dstB);
        canvas.drawBitmap(current, src, dst, p);

        // Fill edge margins if image was close to boundary
        if (dstT > 0)
        {
          android.graphics.Rect topSrc = new android.graphics.Rect((int) srcL, (int) srcT, (int) srcR, (int) Math.min(bmpH, srcT + 2));
          RectF topDst = new RectF(0, 0, targetW, dstT);
          canvas.drawBitmap(current, topSrc, topDst, p);
        }
        if (dstB < targetH)
        {
          android.graphics.Rect botSrc = new android.graphics.Rect((int) srcL, (int) Math.max(0, srcB - 2), (int) srcR, (int) srcB);
          RectF botDst = new RectF(0, dstB, targetW, targetH);
          canvas.drawBitmap(current, botSrc, botDst, p);
        }
      }
      else
      {
        android.graphics.Rect src = new android.graphics.Rect(0, 0, bmpW, bmpH);
        RectF dst = new RectF(0, 0, targetW, targetH);
        canvas.drawBitmap(current, src, dst, p);
      }

      return outBmp;
    }
    catch (Throwable t)
    {
      return null;
    }
  }

  private float dp(float v)
  {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
  }
}
