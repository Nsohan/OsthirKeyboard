package com.nhs.customkeyboard.dict;

import android.content.res.Resources;
import java.util.Arrays;
import com.nhs.customkeyboard.R;

/** Access arrays in [dictionaries.xml]. */
public class SupportedDictionaries
{
  public String[] locales;
  public String[] names;
  public int[] sizes;

  SupportedDictionaries(Resources res)
  {
    locales = res.getStringArray(R.array.dictionaries_locale);
    names = res.getStringArray(R.array.dictionaries_name);
    sizes = res.getIntArray(R.array.dictionaries_size);
  }

  public static SupportedDictionaries get(Resources res)
  {
    if (_cached == null)
      _cached = new SupportedDictionaries(res);
    return _cached;
  }
  static SupportedDictionaries _cached = null;

  /** Find the index for a given dictionary name. Return [-1] if not found. */
  public int find(String dict_name)
  {
    if (dict_name == null || locales == null)
      return -1;
    for (int i = 0; i < locales.length; i++)
    {
      if (dict_name.equals(locales[i]))
        return i;
    }
    return -1;
  }

  public int length() { return (locales != null) ? locales.length : 0; }

  public String dict_name(int i) { return (locales != null && i >= 0 && i < locales.length) ? locales[i] : ""; }
  public String display_name(int i) { return (names != null && i >= 0 && i < names.length) ? names[i] : ""; }
  public int size(int i) { return (sizes != null && i >= 0 && i < sizes.length) ? sizes[i] : 0; }

  public String get_display_name(String dict_name)
  {
    int i = find(dict_name);
    return (i >= 0 && names != null && i < names.length) ? names[i] : dict_name;
  }
}
