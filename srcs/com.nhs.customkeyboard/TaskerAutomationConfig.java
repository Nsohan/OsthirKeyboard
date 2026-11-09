package com.nhs.customkeyboard;

import com.nhs.customkeyboard.tasker.MaterialSymbols;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Parsed, validated form of the Tasker Automation JSON.
 * Maps keyboard shortcut names to Tasker task names and optional Material Symbol icons.
 */
public final class TaskerAutomationConfig
{
    public static final String KEY_REPLACE_TRIGGER = "nhck_replace";
    public static final String KEY_APPEND_TRIGGER = "nhck_append";
    public static final String KEY_TIMEOUT_MS = "nhck_timeout";
    public static final String KEY_EXPAND_PATTERNS = "nhck_patterns";
    public static final String KEY_TASK_ICONS = "nhck_task_icons";

    public static final String LEGACY_KEY_REPLACE_TRIGGER = "nhck_replace";
    public static final String LEGACY_KEY_APPEND_TRIGGER = "nhck_append";
    public static final String LEGACY_KEY_TIMEOUT_MS = "nhck_timeout";
    public static final String LEGACY_KEY_EXPAND_PATTERNS = "nhck_patterns";
    public static final String KEY_EXPAND_PATTERN_REGEX = "regex";

    public static final String DEFAULT_REPLACE_TRIGGER = "";
    public static final String DEFAULT_APPEND_TRIGGER = "";
    public static final long DEFAULT_TIMEOUT_MS = 15000;
    public static final long MIN_TIMEOUT_MS = 1000;
    public static final long MAX_TIMEOUT_MS = 120000;

    public static final String DEFAULT_EXPAND_PATTERN_NAME = "Classic calculation";
    public static final String DEFAULT_EXPAND_PATTERN_PREFIX = "\\.\\.";
    public static final String DEFAULT_EXPAND_PATTERN_SUFFIX = " ";
    public static final String DEFAULT_EXPAND_PATTERN_TASK = "ReplaceYourTaskName";
    public static final String DEFAULT_EXPAND_PATTERN_REPLACE_PREFIX = "true";
    public static final String DEFAULT_EXPAND_PATTERN_FIRE_ON_SUFFIX = "true";

    public static final String DEFAULT_LIVE_PATTERN_NAME = "Live text expander";
    public static final String DEFAULT_LIVE_PATTERN_PREFIX = "(\\s|^)";
    public static final String DEFAULT_LIVE_PATTERN_REGEX = "x.{2,}";
    public static final String DEFAULT_LIVE_PATTERN_SUFFIX = " ";
    public static final String DEFAULT_LIVE_PATTERN_TASK = "ReplaceYourTaskName";
    public static final String DEFAULT_LIVE_PATTERN_REPLACE_PREFIX = "false";
    public static final String DEFAULT_LIVE_PATTERN_FIRE_ON_SUFFIX = "false";

    public static final class ExpandPattern
    {
        public final String name;
        public final String prefix;
        public final Pattern compiled_prefix;
        public final String regex;
        public final Pattern compiled_regex;
        public final String suffix;
        public final Pattern compiled_suffix;
        public final String task;
        public final String replace_prefix_str;
        public final boolean replace_prefix;
        public final String fire_on_suffix_str;
        public final boolean fire_on_suffix;

        public ExpandPattern(String name_, String prefix_, Pattern compiled_prefix_, String regex_, Pattern compiled_regex_,
                             String suffix_, Pattern compiled_suffix_, String task_,
                             String replace_prefix_str_, boolean replace_prefix_,
                             String fire_on_suffix_str_, boolean fire_on_suffix_)
        {
            name = name_;
            prefix = prefix_;
            compiled_prefix = compiled_prefix_;
            regex = regex_;
            compiled_regex = compiled_regex_;
            suffix = suffix_;
            compiled_suffix = compiled_suffix_;
            task = task_;
            replace_prefix_str = replace_prefix_str_;
            replace_prefix = replace_prefix_;
            fire_on_suffix_str = fire_on_suffix_str_;
            fire_on_suffix = fire_on_suffix_;
        }
    }

