package com.nhs.customkeyboard;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.nhs.customkeyboard.suggestions.CandidatesView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeyboardMenuManager
{
  public static final int ITEMS_PER_PAGE = 8; // 2 columns x 4 rows
  private static final String PREF_MENU_ORDER = "keyboard_menu_order_v2";

  private final Keyboard2 _keyboardService;
  private View _menuGridPanel;
  private ViewPager2 _viewPager;
  private LinearLayout _dotsContainer;
  private FrameLayout _btnEdit;
  private ImageView _btnEditIcon;

  private final List<MenuItemInfo> _items = new ArrayList<>();
  private MenuPagerAdapter _adapter;

  private boolean _isEditMode = false;
  private String _draggedItemId = null;
  private View _draggedSourceView = null;

  private final Handler _handler = new Handler(Looper.getMainLooper());
  private Runnable _pageFlipRunnable = null;
  private boolean _isFlippingPage = false;

  public static class MenuItemInfo
  {
    public final String id;
    public final int titleRes;
    public final int iconRes;
    public final Runnable action;

    public MenuItemInfo(String id, int titleRes, int iconRes, Runnable action)
    {
      this.id = id;
      this.titleRes = titleRes;
      this.iconRes = iconRes;
      this.action = action;
    }
  }

  public KeyboardMenuManager(Keyboard2 service)
  {
    _keyboardService = service;
  }

  public void setup(View menuGridPanel, CandidatesView candidatesView)
  {
    _menuGridPanel = menuGridPanel;

    if (candidatesView != null)
    {
      candidatesView.setOnMenuToggleListener(new CandidatesView.OnMenuToggleListener()
      {
        @Override
        public void onMenuToggled(boolean isOpen)
        {
          _keyboardService.set_menu_panel_visible(isOpen);
        }
      });
    }

    if (_menuGridPanel == null)
      return;

    _viewPager = _menuGridPanel.findViewById(R.id.menu_view_pager);
    _dotsContainer = _menuGridPanel.findViewById(R.id.menu_dots_container);
    _btnEdit = _menuGridPanel.findViewById(R.id.menu_btn_edit);
    _btnEditIcon = _menuGridPanel.findViewById(R.id.menu_btn_edit_icon);

    loadOrder();

    _adapter = new MenuPagerAdapter();
    if (_viewPager != null)
    {
      _viewPager.setAdapter(_adapter);
      _viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback()
      {
        @Override
        public void onPageSelected(int position)
        {
          updateDots(position);
        }
      });
    }

    setupDots(getPageCount(), _viewPager != null ? _viewPager.getCurrentItem() : 0);
    updateEditButtonUi();

    if (_btnEdit != null)
    {
      _btnEdit.setOnClickListener(v -> toggleEditMode());
    }
  }

  private boolean _animateNextBind = false;

  public void onMenuVisibilityChanged(boolean visible)
  {
    if (visible)
    {
      playMenuEntranceAnimation();
    }
    else
    {
      if (_isEditMode)
      {
        _isEditMode = false;
        updateEditButtonUi();
        if (_adapter != null)
        {
          _adapter.notifyDataSetChanged();
        }
      }
    }
  }

  public void playMenuEntranceAnimation()
  {
    if (_menuGridPanel == null || _viewPager == null) return;

    _animateNextBind = true;

    _viewPager.post(() -> {
      if (_viewPager == null) return;
      if (_viewPager.getChildCount() > 0)
      {
        View rvChild = _viewPager.getChildAt(0);
        if (rvChild instanceof RecyclerView)
        {
          RecyclerView rv = (RecyclerView) rvChild;
          int currentPos = _viewPager.getCurrentItem();
          RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(currentPos);
          if (vh instanceof PageViewHolder)
          {
            ((PageViewHolder) vh).animateTilesEntrance();
            _animateNextBind = false;
          }
        }
      }
    });

    if (_dotsContainer != null)
    {
      _dotsContainer.setAlpha(0f);
      _dotsContainer.animate().alpha(1.0f).setDuration(220).setStartDelay(60).start();
    }
    if (_btnEdit != null)
    {
      _btnEdit.setAlpha(0f);
      _btnEdit.setScaleX(0.85f);
      _btnEdit.setScaleY(0.85f);
      _btnEdit.animate()
          .alpha(1.0f)
          .scaleX(1.0f)
          .scaleY(1.0f)
          .setDuration(220)
          .setStartDelay(60)
          .setInterpolator(new DecelerateInterpolator(1.4f))
          .start();
    }
  }

  private int getColorFromAttr(int attr, int defaultColor)
  {
    TypedValue tv = new TypedValue();
    if (_keyboardService.getTheme().resolveAttribute(attr, tv, true))
    {
      if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT)
      {
        return tv.data;
      }
      else
      {
        try
        {
          return ContextCompat.getColor(_keyboardService, tv.resourceId);
        }
        catch (Exception ignored) {}
      }
    }
    return defaultColor;
  }

  private void updateEditButtonUi()
  {
    if (_btnEdit == null || _btnEditIcon == null) return;

    if (_isEditMode)
    {
      _btnEdit.setBackgroundResource(R.drawable.bg_menu_edit_pill_active);
      _btnEditIcon.setImageResource(R.drawable.ic_check);
      int labelActionColor = getColorFromAttr(R.attr.colorLabelAction,
          getColorFromAttr(R.attr.colorLabel, Color.WHITE));
      _btnEditIcon.setColorFilter(labelActionColor, PorterDuff.Mode.SRC_IN);
    }
    else
    {
      _btnEdit.setBackgroundResource(R.drawable.bg_menu_edit_pill);
      _btnEditIcon.setImageResource(R.drawable.ic_edit_pencil);
      int keyActionColor = getColorFromAttr(R.attr.colorKeyAction,
          getColorFromAttr(R.attr.colorLabel, Color.LTGRAY));
      _btnEditIcon.setColorFilter(keyActionColor, PorterDuff.Mode.SRC_IN);
    }
  }

  private void toggleEditMode()
  {
    _isEditMode = !_isEditMode;
    updateEditButtonUi();

    if (_isEditMode)
    {
      Toast.makeText(_keyboardService, "Drag items to rearrange", Toast.LENGTH_SHORT).show();
    }
    else
    {
      saveOrder();
    }

    if (_adapter != null)
    {
      _adapter.notifyDataSetChanged();
    }
  }

  private List<MenuItemInfo> createDefaultItems()
  {
    List<MenuItemInfo> list = new ArrayList<>();
    // Page 1 - Row 1: Theme & Text edit
    list.add(new MenuItemInfo("theme", R.string.menu_item_theme, R.drawable.ic_palette, () -> {
      _keyboardService.set_menu_panel_visible(false);
      Intent intent = new Intent(_keyboardService, com.nhs.customkeyboard.theme.ThemeActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      _keyboardService.startActivity(intent);
    }));
    list.add(new MenuItemInfo("text_edit", R.string.menu_item_text_edit, R.drawable.ic_text_edit, () -> {
      _keyboardService.show_text_edit_pane();
    }));

    // Page 1 - Row 2: GIF & Emoji
    list.add(new MenuItemInfo("gif", R.string.menu_item_gif, R.drawable.ic_gif, () -> {
      _keyboardService.set_menu_panel_visible(false);
      _keyboardService.show_gif_pane();
    }));
    list.add(new MenuItemInfo("emoji", R.string.menu_item_emoji, R.drawable.ic_emoji_menu, () -> {
      _keyboardService.set_menu_panel_visible(false);
      _keyboardService.show_emoji_pane();
    }));

    // Page 1 - Row 3: Translate & Clipboard
    list.add(new MenuItemInfo("translate", R.string.menu_item_translate, R.drawable.ic_translate, () -> {
      _keyboardService.set_menu_panel_visible(false);
      _keyboardService.toggle_translate_bar();
    }));
    list.add(new MenuItemInfo("clipboard", R.string.menu_item_clipboard, R.drawable.ic_gboard_clipboard, () -> {
      _keyboardService.show_clipboard_pane();
    }));

    // Page 1 - Row 4: Settings & Resize
    list.add(new MenuItemInfo("settings", R.string.menu_item_settings, R.drawable.ic_settings_gear, () -> {
      _keyboardService.set_menu_panel_visible(false);
      _keyboardService.start_activity(SettingsActivity.class);
    }));
    list.add(new MenuItemInfo("resize", R.string.menu_item_resize, R.drawable.ic_resize, () -> {
      _keyboardService.set_menu_panel_visible(false);
      if (_keyboardService.getResizeManager() != null)
      {
        _keyboardService.getResizeManager().startResize();
      }
    }));

    // Page 2 - Row 1: Sticker & One-handed
    list.add(new MenuItemInfo("sticker", R.string.menu_item_sticker, R.drawable.ic_sticker, () -> {
      _keyboardService.set_menu_panel_visible(false);
      _keyboardService.show_emoji_pane();
    }));
    list.add(new MenuItemInfo("one_handed", R.string.menu_item_one_handed, R.drawable.ic_one_handed, () -> {
      _keyboardService.set_menu_panel_visible(false);
      _keyboardService.toggle_one_handed();
    }));
    return list;
  }

  private void loadOrder()
  {
    List<MenuItemInfo> defaults = createDefaultItems();
    Map<String, MenuItemInfo> map = new HashMap<>();
    for (MenuItemInfo info : defaults)
    {
      map.put(info.id, info);
    }

    SharedPreferences prefs = DirectBootAwarePreferences.get_shared_preferences(_keyboardService);
    String savedOrder = prefs.getString(PREF_MENU_ORDER, null);

    _items.clear();
    if (savedOrder != null && !savedOrder.trim().isEmpty())
    {
      String[] ids = savedOrder.split(",");
      for (String id : ids)
      {
        String trimmed = id.trim();
        if (map.containsKey(trimmed))
        {
          _items.add(map.remove(trimmed));
        }
      }
    }

    for (MenuItemInfo info : defaults)
    {
      if (map.containsKey(info.id))
      {
        _items.add(info);
      }
    }
  }

  private void saveOrder()
  {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < _items.size(); i++)
    {
      if (i > 0) sb.append(",");
      sb.append(_items.get(i).id);
    }
    SharedPreferences prefs = DirectBootAwarePreferences.get_shared_preferences(_keyboardService);
    prefs.edit().putString(PREF_MENU_ORDER, sb.toString()).apply();
  }

  private int getPageCount()
  {
    return Math.max(1, (int) Math.ceil((double) _items.size() / ITEMS_PER_PAGE));
  }

  private void setupDots(int pageCount, int activePage)
  {
    if (_dotsContainer == null) return;
    _dotsContainer.removeAllViews();

    if (pageCount <= 1)
    {
      _dotsContainer.setVisibility(View.GONE);
      return;
    }

    _dotsContainer.setVisibility(View.VISIBLE);
    float density = _keyboardService.getResources().getDisplayMetrics().density;
    int dotSize = (int) (5 * density);
    int dotMargin = (int) (5 * density);

    for (int i = 0; i < pageCount; i++)
    {
      View dot = new View(_keyboardService);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dotSize, dotSize);
      if (i > 0)
      {
        lp.setMarginStart(dotMargin);
      }
      dot.setLayoutParams(lp);
      dot.setBackgroundResource(i == activePage ? R.drawable.menu_dot_active : R.drawable.menu_dot_inactive);
      _dotsContainer.addView(dot);
    }
  }

  private void updateDots(int activePage)
  {
    if (_dotsContainer == null) return;
    int count = _dotsContainer.getChildCount();
    for (int i = 0; i < count; i++)
    {
      View dot = _dotsContainer.getChildAt(i);
      dot.setBackgroundResource(i == activePage ? R.drawable.menu_dot_active : R.drawable.menu_dot_inactive);
    }
  }

  private class MenuPagerAdapter extends RecyclerView.Adapter<PageViewHolder>
  {
    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
      View view = LayoutInflater.from(parent.getContext())
          .inflate(R.layout.keyboard_menu_page, parent, false);
      return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position)
    {
      holder.bind(position);
    }

    @Override
    public int getItemCount()
    {
      return getPageCount();
    }
  }

  private class PageViewHolder extends RecyclerView.ViewHolder
  {
    final View[] slotViews = new View[ITEMS_PER_PAGE];
    final ImageView[] slotIcons = new ImageView[ITEMS_PER_PAGE];
    final TextView[] slotTexts = new TextView[ITEMS_PER_PAGE];
    final ImageView[] slotIndicators = new ImageView[ITEMS_PER_PAGE];

    PageViewHolder(@NonNull View itemView)
    {
      super(itemView);
      int[] slotIds = {
          R.id.menu_slot_0, R.id.menu_slot_1,
          R.id.menu_slot_2, R.id.menu_slot_3,
          R.id.menu_slot_4, R.id.menu_slot_5,
          R.id.menu_slot_6, R.id.menu_slot_7
      };
      int[] iconIds = {
          R.id.slot_0_icon, R.id.slot_1_icon,
          R.id.slot_2_icon, R.id.slot_3_icon,
          R.id.slot_4_icon, R.id.slot_5_icon,
          R.id.slot_6_icon, R.id.slot_7_icon
      };
      int[] textIds = {
          R.id.slot_0_text, R.id.slot_1_text,
          R.id.slot_2_text, R.id.slot_3_text,
          R.id.slot_4_text, R.id.slot_5_text,
          R.id.slot_6_text, R.id.slot_7_text
      };
      int[] indicatorIds = {
          R.id.slot_0_indicator, R.id.slot_1_indicator,
          R.id.slot_2_indicator, R.id.slot_3_indicator,
          R.id.slot_4_indicator, R.id.slot_5_indicator,
          R.id.slot_6_indicator, R.id.slot_7_indicator
      };

      for (int i = 0; i < ITEMS_PER_PAGE; i++)
      {
        slotViews[i] = itemView.findViewById(slotIds[i]);
        slotIcons[i] = itemView.findViewById(iconIds[i]);
        slotTexts[i] = itemView.findViewById(textIds[i]);
        slotIndicators[i] = itemView.findViewById(indicatorIds[i]);
      }
    }

    void bind(int pagePosition)
    {
      for (int i = 0; i < ITEMS_PER_PAGE; i++)
      {
        final int currentSlotIndex = i;
        final int itemIndex = pagePosition * ITEMS_PER_PAGE + i;
        View slotView = slotViews[i];
        ImageView iconView = slotIcons[i];
        TextView textView = slotTexts[i];
        ImageView indicatorView = slotIndicators[i];

        if (itemIndex < _items.size())
        {
          MenuItemInfo item = _items.get(itemIndex);
          slotView.setVisibility(View.VISIBLE);
          slotView.setAlpha(1.0f);
          slotView.setTranslationY(0f);
          slotView.setScaleX(1.0f);
          slotView.setScaleY(1.0f);
          iconView.setImageResource(item.iconRes);
          textView.setText(item.titleRes);

          if (_isEditMode)
          {
            indicatorView.setImageResource(R.drawable.ic_drag_handle);
          }
          else
          {
            indicatorView.setImageResource(R.drawable.ic_chevron_right);
          }

          slotView.setOnClickListener(v -> {
            if (!_isEditMode)
            {
              item.action.run();
            }
          });

          slotView.setOnLongClickListener(v -> {
            if (_isEditMode)
            {
              int pagePos = getAdapterPosition();
              if (pagePos == RecyclerView.NO_POSITION) pagePos = getAdapterPosition();
              int currentPos = (pagePos >= 0 ? pagePos : pagePosition) * ITEMS_PER_PAGE + currentSlotIndex;
              startDrag(v, item, currentPos);
              return true;
            }
            return false;
          });

          slotView.setOnTouchListener(new View.OnTouchListener()
          {
            private float startX, startY;
            private boolean isDragStarted = false;
            private final int touchSlop = ViewConfiguration.get(slotView.getContext()).getScaledTouchSlop();

            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
              if (!_isEditMode) return false;

              switch (event.getAction())
              {
                case MotionEvent.ACTION_DOWN:
                  startX = event.getRawX();
                  startY = event.getRawY();
                  isDragStarted = false;
                  break;

                case MotionEvent.ACTION_MOVE:
                  if (!isDragStarted)
                  {
                    float dx = Math.abs(event.getRawX() - startX);
                    float dy = Math.abs(event.getRawY() - startY);
                    if (dx > touchSlop * 1.8f || dy > touchSlop * 1.8f)
                    {
                      isDragStarted = true;
                      int pagePos = getAdapterPosition();
                      if (pagePos == RecyclerView.NO_POSITION) pagePos = getAdapterPosition();
                      int currentPos = (pagePos >= 0 ? pagePos : pagePosition) * ITEMS_PER_PAGE + currentSlotIndex;
                      startDrag(v, item, currentPos);
                      return true;
                    }
                  }
                  break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                  isDragStarted = false;
                  break;
              }
              return false;
            }
          });
        }
        else
        {
          slotView.setVisibility(View.INVISIBLE);
          slotView.setAlpha(1.0f);
          slotView.setOnClickListener(null);
          slotView.setOnLongClickListener(null);
          slotView.setOnTouchListener(null);
        }

        slotView.setOnDragListener((v, event) ->
            handleSlotDrag(v, event, this, currentSlotIndex));
      }

      if (_animateNextBind && pagePosition == (_viewPager != null ? _viewPager.getCurrentItem() : 0))
      {
        _animateNextBind = false;
        itemView.post(this::animateTilesEntrance);
      }
    }

    void animateTilesEntrance()
    {
      if (itemView == null) return;
      float density = itemView.getResources().getDisplayMetrics().density;
      float translateY = 18f * density;

      for (int i = 0; i < ITEMS_PER_PAGE; i++)
      {
        final View slotView = slotViews[i];
        if (slotView != null && slotView.getVisibility() == View.VISIBLE)
        {
          slotView.animate().cancel();
          slotView.setAlpha(0f);
          slotView.setTranslationY(translateY);
          slotView.setScaleX(0.92f);
          slotView.setScaleY(0.92f);

          int row = i / 2;
          long delay = row * 35L;

          slotView.animate()
              .alpha(1.0f)
              .translationY(0f)
              .scaleX(1.0f)
              .scaleY(1.0f)
              .setDuration(200)
              .setStartDelay(delay)
              .setInterpolator(new DecelerateInterpolator(1.4f))
              .start();
        }
      }
    }
  }

  private void startDrag(View v, MenuItemInfo item, int sourceIndex)
  {
    _draggedItemId = item.id;
    _draggedSourceView = v;
    if (_viewPager != null)
    {
      _viewPager.setUserInputEnabled(false);
    }

    ClipData data = ClipData.newPlainText("menu_item_id", item.id);
    View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(v);

    v.setAlpha(0.25f);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
    {
      v.startDragAndDrop(data, shadowBuilder, item, 0);
    }
    else
    {
      v.startDrag(data, shadowBuilder, item, 0);
    }
  }

  private boolean handleSlotDrag(View v, DragEvent event, PageViewHolder holder, int slotIndex)
  {
    switch (event.getAction())
    {
      case DragEvent.ACTION_DRAG_STARTED:
        return true;

      case DragEvent.ACTION_DRAG_ENTERED:
        if (_isEditMode && v != _draggedSourceView)
        {
          v.setAlpha(0.6f);
        }
        return true;

      case DragEvent.ACTION_DRAG_EXITED:
        if (v != _draggedSourceView)
        {
          v.setAlpha(1.0f);
        }
        return true;

      case DragEvent.ACTION_DRAG_LOCATION:
        checkEdgeScroll(event.getX(), v);
        return true;

      case DragEvent.ACTION_DROP:
        if (v != _draggedSourceView)
        {
          v.setAlpha(1.0f);
        }
        cancelPageFlip();

        if (_draggedItemId == null) return false;

        int sourceIndex = -1;
        for (int i = 0; i < _items.size(); i++)
        {
          if (_items.get(i).id.equals(_draggedItemId))
          {
            sourceIndex = i;
            break;
          }
        }

        int pagePos = holder.getAdapterPosition();
        if (pagePos == RecyclerView.NO_POSITION)
        {
          pagePos = holder.getAdapterPosition();
        }
        if (pagePos < 0) return false;

        int targetIndex = pagePos * ITEMS_PER_PAGE + slotIndex;
        if (targetIndex >= _items.size())
        {
          targetIndex = _items.size() - 1;
        }

        // Exact SWAP rule:
        // "if i drag 2 to place 5 the 2 will be on 5 position and the 5 will be on 2 positions"
        if (sourceIndex >= 0 && targetIndex >= 0 && sourceIndex != targetIndex)
        {
          Collections.swap(_items, sourceIndex, targetIndex);
          saveOrder();
          if (_adapter != null)
          {
            _adapter.notifyDataSetChanged();
          }
        }
        return true;

      case DragEvent.ACTION_DRAG_ENDED:
        if (v != _draggedSourceView)
        {
          v.setAlpha(1.0f);
        }
        if (_draggedSourceView != null)
        {
          _draggedSourceView.setAlpha(1.0f);
          _draggedSourceView = null;
        }
        cancelPageFlip();
        if (_viewPager != null)
        {
          _viewPager.setUserInputEnabled(true);
        }
        _draggedItemId = null;
        return true;
    }
    return false;
  }

  private void checkEdgeScroll(float localX, View childView)
  {
    if (_viewPager == null) return;

    int[] pagerLoc = new int[2];
    _viewPager.getLocationOnScreen(pagerLoc);

    int[] viewLoc = new int[2];
    childView.getLocationOnScreen(viewLoc);

    float screenX = viewLoc[0] + localX;
    float relativeX = screenX - pagerLoc[0];

    float edgeThreshold = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 45, _keyboardService.getResources().getDisplayMetrics());

    int currentItem = _viewPager.getCurrentItem();
    int pageCount = getPageCount();

    if (relativeX < edgeThreshold && currentItem > 0)
    {
      schedulePageFlip(currentItem - 1);
    }
    else if (relativeX > _viewPager.getWidth() - edgeThreshold && currentItem < pageCount - 1)
    {
      schedulePageFlip(currentItem + 1);
    }
    else
    {
      cancelPageFlip();
    }
  }

  private void schedulePageFlip(int targetPage)
  {
    if (_isFlippingPage) return;
    if (_pageFlipRunnable != null) return;

    _pageFlipRunnable = () -> {
      if (_viewPager != null && _viewPager.getCurrentItem() != targetPage)
      {
        _isFlippingPage = true;
        _viewPager.setCurrentItem(targetPage, true);
        _handler.postDelayed(() -> _isFlippingPage = false, 500);
      }
      _pageFlipRunnable = null;
    };
    _handler.postDelayed(_pageFlipRunnable, 400);
  }

  private void cancelPageFlip()
  {
    if (_pageFlipRunnable != null)
    {
      _handler.removeCallbacks(_pageFlipRunnable);
      _pageFlipRunnable = null;
    }
  }
}
