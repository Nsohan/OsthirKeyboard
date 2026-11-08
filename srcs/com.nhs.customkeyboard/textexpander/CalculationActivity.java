package com.nhs.customkeyboard.textexpander;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.nhs.customkeyboard.R;

/**
 * Dedicated settings screen for configuring Inline Math Calculation.
 */
public final class CalculationActivity extends AppCompatActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
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

        setContentView(R.layout.activity_calculation);

        View root = findViewById(R.id.calculation_root);
        if (root != null)
        {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
                androidx.core.graphics.Insets insets = windowInsets.getInsets(
                        androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                return windowInsets;
            });
        }

        MaterialToolbar toolbar = findViewById(R.id.calculation_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
        {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        initControls();
    }

    private void initControls()
    {
        SwitchCompat switchCalc = findViewById(R.id.switch_calc_enabled);
        View layoutOptions = findViewById(R.id.layout_calc_options);
        TextInputEditText etTrigger = findViewById(R.id.et_calc_trigger);
        AutoCompleteTextView actvMode = findViewById(R.id.actv_calc_mode);
        TextView tvPreview = findViewById(R.id.tv_calc_live_preview);

        if (switchCalc == null || layoutOptions == null || etTrigger == null
                || actvMode == null || tvPreview == null)
            return;

        boolean enabled = TextExpanderManager.is_calc_enabled(this);
        String trigger = TextExpanderManager.get_calc_trigger(this);
        String mode = TextExpanderManager.get_calc_mode(this);

        switchCalc.setChecked(enabled);
        layoutOptions.setVisibility(enabled ? View.VISIBLE : View.GONE);
        etTrigger.setText(trigger);

        final String[] modeKeys = new String[] {
                TextExpanderManager.CALC_MODE_REPLACE_LAST_EQ_SPACE,
                TextExpanderManager.CALC_MODE_APPEND,
                TextExpanderManager.CALC_MODE_REPLACE_ALL
        };
        final String[] modeLabels = new String[] {
                getString(R.string.text_expander_calc_mode_replace_last_eq),
                getString(R.string.text_expander_calc_mode_append),
                getString(R.string.text_expander_calc_mode_replace_all)
        };

        ArrayAdapter<String> modeAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, modeLabels);
        actvMode.setAdapter(modeAdapter);

        int selectedIndex = 0;
        for (int i = 0; i < modeKeys.length; i++)
        {
            if (modeKeys[i].equals(mode))
            {
                selectedIndex = i;
                break;
            }
        }
        actvMode.setText(modeLabels[selectedIndex], false);

        Runnable updatePreview = () -> {
            String currentTrigger = etTrigger.getText() != null ? etTrigger.getText().toString().trim() : "==";
            if (currentTrigger.isEmpty())
                currentTrigger = "==";
            String currentMode = TextExpanderManager.get_calc_mode(this);

            if (TextExpanderManager.CALC_MODE_REPLACE_LAST_EQ_SPACE.equals(currentMode))
            {
                if (currentTrigger.length() > 1 && currentTrigger.endsWith("="))
                {
                    String prefix = currentTrigger.substring(0, currentTrigger.length() - 1);
                    tvPreview.setText("50*2" + currentTrigger + "  →  50*2" + prefix + " 100\n2+2+2+2+2-8" + currentTrigger + "  →  2+2+2+2+2-8" + prefix + " 2");
                }
                else
                {
                    tvPreview.setText("50*2" + currentTrigger + "  →  50*2" + currentTrigger + " 100\n2+2+2+2+2-8" + currentTrigger + "  →  2+2+2+2+2-8" + currentTrigger + " 2");
                }
            }
            else if (TextExpanderManager.CALC_MODE_APPEND.equals(currentMode))
            {
                tvPreview.setText("50*2" + currentTrigger + "  →  50*2" + currentTrigger + "100\n2+2+2+2+2-8" + currentTrigger + "  →  2+2+2+2+2-8" + currentTrigger + "2");
            }
            else
            {
                tvPreview.setText("50*2" + currentTrigger + "  →  100\n2+2+2+2+2-8" + currentTrigger + "  →  2");
            }
        };

        updatePreview.run();

        switchCalc.setOnCheckedChangeListener((btn, isChecked) -> {
            TextExpanderManager.set_calc_enabled(this, isChecked);
            layoutOptions.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        etTrigger.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s)
            {
                String t = s != null ? s.toString().trim() : "";
                if (!t.isEmpty())
                {
                    TextExpanderManager.set_calc_trigger(CalculationActivity.this, t);
                    updatePreview.run();
                }
            }
        });

        actvMode.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < modeKeys.length)
            {
                TextExpanderManager.set_calc_mode(CalculationActivity.this, modeKeys[position]);
                updatePreview.run();
            }
        });
    }
}

