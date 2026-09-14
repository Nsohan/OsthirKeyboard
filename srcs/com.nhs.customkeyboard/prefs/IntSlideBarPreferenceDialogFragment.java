package com.nhs.customkeyboard.prefs;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.preference.PreferenceDialogFragmentCompat;
import com.nhs.customkeyboard.R;

/** Dialog shown by IntSlideBarPreference. Updates the summary live while
    dragging, like the previous (pre-AndroidX) implementation did. */
public class IntSlideBarPreferenceDialogFragment extends PreferenceDialogFragmentCompat
{
  private SeekBar _seekBar;
  private TextView _textView;
  private int _min;
  private String _initialSummary;

  public static IntSlideBarPreferenceDialogFragment newInstance(String key)
  {
    IntSlideBarPreferenceDialogFragment f = new IntSlideBarPreferenceDialogFragment();
    Bundle b = new Bundle(1);
    b.putString(ARG_KEY, key);
    f.setArguments(b);
    return f;
  }

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
  }

  @Override
  protected void onBindDialogView(View view)
  {
    super.onBindDialogView(view);
    IntSlideBarPreference pref = (IntSlideBarPreference)getPreference();
    _min = pref.getMin();
    _initialSummary = pref.getInitialSummary();
    _textView = view.findViewById(R.id.pref_slider_text);
    _seekBar = view.findViewById(R.id.pref_slider_seekbar);
    _seekBar.setMax(pref.getMax() - pref.getMin());
    int current = pref.getValue();
    _seekBar.setProgress(current - _min);
    updateText(current);
    _seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
    {
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
      {
        updateText(progress + _min);
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {}

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {}
    });

    LinearLayout presetsContainer = view.findViewById(R.id.pref_slider_presets_container);
    if (presetsContainer != null)
    {
      String key = pref.getKey();
      if ("suggestion_tool_icon_size".equals(key))
      {
        presetsContainer.setVisibility(View.VISIBLE);
        presetsContainer.removeAllViews();
        addPresetButton(presetsContainer, "Small", 75);
        addPresetButton(presetsContainer, "Normal", 100);
        addPresetButton(presetsContainer, "Big", 130);
      }
      else if ("suggestion_tool_gap".equals(key))
      {
        presetsContainer.setVisibility(View.VISIBLE);
        presetsContainer.removeAllViews();
        addPresetButton(presetsContainer, "None", 0);
        addPresetButton(presetsContainer, "Compact", 4);
        addPresetButton(presetsContainer, "Normal", 8);
        addPresetButton(presetsContainer, "Wide", 16);
      }
      else
      {
        presetsContainer.setVisibility(View.GONE);
      }
    }
  }

  private void addPresetButton(LinearLayout container, String label, final int targetValue)
  {
    if (getContext() == null) return;
    com.google.android.material.button.MaterialButton btn =
        new com.google.android.material.button.MaterialButton(
            getContext(), null, com.google.android.material.R.attr.borderlessButtonStyle);
    btn.setText(label);
    btn.setTextSize(12);
    btn.setAllCaps(false);
    int hPad = (int)(8 * getResources().getDisplayMetrics().density);
    btn.setPadding(hPad, 0, hPad, 0);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        (int)(36 * getResources().getDisplayMetrics().density)
    );
    int m = (int)(3 * getResources().getDisplayMetrics().density);
    lp.setMargins(m, 0, m, 0);
    btn.setLayoutParams(lp);
    btn.setOnClickListener(v -> {
      if (_seekBar != null)
      {
        _seekBar.setProgress(targetValue - _min);
        updateText(targetValue);
      }
    });
    container.addView(btn);
  }

  private void updateText(int value)
  {
    if (_textView != null)
    {
      if (getPreference() instanceof IntSlideBarPreference)
      {
        _textView.setText(((IntSlideBarPreference)getPreference()).formatValue(value));
      }
      else
      {
        _textView.setText(String.format(_initialSummary, value));
      }
    }
  }

  @Override
  public void onDialogClosed(boolean positiveResult)
  {
    IntSlideBarPreference pref = (IntSlideBarPreference)getPreference();
    if (positiveResult && _seekBar != null)
    {
      int value = _seekBar.getProgress() + _min;
      if (pref.callChangeListener(value))
        pref.setValue(value);
    }
  }
}