    public final String replace_trigger;
    public final String append_trigger;
    public final long timeout_ms;
    /** keyword -> Tasker task name, in declared order. */
    public final LinkedHashMap<String, String> tasks;
    /** keyword -> Material symbol icon name, in declared order. */
    public final LinkedHashMap<String, String> task_icons;
    public final List<ExpandPattern> expand_patterns;
    public final boolean needs_persist;

    private TaskerAutomationConfig(String replace_trigger_, String append_trigger_,
                                   long timeout_ms_, LinkedHashMap<String, String> tasks_,
                                   LinkedHashMap<String, String> task_icons_,
                                   List<ExpandPattern> expand_patterns_, boolean needs_persist_)
    {
        replace_trigger = replace_trigger_ != null ? replace_trigger_ : "";
        append_trigger = append_trigger_ != null ? append_trigger_ : "";
        timeout_ms = timeout_ms_;
        tasks = tasks_ != null ? tasks_ : new LinkedHashMap<>();
        task_icons = task_icons_ != null ? task_icons_ : new LinkedHashMap<>();
        expand_patterns = expand_patterns_ != null ? expand_patterns_ : new ArrayList<>();
        needs_persist = needs_persist_;
    }

    public String getTaskIcon(String keyword)
    {
        if (keyword == null) return MaterialSymbols.DEFAULT_ICON;
        String icon = task_icons.get(keyword);
        return (icon != null && !icon.isEmpty()) ? icon : MaterialSymbols.DEFAULT_ICON;
    }

    public String getIconForTask(String taskOrKeyword)
    {
        if (taskOrKeyword == null || taskOrKeyword.trim().isEmpty())
        {
            return MaterialSymbols.DEFAULT_ICON;
        }
        String query = taskOrKeyword.trim();
        if (query.startsWith("tasker:"))
        {
            query = query.substring("tasker:".length()).trim();
        }
        else if (query.startsWith("task:"))
        {
            query = query.substring("task:".length()).trim();
        }
        if (task_icons.containsKey(query))
        {
            return task_icons.get(query);
        }
        for (Map.Entry<String, String> e : tasks.entrySet())
        {
            if (query.equalsIgnoreCase(e.getValue()) || query.equalsIgnoreCase(e.getKey()))
            {
                String icon = task_icons.get(e.getKey());
                if (icon != null && !icon.isEmpty())
                {
                    return icon;
                }
            }
        }
        return MaterialSymbols.DEFAULT_ICON;
    }

