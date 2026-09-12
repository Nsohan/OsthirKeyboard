package com.nhs.customkeyboard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;

public class EmojiGroupButtonsBar extends LinearLayout
{
  private EmojiGridView _emoji_grid = null;
  private final List<EmojiGroupButton> _buttons = new ArrayList<>();
  private int _selectedGroupId = EmojiGridView.GROUP_LAST_USE;

  private Paint _indicatorPaint;
  private float _indicatorLeft = -1f;
  private float _indicatorRight = -1f;
  private ValueAnimator _indicatorAnimator = null;

  private float _barDownX;
  private float _barDownY;
  private boolean _barSwiped = false;
  private boolean _barIsHorizontal = false;
  private int _touchSlop;
  private float _swipeThreshold;
  private GestureDetector _barGestureDetector;

  public EmojiGroupButtonsBar(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    setWillNotDraw(false);

    ViewConfiguration vc = ViewConfiguration.get(context);
    _touchSlop = vc.getScaledTouchSlop();
    _swipeThreshold = 45f * context.getResources().getDisplayMetrics().density;

    _indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    TypedValue tv = new TypedValue();
    int color = 0xFFFFFFFF;
    if (context.getTheme().resolveAttribute(R.attr.colorLabel, tv, true))
      color = tv.data;
    _indicatorPaint.setColor(color);

    _barGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener()
    {
      @Override
      public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy)
      {
        if (e1 == null || e2 == null || _barSwiped)
          return false;
        float diffX = e2.getX() - e1.getX();
        float diffY = e2.getY() - e1.getY();
        if (Math.abs(diffX) > Math.abs(diffY) * 1.2f && Math.abs(diffX) > _touchSlop * 2)
        {
          _barSwiped = true;
          EmojiGridView grid = get_emoji_grid();
          if (grid != null)
          {
            if (diffX < 0)
              grid.selectNextGroup();
            else
              grid.selectPreviousGroup();
          }
          return true;
        }
        return false;
      }
    });

    Emoji.init(context.getResources());
    add_group(EmojiGridView.GROUP_LAST_USE, "\uD83D\uDD59");
    for (int i = 0; i < Emoji.getNumGroups(); i++)
    {
      Emoji first = Emoji.getEmojisByGroup(i).get(0);
      add_group(i, first.kv().getString());
    }
  }

  void add_group(int id, String symbol)
  {
    EmojiGroupButton btn = new EmojiGroupButton(getContext(), id, symbol);
    _buttons.add(btn);
    addView(btn, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1.f));
  }

  EmojiGridView get_emoji_grid()
  {
    if (_emoji_grid == null && getParent() instanceof ViewGroup)
      _emoji_grid = (EmojiGridView)((ViewGroup)getParent()).findViewById(R.id.emoji_grid);
    return _emoji_grid;
  }

  public void setSelectedGroupId(int groupId)
  {
    _selectedGroupId = groupId;
    int tabIndex = EmojiGridView.groupToTabIndex(groupId);
    for (int i = 0; i < _buttons.size(); i++)
    {
      EmojiGroupButton btn = _buttons.get(i);
      boolean isSelected = (i == tabIndex);
      btn.setAlpha(isSelected ? 1.0f : 0.5f);
    }
    updateIndicator(tabIndex);
  }

  private void updateIndicator(int tabIndex)
  {
    if (tabIndex < 0 || tabIndex >= _buttons.size())
      return;
    EmojiGroupButton btn = _buttons.get(tabIndex);
    if (btn.getWidth() == 0)
    {
      post(() -> updateIndicator(tabIndex));
      return;
    }
    float padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics());
    float targetLeft = btn.getLeft() + padding;
    float targetRight = btn.getRight() - padding;

    if (_indicatorLeft < 0)
    {
      _indicatorLeft = targetLeft;
      _indicatorRight = targetRight;
      invalidate();
      return;
    }

    if (_indicatorAnimator != null && _indicatorAnimator.isRunning())
      _indicatorAnimator.cancel();

    float startLeft = _indicatorLeft;
    float startRight = _indicatorRight;
    _indicatorAnimator = ValueAnimator.ofFloat(0f, 1f);
    _indicatorAnimator.setDuration(150);
    _indicatorAnimator.addUpdateListener(animation -> {
      float frac = (float) animation.getAnimatedValue();
      _indicatorLeft = startLeft + (targetLeft - startLeft) * frac;
      _indicatorRight = startRight + (targetRight - startRight) * frac;
      invalidate();
    });
    _indicatorAnimator.start();
  }

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();
    EmojiGridView grid = get_emoji_grid();
    if (grid != null)
      setSelectedGroupId(grid.getCurrentGroup());
  }

  @Override
  protected void dispatchDraw(Canvas canvas)
  {
    super.dispatchDraw(canvas);
    if (_indicatorLeft >= 0 && _indicatorRight > _indicatorLeft)
    {
      float h = getHeight();
      float indicatorHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, getResources().getDisplayMetrics());
      float radius = indicatorHeight / 2f;
      RectF rect = new RectF(_indicatorLeft, h - indicatorHeight, _indicatorRight, h);
      canvas.drawRoundRect(rect, radius, radius, _indicatorPaint);
    }
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent ev)
  {
    int action = ev.getActionMasked();
    switch (action)
    {
      case MotionEvent.ACTION_DOWN:
        _barDownX = ev.getX();
        _barDownY = ev.getY();
        _barSwiped = false;
        _barIsHorizontal = false;
        break;

      case MotionEvent.ACTION_MOVE:
        float dx = ev.getX() - _barDownX;
        float dy = ev.getY() - _barDownY;
        if (!_barSwiped && Math.abs(dx) > _touchSlop && Math.abs(dx) > Math.abs(dy) * 1.2f)
        {
          _barIsHorizontal = true;
          return true;
        }
        break;
    }
    return super.onInterceptTouchEvent(ev);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event)
  {
    if (_barGestureDetector != null)
      _barGestureDetector.onTouchEvent(event);

    int action = event.getActionMasked();
    switch (action)
    {
      case MotionEvent.ACTION_MOVE:
        float dx = event.getX() - _barDownX;
        if (_barIsHorizontal && !_barSwiped && Math.abs(dx) >= _swipeThreshold)
        {
          _barSwiped = true;
          EmojiGridView grid = get_emoji_grid();
          if (grid != null)
          {
            if (dx < 0)
              grid.selectNextGroup();
            else
              grid.selectPreviousGroup();
          }
          return true;
        }
        break;

      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        _barIsHorizontal = false;
        _barSwiped = false;
        return true;
    }
    return true;
  }

  class EmojiGroupButton extends Button
  {
    int _group_id;

    public EmojiGroupButton(Context context, int group_id, String symbol)
    {
      super(new ContextThemeWrapper(context, R.style.emojiTypeButton), null, 0);
      _group_id = group_id;
      setText(symbol);
      setOnClickListener(v -> {
        EmojiGridView grid = get_emoji_grid();
        if (grid != null)
          grid.setEmojiGroup(_group_id);
      });
    }
  }
}
