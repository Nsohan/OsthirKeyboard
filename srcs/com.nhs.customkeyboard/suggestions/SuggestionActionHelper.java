package com.nhs.customkeyboard.suggestions;

import android.content.Context;
import com.nhs.customkeyboard.Logs;
import com.nhs.customkeyboard.R;
import com.nhs.customkeyboard.TaskerAutomationConfig;
import com.nhs.customkeyboard.prefs.TaskerAutomationManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SuggestionActionHelper
{
  public static final String TASKER_PREFIX = "tasker:";

  public static class ActionItem
  {
    public final String id;
    public final String title;
    public final String subtitle;
    public final int iconRes;
    public final boolean isTasker;

    public ActionItem(String id, String title, String subtitle, int iconRes, boolean isTasker)
    {
      this.id = id;
      this.title = title;
      this.subtitle = subtitle;
      this.iconRes = iconRes;
      this.isTasker = isTasker;
    }
  }

  public static List<ActionItem> getStandardActions(Context ctx)
  {
    List<ActionItem> list = new ArrayList<>();
    list.add(new ActionItem("copy", ctx.getString(R.string.tool_copy), null, R.drawable.ic_tool_copy, false));
    list.add(new ActionItem("cut", ctx.getString(R.string.tool_cut), null, R.drawable.ic_tool_cut, false));
    list.add(new ActionItem("paste", ctx.getString(R.string.tool_paste), null, R.drawable.ic_tool_paste, false));
    list.add(new ActionItem("selectAll", ctx.getString(R.string.tool_select_all), null, R.drawable.ic_tool_select_all, false));
    list.add(new ActionItem("undo", ctx.getString(R.string.tool_undo), null, R.drawable.ic_tool_undo, false));
    list.add(new ActionItem("redo", ctx.getString(R.string.tool_redo), null, R.drawable.ic_tool_redo, false));
    list.add(new ActionItem("delete", ctx.getString(R.string.tool_delete), null, R.drawable.ic_tool_backspace, false));
    list.add(new ActionItem("delete_word", ctx.getString(R.string.tool_delete_word), null, R.drawable.ic_delete, false));
    list.add(new ActionItem("clipboard", ctx.getString(R.string.tool_clipboard), null, R.drawable.ic_tool_clipboard, false));
    list.add(new ActionItem("translate", ctx.getString(R.string.tool_translate), null, R.drawable.ic_tool_translate, false));
    list.add(new ActionItem("voice_typing", ctx.getString(R.string.tool_voice_typing), null, R.drawable.ic_mic, false));
    list.add(new ActionItem("share", ctx.getString(R.string.tool_share), null, R.drawable.ic_open_in_new, false));
    list.add(new ActionItem("paste_plain", ctx.getString(R.string.tool_paste_plain), null, R.drawable.ic_clipboard_paste, false));
    list.add(new ActionItem("capslock", ctx.getString(R.string.tool_caps_lock), null, R.drawable.ic_capital, false));
    list.add(new ActionItem("switch_keyboard", ctx.getString(R.string.tool_switch_keyboard), null, R.drawable.ic_keyboard, false));
    list.add(new ActionItem("theme", ctx.getString(R.string.tool_theme), null, R.drawable.ic_palette, false));
    list.add(new ActionItem("gif", ctx.getString(R.string.tool_gif), null, R.drawable.ic_gif, false));
    list.add(new ActionItem("settings", ctx.getString(R.string.tool_settings), null, R.drawable.ic_tool_settings, false));
    return list;
  }

  public static List<ActionItem> getTaskerActions(Context ctx)
  {
    List<ActionItem> list = new ArrayList<>();
    String json = TaskerAutomationManager.load(ctx);
    if (json == null || json.trim().isEmpty())
    {
      return list;
    }

    try
    {
      TaskerAutomationConfig config = TaskerAutomationConfig.parse(json);
      Map<String, String> seenTasks = new LinkedHashMap<>();

      if (config.tasks != null)
      {
        for (Map.Entry<String, String> entry : config.tasks.entrySet())
        {
          String keyword = entry.getKey();
          String taskName = entry.getValue();
          if (taskName != null && !taskName.trim().isEmpty())
          {
            seenTasks.put(taskName.trim(), "Keyword: " + keyword);
          }
        }
      }

      if (config.expand_patterns != null)
      {
        for (TaskerAutomationConfig.ExpandPattern pattern : config.expand_patterns)
        {
          if (pattern != null && pattern.task != null && !pattern.task.trim().isEmpty())
          {
            String name = pattern.name != null && !pattern.name.trim().isEmpty() ? pattern.name.trim() : "Pattern";
            if (!seenTasks.containsKey(pattern.task.trim()))
            {
              seenTasks.put(pattern.task.trim(), name);
            }
          }
        }
      }

      for (Map.Entry<String, String> entry : seenTasks.entrySet())
      {
        String taskName = entry.getKey();
        String sub = entry.getValue();
        list.add(new ActionItem(TASKER_PREFIX + taskName, taskName, sub, R.drawable.ic_tasker_bolt, true));
      }
    }
    catch (Exception e)
    {
      Logs.exn("SuggestionActionHelper", e);
    }

    return list;
  }

  public static String getActionDisplayName(Context ctx, String actionId)
  {
    if (actionId == null || actionId.trim().isEmpty())
    {
      return ctx.getString(R.string.tool_remove_action);
    }

    if (actionId.startsWith(TASKER_PREFIX))
    {
      String taskName = actionId.substring(TASKER_PREFIX.length());
      return "Tasker: " + taskName;
    }

    for (ActionItem item : getStandardActions(ctx))
    {
      if (item.id.equals(actionId) ||
          (item.id.equals("capslock") && actionId.equals("caps_lock")) ||
          (item.id.equals("caps_lock") && actionId.equals("capslock")))
      {
        return item.title;
      }
    }

    return actionId;
  }

  public static int getActionIconRes(String actionId)
  {
    if (actionId == null || actionId.trim().isEmpty())
    {
      return R.drawable.ic_close;
    }

    if (actionId.startsWith(TASKER_PREFIX))
    {
      return R.drawable.ic_tasker_bolt;
    }

    switch (actionId)
    {
      case "copy": return R.drawable.ic_tool_copy;
      case "cut": return R.drawable.ic_tool_cut;
      case "paste": return R.drawable.ic_tool_paste;
      case "selectAll": return R.drawable.ic_tool_select_all;
      case "undo": return R.drawable.ic_tool_undo;
      case "redo": return R.drawable.ic_tool_redo;
      case "delete": return R.drawable.ic_tool_backspace;
      case "delete_word": return R.drawable.ic_delete;
      case "clipboard": return R.drawable.ic_tool_clipboard;
      case "translate": return R.drawable.ic_tool_translate;
      case "voice_typing": return R.drawable.ic_mic;
      case "share": return R.drawable.ic_open_in_new;
      case "paste_plain": return R.drawable.ic_clipboard_paste;
      case "capslock":
      case "caps_lock": return R.drawable.ic_capital;
      case "switch_keyboard": return R.drawable.ic_keyboard;
      case "theme": return R.drawable.ic_palette;
      case "gif": return R.drawable.ic_gif;
      case "settings": return R.drawable.ic_tool_settings;
      default: return R.drawable.ic_special_key;
    }
  }
}
