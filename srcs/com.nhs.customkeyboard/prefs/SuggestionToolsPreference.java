package com.nhs.customkeyboard.prefs;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.nhs.customkeyboard.Config;
import com.nhs.customkeyboard.R;
import com.nhs.customkeyboard.suggestions.SuggestionActionHelper;
import com.nhs.customkeyboard.suggestions.SuggestionToolItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SuggestionToolsPreference extends Preference
{
  private List<SuggestionToolItem> _tools;
  private ToolsAdapter _adapter;

  public SuggestionToolsPreference(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    setLayoutResource(R.layout.pref_suggestion_tools);
    setSelectable(false);
  }

  @Override
  public void onBindViewHolder(@NonNull PreferenceViewHolder holder)
  {
    super.onBindViewHolder(holder);

    SharedPreferences prefs = getSharedPreferences();
    _tools = SuggestionToolItem.loadFromPrefs(getContext(), prefs);

    RecyclerView rv = (RecyclerView) holder.findViewById(R.id.tools_recycler_view);
    if (rv != null)
    {
      rv.setLayoutManager(new LinearLayoutManager(getContext()));
      _adapter = new ToolsAdapter(_tools, prefs);
      rv.setAdapter(_adapter);

      ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
          ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0)
      {
        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder,
                              @NonNull RecyclerView.ViewHolder target)
        {
          int fromPos = viewHolder.getAdapterPosition();
          int toPos = target.getAdapterPosition();
          if (fromPos < 0 || toPos < 0 || fromPos >= _tools.size() || toPos >= _tools.size())
          {
            return false;
          }
          Collections.swap(_tools, fromPos, toPos);
          _adapter.notifyItemMoved(fromPos, toPos);
          return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction)
        {
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder)
        {
          super.clearView(recyclerView, viewHolder);
          SuggestionToolItem.saveToPrefs(prefs, _tools);
          if (Config.globalConfig() != null)
          {
            Config.globalConfig().suggestion_tools = _tools;
          }
        }

        @Override
        public boolean isLongPressDragEnabled()
        {
          return false; // Only drag using the handle
        }
      };

      ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
      touchHelper.attachToRecyclerView(rv);
      _adapter.setItemTouchHelper(touchHelper);
    }

    View btnReset = holder.findViewById(R.id.btn_reset_tools);
    if (btnReset != null)
    {
      btnReset.setOnClickListener(v -> {
        _tools.clear();
        _tools.addAll(SuggestionToolItem.getDefaultTools());
        SuggestionToolItem.saveToPrefs(prefs, _tools);
        if (Config.globalConfig() != null)
        {
          Config.globalConfig().suggestion_tools = _tools;
        }
        if (_adapter != null)
        {
          _adapter.notifyDataSetChanged();
        }
      });
    }

    View btnAddTasker = holder.findViewById(R.id.btn_add_tasker_tool);
    if (btnAddTasker != null)
    {
      btnAddTasker.setOnClickListener(v -> showAddTaskerDialog(getContext(), prefs, rv));
    }
  }

  private void showAddTaskerDialog(Context ctx, SharedPreferences prefs, RecyclerView rv)
  {
    View dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_suggestion_action_picker, null);
    TextView tvTitle = dialogView.findViewById(R.id.tv_picker_title);
    tvTitle.setText(R.string.dialog_add_tasker_title);

    TextView tvTip = dialogView.findViewById(R.id.tv_picker_tip);
    if (tvTip != null)
    {
      tvTip.setText("Choose a Tasker task to add to the suggestion bar");
    }

    RecyclerView rvActions = dialogView.findViewById(R.id.rv_actions);
    rvActions.setLayoutManager(new LinearLayoutManager(ctx));

    AlertDialog dialog = new AlertDialog.Builder(ctx)
        .setView(dialogView)
        .create();

    if (dialog.getWindow() != null)
    {
      dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());

    List<PickerRow> rows = new ArrayList<>();
    rows.add(new PickerRow(ctx.getString(R.string.tool_category_tasker)));
    List<SuggestionActionHelper.ActionItem> taskerActions = SuggestionActionHelper.getTaskerActions(ctx);
    if (taskerActions != null)
    {
      for (SuggestionActionHelper.ActionItem t : taskerActions)
      {
        rows.add(new PickerRow(t));
      }
    }

    if (taskerActions == null || taskerActions.isEmpty())
    {
      rows.add(new PickerRow(new SuggestionActionHelper.ActionItem(
          "tasker_empty_info", ctx.getString(R.string.tool_tasker_empty), null, R.drawable.ic_info_outline, true)));
    }

    rows.add(new PickerRow(new SuggestionActionHelper.ActionItem(
        "tasker_custom", ctx.getString(R.string.tool_tasker_custom), null, R.drawable.ic_add, true)));

    ActionPickerAdapter pickerAdapter = new ActionPickerAdapter(rows, null, actionItem -> {
      if ("tasker_custom".equals(actionItem.id) || "tasker_empty_info".equals(actionItem.id))
      {
        final EditText input = new EditText(ctx);
        input.setHint(R.string.dialog_tasker_custom_hint);
        int pad = (int) (16 * ctx.getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(ctx)
            .setTitle(R.string.dialog_tasker_custom_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok, (d, which) -> {
              String name = input.getText().toString().trim();
              if (!name.isEmpty())
              {
                addOrEnableTaskerTool(name, prefs, rv);
                dialog.dismiss();
              }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
      }
      else if (actionItem.id != null && actionItem.id.startsWith(SuggestionActionHelper.TASKER_PREFIX))
      {
        String taskName = actionItem.id.substring(SuggestionActionHelper.TASKER_PREFIX.length());
        addOrEnableTaskerTool(taskName, prefs, rv);
        dialog.dismiss();
      }
    });
    rvActions.setAdapter(pickerAdapter);
    dialog.show();
  }

  private void addOrEnableTaskerTool(String taskName, SharedPreferences prefs, RecyclerView rv)
  {
    String id = SuggestionActionHelper.TASKER_PREFIX + taskName;
    for (int i = 0; i < _tools.size(); i++)
    {
      SuggestionToolItem existing = _tools.get(i);
      if (existing.id.equals(id))
      {
        existing.enabled = true;
        SuggestionToolItem.saveToPrefs(prefs, _tools);
        if (Config.globalConfig() != null)
        {
          Config.globalConfig().suggestion_tools = _tools;
        }
        if (_adapter != null)
        {
          _adapter.notifyItemChanged(i);
        }
        if (rv != null)
        {
          rv.smoothScrollToPosition(i);
        }
        return;
      }
    }

    SuggestionToolItem newItem = new SuggestionToolItem(id, taskName, R.drawable.ic_tasker_bolt, true, null);
    _tools.add(newItem);
    SuggestionToolItem.saveToPrefs(prefs, _tools);
    if (Config.globalConfig() != null)
    {
      Config.globalConfig().suggestion_tools = _tools;
    }
    if (_adapter != null)
    {
      _adapter.notifyItemInserted(_tools.size() - 1);
    }
    if (rv != null)
    {
      rv.smoothScrollToPosition(_tools.size() - 1);
    }
  }

  private static class ToolsAdapter extends RecyclerView.Adapter<ToolsAdapter.ViewHolder>
  {
    private final List<SuggestionToolItem> _items;
    private final SharedPreferences _prefs;
    private ItemTouchHelper _itemTouchHelper;

    public ToolsAdapter(List<SuggestionToolItem> items, SharedPreferences prefs)
    {
      _items = items;
      _prefs = prefs;
    }

    public void setItemTouchHelper(ItemTouchHelper touchHelper)
    {
      _itemTouchHelper = touchHelper;
    }

    private void saveTools()
    {
      SuggestionToolItem.saveToPrefs(_prefs, _items);
      if (Config.globalConfig() != null)
      {
        Config.globalConfig().suggestion_tools = _items;
      }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
      View view = LayoutInflater.from(parent.getContext())
          .inflate(R.layout.pref_suggestion_tool_item, parent, false);
      return new ViewHolder(view);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position)
    {
      Context ctx = holder.itemView.getContext();
      SuggestionToolItem item = _items.get(position);
      holder.title.setText(item.getTitle(ctx));
      holder.icon.setImageResource(item.getIconRes());

      if (item.longPressAction != null && !item.longPressAction.isEmpty())
      {
        String displayName = SuggestionActionHelper.getActionDisplayName(ctx, item.longPressAction);
        holder.longPressSummary.setText(ctx.getString(R.string.tool_long_press_format, displayName));
        TypedValue tv = new TypedValue();
        if (ctx.getTheme().resolveAttribute(android.R.attr.colorAccent, tv, true))
        {
          holder.longPressSummary.setTextColor(tv.data);
        }
      }
      else
      {
        holder.longPressSummary.setText(ctx.getString(R.string.tool_long_press_none));
        TypedValue tv = new TypedValue();
        if (ctx.getTheme().resolveAttribute(android.R.attr.textColorSecondary, tv, true))
        {
          holder.longPressSummary.setTextColor(tv.data);
        }
      }

      // Prevent triggering listener during rebinding
      holder.toggleSwitch.setOnCheckedChangeListener(null);
      holder.toggleSwitch.setChecked(item.enabled);

      holder.toggleSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
        item.enabled = isChecked;
        saveTools();
      });

      // Clicking text area opens action selection dialog
      View.OnClickListener openPicker = v -> showActionPickerDialog(ctx, item, position);
      if (holder.textContainer != null)
      {
        holder.textContainer.setOnClickListener(openPicker);
      }
      else
      {
        holder.title.setOnClickListener(openPicker);
      }

      // Switch is only triggered when directly pressing the switch itself
      holder.itemView.setOnClickListener(null);
      holder.itemView.setClickable(false);

      holder.dragHandle.setOnTouchListener((v, event) -> {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
        {
          if (_itemTouchHelper != null)
          {
            _itemTouchHelper.startDrag(holder);
          }
        }
        return false;
      });
    }

    private void showActionPickerDialog(Context ctx, SuggestionToolItem item, int position)
    {
      View dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_suggestion_action_picker, null);
      TextView tvTitle = dialogView.findViewById(R.id.tv_picker_title);
      tvTitle.setText(ctx.getString(R.string.tool_dialog_title, item.getTitle(ctx)));

      RecyclerView rv = dialogView.findViewById(R.id.rv_actions);
      rv.setLayoutManager(new LinearLayoutManager(ctx));

      AlertDialog dialog = new AlertDialog.Builder(ctx)
          .setView(dialogView)
          .create();

      if (dialog.getWindow() != null)
      {
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
      }

      dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());

      // Prepare list data
      List<PickerRow> rows = new ArrayList<>();

      // 1. None
      rows.add(new PickerRow(new SuggestionActionHelper.ActionItem(
          "", ctx.getString(R.string.tool_remove_action), null, R.drawable.ic_close, false)));

      // 2. Standard Actions
      rows.add(new PickerRow(ctx.getString(R.string.tool_category_actions)));
      for (SuggestionActionHelper.ActionItem a : SuggestionActionHelper.getStandardActions(ctx))
      {
        rows.add(new PickerRow(a));
      }

      // 3. Tasker Automation
      rows.add(new PickerRow(ctx.getString(R.string.tool_category_tasker)));
      List<SuggestionActionHelper.ActionItem> taskerActions = SuggestionActionHelper.getTaskerActions(ctx);
      for (SuggestionActionHelper.ActionItem t : taskerActions)
      {
        rows.add(new PickerRow(t));
      }

      if (taskerActions.isEmpty())
      {
        rows.add(new PickerRow(new SuggestionActionHelper.ActionItem(
            "tasker_empty_info", ctx.getString(R.string.tool_tasker_empty), null, R.drawable.ic_info_outline, true)));
      }

      // Custom Tasker task entry
      rows.add(new PickerRow(new SuggestionActionHelper.ActionItem(
          "tasker_custom", ctx.getString(R.string.tool_tasker_custom), null, R.drawable.ic_add, true)));

      // If this is a Tasker tool, allow deleting it
      if (item.isTasker())
      {
        rows.add(new PickerRow(new SuggestionActionHelper.ActionItem(
            "delete_tasker_tool", ctx.getString(R.string.tool_tasker_delete), null, R.drawable.ic_delete, false)));
      }

      ActionPickerAdapter pickerAdapter = new ActionPickerAdapter(rows, item.longPressAction, actionItem -> {
        if ("delete_tasker_tool".equals(actionItem.id))
        {
          _items.remove(position);
          saveTools();
          notifyItemRemoved(position);
          notifyItemRangeChanged(position, _items.size() - position);
          dialog.dismiss();
        }
        else if ("tasker_custom".equals(actionItem.id) || "tasker_empty_info".equals(actionItem.id))
        {
          showCustomTaskerInputDialog(ctx, item, position, dialog);
        }
        else
        {
          item.longPressAction = (actionItem.id != null && !actionItem.id.isEmpty()) ? actionItem.id : null;
          saveTools();
          notifyItemChanged(position);
          dialog.dismiss();
        }
      });
      rv.setAdapter(pickerAdapter);

      dialog.show();
    }

    private void showCustomTaskerInputDialog(Context ctx, SuggestionToolItem item, int position, AlertDialog parentDialog)
    {
      final EditText input = new EditText(ctx);
      input.setHint(R.string.dialog_tasker_custom_hint);
      int pad = (int) (16 * ctx.getResources().getDisplayMetrics().density);
      input.setPadding(pad, pad, pad, pad);

      new AlertDialog.Builder(ctx)
          .setTitle(R.string.dialog_tasker_custom_title)
          .setView(input)
          .setPositiveButton(android.R.string.ok, (d, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty())
            {
              item.longPressAction = SuggestionActionHelper.TASKER_PREFIX + name;
              saveTools();
              notifyItemChanged(position);
              if (parentDialog != null)
              {
                parentDialog.dismiss();
              }
            }
          })
          .setNegativeButton(android.R.string.cancel, null)
          .show();
    }

    @Override
    public int getItemCount()
    {
      return _items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder
    {
      final ImageView dragHandle;
      final ImageView icon;
      final TextView title;
      final TextView longPressSummary;
      final View textContainer;
      final SwitchCompat toggleSwitch;

      public ViewHolder(@NonNull View itemView)
      {
        super(itemView);
        dragHandle = itemView.findViewById(R.id.tool_drag_handle);
        icon = itemView.findViewById(R.id.tool_icon);
        title = itemView.findViewById(R.id.tool_title);
        longPressSummary = itemView.findViewById(R.id.tool_long_press_summary);
        textContainer = itemView.findViewById(R.id.tool_text_container);
        toggleSwitch = itemView.findViewById(R.id.tool_switch);
      }
    }
  }

  private static class PickerRow
  {
    final boolean isHeader;
    final String headerTitle;
    final SuggestionActionHelper.ActionItem action;

    PickerRow(String header)
    {
      this.isHeader = true;
      this.headerTitle = header;
      this.action = null;
    }

    PickerRow(SuggestionActionHelper.ActionItem action)
    {
      this.isHeader = false;
      this.headerTitle = null;
      this.action = action;
    }
  }

  private interface OnActionSelectedListener
  {
    void onActionSelected(SuggestionActionHelper.ActionItem action);
  }

  private static class ActionPickerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
  {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ACTION = 1;

    private final List<PickerRow> _rows;
    private final String _selectedActionId;
    private final OnActionSelectedListener _listener;

    ActionPickerAdapter(List<PickerRow> rows, String selectedActionId, OnActionSelectedListener listener)
    {
      _rows = rows;
      _selectedActionId = selectedActionId;
      _listener = listener;
    }

    @Override
    public int getItemViewType(int position)
    {
      return _rows.get(position).isHeader ? TYPE_HEADER : TYPE_ACTION;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
      if (viewType == TYPE_HEADER)
      {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.dialog_suggestion_action_header, parent, false);
        return new HeaderViewHolder(view);
      }
      else
      {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.dialog_suggestion_action_item, parent, false);
        return new ActionViewHolder(view);
      }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position)
    {
      PickerRow row = _rows.get(position);
      if (holder instanceof HeaderViewHolder)
      {
        ((HeaderViewHolder) holder).title.setText(row.headerTitle);
      }
      else if (holder instanceof ActionViewHolder)
      {
        ActionViewHolder vh = (ActionViewHolder) holder;
        SuggestionActionHelper.ActionItem item = row.action;
        vh.title.setText(item.title);
        vh.icon.setImageResource(item.iconRes);

        if (item.subtitle != null && !item.subtitle.isEmpty())
        {
          vh.subtitle.setText(item.subtitle);
          vh.subtitle.setVisibility(View.VISIBLE);
        }
        else
        {
          vh.subtitle.setVisibility(View.GONE);
        }

        boolean isSelected = false;
        if (_selectedActionId == null || _selectedActionId.isEmpty())
        {
          isSelected = (item.id == null || item.id.isEmpty());
        }
        else
        {
          isSelected = _selectedActionId.equals(item.id);
        }

        vh.check.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        vh.itemView.setOnClickListener(v -> {
          if (_listener != null)
          {
            _listener.onActionSelected(item);
          }
        });
      }
    }

    @Override
    public int getItemCount()
    {
      return _rows.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder
    {
      final TextView title;

      HeaderViewHolder(@NonNull View itemView)
      {
        super(itemView);
        title = itemView.findViewById(R.id.header_title);
      }
    }

    static class ActionViewHolder extends RecyclerView.ViewHolder
    {
      final ImageView icon;
      final TextView title;
      final TextView subtitle;
      final ImageView check;

      ActionViewHolder(@NonNull View itemView)
      {
        super(itemView);
        icon = itemView.findViewById(R.id.action_icon);
        title = itemView.findViewById(R.id.action_title);
        subtitle = itemView.findViewById(R.id.action_subtitle);
        check = itemView.findViewById(R.id.action_check);
      }
    }
  }
}
