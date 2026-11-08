package com.nhs.customkeyboard.tasker;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.widget.TextView;

import org.json.JSONArray;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Helper class for managing the Google Material Symbols font and icon name registry.
 * Loads 'material_symbols_outlined.ttf' and 'material_symbols.json' from assets.
 */
public final class MaterialSymbols
{
    private static final String LOG_TAG = "MaterialSymbols";
    private static final String FONT_ASSET = "material_symbols_outlined.ttf";
    private static final String JSON_ASSET = "material_symbols.json";
    public static final String DEFAULT_ICON = "bolt";

    private static Typeface sTypeface = null;
    private static List<String> sIconNames = null;

    private MaterialSymbols() {}

    public static synchronized Typeface getTypeface(Context context)
    {
        if (sTypeface == null && context != null)
        {
            try
            {
                sTypeface = Typeface.createFromAsset(context.getApplicationContext().getAssets(), FONT_ASSET);
            }
            catch (Exception e)
            {
                Log.e(LOG_TAG, "Failed to load Material Symbols typeface from assets", e);
            }
        }
        return sTypeface;
    }

    public static synchronized List<String> getIconNames(Context context)
    {
        if (sIconNames != null)
        {
            return sIconNames;
        }

        if (context == null)
        {
            return Collections.emptyList();
        }

        List<String> list = new ArrayList<>();
        try (InputStream is = context.getApplicationContext().getAssets().open(JSON_ASSET))
        {
            byte[] buffer = new byte[is.available()];
            int read = is.read(buffer);
            if (read > 0)
            {
                String jsonStr = new String(buffer, 0, read, StandardCharsets.UTF_8);
                JSONArray array = new JSONArray(jsonStr);
                for (int i = 0; i < array.length(); i++)
                {
                    list.add(array.getString(i));
                }
            }
        }
        catch (Exception e)
        {
            Log.e(LOG_TAG, "Failed to load Material Symbols icon list from assets", e);
        }

        sIconNames = Collections.unmodifiableList(list);
        return sIconNames;
    }

    public static List<String> searchIcons(Context context, String query)
    {
        List<String> all = getIconNames(context);
        if (query == null || query.trim().isEmpty())
        {
            return all;
        }

        String q = query.trim().toLowerCase();
        List<String> results = new ArrayList<>();
        for (String name : all)
        {
            if (name.toLowerCase().contains(q))
            {
                results.add(name);
            }
        }
        return results;
    }

    public static void applyTo(TextView textView)
    {
        if (textView == null) return;
        Typeface tf = getTypeface(textView.getContext());
        if (tf != null)
        {
            textView.setTypeface(tf);
        }
    }
}

