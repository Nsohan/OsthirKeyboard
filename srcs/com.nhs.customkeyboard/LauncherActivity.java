package com.nhs.customkeyboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Animatable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.util.List;
import com.nhs.customkeyboard.dict.DictionariesActivity;
import com.nhs.customkeyboard.R;

public class LauncherActivity extends AppCompatActivity implements Handler.Callback
{
  public static final String EXTRA_FORCE_GUIDE_MODE = "force_guide_mode";
  public static final String PREF_ONBOARDING_COMPLETED = "pref_onboarding_completed";

  private static final int TOTAL_ONBOARDING_STEPS = 6;
  private static final int TOTAL_GUIDE_STEPS = 3;

  private boolean _is_guide_mode = false;
  private int _current_step = 0;

  private MaterialToolbar _toolbar;
  private ViewFlipper _step_flipper;
  private TextView _tv_step_counter;
  private TextView _btn_header_skip;
  private LinearProgressIndicator _progress_steps;

  // Step 0 views
  private LinearLayout _badge_enable_status;
  private ImageView _iv_enable_status;
  private TextView _tv_enable_status;
  private MaterialButton _btn_step0_next;

  // Step 1 views
  private LinearLayout _badge_select_status;
  private ImageView _iv_select_status;
  private TextView _tv_select_status;
  private MaterialButton _btn_step1_next;

  // Animation handling
  private Handler _handler;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    _is_guide_mode = getIntent().getBooleanExtra(EXTRA_FORCE_GUIDE_MODE, false);
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    boolean onboardingCompleted = prefs.getBoolean(PREF_ONBOARDING_COMPLETED, false);

