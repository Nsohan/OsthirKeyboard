package com.nhs.customkeyboard;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.nhs.customkeyboard.prefs.TaskerAutomationManager;
import com.nhs.customkeyboard.tasker.MaterialSymbolPickerDialog;
import com.nhs.customkeyboard.tasker.MaterialSymbols;
import com.nhs.customkeyboard.textexpander.TextExpanderEngine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Guided form editor for Tasker Automation shortcuts and Material Symbol icons.
 */
public class TaskerAutomationBuilderActivity extends AppCompatActivity
{
    private static final String LOG_TAG = "TaskerAutomationBuilder";

    private LinearLayout _tasks_container;
    private TextView _error_text;
    private SeekBar _timeout_seekbar;
    private TextView _timeout_val_text;

    private final List<TaskRow> _task_rows = new ArrayList<>();
    private List<String> _cached_tasker_tasks = new ArrayList<>();

    private static final class TaskRow
    {
        final LinearLayout layout;
        final TextView badge;
        final TextView icon_preview;
        final EditText keyboard_name;
        final AutoCompleteTextView task_name;
        String icon_name;

        TaskRow(LinearLayout layout_, TextView badge_, TextView icon_preview_,
                EditText keyboard_name_, AutoCompleteTextView task_name_, String icon_name_)
        {
            layout = layout_;
            badge = badge_;
            icon_preview = icon_preview_;
            keyboard_name = keyboard_name_;
            task_name = task_name_;
            icon_name = icon_name_;
        }
    }

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

        setContentView(R.layout.activity_tasker_automation_builder);

