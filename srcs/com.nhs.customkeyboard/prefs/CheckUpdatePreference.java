package com.nhs.customkeyboard.prefs;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Toast;

import androidx.preference.Preference;

import com.nhs.customkeyboard.R;
import com.nhs.customkeyboard.UpdateChecker;
import com.nhs.customkeyboard.UpdateDialog;

/** Settings row (placed in its own "About" category, at the very
 bottom of the settings screen) that checks
 https://github.com/Nsohan/OsthirKeyboard for a newer release
 than the one currently installed. The summary always shows the
 currently installed version; tapping the row re-checks and, if a
 newer release exists, shows a dialog with its release notes and a
 "Download now" button that hands off to [UpdateInstaller] to
 download the APK in-app and launch the system installer on it. */
public class CheckUpdatePreference extends Preference
{
    public CheckUpdatePreference(Context ctx, AttributeSet attrs)
    {
        super(ctx, attrs);
        setPersistent(false);
        show_current_version_summary();
    }

    private void show_current_version_summary()
    {
        setSummary(getContext().getString(R.string.pref_check_update_summary_current,
                UpdateChecker.current_version_name(getContext())));
    }

    @Override
    protected void onClick()
    {
        final Context ctx = getContext();
        setSummary(R.string.pref_check_update_checking);
        UpdateChecker.check(ctx, (latest, update_available, error) ->
        {
            if (error != null)
            {
                show_current_version_summary();
                Toast.makeText(ctx, R.string.pref_check_update_error, Toast.LENGTH_LONG).show();
                return;
            }
            if (!update_available)
            {
                show_current_version_summary();
                Toast.makeText(ctx,
                        ctx.getString(R.string.pref_check_update_uptodate, UpdateChecker.current_version_name(ctx)),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            show_current_version_summary();
            show_update_dialog(ctx, latest);
        });
    }

    private void show_update_dialog(final Context ctx, final UpdateChecker.ReleaseInfo latest)
    {
        UpdateDialog.show(ctx, latest);
    }
}
