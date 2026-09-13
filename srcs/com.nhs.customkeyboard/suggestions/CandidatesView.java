package com.nhs.customkeyboard.suggestions;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build.VERSION;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.Toast;
import androidx.preference.PreferenceManager;
import com.nhs.customkeyboard.TaskerAutomationConfig;
import com.nhs.customkeyboard.TaskerBridge;
import com.nhs.customkeyboard.VibratorCompat;
import com.nhs.customkeyboard.prefs.TaskerAutomationManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.nhs.customkeyboard.Config;
import com.nhs.customkeyboard.KeyValue;
import com.nhs.customkeyboard.Logs;
import com.nhs.customkeyboard.Pointers;
import com.nhs.customkeyboard.R;
import com.nhs.customkeyboard.SettingsActivity;

public class CandidatesView extends LinearLayout
{
  public interface OnMenuToggleListener
  {
    void onMenuToggled(boolean isOpen);
  }

  public interface OnTranslateToggleListener
  {
    void onTranslateToggled();
  }

  private OnMenuToggleListener _menu_toggle_listener;
  private OnTranslateToggleListener _translate_toggle_listener;
  private ImageButton _tools_menu_button;
  private FrameLayout _candidates_center_frame;
  private LinearLayout _candidates_words_layout;
  private View _tools_spacer;
  private boolean _tools_open = false;
  private boolean _tools_expanded_over_suggestions = false;
  private boolean _is_animating = false;

  private View _tools_action_scroll;
  private LinearLayout _tools_action_bar;
  private boolean _has_suggestions = false;

  private TextView _emoji_view;
  private HorizontalScrollView _scroll_view;
  private LinearLayout _container;
  private View _status_no_dict = null;
  private View _dictionary_switch_button;
  private View _voice_typing_button;
  private boolean should_show_dictionary_switch = false;
  private boolean show_toolbar = true;
  private boolean show_voice_typing = true;

  private final List<TextView> _item_pool = new ArrayList<>();
  private float _cached_text_size = 0f;

