package com.nhs.customkeyboard.dict;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import nhs.cdict.Cdict;
import com.nhs.customkeyboard.Config;
import com.nhs.customkeyboard.KeyboardData;
import com.nhs.customkeyboard.Logs;
import com.nhs.customkeyboard.Utils;

/** Manage and load installed dictionaries. */
public final class Dictionaries
{
  public static Dictionaries instance(Context ctx)
  {
    if (_instance == null)
      _instance = new Dictionaries(ctx);
    return _instance;
  }

  /** Load the given dictionary and set it as the current dictionary in
    [config]. If [name] is null, unset the current dictionary. */
  public void set_current_dictionary(Config config, String name)
  {
    config.current_dictionary = null;
    config.emoji_dictionary = null;
    if (name == null)
      return;
    Cdict[] dicts = load(name);
    if (dicts == null)
      return;
    config.current_dictionary = find_by_name(dicts, "main");
    config.emoji_dictionary = find_by_name(dicts, "emoji");
  }

  /** Util for finding a dictionary by name. Returns [null] if not found. */
  public static Cdict find_by_name(Cdict[] dicts, String name)
  {
    for (Cdict d : dicts)
      if (d.name.equals(name))
        return d;
    return null;
  }

  /** Load an installed dictionary. Return [null] if the requested dictionary
      is not installed or the dictionary couldn't be loaded. */
  public Cdict[] load(String dict_name)
  {
    if (_loaded_dictionaries.containsKey(dict_name))
      return _loaded_dictionaries.get(dict_name);
    Cdict[] dict = load_uncached(dict_name);
    _loaded_dictionaries.put(dict_name, dict);
    return dict;
  }

  public Set<String> get_installed() { return _installed_dictionaries; }

  public String resolve_dictionary(Config config, KeyboardData layout)
  {
    // 1. Check user-selected preference for this layout
    String selected = get_selected(config, layout);
    if (selected != null && _installed_dictionaries.contains(selected))
      return selected;

    // 2. Auto-detect dictionary from layout
    String auto_detected = auto_detect_dictionary(layout);
    if (auto_detected != null && _installed_dictionaries.contains(auto_detected))
      return auto_detected;

    // 3. Fallback to device locale dictionary if layout is Latin
    String fallback = (config.device_locales.default_ != null) ?
        config.device_locales.default_.dictionary : null;
    if (fallback != null && _installed_dictionaries.contains(fallback))
    {
      if (layout == null || "latin".equalsIgnoreCase(layout.script))
        return fallback;
    }

    // 4. Default fallback for Latin layout: any installed English dictionary
    if (layout == null || "latin".equalsIgnoreCase(layout.script))
    {
      if (_installed_dictionaries.contains("en_US")) return "en_US";
      if (_installed_dictionaries.contains("en_GB")) return "en_GB";
    }

    // 5. Fallback for Bengali/Avro layout
    if (layout != null)
    {
      String sc = (layout.script != null) ? layout.script.toLowerCase() : "";
      if (sc.equals("bengali") || sc.equals("avro") || sc.equals("beng"))
      {
        if (_installed_dictionaries.contains("bn")) return "bn";
        if (_installed_dictionaries.contains("as")) return "as";
      }
    }

    return selected;
  }

