package com.nhs.customkeyboard.prefs;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;

import androidx.preference.Preference;

import com.nhs.customkeyboard.textexpander.CalculationActivity;
import com.nhs.customkeyboard.textexpander.TextExpanderManager;

/** Preference item in Settings that opens [CalculationActivity]. */
public class CalculationPreference extends Preference
{
    public CalculationPreference(Context ctx, AttributeSet attrs)
    {
        super(ctx, attrs);
        refresh_summary();
    }

    public void refresh_summary()
    {
        boolean enabled = TextExpanderManager.is_calc_enabled(getContext());
        if (enabled)
        {
            String trigger = TextExpanderManager.get_calc_trigger(getContext());
            setSummary("Enabled (trigger: " + trigger + ")");
        }
        else
        {
            setSummary("Disabled");
        }
    }

    @Override
    protected void onClick()
    {
        getContext().startActivity(new Intent(getContext(), CalculationActivity.class));
    }
}

