package com.nhs.customkeyboard;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import android.content.res.Resources;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceViewHolder;
import java.util.List;

import com.nhs.customkeyboard.prefs.IntSlideBarPreference;
import com.nhs.customkeyboard.prefs.IntSlideBarPreferenceDialogFragment;
import com.nhs.customkeyboard.prefs.LayoutsPreference;
import com.nhs.customkeyboard.prefs.ListGroupPreference;
import com.nhs.customkeyboard.prefs.SlideBarPreference;
import com.nhs.customkeyboard.prefs.SlideBarPreferenceDialogFragment;
import com.nhs.customkeyboard.prefs.AboutHeaderPreference;
import com.nhs.customkeyboard.prefs.SuggestionToolsPreference;
import com.nhs.customkeyboard.prefs.TaskerAutomationPreference;

public class SettingsActivity extends AppCompatActivity
  implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback
{
  public static final String EXTRA_START_SCREEN = "start_screen";
  private String _initial_start_screen = null;

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.settings_activity);
    Toolbar toolbar = findViewById(R.id.settings_toolbar);
    setSupportActionBar(toolbar);
    setTitle(R.string.settings_title);

    String startScreen = getIntent().getStringExtra(EXTRA_START_SCREEN);
    if (startScreen == null)
    {
      startScreen = getIntent().getStringExtra(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT);
    }
    _initial_start_screen = startScreen;
    DirectBootAwarePreferences.copy_preferences_to_default_storage(this);

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    try
    {
      Config.migrate(prefs);
    }
    catch (Exception _e) { fallbackEncrypted(); return; }

    getSupportFragmentManager().addOnBackStackChangedListener(this::update_navigation);
    if (savedInstanceState == null)
    {
      SettingsFragment fragment = new SettingsFragment();
      if (startScreen != null)
      {
        Bundle args = new Bundle();
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, startScreen);
        fragment.setArguments(args);
      }
      getSupportFragmentManager()
        .beginTransaction()
        .replace(R.id.settings_container, fragment)
        .commit();
      if ("screen_theme".equals(startScreen))
      {
        setTitle(R.string.pref_screen_theme_title);
      }
    }
    update_navigation();
  }

  void update_navigation()
  {
    boolean can_go_back = getSupportFragmentManager().getBackStackEntryCount() > 0 || _initial_start_screen != null;
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(can_go_back);
      if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
        if ("screen_theme".equals(_initial_start_screen)) {
          setTitle(R.string.pref_screen_theme_title);
        } else {
          setTitle(R.string.settings_title);
        }
      }
    }
  }

  @Override
  public boolean onSupportNavigateUp()
  {
    FragmentManager fm = getSupportFragmentManager();
    if (fm.getBackStackEntryCount() > 0)
    {
      fm.popBackStack();
      return true;
    }
    finish();
    return true;
  }

  /** Navigates into a nested <PreferenceScreen> (e.g. "Languages & Layouts",
   "Preferences", "Theme & Appearance"). Requires the PreferenceScreen to have an
   android:key set in res/xml/settings.xml - see SettingsFragment. */
  @Override
  public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen pref)
  {
    SettingsFragment fragment = new SettingsFragment();
    Bundle args = new Bundle();
    args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.getKey());
    fragment.setArguments(args);
    getSupportFragmentManager()
      .beginTransaction()
      .replace(R.id.settings_container, fragment, pref.getKey())
      .addToBackStack(pref.getKey())
      .commit();
    if (pref.getTitle() != null)
    {
      setTitle(pref.getTitle());
    }
    return true;
  }

  void fallbackEncrypted()
  {
    finish();
  }

  @Override
  protected void onStop()
  {
    DirectBootAwarePreferences
            .copy_preferences_to_protected_storage(this,
                    PreferenceManager.getDefaultSharedPreferences(this));
    super.onStop();
  }

  /** The single PreferenceFragmentCompat used for both the root main settings screen
   and every nested <PreferenceScreen> page (distinguished by ARG_PREFERENCE_ROOT). */
  public static class SettingsFragment extends PreferenceFragmentCompat
  {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
      setPreferencesFromResource(R.xml.settings, rootKey);

      boolean foldableDevice = FoldStateTracker.isFoldableDevice(requireContext());
      set_enabled_if_present("margin_bottom_portrait_unfolded", foldableDevice);
      set_enabled_if_present("margin_bottom_landscape_unfolded", foldableDevice);
      set_enabled_if_present("horizontal_margin_portrait_unfolded", foldableDevice);
      set_enabled_if_present("horizontal_margin_landscape_unfolded", foldableDevice);
      set_enabled_if_present("keyboard_height_unfolded", foldableDevice);
      set_enabled_if_present("keyboard_height_landscape_unfolded", foldableDevice);
    }

    @Override
    public boolean onPreferenceTreeClick(@NonNull Preference preference)
    {
      if ("screen_dictionary".equals(preference.getKey()))
      {
        startActivity(new Intent(requireContext(), com.nhs.customkeyboard.dict.DictionariesActivity.class));
        return true;
      }
      if ("screen_guide".equals(preference.getKey()))
      {
        Intent intent = new Intent(requireContext(), LauncherActivity.class);
        intent.putExtra(LauncherActivity.EXTRA_FORCE_GUIDE_MODE, true);
        startActivity(intent);
        return true;
      }
      return super.onPreferenceTreeClick(preference);
    }

    @Override
    protected RecyclerView.Adapter<?> onCreateAdapter(PreferenceScreen preferenceScreen)
    {
      return new PreferenceGroupAdapter(preferenceScreen)
      {
        @Override
        public void onBindViewHolder(@NonNull PreferenceViewHolder holder, int position)
        {
          super.onBindViewHolder(holder, position);
          Preference pref = getItem(position);
          if (pref instanceof PreferenceCategory)
            return;

          if (pref instanceof ListGroupPreference)
          {
            holder.itemView.setVisibility(View.GONE);
            ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
            if (lp != null)
            {
              lp.height = 0;
              lp.width = 0;
              holder.itemView.setLayoutParams(lp);
            }
            return;
          }

          if (pref instanceof SuggestionToolsPreference || pref instanceof AboutHeaderPreference)
          {
            holder.itemView.setBackgroundResource(android.R.color.transparent);
            View divider = holder.findViewById(R.id.card_divider);
            if (divider != null) divider.setVisibility(View.GONE);
            return;
          }

          int count = getItemCount();
          boolean isFirst = true;
          for (int i = position - 1; i >= 0; i--)
          {
            Preference prev = getItem(i);
            if (prev instanceof ListGroupPreference || prev instanceof AboutHeaderPreference || !prev.isVisible())
              continue;
            isFirst = (prev instanceof PreferenceCategory);
            break;
          }

          boolean isLast = true;
          for (int i = position + 1; i < count; i++)
          {
            Preference next = getItem(i);
            if (next instanceof ListGroupPreference || next instanceof AboutHeaderPreference || !next.isVisible())
              continue;
            isLast = (next instanceof PreferenceCategory);
            break;
          }

          View itemView = holder.itemView;
          if (isFirst && isLast)
            itemView.setBackgroundResource(R.drawable.bg_settings_card_single);
          else if (isFirst)
            itemView.setBackgroundResource(R.drawable.bg_settings_card_top);
          else if (isLast)
            itemView.setBackgroundResource(R.drawable.bg_settings_card_bottom);
          else
            itemView.setBackgroundResource(R.drawable.bg_settings_card_middle);

          View divider = holder.findViewById(R.id.card_divider);
          if (divider != null)
          {
            if (isLast)
            {
              divider.setVisibility(View.GONE);
            }
            else
            {
              divider.setVisibility(View.VISIBLE);
              ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) divider.getLayoutParams();
              float density = itemView.getResources().getDisplayMetrics().density;
              if (pref.getIcon() != null)
                lp.leftMargin = (int) (56 * density);
              else
                lp.leftMargin = (int) (16 * density);
              divider.setLayoutParams(lp);
            }
          }
        }
      };
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
      super.onViewCreated(view, savedInstanceState);
      setDivider(null);
      setDividerHeight(0);
      RecyclerView rv = getListView();
      if (rv != null)
      {
        rv.setClipToPadding(false);
        int padTop = (int) (8 * getResources().getDisplayMetrics().density);
        int padBottom = (int) (24 * getResources().getDisplayMetrics().density);
        rv.setPadding(0, padTop, 0, padBottom);
      }
      update_languages_summary();

      PreferenceScreen screen = getPreferenceScreen();
      if (screen != null && screen.getTitle() != null && getParentFragmentManager().getBackStackEntryCount() > 0)
      {
        requireActivity().setTitle(screen.getTitle());
      }
      else
      {
        requireActivity().setTitle(R.string.settings_title);
      }
    }

    void update_languages_summary()
    {
      Preference lang = findPreference("screen_languages");
      if (lang != null && getContext() != null)
      {
        try
        {
          SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
          Resources res = getResources();
          List<KeyboardData> list = LayoutsPreference.load_from_preferences(res, prefs);
          if (list != null && !list.isEmpty())
          {
            StringBuilder sb = new StringBuilder();
            String[] displayNames = res.getStringArray(R.array.pref_layout_entries);
            List<String> names = LayoutsPreference.get_layout_names(res);
            for (KeyboardData kd : list)
            {
              String name = null;
              if (kd != null && kd.name != null)
              {
                int idx = names.indexOf(kd.name);
                name = (idx >= 0 && idx < displayNames.length) ? displayNames[idx] : kd.name;
              }
              else if (kd == null)
              {
                name = getString(R.string.pref_layout_e_system);
              }
              if (name != null && !name.isEmpty())
              {
                if (sb.length() > 0) sb.append(", ");
                sb.append(name);
              }
            }
            if (sb.length() > 0)
              lang.setSummary(sb.toString());
          }
        }
        catch (Exception ignored) {}
      }
    }

    void set_enabled_if_present(String key, boolean enabled)
    {
      Preference p = findPreference(key);
      if (p != null)
        p.setEnabled(enabled);
    }

    @Override
    public void onResume()
    {
      super.onResume();
      update_languages_summary();
      Preference p = findPreference("layouts");
      if (p instanceof LayoutsPreference)
        ((LayoutsPreference)p).reload_from_preferences_and_sync();
      Preference tasker_pref = findPreference("tasker_automation");
      if (tasker_pref instanceof TaskerAutomationPreference)
        ((TaskerAutomationPreference)tasker_pref).refresh_summary();
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference)
    {
      if (preference instanceof IntSlideBarPreference)
      {
        DialogFragment f =
          IntSlideBarPreferenceDialogFragment
            .newInstance(preference.getKey());
        f.setTargetFragment(this, 0);
        f.show(getParentFragmentManager(), "androidx.preference.PreferenceFragment.DIALOG");
      }
      else if (preference instanceof SlideBarPreference)
      {
        DialogFragment f =
          SlideBarPreferenceDialogFragment
            .newInstance(preference.getKey());
        f.setTargetFragment(this, 0);
        f.show(getParentFragmentManager(), "androidx.preference.PreferenceFragment.DIALOG");
      }
      else
      {
        super.onDisplayPreferenceDialog(preference);
      }
    }
  }
}
