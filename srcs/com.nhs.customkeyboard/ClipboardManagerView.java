package com.nhs.customkeyboard;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;

public class ClipboardManagerView extends LinearLayout
    implements ClipboardHistoryService.OnClipboardHistoryChange
{
  private static final String PINNED_PREFS = "pinned_clipboards";
  private static final String PINNED_KEY = "pinned";

  private RecyclerView _recyclerView;
  private View _emptyView;
  private TextView _emptyText;
  private ImageButton _btnBack;
  private ImageButton _btnEdit;
  private ImageButton _btnDeleteSelected;
  private Switch _switchToggle;

  private ClipboardAdapter _adapter;
  private final List<ClipItem> _items = new ArrayList<>();
  private boolean _isEditMode = false;
  private final Set<String> _selectedItems = new HashSet<>();
  private int _targetHeight = 0;

  public ClipboardManagerView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
    int targetH = _targetHeight;
    if (targetH <= 0)
    {
      android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
      targetH = (int) (290 * dm.density);
    }
    int exactHeightSpec = MeasureSpec.makeMeasureSpec(targetH, MeasureSpec.EXACTLY);
    super.onMeasure(widthMeasureSpec, exactHeightSpec);
  }

  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();

    _recyclerView = findViewById(R.id.clipboard_recycler_view);
    _emptyView = findViewById(R.id.clipboard_empty_view);
    _emptyText = findViewById(R.id.clipboard_empty_text);
    _btnBack = findViewById(R.id.clipboard_btn_back);
    _btnEdit = findViewById(R.id.clipboard_btn_edit);
    _btnDeleteSelected = findViewById(R.id.clipboard_btn_delete_selected);
    _switchToggle = findViewById(R.id.clipboard_switch_toggle);

    if (_recyclerView != null)
    {
      StaggeredGridLayoutManager lm =
          new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
      lm.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
      _recyclerView.setLayoutManager(lm);
      _adapter = new ClipboardAdapter();
      _recyclerView.setAdapter(_adapter);
    }

    if (_btnBack != null)
    {
      _btnBack.setOnClickListener(v -> close_clipboard());
    }

    if (_switchToggle != null)
    {
      _switchToggle.setChecked(Config.globalConfig().clipboard_history_enabled);
      _switchToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
        ClipboardHistoryService.set_history_enabled(isChecked);
        reload_data();
      });
    }

    if (_btnEdit != null)
    {
      _btnEdit.setOnClickListener(v -> toggle_edit_mode());
    }

    if (_btnDeleteSelected != null)
    {
      _btnDeleteSelected.setOnClickListener(v -> delete_selected_items());
    }

    ClipboardHistoryService service = ClipboardHistoryService.get_service(getContext());
    if (service != null)
    {
      service.set_on_clipboard_history_change(this);
    }

    reload_data();
  }

  public void onShow(int targetHeight)
  {
    if (targetHeight > 0)
    {
      _targetHeight = targetHeight;
      ViewGroup.LayoutParams lp = getLayoutParams();
      if (lp != null && lp.height != targetHeight)
      {
        lp.height = targetHeight;
        setLayoutParams(lp);
      }
    }
    update_bottom_margin(0);
    requestLayout();
    _isEditMode = false;
    _selectedItems.clear();
    update_edit_ui();
    reload_data();
  }

  private void update_bottom_margin(int insetBottom)
  {
    int basePad = (int) (10 * getResources().getDisplayMetrics().density);
    if (Config.globalConfig() != null && Config.globalConfig().margin_bottom > 0)
    {
      basePad = Math.max(basePad, (int) Config.globalConfig().margin_bottom);
    }
    int totalBottom = basePad + insetBottom;
    if (_recyclerView != null)
    {
      _recyclerView.setClipToPadding(false);
      _recyclerView.setPaddingRelative(
          _recyclerView.getPaddingStart(),
          _recyclerView.getPaddingTop(),
          _recyclerView.getPaddingEnd(),
          totalBottom);
    }
    if (_emptyView != null)
    {
      _emptyView.setPadding(
          _emptyView.getPaddingLeft(),
          _emptyView.getPaddingTop(),
          _emptyView.getPaddingRight(),
          totalBottom);
    }
  }

  @Override
  public WindowInsets onApplyWindowInsets(WindowInsets insets)
  {
    if (Build.VERSION.SDK_INT >= 30)
    {
      android.graphics.Insets navInsets = insets.getInsets(
          WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
      update_bottom_margin(navInsets.bottom);
    }
    return super.onApplyWindowInsets(insets);
  }

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();
    reload_data();
  }

  @Override
  public void on_clipboard_history_change()
  {
    post(this::reload_data);
  }

  private void close_clipboard()
  {
    Config.globalConfig().handler.key_up(
        KeyValue.getKeyByName("switch_back_clipboard"),
        Pointers.Modifiers.EMPTY);
  }

  private void toggle_edit_mode()
  {
    _isEditMode = !_isEditMode;
    _selectedItems.clear();
    update_edit_ui();
    if (_adapter != null)
    {
      _adapter.notifyDataSetChanged();
    }
  }

  private void update_edit_ui()
  {
    if (_btnDeleteSelected != null)
    {
      _btnDeleteSelected.setVisibility(_isEditMode ? View.VISIBLE : View.GONE);
    }
    if (_btnEdit != null)
    {
      _btnEdit.setImageResource(_isEditMode ? R.drawable.ic_close : R.drawable.ic_edit_pencil);
    }
  }

  private void delete_selected_items()
  {
    if (_selectedItems.isEmpty())
    {
      toggle_edit_mode();
      return;
    }

    List<String> pinned = load_pinned(getContext());
    ClipboardHistoryService service = ClipboardHistoryService.get_service(getContext());

    for (String text : _selectedItems)
    {
      if (pinned.contains(text))
      {
        pinned.remove(text);
      }
      if (service != null)
      {
        service.remove_history_entry(text);
      }
    }
    save_pinned(getContext(), pinned);

    _selectedItems.clear();
    _isEditMode = false;
    update_edit_ui();
    reload_data();
  }

  public void reload_data()
  {
    _items.clear();

    boolean enabled = Config.globalConfig().clipboard_history_enabled;
    if (_switchToggle != null && _switchToggle.isChecked() != enabled)
    {
      _switchToggle.setChecked(enabled);
    }

    if (!enabled)
    {
      if (_emptyView != null)
      {
        _emptyView.setVisibility(View.VISIBLE);
        if (_emptyText != null)
          _emptyText.setText("Clipboard history is turned off");
      }
      if (_recyclerView != null) _recyclerView.setVisibility(View.GONE);
      if (_adapter != null) _adapter.notifyDataSetChanged();
      return;
    }

    List<String> pinned = load_pinned(getContext());
    ClipboardHistoryService service = ClipboardHistoryService.get_service(getContext());
    List<String> recent = service != null ? service.clear_expired_and_get_history() : new ArrayList<>();

    if (pinned.isEmpty() && recent.isEmpty())
    {
      if (_emptyView != null)
      {
        _emptyView.setVisibility(View.VISIBLE);
        if (_emptyText != null)
          _emptyText.setText("No copied items yet");
      }
      if (_recyclerView != null) _recyclerView.setVisibility(View.GONE);
    }
    else
    {
      if (_emptyView != null) _emptyView.setVisibility(View.GONE);
      if (_recyclerView != null) _recyclerView.setVisibility(View.VISIBLE);

      if (!pinned.isEmpty())
      {
        _items.add(ClipItem.header("Pinned"));
        for (String p : pinned)
        {
          _items.add(ClipItem.card(p, true));
        }
      }

      if (!recent.isEmpty())
      {
        _items.add(ClipItem.header("Recent"));
        for (String r : recent)
        {
          if (!pinned.contains(r))
          {
            _items.add(ClipItem.card(r, false));
          }
        }
      }
    }

    if (_adapter != null)
    {
      _adapter.notifyDataSetChanged();
    }
  }

  private void show_card_options(View anchor, ClipItem item)
  {
    Context ctx = getContext();
    String[] options = item.isPinned
        ? new String[]{"Unpin", "Delete", "Copy to clipboard"}
        : new String[]{"Pin to top", "Delete", "Copy to clipboard"};

    AlertDialog dialog = new AlertDialog.Builder(ctx)
        .setTitle("Clipboard Item")
        .setItems(options, (d, which) -> {
          if (which == 0)
          {
            // Pin / Unpin
            List<String> pinned = load_pinned(ctx);
            if (item.isPinned)
            {
              pinned.remove(item.text);
              save_pinned(ctx, pinned);
              ClipboardHistoryService service = ClipboardHistoryService.get_service(ctx);
              if (service != null) service.add_clip(item.text);
            }
            else
            {
              if (!pinned.contains(item.text))
              {
                pinned.add(0, item.text);
                save_pinned(ctx, pinned);
              }
              ClipboardHistoryService service = ClipboardHistoryService.get_service(ctx);
              if (service != null) service.remove_history_entry(item.text);
            }
            reload_data();
          }
          else if (which == 1)
          {
            // Delete
            if (item.isPinned)
            {
              List<String> pinned = load_pinned(ctx);
              pinned.remove(item.text);
              save_pinned(ctx, pinned);
            }
            ClipboardHistoryService service = ClipboardHistoryService.get_service(ctx);
            if (service != null) service.remove_history_entry(item.text);
            reload_data();
          }
          else if (which == 2)
          {
            // Copy
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null)
            {
              cm.setPrimaryClip(ClipData.newPlainText("NHCK", item.text));
              Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show();
            }
          }
        })
        .setNegativeButton(android.R.string.cancel, null)
        .create();

    Utils.show_dialog_on_ime(dialog, anchor.getWindowToken());
  }

  // --- Storage Helpers for Pinned Items ---

  private static List<String> load_pinned(Context ctx)
  {
    List<String> list = new ArrayList<>();
    try
    {
      SharedPreferences sp = ctx.getSharedPreferences(PINNED_PREFS, Context.MODE_PRIVATE);
      String json = sp.getString(PINNED_KEY, null);
      if (json != null)
      {
        JSONArray arr = new JSONArray(json);
        for (int i = 0; i < arr.length(); i++)
        {
          list.add(arr.getString(i));
        }
      }
    }
    catch (Exception ignored) {}
    return list;
  }

  private static void save_pinned(Context ctx, List<String> list)
  {
    try
    {
      SharedPreferences sp = ctx.getSharedPreferences(PINNED_PREFS, Context.MODE_PRIVATE);
      JSONArray arr = new JSONArray();
      for (String s : list)
      {
        arr.put(s);
      }
      sp.edit().putString(PINNED_KEY, arr.toString()).apply();
    }
    catch (Exception ignored) {}
  }

  // --- Data Model for Adapter ---

  static class ClipItem
  {
    final boolean isHeader;
    final String headerTitle;
    final String text;
    final boolean isPinned;

    private ClipItem(boolean isHeader, String headerTitle, String text, boolean isPinned)
    {
      this.isHeader = isHeader;
      this.headerTitle = headerTitle;
      this.text = text;
      this.isPinned = isPinned;
    }

    static ClipItem header(String title)
    {
      return new ClipItem(true, title, null, false);
    }

    static ClipItem card(String text, boolean isPinned)
    {
      return new ClipItem(false, null, text, isPinned);
    }
  }

  // --- Recycler Adapter ---

  private class ClipboardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
  {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CARD = 1;

    @Override
    public int getItemViewType(int position)
    {
      return _items.get(position).isHeader ? TYPE_HEADER : TYPE_CARD;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
      LayoutInflater inflater = LayoutInflater.from(parent.getContext());
      if (viewType == TYPE_HEADER)
      {
        View v = inflater.inflate(R.layout.clipboard_section_header, parent, false);
        return new HeaderViewHolder(v);
      }
      else
      {
        View v = inflater.inflate(R.layout.clipboard_card_item, parent, false);
        return new CardViewHolder(v);
      }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position)
    {
      ClipItem item = _items.get(position);

      if (holder instanceof HeaderViewHolder)
      {
        HeaderViewHolder hh = (HeaderViewHolder) holder;
        hh.title.setText(item.headerTitle);
        StaggeredGridLayoutManager.LayoutParams lp =
            (StaggeredGridLayoutManager.LayoutParams) hh.itemView.getLayoutParams();
        if (lp != null)
        {
          lp.setFullSpan(true);
        }
      }
      else if (holder instanceof CardViewHolder)
      {
        CardViewHolder ch = (CardViewHolder) holder;
        ch.text.setText(item.text);

        if (item.isPinned)
        {
          ch.badge.setVisibility(View.VISIBLE);
          ch.badge.setText("PINNED");
        }
        else
        {
          ch.badge.setVisibility(View.GONE);
        }

        if (_isEditMode)
        {
          ch.indicator.setVisibility(View.VISIBLE);
          boolean selected = _selectedItems.contains(item.text);
          ch.indicator.setAlpha(selected ? 1.0f : 0.3f);
          ch.indicator.setImageResource(selected ? R.drawable.ic_check : R.drawable.ic_tool_select_all);
        }
        else
        {
          ch.indicator.setVisibility(View.GONE);
        }

        ch.container.setOnClickListener(v -> {
          if (_isEditMode)
          {
            if (_selectedItems.contains(item.text))
            {
              _selectedItems.remove(item.text);
            }
            else
            {
              _selectedItems.add(item.text);
            }
            notifyItemChanged(position);
          }
          else
          {
            // IMMEDIATELY PASTE!
            ClipboardHistoryService.paste(item.text);
          }
        });

        ch.container.setOnLongClickListener(v -> {
          if (!_isEditMode)
          {
            show_card_options(v, item);
            return true;
          }
          return false;
        });
      }
    }

    @Override
    public int getItemCount()
    {
      return _items.size();
    }
  }

  static class HeaderViewHolder extends RecyclerView.ViewHolder
  {
    final TextView title;

    HeaderViewHolder(@NonNull View itemView)
    {
      super(itemView);
      title = itemView.findViewById(R.id.clipboard_section_title);
    }
  }

  static class CardViewHolder extends RecyclerView.ViewHolder
  {
    final View container;
    final TextView badge;
    final TextView text;
    final ImageView indicator;

    CardViewHolder(@NonNull View itemView)
    {
      super(itemView);
      container = itemView.findViewById(R.id.card_container);
      badge = itemView.findViewById(R.id.card_badge);
      text = itemView.findViewById(R.id.card_text);
      indicator = itemView.findViewById(R.id.card_select_indicator);
    }
  }
}