  public String auto_detect_dictionary(KeyboardData layout)
  {
    if (layout == null)
      return null;

    // A. Explicit dictionary attribute on <keyboard dictionary="...">
    if (layout.dictionary != null && !layout.dictionary.trim().isEmpty())
    {
      String d = layout.dictionary.trim();
      if (_installed_dictionaries.contains(d))
        return d;
    }

    String script = (layout.script != null) ? layout.script.toLowerCase() : "";
    String name = (layout.name != null) ? layout.name.toLowerCase() : "";

    // B. Bengali / Avro
    if (script.equals("bengali") || script.equals("avro") || script.equals("beng")
        || name.contains("বাংলা") || name.contains("bangla") || name.contains("bengali"))
    {
      if (name.contains("assamese") || name.contains("অসমীয়া"))
        return _installed_dictionaries.contains("as") ? "as" : "bn";
      return "bn";
    }

    // C. Arabic / Persian / Urdu
    if (script.equals("arabic") || script.equals("persian"))
    {
      if (name.contains("urdu")) return "ur";
      if (name.contains("persian") || name.contains("farsi")) return "fa";
      return "ar";
    }

    // D. Devanagari
    if (script.equals("devanagari"))
    {
      if (name.contains("marathi")) return "mr";
      if (name.contains("nepali")) return "ne";
      if (name.contains("sanskrit")) return "sa";
      return "hi";
    }

    // E. Cyrillic
    if (script.equals("cyrillic"))
    {
      if (name.contains("ukrainian")) return "uk";
      if (name.contains("belarusian")) return "be";
      if (name.contains("bulgarian")) return "bg";
      if (name.contains("serbian")) return "sr";
      return "ru";
    }

    if (script.equals("hebrew")) return "iw";
    if (script.equals("greek")) return "el";
    if (script.equals("tamil")) return "ta";
    if (script.equals("kannada")) return "kn";
    if (script.equals("malayalam")) return "ml";
    if (script.equals("gujarati")) return "gu";
    if (script.equals("punjabi")) return "pa";

    // F. Latin languages
    if (name.contains("english") || name.contains("qwerty") || name.contains("us"))
    {
      if (name.contains("uk") || name.contains("gb")) return "en_GB";
      if (name.contains("au")) return "en_AU";
      return "en_US";
    }
    if (name.contains("français") || name.contains("french")) return "fr";
    if (name.contains("español") || name.contains("spanish")) return "es";
    if (name.contains("deutsch") || name.contains("german")) return "de";
    if (name.contains("italiano") || name.contains("italian")) return "it";
    if (name.contains("português") || name.contains("portuguese")) return "pt_BR";
    if (name.contains("polski") || name.contains("polish")) return "pl";
    if (name.contains("türkçe") || name.contains("turkish")) return "tr";
    if (name.contains("svenska") || name.contains("swedish")) return "sv";
    if (name.contains("norsk") || name.contains("norwegian")) return "nb";
    if (name.contains("dansk") || name.contains("danish")) return "da";
    if (name.contains("suomi") || name.contains("finnish")) return "fi";
    if (name.contains("čeština") || name.contains("czech")) return "cs";

    return null;
  }

  /** The selected dictionary for the given layout or current layout. */
  public String get_selected(Config config, KeyboardData layout)
  {
    if (_shared_prefs == null)
      return null;
    if (layout != null && layout.name != null)
    {
      String by_name = _shared_prefs.getString("selection_layout:" + layout.name, null);
      if (by_name != null)
        return by_name;
    }
    return _shared_prefs.getString(dict_selection_pref_name(config), null);
  }

  public String get_selected(Config config)
  {
    return get_selected(config, null);
  }

  /** Set the dictionary for the given layout and current layout context. */
  public void set_selected(Config config, KeyboardData layout, String dict_name)
  {
    if (_shared_prefs == null)
      return;
    SharedPreferences.Editor edit = _shared_prefs.edit();
    edit.putString(dict_selection_pref_name(config), dict_name);
    if (layout != null && layout.name != null)
      edit.putString("selection_layout:" + layout.name, dict_name);
    edit.apply();
  }

  public void set_selected(Config config, String dict_name)
  {
    set_selected(config, null, dict_name);
  }

  public void install(String dict_name, byte[] data) throws IOException
  {
    FileOutputStream outp = _context.openFileOutput(dict_file_name(dict_name),
        Context.MODE_PRIVATE);
    outp.write(data);
    outp.close();
    set_installed(dict_name);
  }

