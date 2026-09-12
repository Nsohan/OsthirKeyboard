package com.nhs.customkeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EmojiGridView extends GridView
  implements GridView.OnItemClickListener
{
  public static final int GROUP_LAST_USE = -1;

  private static final String LAST_USE_PREF = "emoji_last_use";

  private List<Emoji> _emojiArray;
  private HashMap<Emoji, Integer> _lastUsed;
  private int _currentGroup = GROUP_LAST_USE;

  private float _downX;
  private float _downY;
  private boolean _swiped = false;
  private boolean _isScrollingVertical = false;
  private boolean _isHorizontalGesture = false;
  private int _touchSlop;
  private float _swipeThreshold;
  private GestureDetector _gestureDetector;

  /*
   ** TODO: adapt column width and emoji size
   ** TODO: use ArraySet instead of Emoji[]
   */
  public EmojiGridView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    initSwipe(context);
    Emoji.init(context.getResources());
    setOnItemClickListener(this);
    loadLastUsed();
    setEmojiGroup((_lastUsed.size() == 0) ? 0 : GROUP_LAST_USE);
  }

  private void initSwipe(Context context)
  {
    ViewConfiguration vc = ViewConfiguration.get(context);
    _touchSlop = vc.getScaledTouchSlop();
    _swipeThreshold = 45f * context.getResources().getDisplayMetrics().density;
    _gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener()
    {
      @Override
      public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy)
      {
        if (e1 == null || e2 == null || _swiped)
          return false;
        float diffX = e2.getX() - e1.getX();
        float diffY = e2.getY() - e1.getY();
        if (Math.abs(diffX) > Math.abs(diffY) * 1.2f && Math.abs(diffX) > _touchSlop * 2)
        {
          _swiped = true;
          _isHorizontalGesture = true;
          if (diffX < 0)
            selectNextGroup();
          else
            selectPreviousGroup();
          return true;
        }
        return false;
      }
    });
  }

  public int getCurrentGroup()
  {
    return _currentGroup;
  }

  public static int groupToTabIndex(int groupId)
  {
    if (groupId == GROUP_LAST_USE)
      return 0;
    return groupId + 1;
  }

  public static int tabIndexToGroup(int tabIndex)
  {
    if (tabIndex <= 0)
      return GROUP_LAST_USE;
    return tabIndex - 1;
  }

  public static int getTotalGroups()
  {
    return 1 + Emoji.getNumGroups();
  }

  public void selectNextGroup()
  {
    int currentIdx = groupToTabIndex(_currentGroup);
    int total = getTotalGroups();
    if (currentIdx < total - 1)
    {
      setEmojiGroup(tabIndexToGroup(currentIdx + 1));
      try { VibratorCompat.vibrate(this, Config.globalConfig()); } catch (Exception ignored) {}
    }
  }

  public void selectPreviousGroup()
  {
    int currentIdx = groupToTabIndex(_currentGroup);
    if (currentIdx > 0)
    {
      setEmojiGroup(tabIndexToGroup(currentIdx - 1));
      try { VibratorCompat.vibrate(this, Config.globalConfig()); } catch (Exception ignored) {}
    }
  }

  EmojiGroupButtonsBar get_group_bar()
  {
    if (getParent() instanceof ViewGroup)
      return (EmojiGroupButtonsBar)((ViewGroup)getParent()).findViewById(R.id.emoji_group_bar);
    return null;
  }

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();
    EmojiGroupButtonsBar bar = get_group_bar();
    if (bar != null)
      bar.setSelectedGroupId(_currentGroup);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event)
  {
    if (_gestureDetector != null)
      _gestureDetector.onTouchEvent(event);

    int action = event.getActionMasked();
    switch (action)
    {
      case MotionEvent.ACTION_DOWN:
        _downX = event.getX();
        _downY = event.getY();
        _swiped = false;
        _isScrollingVertical = false;
        _isHorizontalGesture = false;
        break;

      case MotionEvent.ACTION_MOVE:
        float dx = event.getX() - _downX;
        float dy = event.getY() - _downY;
        float absDx = Math.abs(dx);
        float absDy = Math.abs(dy);

        if (!_swiped && !_isScrollingVertical)
        {
          if (absDy > _touchSlop && absDy > absDx)
          {
            _isScrollingVertical = true;
          }
          else if (absDx > _touchSlop && absDx > absDy * 1.2f)
          {
            _isHorizontalGesture = true;
            MotionEvent cancel = MotionEvent.obtain(event);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.onTouchEvent(cancel);
            cancel.recycle();
          }
        }

        if (_isHorizontalGesture && !_swiped)
        {
          if (absDx >= _swipeThreshold)
          {
            _swiped = true;
            if (dx < 0)
              selectNextGroup();
            else
              selectPreviousGroup();
          }
          return true;
        }

        if (_isHorizontalGesture)
          return true;

        break;

      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        if (_isHorizontalGesture)
        {
          _isHorizontalGesture = false;
          _swiped = false;
          return true;
        }
        break;
    }

    return super.onTouchEvent(event);
  }

  public void setEmojiGroup(int group)
  {
    _currentGroup = group;
    _emojiArray = (group == GROUP_LAST_USE) ? getLastEmojis() : Emoji.getEmojisByGroup(group);
    setAdapter(new EmojiViewAdpater(getContext(), _emojiArray));
    EmojiGroupButtonsBar bar = get_group_bar();
    if (bar != null)
      bar.setSelectedGroupId(group);
  }

  public void onItemClick(AdapterView<?> parent, View v, int pos, long id)
  {
    Config config = Config.globalConfig();
    Integer used = _lastUsed.get(_emojiArray.get(pos));
    _lastUsed.put(_emojiArray.get(pos), (used == null) ? 1 : used.intValue() + 1);
    config.handler.key_up(_emojiArray.get(pos).kv(), Pointers.Modifiers.EMPTY);
    saveLastUsed(); // TODO: opti
  }

  private List<Emoji> getLastEmojis()
  {
    List<Emoji> list = new ArrayList<>(_lastUsed.keySet());
    Collections.sort(list, new Comparator<Emoji>()
        {
          public int compare(Emoji a, Emoji b)
          {
            return _lastUsed.get(b) - _lastUsed.get(a);
          }
        });
    return list;
  }

  private void saveLastUsed()
  {
    SharedPreferences.Editor edit;
    try { edit = emojiSharedPreferences().edit(); }
    catch (Exception _e) { return; }
    HashSet<String> set = new HashSet<String>();
    for (Emoji emoji : _lastUsed.keySet())
      set.add(String.valueOf(_lastUsed.get(emoji)) + "-" + emoji.kv().getString());
    edit.putStringSet(LAST_USE_PREF, set);
    edit.apply();
  }

  private void loadLastUsed()
  {
    _lastUsed = new HashMap<Emoji, Integer>();
    SharedPreferences prefs;
    // Storage might not be available (eg. the device is locked), avoid
    // crashing.
    try { prefs = emojiSharedPreferences(); }
    catch (Exception _e) { return; }
    Set<String> lastUseSet = prefs.getStringSet(LAST_USE_PREF, null);
    if (lastUseSet != null)
      for (String emojiData : lastUseSet)
      {
        String[] data = emojiData.split("-", 2);
        Emoji emoji;
        if (data.length != 2)
          continue ;
        emoji = Emoji.getEmojiByString(data[1]);
        if (emoji == null)
          continue ;
        _lastUsed.put(emoji, Integer.valueOf(data[0]));
      }
  }

  SharedPreferences emojiSharedPreferences()
  {
    return getContext().getSharedPreferences("emoji_last_use", Context.MODE_PRIVATE);
  }

  static class EmojiView extends TextView
  {
    public EmojiView(Context context)
    {
      super(context);
    }

    public void setEmoji(Emoji emoji)
    {
      setText(emoji.kv().getString());
    }
  }

  static class EmojiViewAdpater extends BaseAdapter
  {
    Context _button_context;

    List<Emoji> _emojiArray;

    public EmojiViewAdpater(Context context, List<Emoji> emojiArray)
    {
      _button_context = new ContextThemeWrapper(context, R.style.emojiGridButton);
      _emojiArray = emojiArray;
    }

    public int getCount()
    {
      if (_emojiArray == null)
        return (0);
      return (_emojiArray.size());
    }

    public Object getItem(int pos)
    {
      return (_emojiArray.get(pos));
    }

    public long getItemId(int pos)
    {
      return (pos);
    }

    public View getView(int pos, View convertView, ViewGroup parent)
    {
      EmojiView view = (EmojiView)convertView;

      if (view == null)
        view = new EmojiView(_button_context);
      view.setEmoji(_emojiArray.get(pos));
      return view;
    }
  }
}
