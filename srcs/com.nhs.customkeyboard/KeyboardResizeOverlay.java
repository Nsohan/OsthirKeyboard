package com.nhs.customkeyboard;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class KeyboardResizeOverlay extends FrameLayout
{
  private Keyboard2View _keyboardView;

  public KeyboardResizeOverlay(Context context)
  {
    super(context);
  }

  public KeyboardResizeOverlay(Context context, AttributeSet attrs)
  {
    super(context, attrs);
  }

  public KeyboardResizeOverlay(Context context, AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
  }

  public void setKeyboardView(Keyboard2View kv)
  {
    _keyboardView = kv;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
    int targetH = 0;
    if (_keyboardView != null && _keyboardView.getMeasuredHeight() > 0)
    {
      targetH = _keyboardView.getMeasuredHeight();
    }
    else if (_keyboardView != null && _keyboardView.getHeight() > 0)
    {
      targetH = _keyboardView.getHeight();
    }

    if (targetH > 0)
    {
      int exactSpec = MeasureSpec.makeMeasureSpec(targetH, MeasureSpec.EXACTLY);
      super.onMeasure(widthMeasureSpec, exactSpec);
      setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), targetH);
    }
    else
    {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
  }
}
