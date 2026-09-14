package com.nhs.customkeyboard.prefs;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import androidx.preference.DialogPreference;

/*
 ** IntSlideBarPreference
 ** -
 ** Open a dialog showing a seekbar. The dialog itself is implemented in
 ** IntSlideBarPreferenceDialogFragment (AndroidX Preference dialogs are
 ** owned by a DialogFragment, not by the Preference).
 ** -
 ** xml attrs:
 **   android:defaultValue  Default value (int)
 **   min                   min value (int)
 **   max                   max value (int)
 ** -
 ** Summary field allow to show the current value using %s flag
 */
public class IntSlideBarPreference extends DialogPreference
{
  private final int _min;
  private final int _max;
  private final String _initialSummary;

  public IntSlideBarPreference(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    CharSequence summary = getSummary();
    _initialSummary = (summary == null) ? "%s" : summary.toString();
    _min = attrs.getAttributeIntValue(null, "min", 0);
    _max = attrs.getAttributeIntValue(null, "max", 0);
    setDialogLayoutResource(com.nhs.customkeyboard.R.layout.pref_dialog_slider);
  }

  public int getMin() { return _min; }
  public int getMax() { return _max; }
  public String getInitialSummary() { return _initialSummary; }

  @Override
  protected Object onGetDefaultValue(TypedArray a, int index)
  {
    return a.getInt(index, _min);
  }

  @Override
  protected void onSetInitialValue(Object defaultValue)
  {
    int value;
    if (isPersistent())
      value = getPersistedInt((defaultValue instanceof Integer) ? (Integer)defaultValue : _min);
    else
      value = (defaultValue instanceof Integer) ? (Integer)defaultValue : _min;
    setValue(value);
  }

  /** Current value, from the persisted store. */
  public int getValue()
  {
    return getPersistedInt(_min);
  }

  public String formatValue(int value)
  {
    String key = getKey();
    if ("suggestion_tool_icon_size".equals(key))
    {
      String label;
      if (value <= 65) label = "Extra Small";
      else if (value <= 85) label = "Small";
      else if (value <= 115) label = "Normal";
      else if (value <= 135) label = "Big";
      else label = "Extra Big";
      return label + " (" + value + "%)";
    }
    else if ("suggestion_tool_gap".equals(key))
    {
      String label;
      if (value == 0) label = "None";
      else if (value <= 4) label = "Compact";
      else if (value <= 10) label = "Normal";
      else if (value <= 16) label = "Wide";
      else label = "Extra Wide";
      return label + " (" + value + "dp)";
    }
    return String.format(_initialSummary, value);
  }

  /** Persists [value] and refreshes the summary shown in the list. */
  public void setValue(int value)
  {
    persistInt(value);
    setSummary(formatValue(value));
  }
}
