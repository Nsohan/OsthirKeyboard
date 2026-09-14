package com.nhs.customkeyboard.gif;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import com.nhs.customkeyboard.Config;
import com.nhs.customkeyboard.DialogUtils;
import com.nhs.customkeyboard.KeyValue;
import com.nhs.customkeyboard.Logs;
import com.nhs.customkeyboard.Utils;
import com.nhs.customkeyboard.Pointers;
import com.nhs.customkeyboard.R;
import com.nhs.customkeyboard.SettingsActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class GifManagerView extends LinearLayout
{
  private static final String PREF_RECENT_GIFS = "recent_gifs_history";
  private static final String[] CATEGORIES = new String[] {
      "Please", "Thank you", "Happy", "Sad", "Love", "Agree", "Applause", "Bye", "Dance", "Cat", "Dog", "Meme"
  };

  private RecyclerView _recyclerView;
  private ProgressBar _loadingSpinner;
  private View _emptyView;
  private TextView _emptyText;
  private Button _btnSettings;

  public interface OnSearchClickListener
  {
    void onSearchClick(String currentQuery);
  }
  private OnSearchClickListener _onSearchClickListener;

  public void setOnSearchClickListener(OnSearchClickListener listener)
  {
    _onSearchClickListener = listener;
  }

  private ImageButton _btnBack;
  private View _searchContainer;
  private TextView _searchInput;
  private ImageView _btnClearSearch;
  private ImageButton _btnRecents;
  private LinearLayout _chipsContainer;
  private ImageButton _btnMore;

  private TextView _btnAbc;
  private ImageButton _btnEmoji;
  private FrameLayout _btnGifTab;
  private ImageButton _btnSticker;
  private ImageButton _btnEmoticon;
  private ImageButton _btnBackspace;

  private GifAdapter _adapter;
  private final List<GifItem> _items = new ArrayList<>();
  private String _currentCategory = "Please";
  private final Handler _mainHandler = new Handler(Looper.getMainLooper());
  private int _targetHeight = 0;

  public GifManagerView(Context context, AttributeSet attrs)
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

    _recyclerView = findViewById(R.id.gif_recycler_view);
    _loadingSpinner = findViewById(R.id.gif_loading_spinner);
    _emptyView = findViewById(R.id.gif_empty_view);
    _emptyText = findViewById(R.id.gif_empty_text);
    _btnSettings = findViewById(R.id.gif_btn_settings);

    _btnBack = findViewById(R.id.gif_btn_back);
    _searchContainer = findViewById(R.id.gif_search_container);
    _searchInput = findViewById(R.id.gif_search_input);
    _btnClearSearch = findViewById(R.id.gif_search_clear);
    _btnRecents = findViewById(R.id.gif_btn_recents);
    _chipsContainer = findViewById(R.id.gif_chips_container);
    _btnMore = findViewById(R.id.gif_btn_more);

    _btnAbc = findViewById(R.id.gif_btn_abc);
    _btnEmoji = findViewById(R.id.gif_btn_emoji);
    _btnGifTab = findViewById(R.id.gif_btn_gif_tab);
    _btnSticker = findViewById(R.id.gif_btn_sticker);
    _btnEmoticon = findViewById(R.id.gif_btn_emoticon);
    _btnBackspace = findViewById(R.id.gif_btn_backspace);

    // Setup RecyclerView
    if (_recyclerView != null)
    {
      StaggeredGridLayoutManager lm =
          new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
      lm.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
      _recyclerView.setLayoutManager(lm);
      _adapter = new GifAdapter();
      _recyclerView.setAdapter(_adapter);
    }

    // Setup Top Bar Actions
    if (_btnBack != null)
    {
      _btnBack.setOnClickListener(v -> close_gif_pane());
    }

    if (_btnRecents != null)
    {
      _btnRecents.setOnClickListener(v -> load_recents());
    }

    if (_btnMore != null)
    {
      _btnMore.setOnClickListener(v -> show_more_options());
    }

    if (_btnSettings != null)
    {
      _btnSettings.setOnClickListener(v -> open_giphy_settings());
    }

    setup_search_input();
    setup_category_chips();
    setup_bottom_bar();

    // Initial load with default category "Please"
    load_category("Please");
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
    load_category(_currentCategory != null ? _currentCategory : "Please");
  }

  private void update_bottom_margin(int insetBottom)
  {
    View bottomBar = findViewById(R.id.gif_bottom_bar);
    if (bottomBar != null)
    {
      int basePad = (int) (10 * getResources().getDisplayMetrics().density);
      if (Config.globalConfig() != null && Config.globalConfig().margin_bottom > 0)
      {
        basePad = Math.max(basePad, (int) Config.globalConfig().margin_bottom);
      }
      bottomBar.setPadding(
          bottomBar.getPaddingStart(),
          bottomBar.getPaddingTop(),
          bottomBar.getPaddingEnd(),
          basePad + insetBottom);
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

  private void setup_search_input()
  {
    OnClickListener searchClick = v -> {
      if (_onSearchClickListener != null)
      {
        String q = (_searchInput != null && _searchInput.getText() != null)
            ? _searchInput.getText().toString().trim()
            : "";
        _onSearchClickListener.onSearchClick(q);
      }
    };

    if (_searchContainer != null)
    {
      _searchContainer.setOnClickListener(searchClick);
    }
    if (_searchInput != null)
    {
      _searchInput.setOnClickListener(searchClick);
    }

    if (_btnClearSearch != null)
    {
      _btnClearSearch.setOnClickListener(v -> {
        if (_searchInput != null) _searchInput.setText("");
        _btnClearSearch.setVisibility(View.GONE);
        load_category("Please");
      });
    }
  }

  private void setup_category_chips()
  {
    if (_chipsContainer == null) return;
    _chipsContainer.removeAllViews();

    int hPad = (int) (12 * getResources().getDisplayMetrics().density);
    int vPad = (int) (6 * getResources().getDisplayMetrics().density);
    int margin = (int) (4 * getResources().getDisplayMetrics().density);

    TypedValue outValue = new TypedValue();
    getContext().getTheme().resolveAttribute(R.attr.colorLabel, outValue, true);
    int labelColor = outValue.data;

    for (String cat : CATEGORIES)
    {
      TextView chip = new TextView(getContext());
      chip.setText(cat);
      chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
      chip.setPadding(hPad, vPad, hPad, vPad);
      chip.setGravity(Gravity.CENTER);

      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT,
          (int) (32 * getResources().getDisplayMetrics().density));
      lp.setMargins(margin, 0, margin, 0);
      chip.setLayoutParams(lp);

      chip.setClickable(true);
      chip.setFocusable(true);
      chip.setTag(cat);

      update_chip_style(chip, cat.equalsIgnoreCase(_currentCategory), labelColor);

      chip.setOnClickListener(v -> {
        if (_searchInput != null) _searchInput.setText("");
        load_category(cat);
      });

      _chipsContainer.addView(chip);
    }
  }

  private void update_chip_style(TextView chip, boolean active, int labelColor)
  {
    if (active)
    {
      chip.setBackgroundResource(R.drawable.bg_gif_pill_active);
      chip.setTextColor(Color.parseColor("#1A1A1A"));
      chip.setTypeface(null, android.graphics.Typeface.BOLD);
    }
    else
    {
      chip.setBackgroundResource(R.drawable.bg_gif_pill_inactive);
      chip.setTextColor(labelColor);
      chip.setTypeface(null, android.graphics.Typeface.NORMAL);
    }
  }

  private void refresh_chips_selection()
  {
    if (_chipsContainer == null) return;
    TypedValue outValue = new TypedValue();
    getContext().getTheme().resolveAttribute(R.attr.colorLabel, outValue, true);
    int labelColor = outValue.data;

    for (int i = 0; i < _chipsContainer.getChildCount(); i++)
    {
      View v = _chipsContainer.getChildAt(i);
      if (v instanceof TextView)
      {
        TextView chip = (TextView) v;
        String tag = (String) chip.getTag();
        boolean active = tag != null && tag.equalsIgnoreCase(_currentCategory);
        update_chip_style(chip, active, labelColor);
      }
    }
  }

  private void setup_bottom_bar()
  {
    if (_btnAbc != null)
    {
      _btnAbc.setOnClickListener(v -> close_gif_pane());
    }

    if (_btnEmoji != null)
    {
      _btnEmoji.setOnClickListener(v -> {
        if (Config.globalConfig() != null && Config.globalConfig().handler != null)
        {
          Config.globalConfig().handler.key_up(
              KeyValue.getKeyByName("switch_emoji"),
              Pointers.Modifiers.EMPTY);
        }
      });
    }

    if (_btnSticker != null)
    {
      _btnSticker.setOnClickListener(v -> {
        if (Config.globalConfig() != null && Config.globalConfig().handler != null)
        {
          Config.globalConfig().handler.key_up(
              KeyValue.getKeyByName("switch_emoji"),
              Pointers.Modifiers.EMPTY);
        }
      });
    }

    if (_btnEmoticon != null)
    {
      _btnEmoticon.setOnClickListener(v -> {
        if (Config.globalConfig() != null && Config.globalConfig().handler != null)
        {
          Config.globalConfig().handler.key_up(
              KeyValue.getKeyByName("switch_emoji"),
              Pointers.Modifiers.EMPTY);
        }
      });
    }

    if (_btnBackspace != null)
    {
      _btnBackspace.setOnClickListener(v -> {
        if (Config.globalConfig() != null && Config.globalConfig().handler != null)
        {
          Config.globalConfig().handler.key_up(
              KeyValue.getKeyByName("backspace"),
              Pointers.Modifiers.EMPTY);
        }
      });
    }
  }

  public void load_category(String category)
  {
    _currentCategory = category;
    refresh_chips_selection();
    if (_searchInput != null) _searchInput.setText("");
    if (_btnClearSearch != null) _btnClearSearch.setVisibility(View.GONE);
    set_loading(true);

    GiphyApiClient.searchGifs(getContext(), category, 30, new GiphyApiClient.Callback()
    {
      @Override
      public void onSuccess(List<GifItem> gifs)
      {
        set_loading(false);
        _items.clear();
        _items.addAll(gifs);
        if (_adapter != null) _adapter.notifyDataSetChanged();
        if (_recyclerView != null) _recyclerView.scrollToPosition(0);
        update_empty_view(gifs.isEmpty(), "No GIFs found for \"" + category + "\"");
      }

      @Override
      public void onError(String message)
      {
        set_loading(false);
        List<GifItem> fallbacks = GiphyApiClient.getFallbackGifs(category);
        _items.clear();
        _items.addAll(fallbacks);
        if (_adapter != null) _adapter.notifyDataSetChanged();
        update_empty_view(_items.isEmpty(), "Could not load GIFs. Check internet or API key.");
      }
    });
  }

  public void perform_search(String query)
  {
    _currentCategory = null;
    refresh_chips_selection();
    if (_searchInput != null) _searchInput.setText(query);
    if (_btnClearSearch != null)
    {
      _btnClearSearch.setVisibility(query != null && !query.isEmpty() ? View.VISIBLE : View.GONE);
    }
    set_loading(true);

    GiphyApiClient.searchGifs(getContext(), query, 30, new GiphyApiClient.Callback()
    {
      @Override
      public void onSuccess(List<GifItem> gifs)
      {
        set_loading(false);
        _items.clear();
        _items.addAll(gifs);
        if (_adapter != null) _adapter.notifyDataSetChanged();
        if (_recyclerView != null) _recyclerView.scrollToPosition(0);
        update_empty_view(gifs.isEmpty(), "No GIFs found for \"" + query + "\"");
      }

      @Override
      public void onError(String message)
      {
        set_loading(false);
        List<GifItem> fallbacks = GiphyApiClient.getFallbackGifs(query);
        _items.clear();
        _items.addAll(fallbacks);
        if (_adapter != null) _adapter.notifyDataSetChanged();
        update_empty_view(_items.isEmpty(), "No results. " + message);
      }
    });
  }

  private void load_recents()
  {
    _currentCategory = null;
    refresh_chips_selection();
    if (_searchInput != null) _searchInput.setText("");

    List<GifItem> recents = load_recent_gifs_from_prefs();
    _items.clear();
    _items.addAll(recents);
    if (_adapter != null) _adapter.notifyDataSetChanged();
    update_empty_view(recents.isEmpty(), "No recently used GIFs");
  }

  private void set_loading(boolean loading)
  {
    if (_loadingSpinner != null)
    {
      _loadingSpinner.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
  }

  private void update_empty_view(boolean empty, String message)
  {
    if (_emptyView != null)
    {
      _emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }
    if (_emptyText != null && message != null)
    {
      _emptyText.setText(message);
    }
  }

  private void close_gif_pane()
  {
    if (Config.globalConfig() != null && Config.globalConfig().handler != null)
    {
      Config.globalConfig().handler.key_up(
          KeyValue.getKeyByName("switch_back_gif"),
          Pointers.Modifiers.EMPTY);
    }
  }

  private void open_giphy_settings()
  {
    try
    {
      Intent intent = new Intent(getContext(), SettingsActivity.class);
      intent.putExtra(SettingsActivity.EXTRA_START_SCREEN, "screen_style_advanced");
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      getContext().startActivity(intent);
    }
    catch (Exception e)
    {
      Logs.exn("GifManagerView", e);
    }
  }

  private void show_more_options()
  {
    try
    {
      AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
      builder.setTitle("GIPHY GIF Keyboard");
      builder.setMessage("Powered by GIPHY (developers.giphy.com)\n\nYou can set your own GIPHY API key in Settings to enjoy unlimited GIF searches.");
      builder.setPositiveButton("GIPHY Website", (dialog, which) -> {
        try
        {
          Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://developers.giphy.com/"));
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          getContext().startActivity(intent);
        }
        catch (Exception ignored) {}
      });
      builder.setNeutralButton("Settings", (dialog, which) -> {
        open_giphy_settings();
      });
      builder.setNegativeButton("Close", null);

      AlertDialog dialog = builder.create();
      DialogUtils.apply_modern_style(dialog, getContext());
      Utils.show_dialog_on_ime(dialog, getWindowToken());
    }
    catch (Exception e)
    {
      Logs.exn("GifManagerView", e);
    }
  }

  /**
   * Commits the clicked GIF to the host app (WhatsApp, Telegram, Slack, Messages, etc.)
   * using InputConnectionCompat.commitContent. If not supported, copies URL to clipboard.
   */
  private void on_gif_selected(GifItem item)
  {
    save_to_recents(item);

    new Thread(() -> {
      File file = download_gif_to_cache(item);
      _mainHandler.post(() -> {
        if (file != null && file.exists())
        {
          commit_gif_file(item, file);
        }
        else
        {
          copy_gif_link_fallback(item);
        }
      });
    }).start();
  }

  private File download_gif_to_cache(GifItem item)
  {
    try
    {
      File cacheDir = new File(getContext().getCacheDir(), "gifs");
      if (!cacheDir.exists()) cacheDir.mkdirs();

      String fileName = (item.id != null ? item.id : "temp_gif_" + System.currentTimeMillis()) + ".gif";
      File file = new File(cacheDir, fileName);
      if (file.exists() && file.length() > 0)
      {
        return file;
      }

      String downloadUrl = item.contentUrl != null ? item.contentUrl : item.previewUrl;
      URL url = new URL(downloadUrl);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setConnectTimeout(6000);
      conn.setReadTimeout(8000);
      conn.connect();

      if (conn.getResponseCode() == 200)
      {
        InputStream in = conn.getInputStream();
        FileOutputStream out = new FileOutputStream(file);
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1)
        {
          out.write(buffer, 0, read);
        }
        out.flush();
        out.close();
        in.close();
        conn.disconnect();
        return file;
      }
    }
    catch (Exception e)
    {
      Logs.exn("GifManagerView", e);
    }
    return null;
  }

  private void commit_gif_file(GifItem item, File file)
  {
    try
    {
      Context ctx = getContext();
      Uri contentUri = FileProvider.getUriForFile(
          ctx,
          ctx.getPackageName() + ".fileprovider",
          file);

      InputConnection ic = null;
      EditorInfo ei = null;
      if (Config.globalConfig() != null && Config.globalConfig().handler != null)
      {
        ic = Config.globalConfig().handler.getCurrentInputConnection();
        ei = Config.globalConfig().handler.getCurrentInputEditorInfo();
      }

      if (ic != null && ei != null)
      {
        String[] supportedMimeTypes = EditorInfoCompat.getContentMimeTypes(ei);
        boolean supportsGif = false;
        if (supportedMimeTypes != null)
        {
          for (String mime : supportedMimeTypes)
          {
            if (ClipDescription.compareMimeTypes(mime, "image/gif")
                || ClipDescription.compareMimeTypes(mime, "image/*"))
            {
              supportsGif = true;
              break;
            }
          }
        }

        if (supportsGif)
        {
          InputContentInfoCompat contentInfo = new InputContentInfoCompat(
              contentUri,
              new ClipDescription(item.title, new String[] { "image/gif" }),
              Uri.parse(item.contentUrl != null ? item.contentUrl : item.previewUrl));

          int flags = 0;
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)
          {
            flags |= InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION;
          }

          boolean success = InputConnectionCompat.commitContent(ic, ei, contentInfo, flags, null);
          if (success)
          {
            Toast.makeText(ctx, "GIF inserted", Toast.LENGTH_SHORT).show();
            return;
          }
        }
      }
    }
    catch (Exception e)
    {
      Logs.exn("GifManagerView", e);
    }

    // Fallback: Copy link
    copy_gif_link_fallback(item);
  }

  private void copy_gif_link_fallback(GifItem item)
  {
    Context ctx = getContext();
    ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
    if (cm != null)
    {
      String link = item.contentUrl != null ? item.contentUrl : item.previewUrl;
      cm.setPrimaryClip(ClipData.newPlainText("GIF", link));
      Toast.makeText(ctx, "GIF link copied to clipboard", Toast.LENGTH_SHORT).show();
    }
  }

  private void save_to_recents(GifItem item)
  {
    try
    {
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
      String json = prefs.getString(PREF_RECENT_GIFS, "[]");
      JSONArray arr = new JSONArray(json);
      JSONArray newArr = new JSONArray();

      JSONObject cur = new JSONObject();
      cur.put("id", item.id);
      cur.put("title", item.title);
      cur.put("previewUrl", item.previewUrl);
      cur.put("contentUrl", item.contentUrl);
      cur.put("width", item.width);
      cur.put("height", item.height);
      newArr.put(cur);

      for (int i = 0; i < arr.length() && newArr.length() < 30; i++)
      {
        JSONObject obj = arr.getJSONObject(i);
        if (!obj.optString("id").equals(item.id))
        {
          newArr.put(obj);
        }
      }
      prefs.edit().putString(PREF_RECENT_GIFS, newArr.toString()).apply();
    }
    catch (Exception e)
    {
      Logs.exn("GifManagerView", e);
    }
  }

  private List<GifItem> load_recent_gifs_from_prefs()
  {
    List<GifItem> list = new ArrayList<>();
    try
    {
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
      String json = prefs.getString(PREF_RECENT_GIFS, "[]");
      JSONArray arr = new JSONArray(json);
      for (int i = 0; i < arr.length(); i++)
      {
        JSONObject obj = arr.getJSONObject(i);
        list.add(new GifItem(
            obj.optString("id"),
            obj.optString("title"),
            obj.optString("previewUrl"),
            obj.optString("contentUrl"),
            obj.optInt("width", 200),
            obj.optInt("height", 200)));
      }
    }
    catch (Exception e)
    {
      Logs.exn("GifManagerView", e);
    }
    return list;
  }

  // --- Adapter & ViewHolder ---

  private class GifAdapter extends RecyclerView.Adapter<GifViewHolder>
  {
    @NonNull
    @Override
    public GifViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
      View v = LayoutInflater.from(parent.getContext())
          .inflate(R.layout.gif_card_item, parent, false);
      return new GifViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull GifViewHolder holder, int position)
    {
      GifItem item = _items.get(position);
      holder.card.setOnClickListener(v -> on_gif_selected(item));

      Glide.with(holder.itemView.getContext())
          .asGif()
          .load(item.previewUrl)
          .diskCacheStrategy(DiskCacheStrategy.ALL)
          .placeholder(R.drawable.bg_gif_card)
          .into(holder.image);
    }

    @Override
    public int getItemCount()
    {
      return _items.size();
    }
  }

  static class GifViewHolder extends RecyclerView.ViewHolder
  {
    final CardView card;
    final ImageView image;
    final ProgressBar progress;

    GifViewHolder(@NonNull View itemView)
    {
      super(itemView);
      card = itemView.findViewById(R.id.gif_card);
      image = itemView.findViewById(R.id.gif_image);
      progress = itemView.findViewById(R.id.gif_progress);
    }
  }
}