    public static TaskerAutomationConfig parse(String json) throws KeymapJsonUtils.ParseError
    {
        if (json == null || json.trim().isEmpty())
        {
            return new TaskerAutomationConfig("", "", DEFAULT_TIMEOUT_MS,
                    new LinkedHashMap<>(), new LinkedHashMap<>(), new ArrayList<>(), false);
        }

        try
        {
            JSONObject root = new JSONObject(json);

            String replace_trigger = root.optString(KEY_REPLACE_TRIGGER, root.optString(LEGACY_KEY_REPLACE_TRIGGER, ""));
            String append_trigger = root.optString(KEY_APPEND_TRIGGER, root.optString(LEGACY_KEY_APPEND_TRIGGER, ""));
            long timeout_ms = DEFAULT_TIMEOUT_MS;

            if (root.has(KEY_TIMEOUT_MS) || root.has(LEGACY_KEY_TIMEOUT_MS))
            {
                String timeoutStr = root.optString(KEY_TIMEOUT_MS, root.optString(LEGACY_KEY_TIMEOUT_MS, "15000"));
                try
                {
                    timeout_ms = Long.parseLong(timeoutStr.trim());
                }
                catch (NumberFormatException e)
                {
                    throw new KeymapJsonUtils.ParseError(
                            "\"" + KEY_TIMEOUT_MS + "\" must be a whole number of milliseconds, got \"" + timeoutStr + "\"");
                }
                if (timeout_ms < MIN_TIMEOUT_MS || timeout_ms > MAX_TIMEOUT_MS)
                {
                    throw new KeymapJsonUtils.ParseError(
                            "\"" + KEY_TIMEOUT_MS + "\" must be between " + MIN_TIMEOUT_MS
                                    + " and " + MAX_TIMEOUT_MS + " (ms), got " + timeout_ms);
                }
            }

            LinkedHashMap<String, String> tasks = new LinkedHashMap<>();
            LinkedHashMap<String, String> task_icons = new LinkedHashMap<>();

            // Read explicit icons map if present
            if (root.has(KEY_TASK_ICONS))
            {
                JSONObject iconsObj = root.optJSONObject(KEY_TASK_ICONS);
                if (iconsObj != null)
                {
                    Iterator<String> iconKeys = iconsObj.keys();
                    while (iconKeys.hasNext())
                    {
                        String k = iconKeys.next();
                        task_icons.put(k, iconsObj.optString(k, MaterialSymbols.DEFAULT_ICON));
                    }
                }
            }

            Iterator<String> keys = root.keys();
            while (keys.hasNext())
            {
                String key = keys.next();
                if (key.equals(KEY_REPLACE_TRIGGER) || key.equals(LEGACY_KEY_REPLACE_TRIGGER)
                        || key.equals(KEY_APPEND_TRIGGER) || key.equals(LEGACY_KEY_APPEND_TRIGGER)
                        || key.equals(KEY_TIMEOUT_MS) || key.equals(LEGACY_KEY_TIMEOUT_MS)
                        || key.equals(KEY_EXPAND_PATTERNS) || key.equals(LEGACY_KEY_EXPAND_PATTERNS)
                        || key.equals(KEY_TASK_ICONS))
                {
                    continue;
                }

                Object val = root.get(key);
                if (val instanceof JSONObject)
                {
                    JSONObject taskObj = (JSONObject) val;
                    String taskName = taskObj.optString("task", "");
                    String icon = taskObj.optString("icon", MaterialSymbols.DEFAULT_ICON);
                    tasks.put(key, taskName);
                    task_icons.put(key, icon);
                }
                else if (val instanceof String)
                {
                    tasks.put(key, (String) val);
                    if (!task_icons.containsKey(key))
                    {
                        task_icons.put(key, MaterialSymbols.DEFAULT_ICON);
                    }
                }
            }

            List<ExpandPattern> expand_patterns = new ArrayList<>();
            if (root.has(KEY_EXPAND_PATTERNS) || root.has(LEGACY_KEY_EXPAND_PATTERNS))
            {
                JSONArray arr = root.optJSONArray(KEY_EXPAND_PATTERNS);
                if (arr == null)
                {
                    arr = root.optJSONArray(LEGACY_KEY_EXPAND_PATTERNS);
                }
                if (arr != null)
                {
                    for (int i = 0; i < arr.length(); i++)
                    {
                        JSONObject obj = arr.optJSONObject(i);
                        if (obj == null) continue;

                        String name = obj.optString("name", null);
                        String prefix = obj.optString("prefix", "");
                        String regex = obj.has(KEY_EXPAND_PATTERN_REGEX) ? obj.optString(KEY_EXPAND_PATTERN_REGEX) : null;
                        String suffix = obj.optString("suffix", "");
                        String task = obj.optString("task", "");
                        String replace_prefix_str = obj.optString("replace_prefix", "false");
                        String fire_on_suffix_str = obj.optString("fire_on_suffix", "true");

                        if (prefix.isEmpty() || suffix.isEmpty() || task.isEmpty())
                        {
                            continue;
                        }

                        boolean replace_prefix = Boolean.parseBoolean(replace_prefix_str);
                        boolean fire_on_suffix = Boolean.parseBoolean(fire_on_suffix_str);

                        Pattern compiled_prefix = compile_pattern_field_regex("prefix", prefix);
                        Pattern compiled_regex = regex != null && !regex.isEmpty()
                                ? compile_pattern_field_regex(KEY_EXPAND_PATTERN_REGEX, regex) : null;
                        Pattern compiled_suffix = compile_pattern_field_regex("suffix", suffix);

                        expand_patterns.add(new ExpandPattern(name, prefix, compiled_prefix, regex, compiled_regex,
                                suffix, compiled_suffix, task, replace_prefix_str, replace_prefix,
                                fire_on_suffix_str, fire_on_suffix));
                    }
                }
            }

            return new TaskerAutomationConfig(replace_trigger, append_trigger, timeout_ms, tasks,
                    task_icons, expand_patterns, false);
        }
        catch (KeymapJsonUtils.ParseError pe)
        {
            throw pe;
        }
        catch (Exception e)
        {
            throw new KeymapJsonUtils.ParseError("Invalid Tasker JSON: " + e.getMessage());
        }
    }

