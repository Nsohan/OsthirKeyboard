package com.nhs.customkeyboard;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import nhs.cdict.Cdict;
import com.nhs.customkeyboard.dict.Dictionaries;
import com.nhs.customkeyboard.prefs.CustomExtraKeysPreference;
import com.nhs.customkeyboard.prefs.ExtraKeysPreference;
import com.nhs.customkeyboard.prefs.LayoutsPreference;

public final class Config
{
  /**
   * Width of the Android phones is around 300-600dp in portrait, 600-1400dp in landscape,
   * depending on the user's size settings.
   *
   * 600 dp seems a reasonable midpoint to determine whether the current orientation of the device is "wide"
   * (landsacpe, tablet, unfolded foldable etc.) or not, to switch to a different layout.
   */
  public static final int WIDE_DEVICE_THRESHOLD = 600;

  private final SharedPreferences _prefs;

  // From resources
  public final float marginTop;
  public final float keyPadding;

  public final float labelTextSize;
  public final float sublabelTextSize;

  // From preferences
  /** [null] represent the [system] layout. */
  public List<KeyboardData> layouts;
  public boolean show_numpad = false;
  // From the 'numpad_layout' option, also apply to the numeric pane.
  public boolean inverse_numpad = false;
  public boolean add_number_row;
  public boolean number_row_symbols;
  public float swipe_dist_px;
  public float slide_step_px;
  public boolean suggestions_enabled;
  public boolean next_word_prediction_enabled;
  public boolean user_learning_enabled;
  public boolean show_toolbar;
  public java.util.List<com.nhs.customkeyboard.suggestions.SuggestionToolItem> suggestion_tools;
  public int suggestion_tool_icon_size = 100;
  public int suggestion_tool_gap = 8;
  public boolean show_voice_typing = true;
  public boolean show_dictionary_switch = false;
  public boolean period_key_bottom_row = true;
  // Let the system handle vibration when false.
  public boolean vibrate_custom;
  // Control the vibration if [vibrate_custom] is true.
  public long vibrate_duration;
  public long longPressTimeout;
  public long longPressInterval;
  public boolean keyrepeat_enabled;
  public boolean longpress_custom_char_enabled;
  public float margin_bottom;
  public int keyboard_rows_height_pixels;
  public int screenHeightPixels;
  public float horizontal_margin;
  public float key_vertical_margin;
  public float key_horizontal_margin;
  public int labelBrightness; // 0 - 255
  public int secondaryLabelBrightness; // 0 - 255
  public float keyBrightness; // 0.5 - 2.0 multiplier
  public int keyboardOpacity; // 0 - 255
  public float customBorderRadius; // 0 - 1
  public float customBorderLineWidth; // dp
  public float keyShadow; // dp in pixels
  public int keyOpacity; // 0 - 255
  public int keyActivatedOpacity; // 0 - 255
  public boolean double_tap_lock_shift;
  public float characterSize; // Ratio
  public float secondaryCharacterSize; // Ratio
  public int theme; // Values are R.style.*
  public boolean autocapitalisation;
  public KeyValue change_method_key_replacement;
  public NumberLayout selected_number_layout;
  public boolean borderConfig;
  public boolean keyBorders = true;
  public String themeName = "system";
  public String customThemeImagePath = null;
  public float customThemeDarkness = 0.3f;
  public float customThemeKeyOpacity = 1.0f;
  public float customThemeKeyShadow = 0.0f;
  public int circle_sensitivity;
  public boolean clipboard_history_enabled;
  public int clipboard_history_duration;
  public boolean space_bar_auto_complete;
  public boolean physical_keyboard_hide;
  public float number_row_height_scale = 1.0f;
  public float suggestion_bar_scale = 1.0f;
  public int suggestion_text_size = 15;

