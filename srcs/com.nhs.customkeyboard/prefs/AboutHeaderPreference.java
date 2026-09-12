package com.nhs.customkeyboard.prefs;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.nhs.customkeyboard.R;
import com.nhs.customkeyboard.UpdateChecker;

public class AboutHeaderPreference extends Preference
{
    public AboutHeaderPreference(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        setLayoutResource(R.layout.pref_about_header);
        setSelectable(false);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder)
    {
        super.onBindViewHolder(holder);

        Context ctx = getContext();
        TextView titleView = (TextView) holder.findViewById(R.id.about_app_name);
        if (titleView != null)
        {
            titleView.setText(R.string.app_name);
        }

        TextView versionView = (TextView) holder.findViewById(R.id.about_app_version);
        if (versionView != null && ctx != null)
        {
            String ver = UpdateChecker.current_version_name(ctx);
            versionView.setText("v" + ver);
        }

        ImageView logoView = (ImageView) holder.findViewById(R.id.about_logo_image);
        if (logoView != null)
        {
            logoView.setImageResource(R.drawable.app_logo);
        }
    }
}