    // If onboarding is already completed and user opened the app normally (not via Guide in settings),
    // launch directly into SettingsActivity without showing onboarding.
    if (!_is_guide_mode && onboardingCompleted && isKeyboardEnabled(this) && isKeyboardSelected(this))
    {
      Intent intent = new Intent(this, SettingsActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
      startActivity(intent);
      finish();
      return;
    }

    if (android.os.Build.VERSION.SDK_INT >= 29)
    {
      getWindow().setNavigationBarContrastEnforced(false);
      getWindow().setStatusBarContrastEnforced(false);
    }

    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

    androidx.core.view.WindowInsetsControllerCompat insetsController =
        androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
    if (insetsController != null)
    {
      boolean isNight = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
      insetsController.setAppearanceLightStatusBars(!isNight);
      insetsController.setAppearanceLightNavigationBars(!isNight);
    }

    setContentView(R.layout.launcher_activity);

    View root = findViewById(R.id.launcher_root_layout);
    if (root != null)
    {
      androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
        androidx.core.graphics.Insets insets = windowInsets.getInsets(
            androidx.core.view.WindowInsetsCompat.Type.systemBars());
        v.setPadding(insets.left, insets.top, insets.right, 0);
        return windowInsets;
      });
    }

    View scrollView = findViewById(R.id.launcher_scroll_view);
    if (scrollView != null)
    {
      androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scrollView, (v, windowInsets) -> {
        androidx.core.graphics.Insets insets = windowInsets.getInsets(
            androidx.core.view.WindowInsetsCompat.Type.systemBars());
        int extraBottom = (int) (16 * getResources().getDisplayMetrics().density);
        v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), insets.bottom + extraBottom);
        return windowInsets;
      });
    }

    _toolbar = findViewById(R.id.launcher_toolbar);
    if (_toolbar != null)
    {
      setSupportActionBar(_toolbar);
      if (_is_guide_mode)
      {
        setTitle(R.string.guide_toolbar_title);
        _toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        _toolbar.setNavigationOnClickListener(v -> finish());
      }
    }

    _step_flipper = findViewById(R.id.step_flipper);
    _tv_step_counter = findViewById(R.id.tv_step_counter);
    _btn_header_skip = findViewById(R.id.btn_header_skip);
    _progress_steps = findViewById(R.id.progress_steps);

    init_step_views();

    _handler = new Handler(getMainLooper(), this);

    if (_is_guide_mode)
    {
      // In guide mode, jump directly to step 2 (Swipe Tutorial)
      _current_step = 2;
      _step_flipper.setDisplayedChild(2);
    }
    else
    {
      _current_step = 0;
      _step_flipper.setDisplayedChild(0);
    }

    update_step_ui();
  }

  private void init_step_views()
  {
    // Step 0: Enable
    _badge_enable_status = findViewById(R.id.badge_enable_status);
    _iv_enable_status = findViewById(R.id.iv_enable_status);
    _tv_enable_status = findViewById(R.id.tv_enable_status);
    _btn_step0_next = findViewById(R.id.btn_step0_next);
    findViewById(R.id.btn_step_enable).setOnClickListener(v -> launch_imesettings());
    _btn_step0_next.setOnClickListener(v -> {
      if (!isKeyboardEnabled(this))
      {
        launch_imesettings();
      }
      else
      {
        go_to_step(1);
      }
    });

    // Step 1: Select
    _badge_select_status = findViewById(R.id.badge_select_status);
    _iv_select_status = findViewById(R.id.iv_select_status);
    _tv_select_status = findViewById(R.id.tv_select_status);
    _btn_step1_next = findViewById(R.id.btn_step1_next);
    findViewById(R.id.btn_step_select).setOnClickListener(v -> launch_imepicker());
    findViewById(R.id.btn_step1_back).setOnClickListener(v -> go_to_step(0));
    _btn_step1_next.setOnClickListener(v -> {
      if (!isKeyboardSelected(this))
      {
        launch_imepicker();
      }
      else
      {
        go_to_step(2);
      }
    });

    // Step 2: Swipe
    findViewById(R.id.btn_step2_back).setOnClickListener(v -> {
      if (_is_guide_mode) finish();
      else go_to_step(1);
    });
    findViewById(R.id.btn_step2_next).setOnClickListener(v -> go_to_step(3));

    // Step 3: Circle
    findViewById(R.id.btn_step3_back).setOnClickListener(v -> go_to_step(2));
    findViewById(R.id.btn_step3_next).setOnClickListener(v -> go_to_step(4));

    // Step 4: Round-trip
    findViewById(R.id.btn_step4_back).setOnClickListener(v -> go_to_step(3));
    findViewById(R.id.btn_step4_next).setOnClickListener(v -> {
      if (_is_guide_mode) finish();
      else go_to_step(5);
    });

    // Step 5: Finish
    findViewById(R.id.btn_step5_back).setOnClickListener(v -> go_to_step(4));
    findViewById(R.id.btn_step_dictionaries).setOnClickListener(v -> launch_dictionaries_activity());
    findViewById(R.id.btn_step_finish).setOnClickListener(v -> complete_onboarding());

    // Skip Header button
    if (_btn_header_skip != null)
    {
      _btn_header_skip.setOnClickListener(v -> complete_onboarding());
    }
  }

  @Override
  protected void onResume()
  {
    super.onResume();
    check_ime_status();
    trigger_current_animation();
  }

  @Override
  protected void onStop()
  {
    super.onStop();
    if (_handler != null)
      _handler.removeMessages(0);
  }

  private void check_ime_status()
  {
    boolean enabled = isKeyboardEnabled(this);
    boolean selected = isKeyboardSelected(this);

    // Update Step 0 status
    if (_badge_enable_status != null && _tv_enable_status != null && _iv_enable_status != null)
    {
      if (enabled)
      {
        _badge_enable_status.setBackgroundResource(R.drawable.bg_status_badge_active);
        _iv_enable_status.setImageResource(R.drawable.ic_check);
        _iv_enable_status.setColorFilter(Color.parseColor("#34A853"));
        _tv_enable_status.setText(R.string.onboarding_status_enabled);
        _tv_enable_status.setTextColor(Color.parseColor("#34A853"));
      }
      else
      {
        _badge_enable_status.setBackgroundResource(R.drawable.bg_status_badge_inactive);
        _iv_enable_status.setImageResource(R.drawable.ic_info_outline);
        _iv_enable_status.setColorFilter(ContextCompat.getColor(this, R.color.settings_on_surface_variant));
        _tv_enable_status.setText(R.string.onboarding_status_disabled);
        _tv_enable_status.setTextColor(ContextCompat.getColor(this, R.color.settings_on_surface_variant));
      }
    }

    // Update Step 1 status
    if (_badge_select_status != null && _tv_select_status != null && _iv_select_status != null)
    {
      if (selected)
      {
        _badge_select_status.setBackgroundResource(R.drawable.bg_status_badge_active);
        _iv_select_status.setImageResource(R.drawable.ic_check);
        _iv_select_status.setColorFilter(Color.parseColor("#34A853"));
        _tv_select_status.setText(R.string.onboarding_status_selected);
        _tv_select_status.setTextColor(Color.parseColor("#34A853"));
      }
      else
      {
        _badge_select_status.setBackgroundResource(R.drawable.bg_status_badge_inactive);
        _iv_select_status.setImageResource(R.drawable.ic_info_outline);
        _iv_select_status.setColorFilter(ContextCompat.getColor(this, R.color.settings_on_surface_variant));
        _tv_select_status.setText(R.string.onboarding_status_not_selected);
        _tv_select_status.setTextColor(ContextCompat.getColor(this, R.color.settings_on_surface_variant));
      }
    }

    // Auto-advance if on Step 0 and now enabled
    if (!_is_guide_mode && _current_step == 0 && enabled)
    {
      go_to_step(1);
    }
    // Auto-advance if on Step 1 and now selected
    else if (!_is_guide_mode && _current_step == 1 && selected)
    {
      go_to_step(2);
    }
  }

  public void go_to_step(int stepIndex)
  {
    if (stepIndex < 0 || stepIndex >= TOTAL_ONBOARDING_STEPS)
      return;

    _current_step = stepIndex;
    if (_step_flipper != null)
    {
      _step_flipper.setDisplayedChild(stepIndex);
    }
    update_step_ui();
    trigger_current_animation();
  }

  private void update_step_ui()
  {
    if (_tv_step_counter != null && _progress_steps != null)
    {
      if (_is_guide_mode)
      {
        int guideStep = Math.max(1, Math.min(3, _current_step - 1));
        _tv_step_counter.setText(getString(R.string.onboarding_step_counter_format, guideStep, TOTAL_GUIDE_STEPS));
        int progress = (int) ((guideStep / (float) TOTAL_GUIDE_STEPS) * 100);
        _progress_steps.setProgressCompat(progress, true);
        if (_btn_header_skip != null)
        {
          _btn_header_skip.setVisibility(View.GONE);
        }
      }
      else
      {
        int displayStep = _current_step + 1;
        _tv_step_counter.setText(getString(R.string.onboarding_step_counter_format, displayStep, TOTAL_ONBOARDING_STEPS));
        int progress = (int) ((displayStep / (float) TOTAL_ONBOARDING_STEPS) * 100);
        _progress_steps.setProgressCompat(progress, true);
        if (_btn_header_skip != null)
        {
          // Only show Skip Tutorial on tutorial steps (Step 3, 4, 5 -> index 2, 3, 4) once keyboard is selected
          boolean showSkip = (_current_step >= 2 && _current_step < TOTAL_ONBOARDING_STEPS - 1);
          _btn_header_skip.setVisibility(showSkip ? View.VISIBLE : View.GONE);
        }
      }
    }

    // In guide mode, change step 4 (last tutorial) Next button to "Done"
    if (_is_guide_mode)
    {
      MaterialButton btnStep4Next = findViewById(R.id.btn_step4_next);
      if (btnStep4Next != null)
      {
        btnStep4Next.setText(R.string.key_action_done);
      }
    }
  }

  private void trigger_current_animation()
  {
    if (_handler != null)
    {
      _handler.removeMessages(0);
      _handler.sendEmptyMessageDelayed(0, 300);
    }
  }

  @Override
  public boolean handleMessage(Message _msg)
  {
    Animatable anim = null;
    if (_current_step == 2)
    {
      anim = find_anim(R.id.anim_swipe_view);
    }
    else if (_current_step == 3)
    {
      anim = find_anim(R.id.anim_circle_view);
    }
    else if (_current_step == 4)
    {
      anim = find_anim(R.id.anim_roundtrip_view);
    }

    if (anim != null)
    {
      anim.start();
    }

    if (_handler != null)
    {
      _handler.sendEmptyMessageDelayed(0, 3000);
    }
    return true;
  }

  private Animatable find_anim(int id)
  {
    ImageView img = findViewById(id);
    if (img == null) return null;
    return (Animatable) img.getDrawable();
  }

  private void complete_onboarding()
  {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    prefs.edit().putBoolean(PREF_ONBOARDING_COMPLETED, true).apply();

    if (_is_guide_mode)
    {
      finish();
    }
    else
    {
      Intent intent = new Intent(this, SettingsActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);
      finish();
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
    if (!_is_guide_mode)
    {
      getMenuInflater().inflate(R.menu.launcher_menu, menu);
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
    if (item.getItemId() == R.id.btnLaunchSettingsActivity)
    {
      complete_onboarding();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  public void launch_imesettings()
  {
    startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
  }

  public void launch_imepicker()
  {
    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    if (imm != null)
    {
      imm.showInputMethodPicker();
    }
  }

  public void launch_dictionaries_activity()
  {
    startActivity(new Intent(this, DictionariesActivity.class));
  }

  public static boolean isKeyboardEnabled(Context context)
  {
    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm == null) return false;
    List<InputMethodInfo> enabledMethods = imm.getEnabledInputMethodList();
    String myPackage = context.getPackageName();
    for (InputMethodInfo imi : enabledMethods)
    {
      if (imi.getPackageName().equals(myPackage))
      {
        return true;
      }
    }
    return false;
  }

  public static boolean isKeyboardSelected(Context context)
  {
    String defaultIme = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
    if (defaultIme == null) return false;
    String myPackage = context.getPackageName();
    return defaultIme.contains(myPackage);
  }
}