  // Dynamically set
  /** Configuration options implied by the connected editor. */
  public EditorConfig editor_config;
  public boolean shouldOfferVoiceTyping;
  public ExtraKeys extra_keys_subtype;
  public Map<KeyValue, KeyboardData.PreferredPos> extra_keys_param;
  public Map<KeyValue, KeyboardData.PreferredPos> extra_keys_custom;
  public DeviceLocales device_locales = null;
  public Cdict current_dictionary = null; // Might be 'null'.
  public Cdict emoji_dictionary = null; // Might be 'null'.
  /** Whether to show the dictionary switching button in the candidates view. */
  public boolean should_show_dictionary_switch = false;
  public IKeyEventHandler handler;
  public boolean orientation_landscape = false;
  public boolean foldable_unfolded = false;
  public boolean wide_screen = false;
  /** Index in 'layouts' of the currently used layout. See
      [get_current_layout()] and [set_current_layout()]. */
  int current_layout_narrow;
  int current_layout_wide;
  /** Whether to automatically split the layout. */
  public boolean split_layout;

  public int default_layout_index;

  private Config(SharedPreferences prefs, Resources res,
      Boolean foldableUnfolded, Dictionaries dicts)
  {
    _prefs = prefs;
    editor_config = new EditorConfig();
    // static values
    marginTop = res.getDimension(R.dimen.margin_top);
    keyPadding = res.getDimension(R.dimen.key_padding);
    labelTextSize = 0.33f;
    sublabelTextSize = 0.22f;
    // from prefs
    refresh(res, foldableUnfolded, dicts);
    // initialized later
    shouldOfferVoiceTyping = false;
    extra_keys_subtype = null;
  }

