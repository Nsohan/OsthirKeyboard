package com.nhs.customkeyboard.textexpander;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.nhs.customkeyboard.R;
import com.nhs.customkeyboard.TaskerAutomationConfig;
import com.nhs.customkeyboard.prefs.TaskerAutomationManager;
import com.nhs.customkeyboard.tasker.MaterialSymbols;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Management Activity for viewing, creating, editing, and deleting Text Expander snippets. */
public final class TextExpanderActivity extends AppCompatActivity
{
    private RecyclerView _recyclerView;
    private View _emptyStateView;
    private SnippetsAdapter _adapter;
    private final List<TextExpanderItem> _items = new ArrayList<>();

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

        setContentView(R.layout.activity_text_expander);

        View root = findViewById(R.id.text_expander_root);
        if (root != null)
        {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
                androidx.core.graphics.Insets insets = windowInsets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(insets.left, insets.top, insets.right, 0);
                return windowInsets;
            });
        }

        MaterialToolbar toolbar = findViewById(R.id.text_expander_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
        {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        _recyclerView = findViewById(R.id.recycler_text_expander);
        _emptyStateView = findViewById(R.id.empty_state_view);
        _recyclerView.setLayoutManager(new LinearLayoutManager(this));

        if (_recyclerView != null)
        {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(_recyclerView, (v, windowInsets) -> {
                androidx.core.graphics.Insets insets = windowInsets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars());
                int extraBottom = (int) (88 * getResources().getDisplayMetrics().density);
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), insets.bottom + extraBottom);
                return windowInsets;
            });
        }

        _adapter = new SnippetsAdapter();
        _recyclerView.setAdapter(_adapter);

        ExtendedFloatingActionButton fabAdd = findViewById(R.id.fab_add_snippet);
        if (fabAdd != null)
        {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(fabAdd, (v, windowInsets) -> {
                androidx.core.graphics.Insets insets = windowInsets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars());
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                int baseMargin = (int) (20 * getResources().getDisplayMetrics().density);
                lp.bottomMargin = insets.bottom + baseMargin;
                lp.rightMargin = insets.right + baseMargin;
                v.setLayoutParams(lp);
                return windowInsets;
            });
            fabAdd.setOnClickListener(v -> showEditDialog(null));
        }

        loadSnippets();
    }

    private void loadSnippets()
    {
        _items.clear();
        _items.addAll(TextExpanderManager.get_all_items(this));
        _adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState()
    {
        if (_items.isEmpty())
        {
            _emptyStateView.setVisibility(View.VISIBLE);
            _recyclerView.setVisibility(View.GONE);
        }
        else
        {
            _emptyStateView.setVisibility(View.GONE);
            _recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showEditDialog(final TextExpanderItem existingItem)
    {
        final boolean isEdit = existingItem != null;
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_text_expander_edit, null);

        final TextView tvDialogTitle = dialogView.findViewById(R.id.tv_dialog_title);
        final TextInputEditText etPrefix = dialogView.findViewById(R.id.et_trigger_prefix);
        final MaterialAutoCompleteTextView etKeyword = dialogView.findViewById(R.id.et_trigger_keyword);
        final TextInputLayout tilKeyword = dialogView.findViewById(R.id.til_trigger_keyword);
        final TextInputEditText etOutput = dialogView.findViewById(R.id.et_output);
        final SwitchCompat switchEnabled = dialogView.findViewById(R.id.switch_dialog_enabled);
        final MaterialButton btnInsertVar = dialogView.findViewById(R.id.btn_insert_variable);
        final View layoutOutputContainer = dialogView.findViewById(R.id.layout_output_container);
        final View layoutTaskInfo = dialogView.findViewById(R.id.layout_task_selected_info);
        final TextView tvTaskIcon = dialogView.findViewById(R.id.tv_task_selected_icon);
        final TextView tvTaskName = dialogView.findViewById(R.id.tv_task_selected_name);

        if (tvDialogTitle != null)
        {
            tvDialogTitle.setText(isEdit ? R.string.text_expander_dialog_title_edit : R.string.text_expander_dialog_title_add);
        }

        String taskerJson = TaskerAutomationManager.load(this);
        TaskerAutomationConfig parsedConfig = null;
        if (taskerJson != null)
        {
            try { parsedConfig = TaskerAutomationConfig.parse(taskerJson); }
            catch (Exception ignored) {}
        }
        final TaskerAutomationConfig taskerConfig = parsedConfig;

        final List<String> taskOptions = new ArrayList<>();
        if (taskerConfig != null && taskerConfig.tasks != null)
        {
            for (String kw : taskerConfig.tasks.keySet())
            {
                taskOptions.add(kw);
                taskOptions.add("task:" + kw);
            }
        }

        if (!taskOptions.isEmpty())
        {
            etKeyword.setSimpleItems(taskOptions.toArray(new String[0]));
            if (tilKeyword != null)
            {
                tilKeyword.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
            }
        }
        else
        {
            if (tilKeyword != null)
            {
                tilKeyword.setEndIconMode(TextInputLayout.END_ICON_NONE);
            }
        }

        final Runnable updateOutputVisibility = () -> {
            String kw = etKeyword.getText() != null ? etKeyword.getText().toString().trim() : "";
            String matchedTask = null;
            String icon = MaterialSymbols.DEFAULT_ICON;

            if (taskerConfig != null && taskerConfig.tasks != null)
            {
                String cleanKw = kw.startsWith("task:") ? kw.substring(5).trim() : kw;
                if (taskerConfig.tasks.containsKey(cleanKw))
                {
                    matchedTask = taskerConfig.tasks.get(cleanKw);
                    icon = taskerConfig.getTaskIcon(cleanKw);
                }
            }

            if (matchedTask != null)
            {
                if (layoutOutputContainer != null) layoutOutputContainer.setVisibility(View.GONE);
                if (layoutTaskInfo != null)
                {
                    layoutTaskInfo.setVisibility(View.VISIBLE);
                    if (tvTaskIcon != null)
                    {
                        MaterialSymbols.applyTo(tvTaskIcon);
                        tvTaskIcon.setText(icon);
                    }
                    if (tvTaskName != null)
                    {
                        tvTaskName.setText(matchedTask);
                    }
                }
            }
            else
            {
                if (layoutOutputContainer != null) layoutOutputContainer.setVisibility(View.VISIBLE);
                if (layoutTaskInfo != null) layoutTaskInfo.setVisibility(View.GONE);
            }
        };

        etKeyword.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s)
            {
                updateOutputVisibility.run();
            }
        });

        etKeyword.setOnItemClickListener((parent, view, position, id) -> updateOutputVisibility.run());

        if (isEdit)
        {
            etPrefix.setText(existingItem.trigger_prefix != null ? existingItem.trigger_prefix : "@@");
            etKeyword.setText(existingItem.keyword != null ? existingItem.keyword : "");
            etOutput.setText(existingItem.output != null ? existingItem.output : "");
            switchEnabled.setChecked(existingItem.enabled);
        }
        else
        {
            etPrefix.setText("@@");
            switchEnabled.setChecked(true);
        }

        updateOutputVisibility.run();

        btnInsertVar.setOnClickListener(v -> showVariablePopupMenu(v, etOutput));

        new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String prefix = etPrefix.getText() != null ? etPrefix.getText().toString().trim() : "@@";
                    String keyword = etKeyword.getText() != null ? etKeyword.getText().toString().trim() : "";
                    boolean enabled = switchEnabled.isChecked();

                    if (prefix.isEmpty() || keyword.isEmpty())
                    {
                        Toast.makeText(this, R.string.text_expander_error_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String cleanKw = keyword.startsWith("task:") ? keyword.substring(5).trim() : keyword;
                    String matchedTask = null;
                    if (taskerConfig != null && taskerConfig.tasks != null && taskerConfig.tasks.containsKey(cleanKw))
                    {
                        matchedTask = taskerConfig.tasks.get(cleanKw);
                    }

                    String output;
                    if (matchedTask != null)
                    {
                        output = "$task:" + matchedTask;
                    }
                    else
                    {
                        output = etOutput.getText() != null ? etOutput.getText().toString() : "";
                    }

                    TextExpanderItem item = isEdit ? existingItem : new TextExpanderItem();
                    item.trigger_prefix = prefix;
                    item.keyword = keyword;
                    item.output = output;
                    item.enabled = enabled;

                    TextExpanderManager.add_or_update_item(this, item);
                    loadSnippets();
                    Toast.makeText(this, R.string.text_expander_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showVariablePopupMenu(View anchor, final TextInputEditText etOutput)
    {
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu menu = popup.getMenu();

        List<TextExpanderVariableResolver.VariableInfo> vars =
                TextExpanderVariableResolver.get_available_variables(this);

        Map<String, SubMenu> submenus = new HashMap<>();

        for (int i = 0; i < vars.size(); i++)
        {
            TextExpanderVariableResolver.VariableInfo v = vars.get(i);
            SubMenu categoryMenu = submenus.get(v.category);
            if (categoryMenu == null)
            {
                categoryMenu = menu.addSubMenu(v.category);
                submenus.put(v.category, categoryMenu);
            }
            final String tokenToInsert = v.token;
            categoryMenu.add(v.token + " - " + v.label).setOnMenuItemClickListener(item -> {
                int start = Math.max(etOutput.getSelectionStart(), 0);
                int end = Math.max(etOutput.getSelectionEnd(), 0);
                if (etOutput.getText() != null)
                {
                    etOutput.getText().replace(Math.min(start, end), Math.max(start, end),
                            tokenToInsert, 0, tokenToInsert.length());
                }
                return true;
            });
        }

        popup.show();
    }

    private void confirmDelete(final TextExpanderItem item)
    {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.text_expander_delete_title)
                .setMessage(getString(R.string.text_expander_delete_msg, item.get_full_trigger()))
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> {
                    TextExpanderManager.delete_item(this, item.id);
                    loadSnippets();
                    Toast.makeText(this, R.string.text_expander_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private class SnippetsAdapter extends RecyclerView.Adapter<SnippetViewHolder>
    {
        @NonNull
        @Override
        public SnippetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
        {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_text_expander, parent, false);
            return new SnippetViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SnippetViewHolder holder, int position)
        {
            TextExpanderItem item = _items.get(position);
            holder.tvTrigger.setText(item.get_full_trigger());
            holder.switchEnabled.setChecked(item.enabled);

            if (item.is_tasker_task())
            {
                holder.tvTaskerBadge.setVisibility(View.VISIBLE);
                holder.tvTaskerBadge.setText("TASK: " + item.get_task_name());
            }
            else
            {
                holder.tvTaskerBadge.setVisibility(View.GONE);
            }

            holder.tvOutput.setText(item.output != null && !item.output.isEmpty() ? item.output : "(empty)");

            holder.switchEnabled.setOnCheckedChangeListener(null);
            holder.switchEnabled.setChecked(item.enabled);
            holder.switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                item.enabled = isChecked;
                TextExpanderManager.add_or_update_item(TextExpanderActivity.this, item);
            });

            holder.btnEdit.setOnClickListener(v -> showEditDialog(item));
            holder.btnDelete.setOnClickListener(v -> confirmDelete(item));
            holder.itemView.setOnClickListener(v -> showEditDialog(item));
        }

        @Override
        public int getItemCount()
        {
            return _items.size();
        }
    }

    private static class SnippetViewHolder extends RecyclerView.ViewHolder
    {
        final TextView tvTrigger;
        final TextView tvTaskerBadge;
        final SwitchCompat switchEnabled;
        final TextView tvOutput;
        final ImageButton btnEdit;
        final ImageButton btnDelete;

        SnippetViewHolder(@NonNull View itemView)
        {
            super(itemView);
            tvTrigger = itemView.findViewById(R.id.tv_trigger_badge);
            tvTaskerBadge = itemView.findViewById(R.id.tv_tasker_badge);
            switchEnabled = itemView.findViewById(R.id.switch_snippet_enabled);
            tvOutput = itemView.findViewById(R.id.tv_output_preview);
            btnEdit = itemView.findViewById(R.id.btn_edit_snippet);
            btnDelete = itemView.findViewById(R.id.btn_delete_snippet);
        }
    }
}

