package com.nhs.customkeyboard.textexpander;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/** Represents a single Text Expander snippet rule.
 *  Typing [trigger_prefix][keyword] (e.g. "@@email") in any text field
 *  replaces the typed trigger with [output] (which can contain static text,
 *  variables like $date, $time, $copiedText, or a Tasker task call $task:name). */
public final class TextExpanderItem
{
    public static final String PREFIX_TASKER_TASK = "$task:";
    public static final String DEFAULT_TRIGGER_PREFIX = "@@";

    public String id;
    public String trigger_prefix;
    public String keyword;
    public String output;
    public boolean enabled;

    public TextExpanderItem()
    {
        id = UUID.randomUUID().toString();
        trigger_prefix = DEFAULT_TRIGGER_PREFIX;
        keyword = "";
        output = "";
        enabled = true;
    }

    public TextExpanderItem(String id_, String trigger_prefix_, String keyword_, String output_, boolean enabled_)
    {
        id = id_ != null ? id_ : UUID.randomUUID().toString();
        trigger_prefix = trigger_prefix_ != null ? trigger_prefix_ : DEFAULT_TRIGGER_PREFIX;
        keyword = keyword_ != null ? keyword_ : "";
        output = output_ != null ? output_ : "";
        enabled = enabled_;
    }

    public String get_full_trigger()
    {
        String p = trigger_prefix != null ? trigger_prefix : DEFAULT_TRIGGER_PREFIX;
        String k = keyword != null ? keyword : "";
        return p + k;
    }

    public boolean is_tasker_task()
    {
        return (output != null && (output.startsWith(PREFIX_TASKER_TASK) || output.startsWith("@task:") || output.startsWith("task:")))
                || (keyword != null && keyword.startsWith("task:"));
    }

    public String get_task_name()
    {
        if (output == null)
            return "";
        if (output.startsWith(PREFIX_TASKER_TASK))
            return output.substring(PREFIX_TASKER_TASK.length()).trim();
        if (output.startsWith("@task:"))
            return output.substring(6).trim();
        if (output.startsWith("task:"))
            return output.substring(5).trim();
        if (keyword != null && keyword.startsWith("task:"))
            return keyword.substring(5).trim();
        return output.trim();
    }

    public JSONObject to_json() throws JSONException
    {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("trigger_prefix", trigger_prefix != null ? trigger_prefix : DEFAULT_TRIGGER_PREFIX);
        obj.put("keyword", keyword != null ? keyword : "");
        obj.put("output", output != null ? output : "");
        obj.put("enabled", enabled);
        return obj;
    }

    public static TextExpanderItem from_json(JSONObject obj)
    {
        if (obj == null)
            return null;
        String id = obj.optString("id", UUID.randomUUID().toString());
        String prefix = obj.optString("trigger_prefix", DEFAULT_TRIGGER_PREFIX);
        String keyword = obj.optString("keyword", "");
        String output = obj.optString("output", "");
        boolean enabled = obj.optBoolean("enabled", true);
        return new TextExpanderItem(id, prefix, keyword, output, enabled);
    }
}