  /*
   ** Reload prefs
   */
  public void refresh(Resources res, Boolean foldableUnfolded, Dictionaries dicts)
  {
    DisplayMetrics dm = res.getDisplayMetrics();
    orientation_landscape = res.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    foldable_unfolded = foldableUnfolded;

    float characterSizeScale = 1.f;
    String show_numpad_s = _prefs.getString("show_numpad", "never");
    show_numpad = "always".equals(show_numpad_s);
    int keyboardHeightPercent;
    if (orientation_landscape)
    {
      if ("landscape".equals(show_numpad_s))
        show_numpad = true;
      keyboardHeightPercent = _prefs.getInt(foldable_unfolded ? "keyboard_height_landscape_unfolded" : "keyboard_height_landscape", 50);
      characterSizeScale = 1.25f;
    }
    else
    {
      keyboardHeightPercent = _prefs.getInt(foldable_unfolded ? "keyboard_height_unfolded" : "keyboard_height", 35);
    }
    layouts = LayoutsPreference.load_from_preferences(res, _prefs);
    default_layout_index = _prefs.getInt("default_layout_index", -1);
    inverse_numpad = _prefs.getString("numpad_layout", "default").equals("low_first");
    String number_row = _prefs.getString("number_row", "symbols");
    add_number_row = !number_row.equals("no_number_row");
    number_row_symbols = number_row.equals("symbols");
    int nr_height_percent = _prefs.getInt("number_row_height", 100);
    number_row_height_scale = Math.max(0.4f, Math.min(2.0f, nr_height_percent / 100.0f));
    suggestions_enabled = _prefs.getBoolean("suggestions", true);
    next_word_prediction_enabled = _prefs.getBoolean("next_word_prediction", true);
    user_learning_enabled = _prefs.getBoolean("user_learning_enabled", true);
    int sb_height_percent = _prefs.getInt("suggestion_bar_height", 100);
    suggestion_bar_scale = Math.max(0.5f, Math.min(2.0f, sb_height_percent / 100.0f));
    suggestion_text_size = _prefs.getInt("suggestion_text_size", 15);
    if (suggestion_text_size == 12)
    {
      suggestion_text_size = 15;
      _prefs.edit().putInt("suggestion_text_size", 15).apply();
    }
    show_toolbar = _prefs.getBoolean("show_toolbar", true);
    suggestion_tools = com.nhs.customkeyboard.suggestions.SuggestionToolItem.loadFromPrefs(_prefs);
    suggestion_tool_icon_size = _prefs.getInt("suggestion_tool_icon_size", 100);
    suggestion_tool_gap = _prefs.getInt("suggestion_tool_gap", 8);
    show_voice_typing = _prefs.getBoolean("show_voice_typing", true);
    show_dictionary_switch = _prefs.getBoolean("show_dictionary_switch", false);
    period_key_bottom_row = _prefs.getBoolean("period_key_bottom_row", true);
    // The baseline for the swipe distance correspond to approximately the
    // width of a key in portrait mode, as most layouts have 10 columns.
    // Multipled by the DPI ratio because most swipes are made in the diagonals.
    // The option value uses an unnamed scale where the baseline is around 25.
    float dpi_ratio = Math.max(dm.xdpi, dm.ydpi) / Math.min(dm.xdpi, dm.ydpi);
    float swipe_scaling = Math.min(dm.widthPixels, dm.heightPixels) / 10.f * dpi_ratio;
    float swipe_dist_value = Float.valueOf(_prefs.getString("swipe_dist", "15"));
    swipe_dist_px = swipe_dist_value / 25.f * swipe_scaling;
    float slider_sensitivity = Float.valueOf(_prefs.getString("slider_sensitivity", "30")) / 100.f;
    slide_step_px = slider_sensitivity * swipe_scaling;
    vibrate_custom = _prefs.getBoolean("vibrate_custom", false);
    vibrate_duration = _prefs.getInt("vibrate_duration", 20);
    if (!_prefs.contains("longpress_timeout") || _prefs.getInt("longpress_timeout", 600) == 600)
    {
      _prefs.edit().putInt("longpress_timeout", 200).apply();
    }
    longPressTimeout = _prefs.getInt("longpress_timeout", 200);
    longPressInterval = _prefs.getInt("longpress_interval", 65);
    keyrepeat_enabled = _prefs.getBoolean("keyrepeat_enabled", true);
    longpress_custom_char_enabled = _prefs.getBoolean("longpress_custom_char_enabled", true);
    margin_bottom = get_dip_pref_oriented(dm, "margin_bottom", 7, 3);
    key_vertical_margin = get_dip_pref(dm, "key_vertical_margin", 3.0f) / 100;
    key_horizontal_margin = get_dip_pref(dm, "key_horizontal_margin", 2) / 100;
    // Label brightness is used as the alpha channel
    labelBrightness = _prefs.getInt("label_brightness", 100) * 255 / 100;
    secondaryLabelBrightness = _prefs.getInt("secondary_label_brightness", 80) * 255 / 100;
    keyBrightness = _prefs.getInt("key_brightness", 100) / 100.f;
    // Keyboard opacity
    keyboardOpacity = _prefs.getInt("keyboard_opacity", 100) * 255 / 100;
    keyOpacity = _prefs.getInt("key_opacity", 100) * 255 / 100;
    keyActivatedOpacity = _prefs.getInt("key_activated_opacity", 100) * 255 / 100;
    // keyboard corner radius settings (always enabled)
    borderConfig = true;
    int savedRadius = _prefs.getInt("custom_border_radius", 25);
    if (savedRadius == 0 && !_prefs.getBoolean("border_config", false))
      savedRadius = 25;
    customBorderRadius = savedRadius / 100.f;
    customBorderLineWidth = 0;
    keyShadow = get_dip_pref(dm, "key_shadow", 3.0f);
    screenHeightPixels = dm.heightPixels;
    // Row height is proportional to the screen size.
    // The keyboard is keyboardHeightPercent of the screen height on 16/9
    // screens (or less) and with a 3.95 high layout (in KeyboardData unit)
    float base_height = Math.min(dm.heightPixels, dm.widthPixels * 16.f / 9.f);
    keyboard_rows_height_pixels = (int)(base_height * keyboardHeightPercent / 395);
    horizontal_margin =
      get_dip_pref_oriented(dm, "horizontal_margin", 3, 28);
    double_tap_lock_shift = _prefs.getBoolean("lock_double_tap", true);
    characterSize =
      _prefs.getFloat("character_size", 1.15f)
      * characterSizeScale;
    secondaryCharacterSize = _prefs.getFloat("secondary_character_size", 1.0f);
    keyBorders = _prefs.getBoolean("key_borders", true);
    themeName = _prefs.getString("theme", "monet");
    customThemeImagePath = _prefs.getString("custom_theme_image_path", null);
    customThemeDarkness = _prefs.getFloat("custom_theme_darkness", 0.3f);
    customThemeKeyOpacity = _prefs.getFloat("custom_theme_key_opacity", 1.0f);
    customThemeKeyShadow = _prefs.getFloat("custom_theme_key_shadow", 0.0f);
    if (themeName != null && themeName.startsWith("custom_"))
    {
      keyOpacity = Math.max(0, Math.min(255, Math.round(customThemeKeyOpacity * 255)));
      keyShadow = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, customThemeKeyShadow, dm);
    }
    theme = getThemeId(res, themeName);
    autocapitalisation = _prefs.getBoolean("autocapitalisation", true);
    change_method_key_replacement = get_change_method_key_replacement(_prefs);
    extra_keys_param = ExtraKeysPreference.get_extra_keys(_prefs);
    extra_keys_custom = CustomExtraKeysPreference.get(_prefs);
    selected_number_layout = NumberLayout.of_string(_prefs.getString("number_entry_layout", "pin"));
    current_layout_narrow = _prefs.getInt("current_layout_portrait", 0);
    current_layout_wide = _prefs.getInt("current_layout_landscape", 0);
    circle_sensitivity = Integer.valueOf(_prefs.getString("circle_sensitivity", "2"));
    clipboard_history_enabled = _prefs.getBoolean("clipboard_history_enabled", true);
    clipboard_history_duration = Integer.parseInt(_prefs.getString("clipboard_history_duration", "5"));
    space_bar_auto_complete = _prefs.getBoolean("space_bar_auto_complete", false);
    physical_keyboard_hide = _prefs.getString("physical_keyboard_behavior", "hide").equals("hide");
    float screen_width_dp = dm.widthPixels / dm.density;
    wide_screen = screen_width_dp >= WIDE_DEVICE_THRESHOLD;
    split_layout = get_split_layout();
  }

  public int get_current_layout()
  {
    return (wide_screen)
            ? current_layout_wide : current_layout_narrow;
  }

  public void set_current_layout(int l)
  {
    if (wide_screen)
      current_layout_wide = l;
    else
      current_layout_narrow = l;

    SharedPreferences.Editor e = _prefs.edit();
    e.putInt("current_layout_portrait", current_layout_narrow);
    e.putInt("current_layout_landscape", current_layout_wide);
    e.apply();
  }

  public void set_clipboard_history_enabled(boolean e)
  {
    clipboard_history_enabled = e;
    _prefs.edit().putBoolean("clipboard_history_enabled", e).apply();
  }

  private float get_dip_pref(DisplayMetrics dm, String pref_name, float def)
  {
    float value;
    try { value = _prefs.getInt(pref_name, -1); }
    catch (Exception e) { value = _prefs.getFloat(pref_name, -1f); }
    if (value < 0f)
      value = def;
    return (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, dm));
  }

  /** [get_dip_pref] depending on orientation. */
  float get_dip_pref_oriented(DisplayMetrics dm, String pref_base_name, float def_port, float def_land)
  {
    final String suffix;
    if (foldable_unfolded) {
      suffix = orientation_landscape ? "_landscape_unfolded" : "_portrait_unfolded";
    } else {
      suffix = orientation_landscape ? "_landscape" : "_portrait";
    }

    float def = orientation_landscape ? def_land : def_port;
    return get_dip_pref(dm, pref_base_name + suffix, def);
  }

  private int getThemeId(Resources res, String theme_name)
  {
    int night_mode = res.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    switch (theme_name)
    {
      case "light": return R.style.Light;
      case "black": return R.style.Black;
      case "altblack": return R.style.AltBlack;
      case "dark": return R.style.Dark;
      case "white": return R.style.White;
      case "epaper": return R.style.ePaper;
      case "desert": return R.style.Desert;
      case "jungle": return R.style.Jungle;
      case "monetlight": return R.style.MonetLight;
      case "monetdark": return R.style.MonetDark;
      case "monet":
        if ((night_mode & Configuration.UI_MODE_NIGHT_NO) != 0)
          return R.style.MonetLight;
        return R.style.MonetDark;
      case "rosepine": return R.style.RosePine;
      case "everforestlight": return R.style.EverforestLight;
      case "cobalt": return R.style.Cobalt;
      case "pine": return R.style.Pine;
      case "epaperblack": return R.style.ePaperBlack;
      case "red": return R.style.ThemeRed;
      case "green": return R.style.ThemeGreen;
      case "blue": return R.style.ThemeBlue;
      case "cyan": return R.style.ThemeCyan;
      case "amber": return R.style.ThemeAmber;
      case "purple": return R.style.ThemePurple;
      case "pink": return R.style.ThemePink;
      case "darkteal": return R.style.ThemeDarkTeal;
      case "darkblue": return R.style.ThemeDarkBlue;
      case "mintlight": return R.style.ThemeMintLight;
      default:
        if (theme_name != null && theme_name.startsWith("custom_"))
          return R.style.CustomImageTheme;
      case "system":
        if ((night_mode & Configuration.UI_MODE_NIGHT_NO) != 0)
          return R.style.Light;
        return R.style.Dark;
    }
  }

  private static KeyValue get_change_method_key_replacement(SharedPreferences prefs)
  {
    switch (prefs.getString("change_method_key_replacement", "prev"))
    {
      case "prev": return KeyValue.CHANGE_METHOD_PREV;
      case "next": return KeyValue.CHANGE_METHOD_NEXT;
      default:
      case "picker": return KeyValue.CHANGE_METHOD;
    }
  }

  final boolean get_split_layout()
  {
    switch (_prefs.getString("split_layout", "wide"))
    {
      case "wide": return wide_screen;
      case "landscape": return orientation_landscape;
      default: return false;
    }
  }

  private static Config _globalConfig = null;

  public static void initGlobalConfig(SharedPreferences prefs, Resources res,
      Boolean foldableUnfolded, Dictionaries dicts)
  {
    migrate(prefs);
    _globalConfig = new Config(prefs, res, foldableUnfolded, dicts);
    LayoutModifier.init(_globalConfig, res);
  }

  public static Config globalConfig()
  {
    return _globalConfig;
  }

  public static SharedPreferences globalPrefs()
  {
    return _globalConfig._prefs;
  }

  public static interface IKeyEventHandler
  {
    public void key_down(KeyValue value, boolean is_swipe);
    public void key_up(KeyValue value, Pointers.Modifiers mods);
    public void mods_changed(Pointers.Modifiers mods);
    public void suggestion_entered(String text);
    public android.view.inputmethod.InputConnection getCurrentInputConnection();
    public android.view.inputmethod.EditorInfo getCurrentInputEditorInfo();
  }

  /** Config migrations. */

  private static int CONFIG_VERSION = 5;

  public static void migrate(SharedPreferences prefs)
  {
    int saved_version = prefs.getInt("version", 0);
    Logs.debug_config_migration(saved_version, CONFIG_VERSION);
    if (saved_version == CONFIG_VERSION)
      return;
    SharedPreferences.Editor e = prefs.edit();
    e.putInt("version", CONFIG_VERSION);
    // Migrations might run on an empty [prefs] for new installs, in this case
    // they set the default values of complex options.
    switch (saved_version)
    {
      case 0:
        // Primary, secondary and custom layout options are merged into the new
        // Layouts option. This also sets the default value.
        List<LayoutsPreference.Layout> l = new ArrayList<LayoutsPreference.Layout>();
        l.add(migrate_layout(prefs.getString("layout", "system")));
        String snd_layout = prefs.getString("second_layout", "none");
        if (snd_layout != null && !snd_layout.equals("none"))
          l.add(migrate_layout(snd_layout));
        String custom_layout = prefs.getString("custom_layout", "");
        if (custom_layout != null && !custom_layout.equals(""))
          l.add(LayoutsPreference.CustomLayout.parse(custom_layout));
        LayoutsPreference.save_to_preferences(e, l);
        // Fallthrough
      case 1:
        boolean add_number_row = prefs.getBoolean("number_row", false);
        e.putString("number_row", add_number_row ? "no_symbols" : "no_number_row");
        // Fallthrough
      case 2:
        if (!prefs.contains("number_entry_layout")) {
          e.putString("number_entry_layout", prefs.getBoolean("pin_entry_enabled", true) ? "pin" : "number");
        }
        // Fallthrough
      case 3:
        e.putString("change_method_key_replacement",
            prefs.getBoolean("switch_input_immediate", false) ? "prev" : "picker");
        // Fallthrough
      case 4:
        if (!prefs.contains("number_row") || "no_number_row".equals(prefs.getString("number_row", null))) {
          e.putString("number_row", "symbols");
        }
        // Fallthrough
      default: break;
    }
    e.apply();
  }

  private static LayoutsPreference.Layout migrate_layout(String name)
  {
    if (name == null || name.equals("system"))
      return new LayoutsPreference.SystemLayout();
    return new LayoutsPreference.NamedLayout(name);
  }
}
