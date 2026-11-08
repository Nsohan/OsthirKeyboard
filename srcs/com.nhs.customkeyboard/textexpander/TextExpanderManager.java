package com.nhs.customkeyboard.textexpander;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Manages persistent storage and in-memory caching of Text Expander snippet rules. */
public final class TextExpanderManager
{
    private static final String PREF_NAME = "text_expander";
    private static final String KEY_ITEMS = "snippets_json";
    private static final String KEY_INITIALIZED = "initialized";

    public static final String KEY_CALC_ENABLED = "calc_enabled";
    public static final String KEY_CALC_TRIGGER = "calc_trigger";
    public static final String KEY_CALC_MODE = "calc_mode";

    public static final String CALC_MODE_REPLACE_LAST_EQ_SPACE = "replace_last_eq_space"; // 50*2= 100
    public static final String CALC_MODE_APPEND = "append";                               // 50*2==100
    public static final String CALC_MODE_REPLACE_ALL = "replace_all";                     // 100

    public interface OnSnippetsChangedListener
    {
        void onSnippetsChanged();
    }

    private static final List<OnSnippetsChangedListener> _listeners = new CopyOnWriteArrayList<>();

    public static void add_listener(OnSnippetsChangedListener l)
    {
        if (l != null && !_listeners.contains(l))
            _listeners.add(l);
    }

    public static void remove_listener(OnSnippetsChangedListener l)
    {
        if (l != null)
            _listeners.remove(l);
    }

    private static void notify_changed()
    {
        for (OnSnippetsChangedListener l : _listeners)
        {
            try
            {
                l.onSnippetsChanged();
            }
            catch (Exception ignored) {}
        }
    }

    private static SharedPreferences prefs(Context ctx)
    {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized List<TextExpanderItem> get_all_items(Context ctx)
    {
        SharedPreferences p = prefs(ctx);
        if (!p.getBoolean(KEY_INITIALIZED, false))
        {
            List<TextExpanderItem> defaults = create_default_items();
            save_all_items(ctx, defaults);
            p.edit().putBoolean(KEY_INITIALIZED, true).apply();
            return defaults;
        }

        String json = p.getString(KEY_ITEMS, null);
        if (json == null || json.trim().isEmpty())
            return new ArrayList<>();

        List<TextExpanderItem> list = new ArrayList<>();
        try
        {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++)
            {
                JSONObject obj = arr.getJSONObject(i);
                TextExpanderItem item = TextExpanderItem.from_json(obj);
                if (item != null)
                    list.add(item);
            }
        }
        catch (JSONException e)
        {
            // Return empty list on parse error
        }
        return list;
    }

    public static synchronized void save_all_items(Context ctx, List<TextExpanderItem> items)
    {
        if (items == null)
            items = Collections.emptyList();

        JSONArray arr = new JSONArray();
        for (TextExpanderItem item : items)
        {
            try
            {
                arr.put(item.to_json());
            }
            catch (JSONException ignored) {}
        }

        prefs(ctx).edit()
                .putString(KEY_ITEMS, arr.toString())
                .putBoolean(KEY_INITIALIZED, true)
                .apply();

        notify_changed();
    }

    public static synchronized void add_or_update_item(Context ctx, TextExpanderItem item)
    {
        if (item == null)
            return;
        List<TextExpanderItem> items = get_all_items(ctx);
        int index = -1;
        for (int i = 0; i < items.size(); i++)
        {
            if (items.get(i).id.equals(item.id))
            {
                index = i;
                break;
            }
        }
        if (index >= 0)
            items.set(index, item);
        else
            items.add(item);

        save_all_items(ctx, items);
    }

    public static synchronized void delete_item(Context ctx, String id)
    {
        if (id == null)
            return;
        List<TextExpanderItem> items = get_all_items(ctx);
        boolean changed = false;
        for (int i = items.size() - 1; i >= 0; i--)
        {
            if (items.get(i).id.equals(id))
            {
                items.remove(i);
                changed = true;
            }
        }
        if (changed)
            save_all_items(ctx, items);
    }

    public static synchronized Map<String, TextExpanderItem> get_active_map(Context ctx)
    {
        List<TextExpanderItem> items = get_all_items(ctx);
        Map<String, TextExpanderItem> map = new LinkedHashMap<>();
        for (TextExpanderItem item : items)
        {
            if (item.enabled && item.keyword != null && !item.keyword.trim().isEmpty())
            {
                map.put(item.get_full_trigger(), item);
            }
        }
        return map;
    }

    private static List<TextExpanderItem> create_default_items()
    {
        List<TextExpanderItem> defaults = new ArrayList<>();
        defaults.add(new TextExpanderItem(null, "@@", "email", "user@example.com", true));
        defaults.add(new TextExpanderItem(null, "@@", "date", "$date", true));
        defaults.add(new TextExpanderItem(null, "@@", "time", "$time", true));
        defaults.add(new TextExpanderItem(null, "@@", "tag", "[ $cursor ]", true));
        return defaults;
    }

    public static boolean is_calc_enabled(Context ctx)
    {
        return prefs(ctx).getBoolean(KEY_CALC_ENABLED, true);
    }

    public static void set_calc_enabled(Context ctx, boolean enabled)
    {
        prefs(ctx).edit().putBoolean(KEY_CALC_ENABLED, enabled).apply();
        notify_changed();
    }

    public static String get_calc_trigger(Context ctx)
    {
        return prefs(ctx).getString(KEY_CALC_TRIGGER, "==");
    }

    public static void set_calc_trigger(Context ctx, String trigger)
    {
        if (trigger == null || trigger.trim().isEmpty())
            trigger = "==";
        prefs(ctx).edit().putString(KEY_CALC_TRIGGER, trigger.trim()).apply();
        notify_changed();
    }

    public static String get_calc_mode(Context ctx)
    {
        return prefs(ctx).getString(KEY_CALC_MODE, CALC_MODE_REPLACE_LAST_EQ_SPACE);
    }

    public static void set_calc_mode(Context ctx, String mode)
    {
        if (mode == null)
            mode = CALC_MODE_REPLACE_LAST_EQ_SPACE;
        prefs(ctx).edit().putString(KEY_CALC_MODE, mode).apply();
        notify_changed();
    }

    private TextExpanderManager() {}
}