  public CandidatesView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
  }

  public void setOnMenuToggleListener(OnMenuToggleListener listener)
  {
    _menu_toggle_listener = listener;
  }

  public void setOnTranslateToggleListener(OnTranslateToggleListener listener)
  {
    _translate_toggle_listener = listener;
  }

  public boolean isMenuOpen()
  {
    return _tools_open;
  }

  public void setMenuOpen(boolean open)
  {
    if (_tools_open != open)
    {
      _tools_open = open;
      if (_tools_open)
      {
        _tools_expanded_over_suggestions = false;
      }
      update_toolbar_visibility();
      if (_menu_toggle_listener != null)
      {
        _menu_toggle_listener.onMenuToggled(_tools_open);
      }
    }
  }

  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();
    _tools_menu_button = findViewById(R.id.tools_menu_button);
    _candidates_center_frame = findViewById(R.id.candidates_center_frame);
    _candidates_words_layout = findViewById(R.id.candidates_words_layout);
    _tools_spacer = findViewById(R.id.tools_spacer);
    _tools_action_scroll = findViewById(R.id.tools_action_scroll);
    _tools_action_bar = findViewById(R.id.tools_action_bar);
    _emoji_view = findViewById(R.id.candidates_emoji);
    _scroll_view = findViewById(R.id.candidates_scroll);
    _container = findViewById(R.id.candidates_container);

    setup_tools_menu();
    setup_action_buttons();
    setup_dictionary_switch_button();
    setup_voice_typing_button();
  }

  public void set_candidates(Suggestions s)
  {
    int s_count = (s != null) ? s.count : 0;
    boolean prev_has_suggestions = _has_suggestions;
    _has_suggestions = (s_count > 0) || (s != null && s.emoji_suggestion != null && s.emoji_suggestion.length() > 0);

    if (s_count > 0 && !_tools_open)
    {
      _tools_open = false;
    }

    populate_candidate_views(s, s_count);

    if (_tools_open)
    {
      update_toolbar_visibility();
      return;
    }

    if (_has_suggestions)
    {
      if (!prev_has_suggestions)
      {
        _tools_expanded_over_suggestions = false;
        animate_collapse_tools_into_menu();
      }
      else
      {
        if (_tools_expanded_over_suggestions)
        {
          _tools_expanded_over_suggestions = false;
          animate_collapse_tools_into_menu();
        }
        else
        {
          if (_candidates_words_layout != null && _candidates_words_layout.getVisibility() != View.VISIBLE)
          {
            _candidates_words_layout.setVisibility(View.VISIBLE);
            _candidates_words_layout.setAlpha(1f);
            _candidates_words_layout.setTranslationX(0f);
          }
          if (_tools_action_scroll != null && _tools_action_scroll.getVisibility() != View.GONE)
          {
            _tools_action_scroll.setVisibility(View.GONE);
          }
        }
      }
    }
    else
    {
      if (prev_has_suggestions)
      {
        _tools_expanded_over_suggestions = false;
        animate_expand_tools_from_menu();
      }
      else
      {
        _tools_expanded_over_suggestions = false;
        update_toolbar_visibility();
      }
    }

    if (_voice_typing_button != null)
    {
      _voice_typing_button.setVisibility(show_voice_typing ? View.VISIBLE : View.GONE);
    }

    if (_dictionary_switch_button != null)
    {
      _dictionary_switch_button.setVisibility(
          should_show_dictionary_switch ? View.VISIBLE : View.GONE);
    }
  }

  private void populate_candidate_views(Suggestions s, int s_count)
  {
    if (s != null && s.emoji_suggestion != null && _emoji_view != null)
    {
      final String emoji = s.emoji_suggestion;
      _emoji_view.setText(emoji);
      _emoji_view.setVisibility(View.VISIBLE);
      _emoji_view.setOnClickListener(new OnClickListener()
      {
        @Override
        public void onClick(View _v)
        {
          Config.globalConfig().handler.suggestion_entered(emoji);
        }
      });
    }
    else if (_emoji_view != null)
    {
      _emoji_view.setVisibility(View.GONE);
      _emoji_view.setOnClickListener(null);
    }

    if (s_count != 0 && _status_no_dict != null)
      _status_no_dict.setVisibility(View.GONE);

    if (_container != null)
    {
      _container.removeAllViews();
      for (int i = 0; i < s_count; i++)
      {
        final String word = s.suggestions[i];
        if (word == null) continue;

        TextView v = get_or_create_item_view(i);
        v.setText(word);
        v.setOnClickListener(new OnClickListener()
        {
          @Override
          public void onClick(View _v)
          {
            Config.globalConfig().handler.suggestion_entered(word + " ");
          }
        });
        _container.addView(v);
      }
    }

    if (_scroll_view != null)
      _scroll_view.scrollTo(0, 0);
  }

  private float get_anim_distance()
  {
    if (_tools_action_scroll != null && _tools_action_scroll.getWidth() > 0)
    {
      return _tools_action_scroll.getWidth();
    }
    if (_candidates_center_frame != null && _candidates_center_frame.getWidth() > 0)
    {
      return _candidates_center_frame.getWidth();
    }
    return 200 * getResources().getDisplayMetrics().density;
  }

  private void update_menu_button_icon(final int resId, boolean animated)
  {
    if (_tools_menu_button == null) return;
    if (!animated)
    {
      _tools_menu_button.setImageResource(resId);
      _tools_menu_button.setRotation(0f);
      return;
    }

    _tools_menu_button.animate().cancel();
    _tools_menu_button.animate()
        .rotation(90f)
        .setDuration(100)
        .withEndAction(new Runnable()
        {
          @Override
          public void run()
          {
            if (_tools_menu_button != null)
            {
              _tools_menu_button.setImageResource(resId);
              _tools_menu_button.setRotation(-90f);
              _tools_menu_button.animate()
                  .rotation(0f)
                  .setDuration(100)
                  .start();
            }
          }
        })
        .start();
  }

  private void animate_collapse_tools_into_menu()
  {
    if (_tools_action_scroll == null) return;

    final float distance = get_anim_distance();
    _is_animating = true;

    update_menu_button_icon(R.drawable.ic_grid_menu, true);

    if (_candidates_words_layout != null)
    {
      _candidates_words_layout.animate().cancel();
      _candidates_words_layout.setVisibility(View.VISIBLE);
      _candidates_words_layout.setAlpha(0f);
      _candidates_words_layout.setTranslationX(30 * getResources().getDisplayMetrics().density);
      _candidates_words_layout.animate()
          .alpha(1f)
          .translationX(0f)
          .setDuration(220)
          .setInterpolator(new DecelerateInterpolator())
          .start();
    }

    _tools_action_scroll.animate().cancel();
    _tools_action_scroll.setVisibility(View.VISIBLE);
    _tools_action_scroll.setAlpha(1f);
    _tools_action_scroll.setTranslationX(0f);
    _tools_action_scroll.setPivotX(0f);
    _tools_action_scroll.animate()
        .translationX(-distance)
        .alpha(0f)
        .scaleX(0.85f)
        .setDuration(200)
        .setInterpolator(new AccelerateInterpolator())
        .withEndAction(new Runnable()
        {
          @Override
          public void run()
          {
            if (_tools_action_scroll != null)
            {
              _tools_action_scroll.setVisibility(View.GONE);
              _tools_action_scroll.setTranslationX(0f);
              _tools_action_scroll.setAlpha(1f);
              _tools_action_scroll.setScaleX(1f);
            }
            _is_animating = false;
          }
        })
        .start();
  }

  private void animate_expand_tools_from_menu()
  {
    if (_tools_action_scroll == null) return;

    final float distance = get_anim_distance();
    _is_animating = true;

    int iconRes = _has_suggestions ? R.drawable.ic_arrow_back : R.drawable.ic_grid_menu;
    update_menu_button_icon(iconRes, true);

    if (_candidates_words_layout != null)
    {
      _candidates_words_layout.animate().cancel();
      _candidates_words_layout.animate()
          .alpha(0f)
          .translationX(30 * getResources().getDisplayMetrics().density)
          .setDuration(180)
          .setInterpolator(new AccelerateInterpolator())
          .withEndAction(new Runnable()
          {
            @Override
            public void run()
            {
              if (_candidates_words_layout != null)
              {
                _candidates_words_layout.setVisibility(View.GONE);
                _candidates_words_layout.setAlpha(1f);
                _candidates_words_layout.setTranslationX(0f);
              }
            }
          })
          .start();
    }

    _tools_action_scroll.animate().cancel();
    _tools_action_scroll.setVisibility(View.VISIBLE);
    _tools_action_scroll.setTranslationX(-distance);
    _tools_action_scroll.setAlpha(0f);
    _tools_action_scroll.setPivotX(0f);
    _tools_action_scroll.setScaleX(0.85f);

    _tools_action_scroll.animate()
        .translationX(0f)
        .alpha(1f)
        .scaleX(1f)
        .setDuration(220)
        .setInterpolator(new DecelerateInterpolator())
        .withEndAction(new Runnable()
        {
          @Override
          public void run()
          {
            _is_animating = false;
          }
        })
        .start();
  }

  private void update_toolbar_visibility()
  {
    if (_tools_menu_button != null)
    {
      _tools_menu_button.setVisibility(show_toolbar ? View.VISIBLE : View.GONE);
      int iconRes = _tools_open ? R.drawable.ic_close
          : (_tools_expanded_over_suggestions ? R.drawable.ic_arrow_back : R.drawable.ic_grid_menu);
      _tools_menu_button.setImageResource(iconRes);
      _tools_menu_button.setRotation(0f);
      TypedValue outValue = new TypedValue();
      getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
      _tools_menu_button.setBackgroundResource(outValue.resourceId);
      int pad = (int)(7 * getResources().getDisplayMetrics().density);
      _tools_menu_button.setPadding(pad, pad, pad, pad);
      TypedValue colorVal = new TypedValue();
      if (getContext().getTheme().resolveAttribute(R.attr.colorLabel, colorVal, true))
      {
        _tools_menu_button.setColorFilter(colorVal.data);
      }
    }

    if (_tools_open)
    {
      if (_tools_spacer != null) _tools_spacer.setVisibility(View.VISIBLE);
      if (_tools_action_scroll != null) _tools_action_scroll.setVisibility(View.GONE);
      if (_candidates_words_layout != null) _candidates_words_layout.setVisibility(View.GONE);
      else
      {
        if (_scroll_view != null) _scroll_view.setVisibility(View.GONE);
        if (_emoji_view != null) _emoji_view.setVisibility(View.GONE);
      }
      if (_dictionary_switch_button != null) _dictionary_switch_button.setVisibility(View.GONE);
      if (_voice_typing_button != null)
        _voice_typing_button.setVisibility(show_voice_typing ? View.VISIBLE : View.GONE);
    }
    else
    {
      if (_tools_spacer != null) _tools_spacer.setVisibility(View.GONE);
      if (_has_suggestions)
      {
        if (_tools_expanded_over_suggestions)
        {
          if (_tools_action_scroll != null)
          {
            _tools_action_scroll.setVisibility(View.VISIBLE);
            _tools_action_scroll.setTranslationX(0f);
            _tools_action_scroll.setAlpha(1f);
            _tools_action_scroll.setScaleX(1f);
          }
          if (_candidates_words_layout != null)
            _candidates_words_layout.setVisibility(View.GONE);
          else
          {
            if (_scroll_view != null) _scroll_view.setVisibility(View.GONE);
            if (_emoji_view != null) _emoji_view.setVisibility(View.GONE);
          }
        }
        else
        {
          if (_tools_action_scroll != null) _tools_action_scroll.setVisibility(View.GONE);
          if (_candidates_words_layout != null)
          {
            _candidates_words_layout.setVisibility(View.VISIBLE);
            _candidates_words_layout.setAlpha(1f);
            _candidates_words_layout.setTranslationX(0f);
          }
          else
          {
            if (_scroll_view != null) _scroll_view.setVisibility(View.VISIBLE);
            if (_emoji_view != null && _emoji_view.getText().length() > 0)
              _emoji_view.setVisibility(View.VISIBLE);
          }
        }
      }
      else
      {
        boolean showActionBar = (_status_no_dict == null || _status_no_dict.getVisibility() != View.VISIBLE);
        if (_tools_action_scroll != null)
        {
          _tools_action_scroll.setVisibility(showActionBar ? View.VISIBLE : View.GONE);
          _tools_action_scroll.setTranslationX(0f);
          _tools_action_scroll.setAlpha(1f);
          _tools_action_scroll.setScaleX(1f);
        }
        if (_candidates_words_layout != null)
          _candidates_words_layout.setVisibility(View.GONE);
        else
        {
          if (_scroll_view != null) _scroll_view.setVisibility(View.GONE);
          if (_emoji_view != null) _emoji_view.setVisibility(View.GONE);
        }
      }
      if (_dictionary_switch_button != null)
        _dictionary_switch_button.setVisibility(
            should_show_dictionary_switch ? View.VISIBLE : View.GONE);
      if (_voice_typing_button != null)
        _voice_typing_button.setVisibility(show_voice_typing ? View.VISIBLE : View.GONE);
    }
  }

  private void setup_tools_menu()
  {
    if (_tools_menu_button != null)
    {
      TypedValue colorVal = new TypedValue();
      if (getContext().getTheme().resolveAttribute(R.attr.colorLabel, colorVal, true))
      {
        _tools_menu_button.setColorFilter(colorVal.data);
      }
      _tools_menu_button.setOnClickListener(new OnClickListener()
      {
        @Override
        public void onClick(View v)
        {
          handle_menu_button_click();
        }
      });
      _tools_menu_button.setOnLongClickListener(new OnLongClickListener()
      {
        @Override
        public boolean onLongClick(View v)
        {
          setMenuOpen(!_tools_open);
          return true;
        }
      });
    }
  }

  private void handle_menu_button_click()
  {
    if (_tools_open)
    {
      setMenuOpen(false);
      return;
    }

    if (_has_suggestions)
    {
      _tools_expanded_over_suggestions = !_tools_expanded_over_suggestions;
      if (_tools_expanded_over_suggestions)
      {
        animate_expand_tools_from_menu();
      }
      else
      {
        animate_collapse_tools_into_menu();
      }
    }
    else
    {
      setMenuOpen(!_tools_open);
    }
  }

  private void setup_action_buttons()
  {
    if (_tools_action_bar == null) return;
    _tools_action_bar.removeAllViews();

    List<SuggestionToolItem> tools = null;
    if (Config.globalConfig() != null && Config.globalConfig().suggestion_tools != null)
    {
      tools = Config.globalConfig().suggestion_tools;
    }
    if (tools == null)
    {
      tools = SuggestionToolItem.loadFromPrefs(
          PreferenceManager.getDefaultSharedPreferences(getContext()));
    }

    Context ctx = getContext();
    int pad = (int) (7 * getResources().getDisplayMetrics().density);
    int width = (int) (38 * getResources().getDisplayMetrics().density);
    int margin = (int) (4 * getResources().getDisplayMetrics().density);

    TypedValue outValue = new TypedValue();
    ctx.getTheme().resolveAttribute(R.attr.colorLabel, outValue, true);
    int iconColor = outValue.data;

    TypedValue selectBg = new TypedValue();
    boolean hasSelectBg = ctx.getTheme().resolveAttribute(
        android.R.attr.selectableItemBackgroundBorderless, selectBg, true);

    for (SuggestionToolItem item : tools)
    {
      if (!item.enabled) continue;

      ImageButton btn = new ImageButton(ctx);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT);
      lp.setMargins(margin, 0, margin, 0);
      btn.setLayoutParams(lp);
      btn.setPadding(pad, pad, pad, pad);
      btn.setImageResource(item.getIconRes());
      btn.setColorFilter(iconColor);
      String title = item.getTitle(ctx);
      btn.setContentDescription(title);
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
      {
        btn.setTooltipText(title);
      }

      if (hasSelectBg)
      {
        btn.setBackgroundResource(selectBg.resourceId);
      }
      else
      {
        btn.setBackgroundResource(android.R.color.transparent);
      }

      final SuggestionToolItem currentItem = item;
      if (currentItem.longPressAction != null && !currentItem.longPressAction.trim().isEmpty())
      {
        final Handler handler = new Handler(Looper.getMainLooper());
        final int touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        final boolean[] isLongPress = new boolean[]{false};
        final float[] downX = new float[]{0};
        final float[] downY = new float[]{0};

        final Runnable longPressRunnable = () -> {
          isLongPress[0] = true;
          btn.setPressed(false);
          try
          {
            VibratorCompat.vibrate(btn, Config.globalConfig());
          }
          catch (Exception ignored) {}
          execute_tool_action(currentItem.longPressAction);
        };

        btn.setOnTouchListener((v, event) -> {
          long timeout = (Config.globalConfig() != null && Config.globalConfig().longPressTimeout > 0)
              ? Config.globalConfig().longPressTimeout : 200;
          switch (event.getAction())
          {
            case MotionEvent.ACTION_DOWN:
              isLongPress[0] = false;
              downX[0] = event.getX();
              downY[0] = event.getY();
              btn.setPressed(true);
              handler.postDelayed(longPressRunnable, timeout);
              return true;

            case MotionEvent.ACTION_MOVE:
              if (Math.abs(event.getX() - downX[0]) > touchSlop || Math.abs(event.getY() - downY[0]) > touchSlop)
              {
                handler.removeCallbacks(longPressRunnable);
                btn.setPressed(false);
              }
              return true;

            case MotionEvent.ACTION_UP:
              handler.removeCallbacks(longPressRunnable);
              btn.setPressed(false);
              if (!isLongPress[0])
              {
                try
                {
                  VibratorCompat.vibrate(btn, Config.globalConfig());
                }
                catch (Exception ignored) {}
                execute_tool_action(currentItem.id);
              }
              return true;

            case MotionEvent.ACTION_CANCEL:
              handler.removeCallbacks(longPressRunnable);
              btn.setPressed(false);
              return true;
          }
          return false;
        });
      }
      else
      {
        btn.setOnTouchListener(null);
        btn.setOnClickListener(v -> {
          try
          {
            VibratorCompat.vibrate(btn, Config.globalConfig());
          }
          catch (Exception ignored) {}
          execute_tool_action(currentItem.id);
        });
      }

      _tools_action_bar.addView(btn);
    }
  }

  private void execute_tool_action(String id)
  {
    if (id == null) return;
    if (id.startsWith(SuggestionActionHelper.TASKER_PREFIX))
    {
      String taskName = id.substring(SuggestionActionHelper.TASKER_PREFIX.length());
      execute_tasker_task(taskName);
      return;
    }
    switch (id)
    {
      case "paste":
        Config.globalConfig().handler.key_up(
            KeyValue.getKeyByName("paste"),
            Pointers.Modifiers.EMPTY);
        break;
      case "copy":
        Config.globalConfig().handler.key_up(
            KeyValue.getKeyByName("copy"),
            Pointers.Modifiers.EMPTY);
        break;
      case "cut":
        Config.globalConfig().handler.key_up(
            KeyValue.getKeyByName("cut"),
            Pointers.Modifiers.EMPTY);
        break;
      case "delete":
        Config.globalConfig().handler.key_up(
            KeyValue.getKeyByName("backspace"),
            Pointers.Modifiers.EMPTY);
        break;
      case "selectAll":
        Config.globalConfig().handler.key_up(
            KeyValue.getKeyByName("selectAll"),
            Pointers.Modifiers.EMPTY);
        break;
      case "undo":
        Config.globalConfig().handler.key_up(
            KeyValue.getKeyByName("undo"),
            Pointers.Modifiers.EMPTY);
        break;
      case "redo":
        Config.globalConfig().handler.key_up(
            KeyValue.getKeyByName("redo"),
            Pointers.Modifiers.EMPTY);
        break;
      case "delete_word":
        Config.globalConfig().handler.key_up(
            KeyValue.getKeyByName("delete_word"),
            Pointers.Modifiers.EMPTY);
        break;
      case "clipboard":
        Config.globalConfig().handler.key_up(
            KeyValue.getKeyByName("switch_clipboard"),
            Pointers.Modifiers.EMPTY);
        break;
      case "translate":
        if (_translate_toggle_listener != null)
        {
          _translate_toggle_listener.onTranslateToggled();
        }
        else
        {
          open_translate();
        }
        break;
      case "theme":
        try
        {
          Intent intent = new Intent(getContext(), SettingsActivity.class);
          intent.putExtra(SettingsActivity.EXTRA_START_SCREEN, "screen_theme");
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          getContext().startActivity(intent);
        }
        catch (Exception e)
        {
          Logs.exn("CandidatesView", e);
        }
        break;
      case "gif":
        Config.globalConfig().handler.key_up(
            KeyValue.getKeyByName("switch_gif"),
            Pointers.Modifiers.EMPTY);
        break;
      case "settings":
        try
        {
          Intent intent = new Intent(getContext(), SettingsActivity.class);
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          getContext().startActivity(intent);
        }
        catch (Exception e)
        {
          Logs.exn("CandidatesView", e);
        }
        break;
      case "voice_typing":
        Config.globalConfig().handler.key_up(
            KeyValue.getKeyByName("voice_typing"),
            Pointers.Modifiers.EMPTY);
        break;
      case "share":
        Config.globalConfig().handler.key_up(
            KeyValue.getKeyByName("shareText"),
            Pointers.Modifiers.EMPTY);
        break;
      case "paste_plain":
        Config.globalConfig().handler.key_up(
            KeyValue.getKeyByName("pasteAsPlainText"),
            Pointers.Modifiers.EMPTY);
        break;
      case "capslock":
      case "caps_lock":
        Config.globalConfig().handler.key_up(
            KeyValue.getKeyByName("capslock"),
            Pointers.Modifiers.EMPTY);
        break;
      case "switch_keyboard":
        Config.globalConfig().handler.key_up(
            KeyValue.getKeyByName("change_method"),
            Pointers.Modifiers.EMPTY);
        break;
    }
  }

  private void execute_tasker_task(String taskName)
  {
    final Context ctx = getContext();
    android.view.inputmethod.InputConnection ic =
        (Config.globalConfig() != null && Config.globalConfig().handler != null)
            ? Config.globalConfig().handler.getCurrentInputConnection() : null;

    String text1 = "";
    String text2 = "";
    String keyword = "";

    if (ic != null)
    {
      try
      {
        CharSequence before = ic.getTextBeforeCursor(4000, 0);
        if (before != null) text1 = before.toString();
        CharSequence after = ic.getTextAfterCursor(4000, 0);
        if (after != null) text2 = after.toString();
        CharSequence selected = ic.getSelectedText(0);
        if (selected != null) keyword = selected.toString();
      }
      catch (Exception ignored) {}
    }

    long timeoutMs = 15000;
    try
    {
      String storedJson = TaskerAutomationManager.load(ctx);
      if (storedJson != null)
      {
        TaskerAutomationConfig config = TaskerAutomationConfig.parse(storedJson);
        if (config != null) timeoutMs = config.timeout_ms;
      }
    }
    catch (Exception ignored) {}

    Toast.makeText(ctx, "Running Tasker: " + taskName, Toast.LENGTH_SHORT).show();

    TaskerBridge.run_task(ctx, taskName, text1, text2, keyword, timeoutMs, (output, errorMessage) -> {
      if (errorMessage != null)
      {
        Toast.makeText(ctx, errorMessage, Toast.LENGTH_SHORT).show();
        return;
      }
      if (output != null)
      {
        android.view.inputmethod.InputConnection lateConn =
            (Config.globalConfig() != null && Config.globalConfig().handler != null)
                ? Config.globalConfig().handler.getCurrentInputConnection() : null;
        if (lateConn != null)
        {
          lateConn.beginBatchEdit();
          try
          {
            lateConn.commitText(output, 1);
          }
          finally
          {
            lateConn.endBatchEdit();
          }
        }
      }
    });
  }

  private void open_translate()
  {
    Context ctx = getContext();
    String text = "";
    try
    {
      android.view.inputmethod.InputConnection ic =
          (Config.globalConfig() != null && Config.globalConfig().handler != null)
              ? Config.globalConfig().handler.getCurrentInputConnection() : null;
      if (ic != null)
      {
        CharSequence selected = ic.getSelectedText(0);
        if (selected != null && selected.length() > 0)
        {
          text = selected.toString();
        }
      }
    }
    catch (Exception ignored) {}

    // First attempt: launch Google Translate app
    try
    {
      Intent intent = new Intent(Intent.ACTION_SEND);
      intent.setType("text/plain");
      intent.setPackage("com.google.android.apps.translate");
      intent.putExtra(Intent.EXTRA_TEXT, text);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      ctx.startActivity(intent);
      return;
    }
    catch (Exception ignored) {}

    // Fallback: browser Google Translate
    try
    {
      String url = "https://translate.google.com";
      if (!text.isEmpty())
      {
        url += "/?text=" + Uri.encode(text);
      }
      Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
      webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      ctx.startActivity(webIntent);
    }
    catch (Exception e)
    {
      Toast.makeText(ctx, "Could not open Translate", Toast.LENGTH_SHORT).show();
    }
  }

  private TextView get_or_create_item_view(int index)
  {
    if (index < _item_pool.size())
      {
        return _item_pool.get(index);
      }
    TextView v = new TextView(getContext());
    int gap = getResources().getDimensionPixelSize(R.dimen.candidates_gap);
    int vMargin = getResources().getDimensionPixelSize(R.dimen.candidates_margin_vertical);
    int hPadding = (int)(12 * getResources().getDisplayMetrics().density);

    LayoutParams lp = new LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.MATCH_PARENT);
    lp.setMargins(gap, vMargin, 0, vMargin);
    v.setLayoutParams(lp);
    v.setPadding(hPadding, 0, hPadding, 0);
    v.setGravity(Gravity.CENTER);
    v.setBackgroundResource(R.drawable.suggestions_item_background);
    v.setMaxLines(1);

    TypedValue outValue = new TypedValue();
    getContext().getTheme().resolveAttribute(R.attr.colorLabel, outValue, true);
    v.setTextColor(outValue.data);

    if (_cached_text_size > 0)
      apply_text_size(v, _cached_text_size);

    _item_pool.add(v);
    return v;
  }

  void clear_candidates()
  {
    boolean prev_has_suggestions = _has_suggestions;
    _has_suggestions = false;
    _tools_expanded_over_suggestions = false;
    if (_container != null)
      _container.removeAllViews();
    if (_emoji_view != null)
    {
      _emoji_view.setVisibility(View.GONE);
      _emoji_view.setText("");
      _emoji_view.setOnClickListener(null);
    }
    if (prev_has_suggestions && !_tools_open)
    {
      animate_expand_tools_from_menu();
    }
    else
    {
      update_toolbar_visibility();
    }
  }

  public void refresh_config(Config config)
  {
    clear_candidates();
    show_toolbar = config.show_toolbar;
    show_voice_typing = config.show_voice_typing;
    if (config.current_dictionary == null)
      inflate_status_no_dict(config);
    else if (_status_no_dict != null)
      _status_no_dict.setVisibility(View.GONE);
    should_show_dictionary_switch = config.should_show_dictionary_switch;
    set_sizes(config);
    setup_action_buttons();
    update_toolbar_visibility();
  }

  public void set_key_label_size(float size)
  {
  }

  void set_sizes(Config config)
  {
    float row_height = config.keyboard_rows_height_pixels * (1 - config.key_vertical_margin) * 0.75f * config.suggestion_bar_scale;
    MarginLayoutParams p = (MarginLayoutParams)getLayoutParams();
    p.height = (int)row_height;
    setLayoutParams(p);

    android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
    float size_sp = config.suggestion_text_size > 0 ? config.suggestion_text_size : 15f;
    _cached_text_size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size_sp, dm);

    for (TextView v : _item_pool)
    {
      apply_text_size(v, _cached_text_size);
    }
    if (_emoji_view != null && _cached_text_size > 0)
      apply_text_size(_emoji_view, _cached_text_size);
  }

  private void apply_text_size(TextView v, float text_size)
  {
    if (VERSION.SDK_INT >= 26)
      v.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
    v.setTextSize(TypedValue.COMPLEX_UNIT_PX, text_size);
  }

  void inflate_status_no_dict(Config config)
  {
    if (_status_no_dict == null)
    {
      _status_no_dict = View.inflate(getContext(),
          R.layout.candidates_status_no_dict, null);
      addView(_status_no_dict);
    }
    Locale current_locale = (config.device_locales.default_ != null) ?
      Locale.forLanguageTag(config.device_locales.default_.lang_tag) : null;
    TextView tv = _status_no_dict.findViewById(android.R.id.text1);
    if (tv != null && current_locale != null)
      tv.setText(getResources().getString(
            R.string.candidates_status_click_to_install,
            current_locale.getDisplayName()));
    _status_no_dict.setVisibility(View.VISIBLE);
  }

  void setup_dictionary_switch_button()
  {
    _dictionary_switch_button = findViewById(R.id.dictionary_switch);
    if (_dictionary_switch_button != null)
    {
      _dictionary_switch_button.setOnClickListener(new OnClickListener()
          {
            @Override
            public void onClick(View _v)
            {
              Config.globalConfig().handler.key_up(
                  KeyValue.getKeyByName("change_dictionary"),
                  Pointers.Modifiers.EMPTY);
            }
          });
    }
  }

  void setup_voice_typing_button()
  {
    _voice_typing_button = findViewById(R.id.voice_typing_button);
    if (_voice_typing_button != null)
    {
      if (_voice_typing_button instanceof ImageView)
      {
        TypedValue colorVal = new TypedValue();
        if (getContext().getTheme().resolveAttribute(R.attr.colorLabel, colorVal, true))
        {
          ((ImageView) _voice_typing_button).setColorFilter(colorVal.data);
        }
      }
      _voice_typing_button.setOnClickListener(new OnClickListener()
          {
            @Override
            public void onClick(View _v)
            {
              Config.globalConfig().handler.key_up(
                  KeyValue.getKeyByName("voice_typing"),
                  Pointers.Modifiers.EMPTY);
            }
          });
    }
  }

  public static boolean should_show(EditorInfo info)
  {
    int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
    switch (info.inputType & InputType.TYPE_MASK_CLASS)
    {
      case InputType.TYPE_CLASS_TEXT:
        switch (variation)
        {
          case InputType.TYPE_TEXT_VARIATION_PASSWORD:
          case InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:
          case InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD:
            return false;
          default:
            return true;
        }
      case InputType.TYPE_CLASS_NUMBER:
        return false;
      default: return false;
    }
  }
}
