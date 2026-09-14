package com.nhs.customkeyboard.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.preference.PreferenceManager;
import com.nhs.customkeyboard.R;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ThemeRepository
{
  private static final String PREF_CUSTOM_THEMES = "custom_themes_list";

  public static List<ThemeModel> getDefaultThemes()
  {
    List<ThemeModel> list = new ArrayList<>();
    // Dynamic Color (Material You - API 31+)
    list.add(new ThemeModel(
        "monet",
        ThemeModel.CATEGORY_DEFAULT,
        R.string.theme_dynamic_color,
        R.style.MonetLight,
        R.style.MonetDark,
        0xFFDDE3EA, // preview bg
        0xFFFFFFFF, // preview key
        0xFF0B57D0, // preview accent
        true,
        false,
        false,
        null,
        0f
    ));

    // System Auto
    list.add(new ThemeModel(
        "system",
        ThemeModel.CATEGORY_DEFAULT,
        R.string.theme_system_auto,
        R.style.Light,
        R.style.Dark,
        0xFFECEFF1, // preview bg (split)
        0xFFFFFFFF,
        0xFF0066CC,
        false,
        true,
        false,
        null,
        0f
    ));

    // Default (Light)
    list.add(new ThemeModel(
        "light",
        ThemeModel.CATEGORY_DEFAULT,
        R.string.theme_default_light,
        R.style.Light,
        0,
        0xFFECEFF1,
        0xFFFFFFFF,
        0xFF0066CC,
        false,
        false,
        false,
        null,
        0f
    ));

    // Default Dark
    list.add(new ThemeModel(
        "dark",
        ThemeModel.CATEGORY_DEFAULT,
        R.string.theme_default_dark,
        R.style.Dark,
        0,
        0xFF1F1F1F,
        0xFF303030,
        0xFFDFB7A6,
        false,
        false,
        false,
        null,
        0f
    ));

    return list;
  }

  public static List<ThemeModel> getColorThemes()
  {
    List<ThemeModel> list = new ArrayList<>();

    // 1. Mint Light
    list.add(new ThemeModel("mintlight", ThemeModel.CATEGORY_COLORS, R.string.pref_theme_e_mintlight,
        R.style.ThemeMintLight, 0, 0xFFE0F2F1, 0xFFFFFFFF, 0xFF00897B, false, false, false, null, 0f));

    // 2. Dark Teal
    list.add(new ThemeModel("darkteal", ThemeModel.CATEGORY_COLORS, R.string.pref_theme_e_darkteal,
        R.style.ThemeDarkTeal, 0, 0xFF1E293B, 0xFF334155, 0xFF0D9488, false, false, false, null, 0f));

    // 3. White
    list.add(new ThemeModel("white", ThemeModel.CATEGORY_COLORS, R.string.pref_theme_e_white,
        R.style.White, 0, 0xFFFFFFFF, 0xFFF1F3F4, 0xFF0066CC, false, false, false, null, 0f));

    // 4. Dark Blue
    list.add(new ThemeModel("darkblue", ThemeModel.CATEGORY_COLORS, R.string.pref_theme_e_darkblue,
        R.style.ThemeDarkBlue, 0, 0xFF1A202C, 0xFF2D3748, 0xFF3182CE, false, false, false, null, 0f));

    // 5. Red
    list.add(new ThemeModel("red", ThemeModel.CATEGORY_COLORS, R.string.pref_theme_e_red,
        R.style.ThemeRed, 0, 0xFFB71C1C, 0xFFC62828, 0xFFEF5350, false, false, false, null, 0f));

    // 6. Green
    list.add(new ThemeModel("green", ThemeModel.CATEGORY_COLORS, R.string.pref_theme_e_green,
        R.style.ThemeGreen, 0, 0xFF2E7D32, 0xFF388E3C, 0xFF4CAF50, false, false, false, null, 0f));

    // 7. Pine / Dark Green
    list.add(new ThemeModel("pine", ThemeModel.CATEGORY_COLORS, R.string.pref_theme_e_pine,
        R.style.Pine, 0, 0xFF004D40, 0xFF00695C, 0xFF64FFDA, false, false, false, null, 0f));

    // 8. Royal Blue
    list.add(new ThemeModel("blue", ThemeModel.CATEGORY_COLORS, R.string.pref_theme_e_blue,
        R.style.ThemeBlue, 0, 0xFF1565C0, 0xFF1976D2, 0xFF2196F3, false, false, false, null, 0f));

    // 9. Cyan
    list.add(new ThemeModel("cyan", ThemeModel.CATEGORY_COLORS, R.string.pref_theme_e_cyan,
        R.style.ThemeCyan, 0, 0xFF00838F, 0xFF0097A7, 0xFF00BCD4, false, false, false, null, 0f));

    // 10. Amber
    list.add(new ThemeModel("amber", ThemeModel.CATEGORY_COLORS, R.string.pref_theme_e_amber,
        R.style.ThemeAmber, 0, 0xFFE65100, 0xFFEF6C00, 0xFFFB8C00, false, false, false, null, 0f));

    // 11. Purple
    list.add(new ThemeModel("purple", ThemeModel.CATEGORY_COLORS, R.string.pref_theme_e_purple,
        R.style.ThemePurple, 0, 0xFF4A148C, 0xFF6A1B9A, 0xFF8E24AA, false, false, false, null, 0f));

    // 12. Pink
    list.add(new ThemeModel("pink", ThemeModel.CATEGORY_COLORS, R.string.pref_theme_e_pink,
        R.style.ThemePink, 0, 0xFF880E4F, 0xFFAD1457, 0xFFD81B60, false, false, false, null, 0f));

    // 13. Black (AMOLED)
    list.add(new ThemeModel("black", ThemeModel.CATEGORY_COLORS, R.string.pref_theme_e_black,
        R.style.Black, 0, 0xFF000000, 0xFF212121, 0xFF009DFF, false, false, false, null, 0f));

    // 14. Desert
    list.add(new ThemeModel("desert", ThemeModel.CATEGORY_COLORS, R.string.pref_theme_e_desert,
        R.style.Desert, 0, 0xFFFFE0B2, 0xFFFFF3E0, 0xFFE65100, false, false, false, null, 0f));

    // 15. Rosé Pine
    list.add(new ThemeModel("rosepine", ThemeModel.CATEGORY_COLORS, R.string.pref_theme_e_rosepine,
        R.style.RosePine, 0, 0xFF191724, 0xFF26233A, 0xFFC4A7E7, false, false, false, null, 0f));

    // 16. ePaper
    list.add(new ThemeModel("epaper", ThemeModel.CATEGORY_COLORS, R.string.pref_theme_e_epaper,
        R.style.ePaper, 0, 0xFFFFFFFF, 0xFFE8E8E8, 0xFF000000, false, false, false, null, 0f));

    return list;
  }

  public static List<ThemeModel> getCustomThemes(Context context)
  {
    List<ThemeModel> list = new ArrayList<>();
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    String json = prefs.getString(PREF_CUSTOM_THEMES, null);
    if (json != null)
    {
      try
      {
        JSONArray arr = new JSONArray(json);
        for (int i = 0; i < arr.length(); i++)
        {
          JSONObject obj = arr.getJSONObject(i);
          String id = obj.getString("id");
          String title = obj.optString("title", context.getString(R.string.theme_custom_theme_title));
          String path = obj.getString("path");
          double darkness = obj.optDouble("darkness", 0.3);
          double keyOpacity = obj.optDouble("key_opacity", 1.0);
          double blur = obj.optDouble("blur", 0.0);
          if (new File(path).exists())
          {
            list.add(new ThemeModel(id, title, path, (float) darkness, (float) keyOpacity, (float) blur));
          }
        }
      }
      catch (Exception ignored) {}
    }
    return list;
  }

  public static void saveCustomTheme(Context context, ThemeModel model)
  {
    List<ThemeModel> existing = getCustomThemes(context);
    boolean replaced = false;
    for (int i = 0; i < existing.size(); i++)
    {
      if (existing.get(i).id.equals(model.id))
      {
        existing.set(i, model);
        replaced = true;
        break;
      }
    }
    if (!replaced)
    {
      existing.add(0, model); // newest first
    }
    writeCustomThemes(context, existing);
  }

  public static void deleteCustomTheme(Context context, String id)
  {
    List<ThemeModel> existing = getCustomThemes(context);
    for (int i = existing.size() - 1; i >= 0; i--)
    {
      if (existing.get(i).id.equals(id))
      {
        if (existing.get(i).imagePath != null)
        {
          try
          {
            new File(existing.get(i).imagePath).delete();
            String rawPath = existing.get(i).imagePath.replace(".jpg", "_raw.jpg");
            new File(rawPath).delete();
          }
          catch (Exception ignored) {}
        }
        existing.remove(i);
      }
    }
    writeCustomThemes(context, existing);
  }

  private static void writeCustomThemes(Context context, List<ThemeModel> list)
  {
    try
    {
      JSONArray arr = new JSONArray();
      for (ThemeModel m : list)
      {
        JSONObject obj = new JSONObject();
        obj.put("id", m.id);
        obj.put("title", m.customTitle);
        obj.put("path", m.imagePath);
        obj.put("darkness", m.darknessOverlay);
        obj.put("key_opacity", m.keyOpacity);
        obj.put("blur", m.blur);
        arr.put(obj);
      }
      PreferenceManager.getDefaultSharedPreferences(context)
          .edit()
          .putString(PREF_CUSTOM_THEMES, arr.toString())
          .apply();
    }
    catch (Exception ignored) {}
  }

  public static ThemeModel findThemeById(Context context, String id)
  {
    if (id == null || id.isEmpty()) id = "system";
    for (ThemeModel m : getDefaultThemes())
    {
      if (m.id.equals(id)) return m;
    }
    for (ThemeModel m : getColorThemes())
    {
      if (m.id.equals(id)) return m;
    }
    for (ThemeModel m : getCustomThemes(context))
    {
      if (m.id.equals(id)) return m;
    }
    return getDefaultThemes().get(1); // fallback to system
  }
}
