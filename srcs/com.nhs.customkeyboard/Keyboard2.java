package com.nhs.customkeyboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.util.Log;
import android.util.LogPrinter;
import android.view.*;
import android.widget.Toast;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nhs.cdict.Cdict;
import com.nhs.customkeyboard.dict.Dictionaries;
import com.nhs.customkeyboard.dict.DictionariesActivity;
import com.nhs.customkeyboard.dict.DictionarySwitcher;
import com.nhs.customkeyboard.gif.GifManagerView;
import com.nhs.customkeyboard.prefs.LayoutsPreference;
import com.nhs.customkeyboard.suggestions.CandidatesView;
import com.nhs.customkeyboard.suggestions.NextWordPredictor;
import com.nhs.customkeyboard.suggestions.Suggestions;
import com.nhs.customkeyboard.translate.TranslationBarView;
import com.nhs.customkeyboard.avro.AvroEngine;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.ImageButton;
import android.widget.TextView;

public class Keyboard2 extends InputMethodService
        implements SharedPreferences.OnSharedPreferenceChangeListener
{
  private static java.lang.ref.WeakReference<Keyboard2> sActiveInstance;

  /** The view containing the keyboard and candidates view. */
  private ViewGroup _keyboard_container_view;
  private Keyboard2View _keyboard_layout_view;
  private CandidatesView _candidates_view;
  private View _menu_grid_panel;
  private KeyboardResizeManager _resizeManager;
  private KeyboardMenuManager _menuManager;
  private TranslationBarView _translation_bar_view;
  private View _voice_typing_bar_view;
  private View _gif_search_bar_view;
  private TextView _tv_gif_search_query;
  private View _btn_gif_search_clear;
  private StringBuilder _gif_search_text = new StringBuilder();
  private boolean _gif_search_active = false;
  private TextView _tv_voice_status;
  private TextView _tv_voice_lang_badge;
  private ImageButton _btn_voice_back;
  private ImageButton _btn_voice_mic;
  private String _current_voice_lang = "en-US";
  private boolean _is_voice_typing_active = false;
  private SpeechRecognizer _speech_recognizer;
  private Suggestions _suggestions;
  private KeyEventHandler _keyeventhandler;
  /** If not 'null', the layout to use instead of [_config.current_layout]. */
  private KeyboardData _currentSpecialLayout;
  private boolean _is_period_symbols_active = false;
  /** Layout associated with the currently selected locale. Not 'null'. */
  private KeyboardData _localeTextLayout;

  /** Installed and current locales. */
  private Dictionaries _dictionaries;
  private ViewGroup _emojiPane = null;
  private ViewGroup _clipboard_pane = null;
  private ViewGroup _gif_pane = null;
  private ViewGroup _text_edit_pane = null;
  private Handler _handler;

  private Config _config;

  private FoldStateTracker _foldStateTracker;

  /** Layout currently visible before it has been modified. */
  KeyboardData current_layout_unmodified()
  {
    if (_currentSpecialLayout != null)
      return _currentSpecialLayout;
    KeyboardData layout = null;
    int layout_i = _config.get_current_layout();
    if (layout_i >= _config.layouts.size())
      layout_i = 0;
    if (layout_i < _config.layouts.size())
      layout = _config.layouts.get(layout_i);
    if (layout == null)
      layout = _localeTextLayout;
    return layout;
  }

  /** Layout currently visible. */
  KeyboardData current_layout()
  {
    if (_currentSpecialLayout != null)
      return _currentSpecialLayout;
    return LayoutModifier.modify_layout(current_layout_unmodified());
  }

  void setTextLayout(int l)
  {
    _config.set_current_layout(l);
    _currentSpecialLayout = null;
    _is_period_symbols_active = false;
    // The active dictionary depends on the current layout.
    refresh_current_dictionary();
    refresh_candidates_view();
    _keyboard_layout_view.setKeyboard(current_layout());
    refresh_keymap();
  }

  void incrTextLayout(int delta)
  {
    int s = _config.layouts.size();
    setTextLayout((_config.get_current_layout() + delta + s) % s);
  }

  void setSpecialLayout(KeyboardData l)
  {
    _currentSpecialLayout = l;
    _keyboard_layout_view.setKeyboard(l);
    refresh_keymap();
  }

  /** Loads (or clears) the KeymapEngine mapping based on the "keymap" and
   "swipekeymap" attributes of the layout currently on screen. */
  private void refresh_keymap()
  {
    KeyboardData layout = current_layout();
    String keymap_name = (layout != null) ? layout.keymap : null;
    boolean allow_swipe = (layout != null) && layout.swipekeymap;
    KeymapEngine.get().load(this, keymap_name, allow_swipe);
    boolean is_avro = is_avro_active(layout);
    AvroEngine.get().set_active(is_avro);
  }

  public boolean is_avro_active(KeyboardData layout)
  {
    if (layout == null)
      return false;
    String script = layout.script != null ? layout.script.toLowerCase() : "";
    String name = layout.name != null ? layout.name.toLowerCase() : "";
    String keymap = layout.keymap != null ? layout.keymap.toLowerCase() : "";
    return script.equals("avro") || name.contains("avro") || keymap.contains("avro");
  }

  private void refresh_tasker_automation()
  {
    TaskerTriggerEngine.get().reload(this);
  }

  KeyboardData loadLayout(int layout_id)
  {
    return KeyboardData.load(getResources(), layout_id);
  }

  /** Load a layout that contains a numpad. */
  KeyboardData loadNumpad(int layout_id)
  {
    return LayoutModifier.modify_numpad(KeyboardData.load(getResources(), layout_id),
            current_layout_unmodified());
  }

  KeyboardData loadNumericLayout()
  {
    return loadNumpad(_config.orientation_landscape ?
            R.xml.numeric_landscape : R.xml.numeric);
  }

  KeyboardData loadPinentry(int layout_id)
  {
    return LayoutModifier.modify_pinentry(KeyboardData.load(getResources(), layout_id),
            current_layout_unmodified());
  }

  @Override
  public void onCreate()
  {
    super.onCreate();
    sActiveInstance = new java.lang.ref.WeakReference<>(this);
    SharedPreferences prefs = DirectBootAwarePreferences.get_shared_preferences(this);
    _handler = new Handler(getMainLooper());
    _foldStateTracker = new FoldStateTracker(this);
    _dictionaries = Dictionaries.instance(this);
    Config.initGlobalConfig(prefs, getResources(),
            _foldStateTracker.isUnfolded(), _dictionaries);
    _config = Config.globalConfig();
    Receiver recvr = this.new Receiver();
    _suggestions = new Suggestions(recvr, _config);
    _keyeventhandler = new KeyEventHandler(recvr, _suggestions);
    _keyeventhandler.setTranslationInterceptor(new KeyEventHandler.ITranslationInterceptor()
    {
      @Override
      public boolean isTranslationActive()
      {
        if (_gif_search_active)
          return true;
        return _translation_bar_view != null && _translation_bar_view.isOpen();
      }

      @Override
      public void onCharTyped(char c)
      {
        if (_gif_search_active)
        {
          append_gif_search_char(c);
          return;
        }
        if (_translation_bar_view != null)
          _translation_bar_view.append_char(c);
      }

      @Override
      public void onStringTyped(String s)
      {
        if (_gif_search_active)
        {
          append_gif_search_string(s);
          return;
        }
        if (_translation_bar_view != null)
          _translation_bar_view.append_string(s);
      }

      @Override
      public void onBackspace()
      {
        if (_gif_search_active)
        {
          delete_gif_search_char();
          return;
        }
        if (_translation_bar_view != null)
          _translation_bar_view.delete_char();
      }

      @Override
      public void onEnter()
      {
        if (_gif_search_active)
        {
          submit_gif_search();
          return;
        }
        if (_translation_bar_view != null)
          _translation_bar_view.handle_enter();
      }

      @Override
      public void onSuggestionEntered(String oldWord, String newWord)
      {
        if (_gif_search_active)
        {
          append_gif_search_string(newWord);
          return;
        }
        if (_translation_bar_view != null)
        {
          int oldLen = (oldWord != null) ? oldWord.length() : 0;
          _translation_bar_view.replace_last_word(oldLen, newWord);
        }
      }
    });
    refresh_tasker_automation();

    KeyValue.Stateful._handler = recvr;
    _config.handler = _keyeventhandler;
    prefs.registerOnSharedPreferenceChangeListener(this);
    Logs.set_debug_logs(getResources().getBoolean(R.bool.debug_logs));
    refreshSubtypeImm();
    create_keyboard_view();
    ClipboardHistoryService.on_startup(this, _keyeventhandler);
    _foldStateTracker.setChangedCallback(() -> { refresh_config(); });
  }

  @Override
  public void onDestroy() {
    if (sActiveInstance != null && sActiveInstance.get() == this)
      sActiveInstance = null;
    stop_voice_typing();
    super.onDestroy();

    _foldStateTracker.close();
  }

  private void create_keyboard_view()
  {
    _keyboard_container_view = (ViewGroup)inflate_view(R.layout.keyboard);
    _keyboard_container_view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      int h = bottom - top;
      if (h > 0)
      {
        _last_keyboard_height = h;
      }
    });
    _keyboard_layout_view = (Keyboard2View)_keyboard_container_view.findViewById(R.id.keyboard_view);
    _candidates_view = (CandidatesView)_keyboard_container_view.findViewById(R.id.candidates_view);
    _menu_grid_panel = _keyboard_container_view.findViewById(R.id.menu_grid_panel);
    _translation_bar_view = (TranslationBarView)_keyboard_container_view.findViewById(R.id.translation_bar_view);
    if (_translation_bar_view == null)
    {
      _translation_bar_view = (TranslationBarView)_keyboard_container_view.findViewById(R.id.translation_bar);
    }
    if (_translation_bar_view != null)
    {
      _translation_bar_view.setOnTranslationBarListener(new TranslationBarView.OnTranslationBarListener()
      {
        @Override
        public void onCloseTranslation()
        {
          if (_is_voice_typing_active)
          {
            stop_voice_typing();
          }
          refresh_candidates_view();
        }

        @Override
        public InputConnection getInputConnection()
        {
          return getCurrentInputConnection();
        }

        @Override
        public void onLanguagesChanged(String sourceLang, String targetLang)
        {
          if (_is_voice_typing_active)
          {
            _current_voice_lang = map_lang_to_speech_locale(sourceLang);
            update_voice_ui_for_language();
            restart_voice_listening();
          }
        }
      });
    }
    if (_candidates_view != null)
    {
      _candidates_view.setOnTranslateToggleListener(new CandidatesView.OnTranslateToggleListener()
      {
        @Override
        public void onTranslateToggled()
        {
          if (_translation_bar_view != null)
          {
            if (_translation_bar_view.isOpen())
            {
              _translation_bar_view.close();
            }
            else
            {
              set_menu_panel_visible(false);
              _translation_bar_view.open();
            }
          }
        }
      });
    }
    setup_voice_typing_bar();
    setup_gif_search_bar();
    setup_menu_grid_panel();
    _resizeManager = new KeyboardResizeManager(this);
    _resizeManager.setupViews(_keyboard_container_view, _keyboard_layout_view, _candidates_view);
  }

  public void set_menu_panel_visible(boolean visible)
  {
    if (_menu_grid_panel == null || _keyboard_layout_view == null)
      return;

    if (visible)
    {
      int h = _keyboard_layout_view.getHeight();
      if (h <= 0)
        h = _keyboard_layout_view.getMeasuredHeight();
      if (h > 0)
      {
        int candHeight = (_candidates_view != null && _candidates_view.getHeight() > 0)
            ? _candidates_view.getHeight() : 0;
        _last_keyboard_height = h + candHeight;
        ViewGroup.LayoutParams lp = _menu_grid_panel.getLayoutParams();
        if (lp != null)
        {
          lp.height = h;
          _menu_grid_panel.setLayoutParams(lp);
        }
      }

      int bottomPad = (int) (40 * getResources().getDisplayMetrics().density);
      if (_keyboard_layout_view != null)
      {
        bottomPad = Math.max(bottomPad, (int) _keyboard_layout_view.getMarginBottom());
      }
      if (VERSION.SDK_INT >= 30)
      {
        WindowInsets insets = _menu_grid_panel.getRootWindowInsets();
        if (insets != null)
        {
          android.graphics.Insets barInsets = insets.getInsets(
              WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
          bottomPad = Math.max(bottomPad, barInsets.bottom + (int) (8 * getResources().getDisplayMetrics().density));
        }
      }
      _menu_grid_panel.setPaddingRelative(
          _menu_grid_panel.getPaddingStart(),
          _menu_grid_panel.getPaddingTop(),
          _menu_grid_panel.getPaddingEnd(),
          bottomPad);

      _keyboard_layout_view.setVisibility(View.GONE);
      _menu_grid_panel.setVisibility(View.VISIBLE);
      if (_candidates_view != null && !_candidates_view.isMenuOpen())
      {
        _candidates_view.setMenuOpen(true);
      }
      if (_menuManager != null)
      {
        _menuManager.onMenuVisibilityChanged(true);
      }
    }
    else
    {
      _menu_grid_panel.setVisibility(View.GONE);
      _keyboard_layout_view.setVisibility(View.VISIBLE);
      if (_candidates_view != null && _candidates_view.isMenuOpen())
      {
        _candidates_view.setMenuOpen(false);
      }
      if (_menuManager != null)
      {
        _menuManager.onMenuVisibilityChanged(false);
      }
    }
  }

  private void setup_menu_grid_panel()
  {
    if (_menuManager == null)
    {
      _menuManager = new KeyboardMenuManager(this);
    }
    _menuManager.setup(_menu_grid_panel, _candidates_view);
  }

  public KeyboardResizeManager getResizeManager()
  {
    return _resizeManager;
  }

  public void show_emoji_pane()
  {
    if (_emojiPane == null)
      _emojiPane = (ViewGroup) inflate_view(R.layout.emoji_pane);
    setInputView(_emojiPane);
  }

  public void toggle_one_handed()
  {
    SharedPreferences prefs = DirectBootAwarePreferences.get_shared_preferences(this);
    boolean currentSplit = _config.split_layout;
    prefs.edit().putString("split_layout", currentSplit ? "never" : "wide").apply();
    Toast.makeText(this, currentSplit ? "Full layout" : "One-handed / Split layout", Toast.LENGTH_SHORT).show();
  }

  public void toggle_translate_bar()
  {
    if (_translation_bar_view != null)
    {
      if (_translation_bar_view.isOpen())
      {
        _translation_bar_view.close();
      }
      else
      {
        _translation_bar_view.open();
      }
    }
  }

  public void refresh_config_from_resize()
  {
    _last_keyboard_height = 0;
    refresh_config();
    if (_keyboard_layout_view != null)
    {
      _keyboard_layout_view.setKeyboard(current_layout());
    }
  }

  private int _last_keyboard_height = 0;

  public int calculate_expected_keyboard_height()
  {
    Config config = (_config != null) ? _config : Config.globalConfig();
    android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
    if (config == null)
    {
      return (int) (290 * dm.density);
    }

    KeyboardData layout = current_layout();
    if (layout == null)
    {
      layout = _localeTextLayout;
    }

    float keysHeight = (layout != null && layout.keysHeight > 0) ? layout.keysHeight : 4.0f;
    float row_height = Math.min(config.keyboard_rows_height_pixels,
        (config.screenHeightPixels - config.keyboard_rows_height_pixels) / keysHeight);

    int kbHeight = (int)(row_height * keysHeight + config.marginTop + config.margin_bottom);

    int candHeight = 0;
    if (config.suggestions_enabled && config.editor_config != null && config.editor_config.should_show_candidates_view)
    {
      candHeight = (int)(config.keyboard_rows_height_pixels * (1 - config.key_vertical_margin) * 0.75f * config.suggestion_bar_scale);
      if (candHeight <= 0)
        candHeight = (int)(44 * dm.density);
    }
    else if (_candidates_view != null && _candidates_view.getHeight() > 0)
    {
      candHeight = _candidates_view.getHeight();
    }

    int total = kbHeight + candHeight;
    return (total > 0) ? total : (int) (290 * dm.density);
  }

  public int get_keyboard_height()
  {
    if (_keyboard_container_view != null && _keyboard_container_view.isShown() && _keyboard_container_view.getHeight() > 0)
    {
      _last_keyboard_height = _keyboard_container_view.getHeight();
      return _last_keyboard_height;
    }
    if (_keyboard_layout_view != null && _keyboard_layout_view.isShown() && _keyboard_layout_view.getHeight() > 0)
    {
      int candHeight = (_candidates_view != null && _candidates_view.getHeight() > 0)
          ? _candidates_view.getHeight()
          : (int)(44 * getResources().getDisplayMetrics().density);
      _last_keyboard_height = _keyboard_layout_view.getHeight() + candHeight;
      return _last_keyboard_height;
    }
    if (_keyboard_layout_view != null)
    {
      int kh = _keyboard_layout_view.getKeyboardLayoutHeight();
      if (kh > 0)
      {
        int candHeight = (_candidates_view != null && _candidates_view.getHeight() > 0)
            ? _candidates_view.getHeight()
            : (int)(44 * getResources().getDisplayMetrics().density);
        _last_keyboard_height = kh + candHeight;
        return _last_keyboard_height;
      }
    }
    int expected = calculate_expected_keyboard_height();
    if (expected > 0)
    {
      _last_keyboard_height = expected;
      return expected;
    }
    if (_last_keyboard_height > 0)
    {
      return _last_keyboard_height;
    }
    android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
    return (int) (290 * dm.density);
  }

  private void setup_gif_search_bar()
  {
    _gif_search_bar_view = _keyboard_container_view.findViewById(R.id.gif_search_bar_view);
    if (_gif_search_bar_view == null)
    {
      _gif_search_bar_view = _keyboard_container_view.findViewById(R.id.gif_search_bar);
    }
    if (_gif_search_bar_view != null)
    {
      _tv_gif_search_query = _gif_search_bar_view.findViewById(R.id.tv_gif_search_query);
      _btn_gif_search_clear = _gif_search_bar_view.findViewById(R.id.btn_gif_search_clear);

      View btnBack = _gif_search_bar_view.findViewById(R.id.btn_gif_search_back);
      if (btnBack != null)
      {
        btnBack.setOnClickListener(v -> close_gif_search(true));
      }

      View btnSubmit = _gif_search_bar_view.findViewById(R.id.btn_gif_search_submit);
      if (btnSubmit != null)
      {
        btnSubmit.setOnClickListener(v -> submit_gif_search());
      }

      if (_btn_gif_search_clear != null)
      {
        _btn_gif_search_clear.setOnClickListener(v -> {
          _gif_search_text.setLength(0);
          update_gif_search_display();
        });
      }
    }
  }

  public void show_clipboard_pane()
  {
    set_menu_panel_visible(false);
    close_gif_search(false);
    if (_clipboard_pane == null)
      _clipboard_pane = (ViewGroup)inflate_view(R.layout.clipboard_pane);
    if (_clipboard_pane instanceof ClipboardManagerView)
      ((ClipboardManagerView)_clipboard_pane).onShow(get_keyboard_height());
    setInputView(_clipboard_pane);
  }

  public void show_text_edit_pane()
  {
    set_menu_panel_visible(false);
    close_gif_search(false);
    if (_text_edit_pane == null)
      _text_edit_pane = (ViewGroup)inflate_view(R.layout.text_edit_pane);
    if (_text_edit_pane instanceof TextEditManagerView)
    {
      ((TextEditManagerView)_text_edit_pane).setKeyboardService(this);
      ((TextEditManagerView)_text_edit_pane).onShow(get_keyboard_height());
    }
    setInputView(_text_edit_pane);
  }

  public void close_text_edit()
  {
    setInputView(_keyboard_container_view);
  }

  public void show_gif_pane()
  {
    close_gif_search(false);
    if (_gif_pane == null)
      _gif_pane = (ViewGroup)inflate_view(R.layout.gif_pane);
    if (_gif_pane instanceof GifManagerView)
      {
        GifManagerView gmv = (GifManagerView)_gif_pane;
        gmv.setOnSearchClickListener(this::open_gif_search);
        gmv.onShow(get_keyboard_height());
      }
    setInputView(_gif_pane);
  }

  public void open_gif_search(String initialQuery)
  {
    _gif_search_active = true;
    _gif_search_text.setLength(0);
    if (initialQuery != null && !initialQuery.isEmpty())
    {
      _gif_search_text.append(initialQuery);
    }
    update_gif_search_display();
    if (_gif_search_bar_view != null)
    {
      _gif_search_bar_view.setVisibility(View.VISIBLE);
    }
    if (_candidates_view != null)
    {
      _candidates_view.setVisibility(View.GONE);
    }
    setInputView(_keyboard_container_view);
  }

  public void close_gif_search(boolean returnToGifPane)
  {
    _gif_search_active = false;
    if (_gif_search_bar_view != null)
    {
      _gif_search_bar_view.setVisibility(View.GONE);
    }
    if (_candidates_view != null)
    {
      _candidates_view.setVisibility(View.VISIBLE);
    }
    if (returnToGifPane)
    {
      show_gif_pane();
    }
  }

  public void submit_gif_search()
  {
    String query = _gif_search_text.toString().trim();
    close_gif_search(true);
    if (_gif_pane instanceof GifManagerView && !query.isEmpty())
    {
      ((GifManagerView)_gif_pane).perform_search(query);
    }
  }

  public void append_gif_search_char(char c)
  {
    _gif_search_text.append(c);
    update_gif_search_display();
  }

  public void append_gif_search_string(String s)
  {
    _gif_search_text.append(s);
    update_gif_search_display();
  }

  public void delete_gif_search_char()
  {
    if (_gif_search_text.length() > 0)
    {
      _gif_search_text.deleteCharAt(_gif_search_text.length() - 1);
      update_gif_search_display();
    }
  }

  private void update_gif_search_display()
  {
    if (_tv_gif_search_query != null)
    {
      _tv_gif_search_query.setText(_gif_search_text.toString());
    }
    if (_btn_gif_search_clear != null)
    {
      _btn_gif_search_clear.setVisibility(_gif_search_text.length() > 0 ? View.VISIBLE : View.GONE);
    }
  }

  InputMethodManager get_imm()
  {
    return (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
  }

  private void refreshSubtypeImm()
  {
    _config.shouldOfferVoiceTyping = _config.show_voice_typing;
    KeyboardData default_layout = null;
    _config.device_locales = DeviceLocales.load(this);
    if (_config.device_locales.default_ != null)
    {
      String layout_name = _config.device_locales.default_.default_layout;
      if (layout_name != null)
        default_layout = LayoutsPreference.layout_of_string(getResources(), layout_name);
    }
    _config.extra_keys_subtype = _config.device_locales.extra_keys();
    if (default_layout == null)
      default_layout = loadLayout(R.xml.latn_qwerty_us);
    _localeTextLayout = default_layout;
  }

  private void refresh_current_dictionary()
  {
    _config.should_show_dictionary_switch = _config.show_dictionary_switch;
    KeyboardData layout = current_layout_unmodified();
    String dict = _dictionaries.resolve_dictionary(_config, layout);
    _dictionaries.set_current_dictionary(_config, dict);
  }

  /** Remember and apply the dictionary chosen by the user for the current
   context. */
  private void select_dictionary(String dict_name)
  {
    _dictionaries.set_selected(_config, current_layout_unmodified(), dict_name);
    refresh_current_dictionary();
    refresh_candidates_view();
  }

  private void refresh_candidates_view()
  {
    boolean should_show =
            _config.suggestions_enabled
                    && _config.editor_config.should_show_candidates_view
                    && !_config.split_layout;
    if (should_show)
    {
      _candidates_view.refresh_config(_config);
      _keyeventhandler.dictionary_changed();
    }
    _candidates_view.setVisibility(should_show ? View.VISIBLE : View.GONE);
  }

  /** Might re-create the keyboard view. [_keyboard_layout_view.setKeyboard()] and
   [setInputView()] must be called soon after. */
  private void refresh_config()
  {
    int prev_theme = _config.theme;
    _config.refresh(getResources(), _foldStateTracker.isUnfolded(), _dictionaries);
    refresh_current_dictionary();
    // Refreshing the theme config requires re-creating the views
    if (prev_theme != _config.theme)
    {
      create_keyboard_view();
      _emojiPane = null;
      _clipboard_pane = null;
      _gif_pane = null;
      _text_edit_pane = null;
      setInputView(_keyboard_container_view);
    }
    // Set keyboard background opacity
    Drawable bg = _keyboard_container_view.getBackground().mutate();
    bg.setAlpha(_config.keyboardOpacity);
    _keyboard_container_view.setBackground(bg);
    _keyboard_layout_view.reset();
    refresh_candidates_view();
  }

  private KeyboardData refresh_special_layout()
  {
    if (_config.editor_config.numeric_layout)
    {
      switch (_config.selected_number_layout)
      {
        case PIN:
          return loadPinentry(_config.orientation_landscape ?
                  R.xml.pin_landscape : R.xml.pin);
        case NUMBER:
          return loadNumericLayout();
      }
    }
    return null;
  }

  @Override
  public void onStartInputView(EditorInfo info, boolean restarting)
  {
    _config.editor_config.refresh(info, getResources());
    refresh_config();
    if (_config.default_layout_index >= 0 && _config.default_layout_index < _config.layouts.size())
      _config.set_current_layout(_config.default_layout_index);
    if (_is_period_symbols_active)
      _currentSpecialLayout = LayoutModifier.modify_period_symbols(KeyboardData.load(getResources(), R.xml.period_symbols));
    else
      _currentSpecialLayout = refresh_special_layout();
    _keyboard_layout_view.setKeyboard(current_layout());
    refresh_keymap();
    // Pause Tasker trigger/expand-pattern detection while editing a
    // field that belongs to this app's own UI (namely the Tasker
    // Automation and keymap JSON editor dialogs) - typing the very
    // prefix/suffix/trigger characters being configured, or JSON
    // syntax in general, would otherwise misfire tasks or mangle the
    // JSON being edited. Any other app, including one editing its
    // own similarly-shaped JSON, is unaffected.
    TaskerTriggerEngine.get().set_paused(
            info.packageName != null && info.packageName.equals(getPackageName()));
    _keyeventhandler.started(_config);
    set_menu_panel_visible(false);
    setInputView(_keyboard_container_view);
    Logs.debug_startup_input_view(info, _config);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event)
  {
    if (keyCode == KeyEvent.KEYCODE_BACK)
    {
      if (_resizeManager != null && _resizeManager.isResizeActive())
      {
        _resizeManager.stopResize(true);
        return true;
      }
      if (_text_edit_pane != null && _text_edit_pane.isShown())
      {
        close_text_edit();
        return true;
      }
      if (_is_voice_typing_active)
      {
        stop_voice_typing();
        return true;
      }
      if (_translation_bar_view != null && _translation_bar_view.isOpen())
      {
        _translation_bar_view.close();
        return true;
      }
      if (_menu_grid_panel != null && _menu_grid_panel.getVisibility() == View.VISIBLE)
      {
        set_menu_panel_visible(false);
        return true;
      }
      if (_is_period_symbols_active)
      {
        _is_period_symbols_active = false;
        _currentSpecialLayout = null;
        _keyboard_layout_view.setKeyboard(current_layout());
        refresh_keymap();
        return true;
      }
    }
    return super.onKeyDown(keyCode, event);
  }



  @Override
  public void setInputView(View v)
  {
    ViewParent parent = v.getParent();
    if (parent != null && parent instanceof ViewGroup)
      ((ViewGroup)parent).removeView(v);
    super.setInputView(v);
    updateSoftInputWindowLayoutParams();
    v.requestApplyInsets();
  }

  @Override
  public void updateFullscreenMode() {
    super.updateFullscreenMode();
    updateSoftInputWindowLayoutParams();
  }

  private void updateSoftInputWindowLayoutParams() {
    final Window window = getWindow().getWindow();
    // On API >= 35, Keyboard2View behaves as edge-to-edge
    // APIs 30 to 34 have visual artifact when edge-to-edge is enabled
    if (VERSION.SDK_INT >= 35)
    {
      WindowManager.LayoutParams wattrs = window.getAttributes();
      wattrs.layoutInDisplayCutoutMode =
              WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
      // Allow to draw behind system bars
      wattrs.setFitInsetsTypes(0);
      window.setDecorFitsSystemWindows(false);
    }
    updateLayoutHeightOf(window, ViewGroup.LayoutParams.MATCH_PARENT);
    final View inputArea = window.findViewById(android.R.id.inputArea);

    updateLayoutHeightOf(
            (View) inputArea.getParent(),
            isFullscreenMode()
                    ? ViewGroup.LayoutParams.MATCH_PARENT
                    : ViewGroup.LayoutParams.WRAP_CONTENT);
    updateLayoutGravityOf((View) inputArea.getParent(), Gravity.BOTTOM);

  }

  private static void updateLayoutHeightOf(final Window window, final int layoutHeight) {
    final WindowManager.LayoutParams params = window.getAttributes();
    if (params != null && params.height != layoutHeight) {
      params.height = layoutHeight;
      window.setAttributes(params);
    }
  }

  private static void updateLayoutHeightOf(final View view, final int layoutHeight) {
    final ViewGroup.LayoutParams params = view.getLayoutParams();
    if (params != null && params.height != layoutHeight) {
      params.height = layoutHeight;
      view.setLayoutParams(params);
    }
  }

  private static void updateLayoutGravityOf(final View view, final int layoutGravity) {
    final ViewGroup.LayoutParams lp = view.getLayoutParams();
    if (lp instanceof LinearLayout.LayoutParams) {
      final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) lp;
      if (params.gravity != layoutGravity) {
        params.gravity = layoutGravity;
        view.setLayoutParams(params);
      }
    } else if (lp instanceof FrameLayout.LayoutParams) {
      final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) lp;
      if (params.gravity != layoutGravity) {
        params.gravity = layoutGravity;
        view.setLayoutParams(params);
      }
    }
  }

  @Override
  public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype)
  {
    refreshSubtypeImm();
    refresh_current_dictionary();
    refresh_candidates_view();
    _keyboard_layout_view.setKeyboard(current_layout());
    refresh_keymap();
  }

  @Override
  public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd)
  {
    super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
    _keyeventhandler.selection_updated(oldSelStart, newSelStart, newSelEnd);
    if ((oldSelStart == oldSelEnd) != (newSelStart == newSelEnd))
      _keyboard_layout_view.set_selection_state(newSelStart != newSelEnd);
  }

  @Override
  public void onFinishInputView(boolean finishingInput)
  {
    super.onFinishInputView(finishingInput);
    if (_is_voice_typing_active)
    {
      stop_voice_typing();
    }
    if (_translation_bar_view != null && _translation_bar_view.isOpen())
    {
      _translation_bar_view.close();
    }
    set_menu_panel_visible(false);
    if (_text_edit_pane != null && _text_edit_pane.isShown())
    {
      close_text_edit();
    }
    if (_resizeManager != null && _resizeManager.isResizeActive())
    {
      _resizeManager.stopResize(true);
    }
    _is_period_symbols_active = false;
    _keyboard_layout_view.reset();
    KeymapEngine.get().reset();
    AvroEngine.get().reset();
    // The keyboard is being hidden/closed (user dismissed it, switched
    // app, etc.) without necessarily a matching onStartInputView ever
    // following for this field again. Any Tasker call still in flight
    // must be invalidated now, or its result/timeout-fallback can land
    // later in whatever field happens to be focused at that point,
    // even with the keyboard no longer showing.
    TaskerTriggerEngine.get().new_field_started();
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences _prefs, String _key)
  {
    refresh_config();
    _keyboard_layout_view.setKeyboard(current_layout());
    refresh_keymap();
    refresh_tasker_automation();
  }

  @Override
  public boolean onEvaluateFullscreenMode()
  {
    /* Entirely disable fullscreen mode. */
    return false;
  }

  @Override
  public boolean onEvaluateInputViewShown()
  {
    // Since Android 16, this method returns [false] for unknown reasons.
    if (super.onEvaluateInputViewShown())
      return true;
    if (getResources().getConfiguration().hardKeyboardHidden
            == Configuration.HARDKEYBOARDHIDDEN_NO
            && _config.physical_keyboard_hide)
    {
      Logs.debug("Physical keyboard is present");
      return false;
    }
    return true;
  }

  public void launch_dictionaries_activity()
  {
    start_activity(DictionariesActivity.class);
  }

  /** Called from [onClick] attributes. */
  public void launch_dictionaries_activity(View v)
  {
    launch_dictionaries_activity();
  }

  void start_activity(Class cls)
  {
    Intent intent = new Intent(this, cls);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(intent);
  }

  /** Not static */
  public class Receiver implements KeyEventHandler.IReceiver,
          KeyValue.Stateful.Symbol_provider, DictionarySwitcher.Callback
  {
    public void handle_event_key(KeyValue.Event ev)
    {
      set_menu_panel_visible(false);
      switch (ev)
      {
        case CONFIG:
          start_activity(SettingsActivity.class);
          break;

        case SWITCH_TEXT:
          _is_period_symbols_active = false;
          _currentSpecialLayout = null;
          _keyboard_layout_view.setKeyboard(current_layout());
          refresh_keymap();
          break;

        case SWITCH_PERIOD_SYMBOLS:
          if (_is_period_symbols_active)
          {
            _is_period_symbols_active = false;
            _currentSpecialLayout = null;
            _keyboard_layout_view.setKeyboard(current_layout());
            refresh_keymap();
          }
          else
          {
            _is_period_symbols_active = true;
            setSpecialLayout(LayoutModifier.modify_period_symbols(KeyboardData.load(getResources(), R.xml.period_symbols)));
          }
          break;


        case SWITCH_NUMERIC:
          setSpecialLayout(loadNumericLayout());
          break;

        case SWITCH_EMOJI:
          if (_emojiPane == null)
            _emojiPane = (ViewGroup)inflate_view(R.layout.emoji_pane);
          setInputView(_emojiPane);
          break;

        case SWITCH_CLIPBOARD:
          show_clipboard_pane();
          break;

        case SWITCH_GIF:
          show_gif_pane();
          break;

        case SWITCH_BACK_EMOJI:
        case SWITCH_BACK_CLIPBOARD:
        case SWITCH_BACK_GIF:
          close_gif_search(false);
          setInputView(_keyboard_container_view);
          break;

        case CHANGE_METHOD_PICKER:
          get_imm().showInputMethodPicker();
          break;

        case CHANGE_METHOD_PREV:
          if (VERSION.SDK_INT < 28)
            get_imm().switchToLastInputMethod(getConnectionToken());
          else
            switchToPreviousInputMethod();
          break;

        case CHANGE_METHOD_NEXT:
          if (VERSION.SDK_INT < 28)
            get_imm().switchToNextInputMethod(getConnectionToken(), false);
          else
            switchToNextInputMethod(false);
          break;

        case ACTION:
          if (_translation_bar_view != null && _translation_bar_view.isOpen())
          {
            _translation_bar_view.handle_enter();
          }
          InputConnection conn = getCurrentInputConnection();
          if (conn != null)
            conn.performEditorAction(_config.editor_config.actionId);
          break;

        case SWITCH_FORWARD:
          incrTextLayout(1);
          break;

        case SWITCH_BACKWARD:
          incrTextLayout(-1);
          break;

        case SWITCH_GREEKMATH:
          setSpecialLayout(loadNumpad(R.xml.greekmath));
          break;

        case CAPS_LOCK:
          set_shift_state(true, true);
          break;

        case SWITCH_VOICE_TYPING:
          Keyboard2.this.start_dynamic_voice_typing();
          break;

        case SWITCH_VOICE_TYPING_CHOOSER:
          VoiceImeSwitcher.choose_voice_ime(Keyboard2.this, get_imm(),
                  Config.globalPrefs());
          break;

        case HIDE_SELF:
          Keyboard2.this.requestHideSelf(0);
          break;

        case CHANGE_DICTIONARY:
          new DictionarySwitcher(Keyboard2.this, _dictionaries, this).choose();
          break;
      }
    }

    public void set_shift_state(boolean state, boolean lock)
    {
      _keyboard_layout_view.set_shift_state(state, lock);
    }

    public void set_compose_pending(boolean pending)
    {
      _keyboard_layout_view.set_compose_pending(pending);
    }

    public void selection_state_changed(boolean selection_is_ongoing)
    {
      _keyboard_layout_view.set_selection_state(selection_is_ongoing);
    }

    public InputConnection getCurrentInputConnection()
    {
      return Keyboard2.this.getCurrentInputConnection();
    }

    public EditorInfo getCurrentInputEditorInfo()
    {
      return Keyboard2.this.getCurrentInputEditorInfo();
    }

    public Handler getHandler()
    {
      return _handler;
    }

    public Context getContext()
    {
      return Keyboard2.this;
    }

    public void set_suggestions(Suggestions suggestions)
    {
      _candidates_view.set_candidates(suggestions);
    }

    public String provide_stateful_key_symbol(KeyValue.Stateful q)
    {
      switch (q)
      {
        case Complete_first: return _suggestions.suggestions[0];
        case Complete_second: return _suggestions.suggestions[1];
        case Complete_third: return _suggestions.suggestions[2];
        case Complete_emoji: return _suggestions.emoji_suggestion;
      }
      return "";
    }

    public void on_change_dictionary(String dict_name)
    {
      select_dictionary(dict_name);
    }

    public void launch_dictionaries_activity()
    {
      Keyboard2.this.launch_dictionaries_activity();
    }

    @Override
    public void stop_voice_typing()
    {
      Keyboard2.this.stop_voice_typing();
    }

    public void onKeyCommitted()
    {
    }
  }

  private void setup_voice_typing_bar()
  {
    if (_keyboard_container_view == null) return;
    _voice_typing_bar_view = _keyboard_container_view.findViewById(R.id.voice_typing_bar_view);
    if (_voice_typing_bar_view == null)
    {
      _voice_typing_bar_view = _keyboard_container_view.findViewById(R.id.voice_typing_bar);
    }
    if (_voice_typing_bar_view == null) return;

    _btn_voice_back = _voice_typing_bar_view.findViewById(R.id.btn_voice_back);
    _tv_voice_status = _voice_typing_bar_view.findViewById(R.id.tv_voice_status);
    _tv_voice_lang_badge = _voice_typing_bar_view.findViewById(R.id.tv_voice_lang_badge);
    _btn_voice_mic = _voice_typing_bar_view.findViewById(R.id.btn_voice_mic);

    if (_btn_voice_back != null)
    {
      _btn_voice_back.setOnClickListener(v -> stop_voice_typing());
    }
    if (_tv_voice_lang_badge != null)
    {
      _tv_voice_lang_badge.setOnClickListener(v -> toggle_voice_language());
    }
    if (_btn_voice_mic != null)
    {
      _btn_voice_mic.setOnClickListener(v -> {
        if (_is_voice_typing_active)
        {
          stop_voice_typing();
        }
        else
        {
          start_dynamic_voice_typing();
        }
      });
    }
  }

  public boolean is_bangla_active(KeyboardData layout)
  {
    if (layout != null)
    {
      String name = layout.name != null ? layout.name.toLowerCase() : "";
      String script = layout.script != null ? layout.script.toLowerCase() : "";
      String keymap = layout.keymap != null ? layout.keymap.toLowerCase() : "";

      if (name.contains("বাংলা") || name.contains("bengali") || name.contains("bangla")
          || name.contains("probhat") || name.contains("provat") || name.contains("national")
          || name.contains("জাতীয়") || name.contains("জাতীয়") || name.contains("bn")
          || script.contains("beng") || script.contains("bangla") || script.contains("avro")
          || keymap.contains("bangla") || keymap.contains("beng") || keymap.contains("avro")
          || keymap.contains("probhat"))
      {
        return true;
      }
    }

    if (_dictionaries != null && _config != null)
    {
      String dict = _dictionaries.get_selected(_config);
      if (dict != null && (dict.toLowerCase().contains("bn") || dict.toLowerCase().contains("bangla") || dict.toLowerCase().contains("bengali")))
      {
        return true;
      }
    }

    if (_config != null && _config.device_locales != null && _config.device_locales.default_ != null)
    {
      String lang = _config.device_locales.default_.lang_tag;
      if (lang != null && (lang.toLowerCase().startsWith("bn") || lang.toLowerCase().contains("beng")))
      {
        return true;
      }
    }

    return false;
  }

  public String map_lang_to_speech_locale(String langCode)
  {
    if (langCode == null || langCode.isEmpty() || "auto".equalsIgnoreCase(langCode))
    {
      KeyboardData layout = current_layout();
      boolean isBangla = is_bangla_active(layout);
      return isBangla ? "bn-BD" : "en-US";
    }
    String code = langCode.toLowerCase(java.util.Locale.US).trim();
    if (code.equals("bn")) return "bn-BD";
    if (code.equals("en")) return "en-US";
    if (code.equals("hi")) return "hi-IN";
    if (code.equals("es")) return "es-ES";
    if (code.equals("fr")) return "fr-FR";
    if (code.equals("de")) return "de-DE";
    if (code.equals("ar")) return "ar-SA";
    if (code.equals("zh") || code.equals("zh-cn")) return "zh-CN";
    if (code.equals("zh-tw")) return "zh-TW";
    if (code.equals("ja")) return "ja-JP";
    if (code.equals("ru")) return "ru-RU";
    if (code.equals("pt")) return "pt-BR";
    if (code.equals("it")) return "it-IT";
    if (code.equals("ko")) return "ko-KR";
    if (code.equals("tr")) return "tr-TR";
    if (code.equals("id")) return "id-ID";
    if (code.equals("vi")) return "vi-VN";
    if (code.equals("th")) return "th-TH";
    if (code.equals("ur")) return "ur-PK";
    if (code.contains("-")) return code;
    return code;
  }

  public void start_dynamic_voice_typing()
  {
    if (VERSION.SDK_INT >= 23)
    {
      if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
      {
        Intent permIntent = new Intent(this, VoicePermissionActivity.class);
        permIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(permIntent);
        Toast.makeText(this, "Microphone permission required for voice typing", Toast.LENGTH_SHORT).show();
        return;
      }
    }

    if (!SpeechRecognizer.isRecognitionAvailable(this))
    {
      Toast.makeText(this, R.string.toast_no_voice_input, Toast.LENGTH_SHORT).show();
      return;
    }

    boolean isTranslation = (_translation_bar_view != null && _translation_bar_view.isOpen());
    if (isTranslation)
    {
      _current_voice_lang = map_lang_to_speech_locale(_translation_bar_view.getSourceLang());
    }
    else
    {
      KeyboardData layout = current_layout();
      boolean isBangla = is_bangla_active(layout);
      _current_voice_lang = isBangla ? "bn-BD" : "en-US";
    }
    _is_voice_typing_active = true;

    set_menu_panel_visible(false);

    if (_candidates_view != null)
    {
      int candHeight = _candidates_view.getHeight();
      if (candHeight > 0 && _voice_typing_bar_view != null)
      {
        ViewGroup.LayoutParams lp = _voice_typing_bar_view.getLayoutParams();
        if (lp != null)
        {
          lp.height = candHeight;
          _voice_typing_bar_view.setLayoutParams(lp);
        }
      }
      _candidates_view.setVisibility(View.GONE);
    }

    if (_voice_typing_bar_view != null)
    {
      _voice_typing_bar_view.setVisibility(View.VISIBLE);
    }

    update_voice_ui_for_language();
    listen_speech();
  }

  private void update_voice_ui_for_language()
  {
    boolean isBangla = _current_voice_lang != null && _current_voice_lang.startsWith("bn");
    if (_tv_voice_lang_badge != null)
    {
      String badge = "EN";
      if (_current_voice_lang != null && _current_voice_lang.length() >= 2)
      {
        badge = _current_voice_lang.substring(0, 2).toUpperCase(java.util.Locale.US);
      }
      _tv_voice_lang_badge.setText(badge);
    }
    if (_tv_voice_status != null)
    {
      _tv_voice_status.setText(isBangla ? "এখনই বলুন" : "Speak now");
    }
  }

  private void toggle_voice_language()
  {
    if (_translation_bar_view != null && _translation_bar_view.isOpen())
    {
      _translation_bar_view.swap_languages();
      return;
    }

    if (_current_voice_lang.startsWith("bn"))
    {
      _current_voice_lang = "en-US";
    }
    else
    {
      _current_voice_lang = "bn-BD";
    }
    update_voice_ui_for_language();
    restart_voice_listening();
  }

  private void listen_speech()
  {
    if (!_is_voice_typing_active) return;

    try
    {
      if (_speech_recognizer != null)
      {
        try {
          _speech_recognizer.cancel();
          _speech_recognizer.destroy();
        } catch (Exception ignored) {}
        _speech_recognizer = null;
      }

      _speech_recognizer = SpeechRecognizer.createSpeechRecognizer(this);
      Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
      intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
      intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, _current_voice_lang);
      intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, _current_voice_lang);
      intent.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{_current_voice_lang});
      intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
      intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
      intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());

      _speech_recognizer.setRecognitionListener(new RecognitionListener()
      {
        @Override
        public void onReadyForSpeech(Bundle params)
        {
          if (_tv_voice_status != null && _is_voice_typing_active)
          {
            _tv_voice_status.setText(_current_voice_lang.startsWith("bn") ? "এখনই বলুন" : "Speak now");
          }
        }

        @Override
        public void onBeginningOfSpeech()
        {
          if (_tv_voice_status != null && _is_voice_typing_active)
          {
            _tv_voice_status.setText(_current_voice_lang.startsWith("bn") ? "শুনছি..." : "Listening...");
          }
        }

        @Override
        public void onRmsChanged(float rmsdB)
        {
          if (_btn_voice_mic != null && _is_voice_typing_active && rmsdB > 2f)
          {
            float scale = 1.0f + Math.min(rmsdB / 20f, 0.25f);
            _btn_voice_mic.setScaleX(scale);
            _btn_voice_mic.setScaleY(scale);
          }
          else if (_btn_voice_mic != null)
          {
            _btn_voice_mic.setScaleX(1.0f);
            _btn_voice_mic.setScaleY(1.0f);
          }
        }

        @Override public void onBufferReceived(byte[] buffer) {}
        @Override
        public void onEndOfSpeech()
        {
          if (_btn_voice_mic != null)
          {
            _btn_voice_mic.setScaleX(1.0f);
            _btn_voice_mic.setScaleY(1.0f);
          }
        }

        @Override
        public void onError(int error)
        {
          Log.d("VoiceTyping", "SpeechRecognizer error: " + error);
          if (!_is_voice_typing_active) return;

          if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
          {
            stop_voice_typing();
            Intent permIntent = new Intent(Keyboard2.this, VoicePermissionActivity.class);
            permIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(permIntent);
            Toast.makeText(Keyboard2.this, "Microphone permission required", Toast.LENGTH_SHORT).show();
          }
          else
          {
            stop_voice_typing();
          }
        }

        @Override
        public void onResults(Bundle results)
        {
          if (!_is_voice_typing_active) return;
          ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
          if (matches != null && !matches.isEmpty())
          {
            String text = matches.get(0);
            if (text != null && !text.trim().isEmpty())
            {
              if (_translation_bar_view != null && _translation_bar_view.isOpen())
              {
                _translation_bar_view.append_string(text.trim());
              }
              else
              {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null)
                {
                  ic.commitText(text.trim() + " ", 1);
                }
              }
            }
          }
          stop_voice_typing();
        }

        @Override
        public void onPartialResults(Bundle partialResults)
        {
          if (!_is_voice_typing_active) return;
          ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
          if (matches != null && !matches.isEmpty())
          {
            String text = matches.get(0);
            if (_tv_voice_status != null && text != null && !text.isEmpty())
            {
              _tv_voice_status.setText(text);
            }
          }
        }

        @Override public void onEvent(int eventType, Bundle params) {}
      });

      _speech_recognizer.startListening(intent);
    }
    catch (Exception e)
    {
      Log.e("VoiceTyping", "SpeechRecognizer startListening failed", e);
    }
  }

  private void restart_voice_listening()
  {
    listen_speech();
  }

  public void stop_voice_typing()
  {
    _is_voice_typing_active = false;
    if (_btn_voice_mic != null)
    {
      _btn_voice_mic.setScaleX(1.0f);
      _btn_voice_mic.setScaleY(1.0f);
    }
    if (_speech_recognizer != null)
    {
      try {
        _speech_recognizer.stopListening();
        _speech_recognizer.cancel();
        _speech_recognizer.destroy();
      } catch (Exception ignored) {}
      _speech_recognizer = null;
    }
    if (_translation_bar_view == null || !_translation_bar_view.isOpen())
    {
      InputConnection ic = getCurrentInputConnection();
      if (ic != null)
      {
        try {
          ic.finishComposingText();
        } catch (Exception ignored) {}
      }
    }
    if (_voice_typing_bar_view != null)
    {
      _voice_typing_bar_view.setVisibility(View.GONE);
    }
    if (_translation_bar_view != null && _translation_bar_view.isOpen())
    {
      if (_candidates_view != null)
      {
        _candidates_view.setVisibility(View.VISIBLE);
      }
    }
    else
    {
      refresh_candidates_view();
    }
  }

  private IBinder getConnectionToken()
  {
    return getWindow().getWindow().getAttributes().token;
  }

  private View inflate_view(int layout)
  {
    return View.inflate(new ContextThemeWrapper(this, _config.theme), layout, null);
  }

  public static boolean commitTextDirectly(final String text)
  {
    Keyboard2 ime = (sActiveInstance != null) ? sActiveInstance.get() : null;
    if (ime == null || text == null)
      return false;
    InputConnection ic = ime.getCurrentInputConnection();
    if (ic == null)
      return false;
    return ic.commitText(text, 1);
  }
}