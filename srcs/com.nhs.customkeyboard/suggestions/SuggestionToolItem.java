package com.nhs.customkeyboard.suggestions;

import android.content.Context;
import android.content.SharedPreferences;
import com.nhs.customkeyboard.Logs;
import com.nhs.customkeyboard.R;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SuggestionToolItem
{
  public static final String PREF_KEY = "suggestion_bar_tools_config_v3";

  public final String id;
  public final int titleRes;
  public final int iconRes;
  public String customTitle;
  public boolean enabled;
  public String longPressAction;

  public SuggestionToolItem(String id, int titleRes, int iconRes, boolean enabled)
  {
    this(id, titleRes, null, iconRes, enabled, null);
  }

  public SuggestionToolItem(String id, int titleRes, int iconRes, boolean enabled, String longPressAction)
  {
    this(id, titleRes, null, iconRes, enabled, longPressAction);
  }

  public SuggestionToolItem(String id, String customTitle, int iconRes, boolean enabled, String longPressAction)
  {
    this(id, 0, customTitle, iconRes, enabled, longPressAction);
  }

  public SuggestionToolItem(String id, int titleRes, String customTitle, int iconRes, boolean enabled, String longPressAction)
  {
    this.id = id;
    this.titleRes = titleRes;
    this.customTitle = customTitle;
    this.iconRes = iconRes;
    this.enabled = enabled;
    this.longPressAction = longPressAction;
  }

  public boolean isTasker()
  {
    return id != null && id.startsWith(SuggestionActionHelper.TASKER_PREFIX);
  }

  public String getTitle(Context ctx)
  {
    if (customTitle != null && !customTitle.trim().isEmpty())
    {
      return customTitle.trim();
    }
    if (isTasker())
    {
      return id.substring(SuggestionActionHelper.TASKER_PREFIX.length());
    }
    if (titleRes != 0 && ctx != null)
    {
      return ctx.getString(titleRes);
    }
    return id != null ? id : "";
  }

  public int getIconRes()
  {
    if (isTasker())
    {
      return R.drawable.ic_tasker_bolt;
    }
    return iconRes != 0 ? iconRes : R.drawable.ic_special_key;
  }

  public SuggestionToolItem copy()
  {
    return new SuggestionToolItem(this.id, this.titleRes, this.customTitle, this.iconRes, this.enabled, this.longPressAction);
  }

  public static List<SuggestionToolItem> getDefaultTools()
  {
    List<SuggestionToolItem> list = new ArrayList<>();
    list.add(new SuggestionToolItem("paste", R.string.tool_paste, R.drawable.ic_tool_paste, true, "clipboard"));
    list.add(new SuggestionToolItem("copy", R.string.tool_copy, R.drawable.ic_tool_copy, true, "selectAll"));
    list.add(new SuggestionToolItem("cut", R.string.tool_cut, R.drawable.ic_tool_cut, false));
    list.add(new SuggestionToolItem("selectAll", R.string.tool_select_all, R.drawable.ic_tool_select_all, false));
    list.add(new SuggestionToolItem("translate", R.string.tool_translate, R.drawable.ic_tool_translate, true));
    list.add(new SuggestionToolItem("undo", R.string.tool_undo, R.drawable.ic_tool_undo, false));
    list.add(new SuggestionToolItem("redo", R.string.tool_redo, R.drawable.ic_tool_redo, false));
    list.add(new SuggestionToolItem("delete", R.string.tool_delete, R.drawable.ic_tool_backspace, false));
    list.add(new SuggestionToolItem("delete_word", R.string.tool_delete_word, R.drawable.ic_delete, false));
    list.add(new SuggestionToolItem("clipboard", R.string.tool_clipboard, R.drawable.ic_tool_clipboard, false));
    list.add(new SuggestionToolItem("voice_typing", R.string.tool_voice_typing, R.drawable.ic_mic, false));
    list.add(new SuggestionToolItem("share", R.string.tool_share, R.drawable.ic_open_in_new, false));
    list.add(new SuggestionToolItem("paste_plain", R.string.tool_paste_plain, R.drawable.ic_clipboard_paste, false));
    list.add(new SuggestionToolItem("capslock", R.string.tool_caps_lock, R.drawable.ic_capital, false));
    list.add(new SuggestionToolItem("switch_keyboard", R.string.tool_switch_keyboard, R.drawable.ic_keyboard, false));
    list.add(new SuggestionToolItem("theme", R.string.tool_theme, R.drawable.ic_palette, false));
    list.add(new SuggestionToolItem("gif", R.string.tool_gif, R.drawable.ic_gif, false));
    list.add(new SuggestionToolItem("settings", R.string.tool_settings, R.drawable.ic_tool_settings, false));
    return list;
  }

  public static List<SuggestionToolItem> loadFromPrefs(SharedPreferences prefs)
  {
    return loadFromPrefs(null, prefs);
  }

  public static List<SuggestionToolItem> loadFromPrefs(Context ctx, SharedPreferences prefs)
  {
    List<SuggestionToolItem> defaults = getDefaultTools();
    if (prefs == null) return defaults;

    String jsonStr = prefs.getString(PREF_KEY, null);
    List<SuggestionToolItem> result = new ArrayList<>();
    Set<String> seenIds = new HashSet<>();

    if (jsonStr != null && !jsonStr.trim().isEmpty())
    {
      try
      {
        JSONArray arr = new JSONArray(jsonStr);
        for (int i = 0; i < arr.length(); i++)
        {
          JSONObject obj = arr.getJSONObject(i);
          String id = obj.optString("id", null);
          if (id == null) continue;

          boolean enabled = obj.optBoolean("enabled", true);
          String longPressAction = obj.optString("long_press_action", null);
          if (longPressAction != null && longPressAction.trim().isEmpty())
          {
            longPressAction = null;
          }
          String customTitle = obj.optString("custom_title", null);

          if (id.startsWith(SuggestionActionHelper.TASKER_PREFIX))
          {
            if (!seenIds.contains(id))
            {
              seenIds.add(id);
              result.add(new SuggestionToolItem(id, customTitle, R.drawable.ic_tasker_bolt, enabled, longPressAction));
            }
          }
          else
          {
            SuggestionToolItem base = findInList(defaults, id);
            if (base != null && !seenIds.contains(base.id))
            {
              seenIds.add(base.id);
              result.add(new SuggestionToolItem(base.id, base.titleRes, customTitle, base.iconRes, enabled, longPressAction));
            }
          }
        }
      }
      catch (JSONException e)
      {
        Logs.exn("SuggestionToolItem", e);
      }
    }

    // Append any default tools that were not in saved prefs
    for (SuggestionToolItem def : defaults)
    {
      if (!seenIds.contains(def.id))
      {
        seenIds.add(def.id);
        result.add(def.copy());
      }
    }

    // If context is provided, also discover any configured Tasker tasks (if exist)
    if (ctx != null)
    {
      try
      {
        List<SuggestionActionHelper.ActionItem> taskerActions = SuggestionActionHelper.getTaskerActions(ctx);
        if (taskerActions != null)
        {
          for (SuggestionActionHelper.ActionItem action : taskerActions)
          {
            if (!seenIds.contains(action.id))
            {
              seenIds.add(action.id);
              result.add(new SuggestionToolItem(action.id, action.title, R.drawable.ic_tasker_bolt, false, null));
            }
          }
        }
      }
      catch (Exception e)
      {
        Logs.exn("SuggestionToolItem", e);
      }
    }

    return result;
  }

  public static void saveToPrefs(SharedPreferences prefs, List<SuggestionToolItem> tools)
  {
    if (prefs == null || tools == null) return;
    try
    {
      JSONArray arr = new JSONArray();
      for (SuggestionToolItem item : tools)
      {
        JSONObject obj = new JSONObject();
        obj.put("id", item.id);
        obj.put("enabled", item.enabled);
        if (item.customTitle != null && !item.customTitle.trim().isEmpty())
        {
          obj.put("custom_title", item.customTitle.trim());
        }
        if (item.longPressAction != null && !item.longPressAction.trim().isEmpty())
        {
          obj.put("long_press_action", item.longPressAction.trim());
        }
        arr.put(obj);
      }
      prefs.edit().putString(PREF_KEY, arr.toString()).apply();
    }
    catch (JSONException e)
    {
      Logs.exn("SuggestionToolItem", e);
    }
  }

  public static SuggestionToolItem findInList(List<SuggestionToolItem> list, String id)
  {
    if (list == null || id == null) return null;
    for (SuggestionToolItem item : list)
    {
      if (item.id.equals(id) ||
          (item.id.equals("capslock") && id.equals("caps_lock")) ||
          (item.id.equals("caps_lock") && id.equals("capslock")))
      {
        return item;
      }
    }
    return null;
  }
}