        View root = findViewById(R.id.tab_root);
        if (root != null)
        {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
                androidx.core.graphics.Insets insets = windowInsets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(insets.left, insets.top, insets.right, 0);
                return windowInsets;
            });
        }

        View scrollView = findViewById(R.id.tab_scroll_view);
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

        MaterialToolbar toolbar = findViewById(R.id.tab_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        _tasks_container = findViewById(R.id.tab_tasks_container);
        _error_text = findViewById(R.id.tab_error_text);
        _timeout_seekbar = findViewById(R.id.tab_timeout_seekbar);
        _timeout_val_text = findViewById(R.id.tab_timeout_val);

        if (_timeout_seekbar != null)
        {
            _timeout_seekbar.setMax(120);
            _timeout_seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
            {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
                {
                    int sec = Math.max(1, progress);
                    if (_timeout_val_text != null)
                    {
                        _timeout_val_text.setText(getString(R.string.tasker_builder_timeout_unit_sec, sec));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        View guideBtn = findViewById(R.id.tab_view_guide_button);
        if (guideBtn != null)
        {
            guideBtn.setOnClickListener(v -> startActivity(new Intent(this, TaskerAutomationGuideActivity.class)));
        }

        // Pre-query Tasker tasks in background
        new Thread(() -> {
            _cached_tasker_tasks = queryTaskerTasks(this);
            runOnUiThread(() -> {
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                        android.R.layout.simple_dropdown_item_1line, _cached_tasker_tasks);
                for (TaskRow row : _task_rows)
                {
                    row.task_name.setAdapter(adapter);
                }
            });
        }).start();

        load_existing_or_defaults();

        findViewById(R.id.tab_add_task_button).setOnClickListener(v -> add_task_row("", "", MaterialSymbols.DEFAULT_ICON));
        findViewById(R.id.tab_save_button).setOnClickListener(v -> save());
    }

    private void load_existing_or_defaults()
    {
        String stored = TaskerAutomationManager.load(this);
        TaskerAutomationConfig config = null;
        if (stored != null)
        {
            try { config = TaskerAutomationConfig.parse(stored); }
            catch (Exception e) { config = null; }
        }

        if (config != null && !config.tasks.isEmpty())
        {
            for (Map.Entry<String, String> e : config.tasks.entrySet())
            {
                String keyword = e.getKey();
                String taskName = e.getValue();
                String icon = config.getTaskIcon(keyword);
                add_task_row(keyword, taskName, icon);
            }
        }
        else
        {
            add_task_row("task1", "taskerTask1", "bolt");
        }

        if (_task_rows.isEmpty())
        {
            add_task_row("", "", MaterialSymbols.DEFAULT_ICON);
        }

        long timeoutMs = 60000;
        if (config != null)
        {
            timeoutMs = config.timeout_ms;
        }
        else if (stored != null)
        {
            try
            {
                JSONObject obj = new JSONObject(stored);
                timeoutMs = Long.parseLong(obj.optString(TaskerAutomationConfig.KEY_TIMEOUT_MS, "60000"));
            }
            catch (Exception ignored) {}
        }
        int timeoutSec = (int) Math.max(1, Math.min(120, timeoutMs / 1000));
        if (_timeout_seekbar != null)
        {
            _timeout_seekbar.setProgress(timeoutSec);
        }
        if (_timeout_val_text != null)
        {
            _timeout_val_text.setText(getString(R.string.tasker_builder_timeout_unit_sec, timeoutSec));
        }
    }

    private TaskRow add_task_row(String keyword, String taskName, String iconName)
    {
        if (iconName == null || iconName.isEmpty())
        {
            iconName = MaterialSymbols.DEFAULT_ICON;
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(8), dp(10), dp(8));
        card.setBackground(card_background());
        card.setElevation(0);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(6);

        // Row 1: Index Badge + Icon Button + Keyboard Trigger Input + Delete Button
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = make_index_badge();
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        badgeLp.setMarginEnd(dp(8));
        row1.addView(badge, badgeLp);

        FrameLayout iconBtn = make_icon_button();
        TextView tvGlyph = new TextView(this);
        tvGlyph.setTextSize(22f);
        tvGlyph.setTextColor(ContextCompat.getColor(this, R.color.settings_primary));
        tvGlyph.setGravity(Gravity.CENTER);
        MaterialSymbols.applyTo(tvGlyph);
        tvGlyph.setText(iconName);
        FrameLayout.LayoutParams glyphLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        glyphLp.gravity = Gravity.CENTER;
        iconBtn.addView(tvGlyph, glyphLp);

        LinearLayout.LayoutParams iconBtnLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        iconBtnLp.setMarginEnd(dp(8));
        row1.addView(iconBtn, iconBtnLp);

        EditText keywordInput = new EditText(this);
        keywordInput.setHint(R.string.tasker_builder_keyboard_name_hint);
        keywordInput.setSingleLine(true);
        style_edit_text(keywordInput);
        keywordInput.setText(keyword);
        LinearLayout.LayoutParams kwLp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        kwLp.setMarginEnd(dp(6));
        row1.addView(keywordInput, kwLp);

        ImageButton remove = make_remove_button();
        row1.addView(remove, new LinearLayout.LayoutParams(dp(28), dp(28)));
        card.addView(row1);

        // Row 2: Tasker Task Name Autocomplete + Pick Task Button
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        row2.setPadding(0, dp(6), 0, 0);

        AutoCompleteTextView taskInput = new AutoCompleteTextView(this);
        taskInput.setHint(R.string.tasker_builder_task_name_hint);
        taskInput.setSingleLine(true);
        style_edit_text(taskInput);
        taskInput.setText(taskName);
        if (!_cached_tasker_tasks.isEmpty())
        {
            taskInput.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line, _cached_tasker_tasks));
        }
        LinearLayout.LayoutParams taskInputLp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        taskInputLp.setMarginEnd(dp(6));
        row2.addView(taskInput, taskInputLp);

        MaterialButton btnPick = new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle);
        btnPick.setText(R.string.tasker_builder_pick_task);
        btnPick.setTextSize(12f);
        btnPick.setTextColor(ContextCompat.getColor(this, R.color.settings_primary));
        btnPick.setPadding(dp(10), 0, dp(10), 0);
        btnPick.setAllCaps(false);
        btnPick.setOnClickListener(v -> showTaskPicker(taskInput, keywordInput));
        row2.addView(btnPick, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)));

        card.addView(row2);

        final TaskRow rowObj = new TaskRow(card, badge, tvGlyph, keywordInput, taskInput, iconName);
        _task_rows.add(rowObj);
        _tasks_container.addView(card, cardLp);
        refresh_task_indices();

        iconBtn.setOnClickListener(v -> {
            MaterialSymbolPickerDialog.show(this, selectedIcon -> {
                rowObj.icon_name = selectedIcon;
                tvGlyph.setText(selectedIcon);
            });
        });

        remove.setOnClickListener(v -> {
            if (_task_rows.size() <= 1) return;
            _task_rows.remove(rowObj);
            _tasks_container.removeView(card);
            refresh_task_indices();
        });

        return rowObj;
    }

    private void showTaskPicker(final AutoCompleteTextView taskInput, final EditText keywordInput)
    {
        if (_cached_tasker_tasks.isEmpty())
        {
            _cached_tasker_tasks = queryTaskerTasks(this);
        }

        if (_cached_tasker_tasks.isEmpty())
        {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.tasker_builder_pick_task_title)
                    .setMessage(R.string.tasker_builder_no_tasks_found)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        final String[] taskArray = _cached_tasker_tasks.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.tasker_builder_pick_task_title)
                .setItems(taskArray, (dialog, which) -> {
                    String selected = taskArray[which];
                    taskInput.setText(selected);
                    if (keywordInput.getText() == null || keywordInput.getText().toString().trim().isEmpty())
                    {
                        // Auto-suggest a clean keyboard name
                        String suggested = selected.toLowerCase().replaceAll("[^a-z0-9_]", "_");
                        keywordInput.setText(suggested);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static List<String> queryTaskerTasks(Context context)
    {
        List<String> taskNames = new ArrayList<>();
        Uri[] uris = new Uri[] {
            Uri.parse("content://net.dinglisch.android.tasker/tasks"),
            Uri.parse("content://net.dinglisch.android.taskerm/tasks")
        };
        for (Uri uri : uris)
        {
            Cursor cursor = null;
            try
            {
                cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null)
                {
                    int nameIndex = cursor.getColumnIndex("name");
                    if (nameIndex != -1)
                    {
                        while (cursor.moveToNext())
                        {
                            String name = cursor.getString(nameIndex);
                            if (name != null && !name.trim().isEmpty() && !taskNames.contains(name.trim()))
                            {
                                taskNames.add(name.trim());
                            }
                        }
                    }
                }
            }
            catch (Exception e)
            {
                Log.w(LOG_TAG, "Querying Tasker tasks failed from " + uri, e);
            }
            finally
            {
                if (cursor != null)
                {
                    try { cursor.close(); } catch (Exception ignored) {}
                }
            }
            if (!taskNames.isEmpty())
                break;
        }
        return taskNames;
    }

    private void refresh_task_indices()
    {
        for (int i = 0; i < _task_rows.size(); i++)
        {
            _task_rows.get(i).badge.setText(String.valueOf(i + 1));
        }
    }

    private void save()
    {
        List<String> errors = new ArrayList<>();
        Set<String> seenKeywords = new HashSet<>();
        JSONObject root = new JSONObject();

        try
        {
            int timeoutSec = _timeout_seekbar != null ? Math.max(1, _timeout_seekbar.getProgress()) : 60;
            String replaceTrigger = "##";
            String appendTrigger = "@@";
            JSONArray patterns = null;

            String stored = TaskerAutomationManager.load(this);
            if (stored != null)
            {
                try
                {
                    JSONObject existing = new JSONObject(stored);
                    replaceTrigger = existing.optString(TaskerAutomationConfig.KEY_REPLACE_TRIGGER, "##");
                    appendTrigger = existing.optString(TaskerAutomationConfig.KEY_APPEND_TRIGGER, "@@");
                    patterns = existing.optJSONArray(TaskerAutomationConfig.KEY_EXPAND_PATTERNS);
                }
                catch (Exception ignored) {}
            }
            if (replaceTrigger == null || replaceTrigger.trim().isEmpty())
                replaceTrigger = "##";
            if (appendTrigger == null || appendTrigger.trim().isEmpty())
                appendTrigger = "@@";

            root.put(TaskerAutomationConfig.KEY_REPLACE_TRIGGER, replaceTrigger);
            root.put(TaskerAutomationConfig.KEY_APPEND_TRIGGER, appendTrigger);
            root.put(TaskerAutomationConfig.KEY_TIMEOUT_MS, String.valueOf(timeoutSec * 1000));
            if (patterns != null)
            {
                root.put(TaskerAutomationConfig.KEY_EXPAND_PATTERNS, patterns);
            }

            int validCount = 0;
            for (int i = 0; i < _task_rows.size(); i++)
            {
                TaskRow row = _task_rows.get(i);
                String kw = text_of(row.keyboard_name).trim();
                String task = text_of(row.task_name).trim();
                String icon = row.icon_name != null ? row.icon_name : MaterialSymbols.DEFAULT_ICON;

                if (kw.isEmpty() && task.isEmpty())
                {
                    continue;
                }
                if (kw.isEmpty() || task.isEmpty())
                {
                    errors.add("Row " + (i + 1) + ": fill in both the keyboard name and the Tasker task name, or remove the row");
                    continue;
                }
                if (seenKeywords.contains(kw.toLowerCase()))
                {
                    errors.add("Duplicate keyboard name \"" + kw + "\" at row " + (i + 1));
                    continue;
                }
                seenKeywords.add(kw.toLowerCase());

                JSONObject taskObj = new JSONObject();
                taskObj.put("task", task);
                taskObj.put("icon", icon);
                root.put(kw, taskObj);
                validCount++;
            }

            if (!errors.isEmpty())
            {
                show_error(android.text.TextUtils.join("\n", errors));
                return;
            }

            if (validCount == 0)
            {
                show_error("Please add at least one task mapping.");
                return;
            }

            String json = root.toString(2);
            TaskerAutomationManager.save(this, json);
            TaskerTriggerEngine.get().reload(this);
            TextExpanderEngine.get().reload(this);

            Toast.makeText(this, R.string.tasker_builder_saved_toast, Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        }
        catch (Exception e)
        {
            show_error("Save error: " + e.getMessage());
        }
    }

    private void show_error(String message)
    {
        _error_text.setText(message);
        _error_text.setVisibility(View.VISIBLE);
    }

    private static String text_of(EditText et)
    {
        return et == null || et.getText() == null ? "" : et.getText().toString();
    }

    private void style_edit_text(EditText et)
    {
        et.setTextColor(ContextCompat.getColor(this, R.color.settings_on_surface));
        et.setHintTextColor(ContextCompat.getColor(this, R.color.settings_on_surface_variant));
        et.setTextSize(13f);
        et.setBackground(card_background());
        et.setPadding(dp(10), 0, dp(10), 0);
        et.setGravity(Gravity.CENTER_VERTICAL);
    }

    private TextView make_index_badge()
    {
        TextView badge = new TextView(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(ContextCompat.getColor(this, R.color.settings_primary));
        badge.setBackground(bg);
        badge.setTextColor(ContextCompat.getColor(this, R.color.settings_on_primary));
        badge.setTextSize(10f);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        return badge;
    }

    private FrameLayout make_icon_button()
    {
        FrameLayout btn = new FrameLayout(this);
        btn.setClickable(true);
        btn.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(8));
        bg.setColor(ContextCompat.getColor(this, R.color.settings_card_bg));
        bg.setStroke(dp(1), ContextCompat.getColor(this, R.color.settings_divider));
        btn.setBackground(bg);
        btn.setContentDescription(getString(R.string.tasker_builder_select_icon));
        return btn;
    }

    private android.graphics.drawable.Drawable card_background()
    {
        return ContextCompat.getDrawable(this, R.drawable.bg_settings_card);
    }

    private ImageButton make_remove_button()
    {
        ImageButton remove = new ImageButton(this);
        remove.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        remove.setBackgroundColor(Color.TRANSPARENT);
        remove.setColorFilter(ContextCompat.getColor(this, R.color.settings_error));
        remove.setScaleType(ImageView.ScaleType.CENTER);
        remove.setPadding(0, 0, 0, 0);
        remove.setContentDescription(getString(R.string.tasker_builder_remove_row));
        return remove;
    }

    private int dp(int dp)
    {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