  /** Return the absolute path used to store the dictionary with the given
      name. Return the same result whether the dictionary is installed or not. */
  public File get_install_location(String dict_name)
  {
    return _context.getFileStreamPath(dict_file_name(dict_name));
  }

  /** Declare a dictionary as installed. A dictionary file must exist at the
      path returned by [get_install_location(dict_name)]. */
  public void set_installed(String dict_name)
  {
    _installed_dictionaries.add(dict_name);
    _loaded_dictionaries.remove(dict_name);
    save();
  }

  public void uninstall(String dict_name)
  {
    _context.deleteFile(dict_file_name(dict_name));
    _installed_dictionaries.remove(dict_name);
    _loaded_dictionaries.remove(dict_name);
    save();
  }

  /** Private */

  Context _context;
  Set<String> _installed_dictionaries;
  /** Might be 'null' when safe storage is not available. */
  SharedPreferences _shared_prefs;
  Map<String, Cdict[]> _loaded_dictionaries;

  static Dictionaries _instance = null;

  static final String PREF_INSTALLED_DICTS = "installed";

  Dictionaries(Context ctx)
  {
    _context = ctx;
    _installed_dictionaries = new HashSet();
    _loaded_dictionaries = new TreeMap<String, Cdict[]>();
    load_prefs();
    install_bundled_dictionaries();
  }

  void install_bundled_dictionaries()
  {
    try
    {
      String[] files = _context.getAssets().list("dictionaries");
      if (files != null && files.length > 0)
      {
        boolean changed = false;
        for (String file : files)
        {
          if (file.endsWith(".dict"))
          {
            String dict_name = file.substring(0, file.length() - 5);
            File dest = get_install_location(dict_name);
            if (!dest.exists() || !_installed_dictionaries.contains(dict_name))
            {
              InputStream inp = _context.getAssets().open("dictionaries/" + file);
              byte[] data = Utils.read_all_bytes(inp);
              inp.close();
              Cdict.of_bytes(data);
              FileOutputStream outp = _context.openFileOutput(dict_file_name(dict_name), Context.MODE_PRIVATE);
              outp.write(data);
              outp.close();
              _installed_dictionaries.add(dict_name);
              changed = true;
              Logs.debug("Bundled dictionary auto-installed: " + dict_name);
            }
          }
        }
        if (changed)
          save();
      }
    }
    catch (Exception e)
    {
      Logs.exn("Error installing bundled dictionaries", e);
    }
  }

  void load_prefs()
  {
    _shared_prefs = null;
    try
    {
      _shared_prefs =
        _context.getSharedPreferences("dictionaries", Context.MODE_PRIVATE);
      Set<String> s = _shared_prefs.getStringSet(PREF_INSTALLED_DICTS, null);
      if (s != null)
        _installed_dictionaries.addAll(s);
    }
    catch (Exception e)
    {
      Logs.exn("", e);
    }
  }

  Cdict[] load_uncached(String dict_name)
  {
    if (!_installed_dictionaries.contains(dict_name))
      return null;
    try
    {
      FileInputStream inp = _context.openFileInput(dict_file_name(dict_name));
      byte[] data = Utils.read_all_bytes(inp);
      inp.close();
      return Cdict.of_bytes(data);
    }
    catch (IOException e) { return null; }
    catch (Cdict.ConstructionError e) { return null; }
  }

  void save()
  {
    if (_shared_prefs == null)
      return;
    _shared_prefs.edit()
      .putStringSet(PREF_INSTALLED_DICTS, _installed_dictionaries)
      .commit();
  }

  static String dict_file_name(String dict_name)
  {
    return dict_name + ".dict";
  }

  static String dict_selection_pref_name(Config config)
  {
    String lang_tag = (config.device_locales.default_ != null) ?
      config.device_locales.default_.lang_tag : "";
    return "selection:" + lang_tag + "-" + config.get_current_layout();
  }
}