    private static Pattern compile_pattern_field_regex(String field_name, String pattern_source) throws KeymapJsonUtils.ParseError
    {
        try
        {
            return Pattern.compile(pattern_source, Pattern.MULTILINE);
        }
        catch (PatternSyntaxException pse)
        {
            throw new KeymapJsonUtils.ParseError(
                    "Invalid \"" + field_name + "\" in \"" + KEY_EXPAND_PATTERNS + "\" entry (\"" + pattern_source
                            + "\"): " + pse.getDescription());
        }
    }

    public String to_json()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"").append(KEY_TIMEOUT_MS).append("\": \"").append(timeout_ms).append("\"");

        if (replace_trigger != null && !replace_trigger.isEmpty())
        {
            sb.append(",\n  \"").append(KEY_REPLACE_TRIGGER).append("\": \"").append(escape_json(replace_trigger)).append("\"");
        }
        if (append_trigger != null && !append_trigger.isEmpty())
        {
            sb.append(",\n  \"").append(KEY_APPEND_TRIGGER).append("\": \"").append(escape_json(append_trigger)).append("\"");
        }

        for (Map.Entry<String, String> e : tasks.entrySet())
        {
            String keyword = e.getKey();
            String taskName = e.getValue();
            String icon = getTaskIcon(keyword);

            sb.append(",\n  \"").append(escape_json(keyword)).append("\": {\n");
            sb.append("    \"task\": \"").append(escape_json(taskName)).append("\",\n");
            sb.append("    \"icon\": \"").append(escape_json(icon)).append("\"\n");
            sb.append("  }");
        }

        if (expand_patterns != null && !expand_patterns.isEmpty())
        {
            sb.append(",\n  \"").append(KEY_EXPAND_PATTERNS).append("\": [\n");
            for (int i = 0; i < expand_patterns.size(); i++)
            {
                ExpandPattern p = expand_patterns.get(i);
                sb.append("    {\n");
                if (p.name != null)
                    sb.append("      \"name\": \"").append(escape_json(p.name)).append("\",\n");
                sb.append("      \"prefix\": \"").append(escape_json(p.prefix)).append("\",\n");
                if (p.regex != null)
                    sb.append("      \"").append(KEY_EXPAND_PATTERN_REGEX).append("\": \"").append(escape_json(p.regex)).append("\",\n");
                sb.append("      \"suffix\": \"").append(escape_json(p.suffix)).append("\",\n");
                sb.append("      \"replace_prefix\": \"").append(escape_json(p.replace_prefix_str)).append("\",\n");
                sb.append("      \"fire_on_suffix\": \"").append(escape_json(p.fire_on_suffix_str)).append("\",\n");
                sb.append("      \"task\": \"").append(escape_json(p.task)).append("\"\n");
                sb.append("    }").append(i < expand_patterns.size() - 1 ? ",\n" : "\n");
            }
            sb.append("  ]");
        }

        sb.append("\n}\n");
        return sb.toString();
    }

    private static String escape_json(String s)
    {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            switch (c)
            {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20)
                        out.append(String.format("\\u%04x", (int) c));
                    else
                        out.append(c);
            }
        }
        return out.toString();
    }
}
