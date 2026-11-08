package com.nhs.customkeyboard.prefs;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;

import androidx.preference.Preference;

import com.nhs.customkeyboard.textexpander.TextExpanderActivity;
import com.nhs.customkeyboard.textexpander.TextExpanderManager;

/** Preference item in Settings that opens [TextExpanderActivity]. */
public class TextExpanderPreference extends Preference
{
    public TextExpanderPreference(Context ctx, AttributeSet attrs)
    {
        super(ctx, attrs);
        refresh_summary();
    }

    public void refresh_summary()
    {
        int count = TextExpanderManager.get_all_items(getContext()).size();
        setSummary(count == 1 ? "1 snippet configured" : count + " snippets configured");
    }

    @Override
    protected void onClick()
    {
        getContext().startActivity(new Intent(getContext(), TextExpanderActivity.class));
    }
}

