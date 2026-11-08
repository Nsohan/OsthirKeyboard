package com.nhs.customkeyboard.tasker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Android Drawable that renders any Google Material Symbol glyph from the bundled TTF font asset.
 * Adapts to any icon size, bounds, and tint color (ColorFilter) for use in ImageViews and ImageButtons.
 */
public class MaterialSymbolDrawable extends Drawable
{
    private final TextPaint mPaint;
    private final String mGlyph;
    private final int mIntrinsicSize;

    public MaterialSymbolDrawable(Context context, String glyph, int sizePx, int color)
    {
        mGlyph = (glyph != null && !glyph.trim().isEmpty()) ? glyph.trim() : MaterialSymbols.DEFAULT_ICON;
        mIntrinsicSize = Math.max(1, sizePx);

        mPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        mPaint.setColor(color);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setTextSize(sizePx * 0.90f);

        if (context != null)
        {
            Typeface tf = MaterialSymbols.getTypeface(context);
            if (tf != null)
            {
                mPaint.setTypeface(tf);
            }
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas)
    {
        Rect bounds = getBounds();
        if (bounds.isEmpty())
        {
            bounds = new Rect(0, 0, mIntrinsicSize, mIntrinsicSize);
        }
        float x = bounds.exactCenterX();
        Paint.FontMetrics fm = mPaint.getFontMetrics();
        float y = bounds.exactCenterY() - (fm.descent + fm.ascent) / 2.0f;
        canvas.drawText(mGlyph, x, y, mPaint);
    }

    @Override
    public int getIntrinsicWidth()
    {
        return mIntrinsicSize;
    }

    @Override
    public int getIntrinsicHeight()
    {
        return mIntrinsicSize;
    }

    @Override
    public void setAlpha(int alpha)
    {
        mPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter)
    {
        mPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity()
    {
        return PixelFormat.TRANSLUCENT;
    }
}

