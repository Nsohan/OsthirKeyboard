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
  private Bitmap _bitmap;
  private final Matrix _matrix = new Matrix();
  private final Matrix _inverseMatrix = new Matrix();
  private final RectF _cropRect = new RectF();
  private final RectF _bitmapRect = new RectF();
  private final RectF _mappedBitmapRect = new RectF();

  public static final float EXTRA_TOP_RATIO = 0.28f;
  public static final float EXTRA_BOTTOM_RATIO = 0.12f;

  private final Paint _maskPaint = new Paint();
  private final Paint _borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint _guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

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

    _borderPaint.setColor(Color.WHITE);
    _borderPaint.setStyle(Paint.Style.STROKE);
    _borderPaint.setStrokeWidth(dp(1.5f));

    _guidePaint.setColor(0x88FFFFFF);
    _guidePaint.setStyle(Paint.Style.STROKE);
    _guidePaint.setStrokeWidth(dp(1f));
    _guidePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(4), dp(4)}, 0));

    _scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener()
    {
      @Override
      public boolean onScale(ScaleGestureDetector detector)
      {
        if (_bitmap == null) return false;
        float factor = detector.getScaleFactor();
        _matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
        checkBounds();
        invalidate();
        return true;
      }
    });
  }

  public void setImageBitmap(Bitmap bitmap)
  {
    _bitmap = bitmap;
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

    // Crop frame matching keyboard aspect ratio (approx 1.55:1)
    float cropW = Math.min(w * 0.90f, dp(380));
    float cropH = cropW / 1.55f;

    float left = (w - cropW) / 2f;
    float top = (h - cropH) / 2f;
    _cropRect.set(left, top, left + cropW, top + cropH);

    setupInitialMatrix();
  }

  private void setupInitialMatrix()
  {
    if (_bitmap == null || _cropRect.isEmpty() || _isInitialized) return;

    _matrix.reset();
    float scale = Math.max(_cropRect.width() / _bitmap.getWidth(), _cropRect.height() / _bitmap.getHeight());
    _matrix.setScale(scale, scale);

    float dx = _cropRect.centerX() - (_bitmap.getWidth() * scale) / 2f;
    float dy = _cropRect.centerY() - (_bitmap.getHeight() * scale) / 2f;
    _matrix.postTranslate(dx, dy);

    _isInitialized = true;
    checkBounds();
    invalidate();
  }

  private void checkBounds()
  {
    if (_bitmap == null || _cropRect.isEmpty()) return;

    _matrix.mapRect(_mappedBitmapRect, _bitmapRect);

    // Ensure scale covers crop rect
    float scaleFix = 1f;
    if (_mappedBitmapRect.width() < _cropRect.width())
    {
      scaleFix = Math.max(scaleFix, _cropRect.width() / _mappedBitmapRect.width());
    }
    if (_mappedBitmapRect.height() < _cropRect.height())
    {
      scaleFix = Math.max(scaleFix, _cropRect.height() / _mappedBitmapRect.height());
    }
    if (scaleFix > 1f)
    {
      _matrix.postScale(scaleFix, scaleFix, _cropRect.centerX(), _cropRect.centerY());
      _matrix.mapRect(_mappedBitmapRect, _bitmapRect);
    }

    // Translate to keep crop window filled
    float dx = 0f;
    float dy = 0f;

    if (_mappedBitmapRect.left > _cropRect.left)
    {
      dx = _cropRect.left - _mappedBitmapRect.left;
    }
    else if (_mappedBitmapRect.right < _cropRect.right)
    {
      dx = _cropRect.right - _mappedBitmapRect.right;
    }

    if (_mappedBitmapRect.top > _cropRect.top)
    {
      dy = _cropRect.top - _mappedBitmapRect.top;
    }
    else if (_mappedBitmapRect.bottom < _cropRect.bottom)
    {
      dy = _cropRect.bottom - _mappedBitmapRect.bottom;
    }

    if (dx != 0f || dy != 0f)
    {
      _matrix.postTranslate(dx, dy);
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event)
  {
    if (_bitmap == null) return false;

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
    if (_bitmap == null) return;

    canvas.drawBitmap(_bitmap, _matrix, null);

    // Draw dark mask around crop rectangle
    float w = getWidth();
    float h = getHeight();

    // Top rect
    canvas.drawRect(0, 0, w, _cropRect.top, _maskPaint);
    // Bottom rect
    canvas.drawRect(0, _cropRect.bottom, w, h, _maskPaint);
    // Left rect
    canvas.drawRect(0, _cropRect.top, _cropRect.left, _cropRect.bottom, _maskPaint);
    // Right rect
    canvas.drawRect(_cropRect.right, _cropRect.top, w, _cropRect.bottom, _maskPaint);

    // Draw top & bottom expansion guidelines
    float topHeadroom = _cropRect.height() * EXTRA_TOP_RATIO;
    float botFootroom = _cropRect.height() * EXTRA_BOTTOM_RATIO;
    float guideTop = _cropRect.top - topHeadroom;
    float guideBot = _cropRect.bottom + botFootroom;

    if (guideTop > 0)
    {
      canvas.drawLine(_cropRect.left, guideTop, _cropRect.right, guideTop, _guidePaint);
    }
    if (guideBot < h)
    {
      canvas.drawLine(_cropRect.left, guideBot, _cropRect.right, guideBot, _guidePaint);
    }

    // Draw main white crop border
    canvas.drawRect(_cropRect, _borderPaint);
  }

  @Nullable
  public Bitmap getCroppedBitmap()
  {
    if (_bitmap == null || _cropRect.isEmpty()) return null;

    try
    {
      _matrix.invert(_inverseMatrix);

      float cropW = _cropRect.width();
      float cropH = _cropRect.height();
      float topHeadroom = cropH * EXTRA_TOP_RATIO;
      float bottomFootroom = cropH * EXTRA_BOTTOM_RATIO;

      RectF extCropRect = new RectF(
          _cropRect.left,
          _cropRect.top - topHeadroom,
          _cropRect.right,
          _cropRect.bottom + bottomFootroom
      );

      RectF srcRect = new RectF();
      _inverseMatrix.mapRect(srcRect, extCropRect);

      int targetW = 1080;
      float totalRatio = 1f + EXTRA_TOP_RATIO + EXTRA_BOTTOM_RATIO;
      int targetH = Math.round((targetW / 1.55f) * totalRatio);

      Bitmap outBmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(outBmp);
      Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

      int bmpW = _bitmap.getWidth();
      int bmpH = _bitmap.getHeight();

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
        canvas.drawBitmap(_bitmap, src, dst, p);

        // Fill edge margins if image was close to boundary
        if (dstT > 0)
        {
          android.graphics.Rect topSrc = new android.graphics.Rect((int) srcL, (int) srcT, (int) srcR, (int) Math.min(bmpH, srcT + 2));
          RectF topDst = new RectF(0, 0, targetW, dstT);
          canvas.drawBitmap(_bitmap, topSrc, topDst, p);
        }
        if (dstB < targetH)
        {
          android.graphics.Rect botSrc = new android.graphics.Rect((int) srcL, (int) Math.max(0, srcB - 2), (int) srcR, (int) srcB);
          RectF botDst = new RectF(0, dstB, targetW, targetH);
          canvas.drawBitmap(_bitmap, botSrc, botDst, p);
        }
      }
      else
      {
        android.graphics.Rect src = new android.graphics.Rect(0, 0, bmpW, bmpH);
        RectF dst = new RectF(0, 0, targetW, targetH);
        canvas.drawBitmap(_bitmap, src, dst, p);
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
