package com.nhs.customkeyboard;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.nhs.customkeyboard.prefs.KeymapManager;
import com.nhs.customkeyboard.prefs.LayoutsPreference;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.os.Build;
import android.view.ViewOutlineProvider;
import androidx.preference.PreferenceManager;

public class LayoutEditorActivity extends Activity
{
  public static final String EXTRA_INITIAL_XML = "initial_xml";
  public static final String EXTRA_LAYOUT_INDEX = "layout_index";
  public static final String EXTRA_LAYOUT_NAME = "layout_name";
  public static final String EXTRA_ALLOW_REMOVE = "allow_remove";

  private CustomLayoutEditDialog.LayoutEntryEditText _input_editor;
  private EditText _name_input;
  private ImageButton _name_clear_btn;
  private Spinner _keymap_spinner;
  private SwitchCompat _swipekeymap_switch;
  private FrameLayout _preview_holder;
  private Keyboard2View _keyboard_view;
  private TextView _visual_deck_row_info;
  private TextView _error_view;
  private TextView _file_name_label;

  private final List<String> _keymap_names = new ArrayList<>();
  private boolean _syncing = false;
  private int _layout_index = -1;
  private boolean _allow_remove = false;
  private final Handler _deckHandler = new Handler(Looper.getMainLooper());
  private Runnable _deckUpdateRunnable = null;
  private CustomLayoutEditDialog.MaxHeightScrollView _code_scroll;
  private boolean _isSelectingFromPreview = false;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_layout_editor);

    Config config = Config.globalConfig();
    if (config == null)
    {
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
      Config.initGlobalConfig(prefs, getResources(), false, com.nhs.customkeyboard.dict.Dictionaries.instance(this));
      config = Config.globalConfig();
    }
    LayoutModifier.init(config, getResources());

    Intent intent = getIntent();
    String initial_xml = intent != null ? intent.getStringExtra(EXTRA_INITIAL_XML) : null;
    if (initial_xml == null || initial_xml.isEmpty())
      initial_xml = LayoutsPreference.read_builtin_layout_xml(this, "latn_qwerty_us_custom");

    _layout_index = intent != null ? intent.getIntExtra(EXTRA_LAYOUT_INDEX, -1) : -1;
    _allow_remove = intent != null && intent.getBooleanExtra(EXTRA_ALLOW_REMOVE, false);
    String layout_name = intent != null ? intent.getStringExtra(EXTRA_LAYOUT_NAME) : null;

    init_views(initial_xml, layout_name);
  }

  private void init_views(String initial_xml, String layout_name)
  {
    MaterialToolbar toolbar = findViewById(R.id.layout_editor_toolbar);
    toolbar.setNavigationOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) { finish(); }
    });

    if (_layout_index >= 0)
      toolbar.setSubtitle("Active Slot: Layout " + (_layout_index + 1));
    else
      toolbar.setSubtitle("New Custom Layout");

    _name_input = findViewById(R.id.layout_editor_name_input);
    _name_clear_btn = findViewById(R.id.layout_editor_name_clear_btn);
    _keymap_spinner = findViewById(R.id.layout_editor_keymap_spinner);
    _swipekeymap_switch = findViewById(R.id.layout_editor_swipekeymap_switch);
    _preview_holder = findViewById(R.id.visual_deck_preview_holder);
    _visual_deck_row_info = findViewById(R.id.visual_deck_row_info);
    _error_view = findViewById(R.id.layout_editor_error_view);
    _file_name_label = findViewById(R.id.layout_file_name_label);

    // Create real native Keyboard2View with active keyboard theme
    Config config = Config.globalConfig();
    if (config == null)
    {
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
      Config.initGlobalConfig(prefs, getResources(), false, com.nhs.customkeyboard.dict.Dictionaries.instance(this));
      config = Config.globalConfig();
    }
    int themeResId = (config != null && config.theme != 0) ? config.theme : R.style.Light;
    Context themeContext = new android.view.ContextThemeWrapper(this, themeResId);
    _keyboard_view = new Keyboard2View(themeContext);
    _keyboard_view.setPreviewMode(true);

    boolean isCustomTheme = (config != null && config.themeName != null && config.themeName.startsWith("custom_"));
    if (isCustomTheme && config.customThemeImagePath != null)
    {
      try
      {
        Bitmap bmp = BitmapFactory.decodeFile(config.customThemeImagePath);
        if (bmp != null)
        {
          _keyboard_view.setCustomBackgroundBitmap(bmp, config.customThemeDarkness);
        }
      }
      catch (Throwable ignored) {}
    }

    FrameLayout cardDeck = findViewById(R.id.card_visual_deck);
    if (cardDeck != null)
    {
      int cardBgColor;
      if (isCustomTheme)
      {
        cardBgColor = 0xFF1E1F22;
      }
      else
      {
        android.content.res.TypedArray a = themeContext.getTheme().obtainStyledAttributes(new int[]{ R.attr.colorKeyboard });
        int colorKeyboard = a.getColor(0, Color.TRANSPARENT);
        a.recycle();
        cardBgColor = (colorKeyboard != Color.TRANSPARENT) ? colorKeyboard : ContextCompat.getColor(this, R.color.settings_card_bg);
      }

      GradientDrawable deckBg = new GradientDrawable();
      deckBg.setCornerRadius(dp(16));
      deckBg.setColor(cardBgColor);
      deckBg.setStroke(dp(1), ContextCompat.getColor(this, R.color.settings_divider));
      cardDeck.setBackground(deckBg);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
      {
        cardDeck.setOutlineProvider(new ViewOutlineProvider()
        {
          @Override
          public void getOutline(View view, Outline outline)
          {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
          }
        });
        cardDeck.setClipToOutline(true);
      }
    }

    _preview_holder.addView(_keyboard_view, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

    // Setup LayoutEntryEditText inside editor container
    LinearLayout input_wrapper = findViewById(R.id.layout_editor_input_wrapper);
    _input_editor = new CustomLayoutEditDialog.LayoutEntryEditText(this);
    _input_editor.setText(initial_xml);
    _input_editor.setBackgroundColor(Color.TRANSPARENT);
    _input_editor.setGutterBackgroundColor(ContextCompat.getColor(this, R.color.settings_background));

    int textColor = ContextCompat.getColor(this, R.color.settings_on_surface);
    _input_editor.setTextColor(textColor);
    _input_editor.setTextSize(13.5f);

    _code_scroll = new CustomLayoutEditDialog.MaxHeightScrollView(this);
    _code_scroll.set_max_height(dp(360));
    _code_scroll.addView(_input_editor, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
    input_wrapper.addView(_code_scroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    _keyboard_view.setOnKeyClickListener(new Keyboard2View.OnKeyClickListener() {
      @Override
      public void onKeyClick(KeyboardData.Key key) {
        highlight_key_in_editor(key);
      }
    });

    _input_editor.setOnLineSelectListener(new CustomLayoutEditDialog.LayoutEntryEditText.OnLineSelectListener() {
      @Override
      public void onLineSelected(int lineIndex) {
        if (_isSelectingFromPreview) return;
        highlight_key_for_line(lineIndex);
      }
    });

    // Setup keymaps spinner
    _keymap_names.clear();
    _keymap_names.add(getString(R.string.layout_keymap_none));
    for (KeymapManager.StoredKeymap k : KeymapManager.load(this))
      _keymap_names.add(k.name);

    final int onSurfaceColor = ContextCompat.getColor(this, R.color.settings_on_surface);
    final int cardBgColor = ContextCompat.getColor(this, R.color.settings_card_bg);

    ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(this,
            android.R.layout.simple_spinner_item, _keymap_names) {
      @Override
      public View getView(int position, View convertView, ViewGroup parent) {
        View v = super.getView(position, convertView, parent);
        if (v instanceof TextView) {
          ((TextView) v).setTextColor(onSurfaceColor);
          ((TextView) v).setTextSize(13.5f);
        }
        return v;
      }
      @Override
      public View getDropDownView(int position, View convertView, ViewGroup parent) {
        View v = super.getDropDownView(position, convertView, parent);
        if (v instanceof TextView) {
          ((TextView) v).setTextColor(onSurfaceColor);
          ((TextView) v).setBackgroundColor(cardBgColor);
          ((TextView) v).setTextSize(14f);
          ((TextView) v).setPadding(dp(12), dp(10), dp(12), dp(10));
        }
        return v;
      }
    };
    spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    _keymap_spinner.setAdapter(spinnerAdapter);

    // Initial sync
    _syncing = true;
    String xmlName = KeymapXmlAttrUtils.get_name_attr(initial_xml);
    _name_input.setText(xmlName != null ? xmlName : (layout_name != null ? layout_name : ""));
    update_file_label(_name_input.getText().toString());

    String curKeymap = KeymapXmlAttrUtils.get_keymap_attr(initial_xml);
    int initialIdx = (curKeymap != null) ? Math.max(0, _keymap_names.indexOf(curKeymap)) : 0;
    _keymap_spinner.setSelection(initialIdx);
    _swipekeymap_switch.setChecked(KeymapXmlAttrUtils.get_swipekeymap_attr(initial_xml));
    _swipekeymap_switch.setEnabled(initialIdx != 0);
    _syncing = false;

    // Listeners for top parameters
    _name_input.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
      public void onTextChanged(CharSequence s, int a, int b, int c) {}
      public void afterTextChanged(Editable s) {
        _name_clear_btn.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
        update_file_label(s.toString());
        if (_syncing) return;
        _syncing = true;
        String cur = _input_editor.getText().toString();
        String updated = KeymapXmlAttrUtils.set_name_attr(cur, s.toString());
        int cursor = _input_editor.getSelectionStart();
        _input_editor.setText(updated);
        _input_editor.setSelection(Math.min(cursor, updated.length()));
        _syncing = false;
        validate_and_refresh_deck(updated);
      }
    });

    _name_clear_btn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) { _name_input.setText(""); }
    });

    _keymap_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (_syncing) return;
        _syncing = true;
        String cur = _input_editor.getText().toString();
        String updated;
        if (position == 0) {
          _swipekeymap_switch.setChecked(false);
          _swipekeymap_switch.setEnabled(false);
          updated = KeymapXmlAttrUtils.remove_keymap_attrs(cur);
        } else {
          _swipekeymap_switch.setEnabled(true);
          String t = KeymapXmlAttrUtils.set_keymap_attr(cur, _keymap_names.get(position));
          updated = KeymapXmlAttrUtils.set_swipekeymap_attr(t, _swipekeymap_switch.isChecked());
        }
        _input_editor.setText(updated);
        _input_editor.setSelection(updated.length());
        _syncing = false;
        validate_and_refresh_deck(updated);
      }
      @Override
      public void onNothingSelected(AdapterView<?> parent) {}
    });

    _swipekeymap_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (_syncing) return;
        _syncing = true;
        String cur = _input_editor.getText().toString();
        String updated = KeymapXmlAttrUtils.set_swipekeymap_attr(cur, isChecked);
        _input_editor.setText(updated);
        _input_editor.setSelection(updated.length());
        _syncing = false;
        validate_and_refresh_deck(updated);
      }
    });

    // Quick insertion chips
    setup_quick_chips();

    // Editor text change watcher
    _input_editor.set_on_text_change(new CustomLayoutEditDialog.LayoutEntryEditText.OnChangeListener() {
      public void on_change() {
        String text = _input_editor.getText().toString();
        if (!_syncing) {
          _syncing = true;
          String n = KeymapXmlAttrUtils.get_name_attr(text);
          if (n != null && !_name_input.getText().toString().equals(n))
            _name_input.setText(n);
          String km = KeymapXmlAttrUtils.get_keymap_attr(text);
          int idx = (km != null) ? Math.max(0, _keymap_names.indexOf(km)) : 0;
          _keymap_spinner.setSelection(idx);
          _swipekeymap_switch.setChecked(KeymapXmlAttrUtils.get_swipekeymap_attr(text));
          _swipekeymap_switch.setEnabled(idx != 0);
          _syncing = false;
        }
        schedule_deck_update(text);
        Layout l = _input_editor.getLayout();
        if (l != null && _input_editor.getText() != null) {
          int curOffset = _input_editor.getSelectionStart();
          if (curOffset >= 0 && curOffset <= _input_editor.getText().length()) {
            int curLine = l.getLineForOffset(curOffset);
            if (curLine >= 0 && curLine < l.getLineCount()) {
              int start = l.getLineStart(curLine);
              int end = l.getLineEnd(curLine);
              CharSequence s = _input_editor.getText();
              while (end > start && (s.charAt(end - 1) == '\n' || s.charAt(end - 1) == '\r'))
                end--;
              update_quick_chips_highlight(s.subSequence(start, end).toString());
            }
          }
        }
      }
    });

    // Copy and format buttons
    Button btn_copy = findViewById(R.id.layout_editor_btn_copy);
    btn_copy.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
          cm.setPrimaryClip(ClipData.newPlainText("layout_xml", _input_editor.getText().toString()));
          Toast.makeText(LayoutEditorActivity.this, "Layout XML copied to clipboard", Toast.LENGTH_SHORT).show();
        }
      }
    });

    Button btn_format = findViewById(R.id.layout_editor_btn_format);
    btn_format.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) { format_xml_code(); }
    });

    // Save button
    MaterialButton btn_save = findViewById(R.id.layout_editor_btn_save);
    btn_save.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) { save_and_exit(); }
    });

    MaterialButton btn_remove = findViewById(R.id.layout_editor_btn_remove);
    if (_allow_remove) {
      btn_remove.setVisibility(View.VISIBLE);
      btn_remove.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) { remove_and_exit(); }
      });
    }

    // Render initial live deck
    validate_and_refresh_deck(initial_xml);
  }

  private void update_file_label(String name)
  {
    if (name == null || name.trim().isEmpty())
      _file_name_label.setText("layout.xml");
    else {
      String clean = name.trim().toLowerCase().replaceAll("[^a-z0-9_]+", "_");
      _file_name_label.setText(clean + "_layout.xml");
    }
  }

  private static class QuickChip {
    final Button button;
    final String attrName;
    final String insertText;
    final String defaultLabel;
    final String labelPrefix;
    final String desc;
    QuickChip(Button b, String attr, String insertText, String defaultLabel, String labelPrefix, String desc) {
      this.button = b;
      this.attrName = attr;
      this.insertText = insertText;
      this.defaultLabel = defaultLabel;
      this.labelPrefix = labelPrefix;
      this.desc = desc;
    }
  }
  private final List<QuickChip> _quickChips = new ArrayList<QuickChip>();

  private void setup_quick_chips()
  {
    LinearLayout chipsBar = findViewById(R.id.layout_editor_quick_chips_bar);
    chipsBar.removeAllViews();
    _quickChips.clear();

    final String[][] chips = new String[][]{
            {"⊙ c=\"\"", "c=\"\"", "c : Center primary character"},
            {"↖ nw=\"\"", "nw=\"\"", "nw : North-West (Top-Left swipe / hint)"},
            {"↗ ne=\"\"", "ne=\"\"", "ne : North-East (Top-Right swipe / hint)"},
            {"↙ sw=\"\"", "sw=\"\"", "sw : South-West (Bottom-Left swipe / hint)"},
            {"↘ se=\"\"", "se=\"\"", "se : South-East (Bottom-Right swipe / hint)"},
            {"↑ n=\"\"", "n=\"\"", "n : North (Top swipe / hint)"},
            {"↓ s=\"\"", "s=\"\"", "s : South (Bottom swipe / hint)"},
            {"← w=\"\"", "w=\"\"", "w : West (Left swipe)"},
            {"→ e=\"\"", "e=\"\"", "e : East (Right swipe)"},
            {"⌖ lp=\"\"", "lp=\"\"", "lp : Long-press output character"},
            {"⊙ cL=\"\"", "cL=\"\"", "cL : Center popup candidates list"},
            {"↖ nwL=\"\"", "nwL=\"\"", "nwL : North-West popup candidates list"},
            {"↗ neL=\"\"", "neL=\"\"", "neL : North-East popup candidates list"},
            {"↙ swL=\"\"", "swL=\"\"", "swL : South-West popup candidates list"},
            {"↘ seL=\"\"", "seL=\"\"", "seL : South-East popup candidates list"},
            {"⎵ C=\"\"", "C=\"\"", "C : Shifted / Uppercase character"},
            {"<key />", "<key c=\"\" />", "Insert new <key /> tag"},
            {"<row>", "<row>", "Open <row> tag"},
            {"</row>", "</row>", "Close </row> tag"}
    };

    int primaryColor = ContextCompat.getColor(this, R.color.settings_primary);
    int normalBgColor = ContextCompat.getColor(this, R.color.settings_background);
    int dividerColor = ContextCompat.getColor(this, R.color.settings_divider);

    for (final String[] chip : chips)
    {
      final String label = chip[0];
      final String insertText = chip[1];
      final String desc = chip[2];

      String attr = "";
      if (insertText.contains("=")) {
        attr = insertText.substring(0, insertText.indexOf("=")).trim();
      } else if (insertText.startsWith("<") || insertText.startsWith("/")) {
        attr = insertText.trim();
      }

      String prefix = null;
      int quoteIdx = label.indexOf("\"\"");
      if (quoteIdx >= 0) {
        prefix = label.substring(0, quoteIdx + 1);
      }
      final String finalAttr = attr;
      final String finalPrefix = prefix;

      Button b = new Button(this);
      b.setText(label);
      b.setTextSize(11.5f);
      b.setTextColor(primaryColor);

      GradientDrawable gd = new GradientDrawable();
      gd.setColor(normalBgColor);
      gd.setCornerRadius(dp(8));
      gd.setStroke(dp(1), dividerColor);
      b.setBackground(gd);

      b.setPadding(dp(6), dp(1), dp(6), dp(1));
      b.setMinHeight(0);
      b.setMinimumHeight(0);
      b.setMinWidth(0);
      b.setMinimumWidth(0);
      b.setAllCaps(false);

      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
        b.setTooltipText(desc);

      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.WRAP_CONTENT, dp(30));
      lp.setMargins(0, 0, dp(5), 0);
      b.setLayoutParams(lp);

      b.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          Editable editable = _input_editor.getText();
          if (editable == null) return;
          Layout l = _input_editor.getLayout();
          int curOffset = _input_editor.getSelectionStart();
          int curLine = (l != null && curOffset >= 0) ? l.getLineForOffset(curOffset) : -1;

          if (finalAttr != null && !finalAttr.isEmpty() && !finalAttr.startsWith("<") && !finalAttr.startsWith("/") && l != null && curLine >= 0) {
            int lineStart = l.getLineStart(curLine);
            int lineEnd = l.getLineEnd(curLine);
            String lineStr = editable.subSequence(lineStart, lineEnd).toString();
            if (lineStr.contains("<key")) {
              Pattern p = Pattern.compile("(?:\\s|^)" + Pattern.quote(finalAttr) + "\\s*=\\s*([\"'])(.*?)\\1");
              Matcher m = p.matcher(lineStr);
              if (m.find()) {
                int valStart = lineStart + m.start(2);
                int valEnd = lineStart + m.end(2);
                _input_editor.requestFocus();
                _input_editor.setSelection(valStart, valEnd);
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null)
                  imm.showSoftInput(_input_editor, InputMethodManager.SHOW_IMPLICIT);
                return;
              } else {
                int closeIdx = lineStr.lastIndexOf("/>");
                if (closeIdx < 0) closeIdx = lineStr.lastIndexOf(">");
                if (closeIdx >= 0) {
                  int insertPos = lineStart + closeIdx;
                  String insertStr = " " + finalAttr + "=\"\"";
                  editable.insert(insertPos, insertStr);
                  int quotePos = insertPos + insertStr.indexOf("\"\"") + 1;
                  _input_editor.requestFocus();
                  _input_editor.setSelection(quotePos);
                  highlight_key_for_line(curLine);
                  InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                  if (imm != null)
                    imm.showSoftInput(_input_editor, InputMethodManager.SHOW_IMPLICIT);
                  return;
                }
              }
            }
          }

          int start = _input_editor.getSelectionStart();
          int end = _input_editor.getSelectionEnd();
          if (start >= 0 && end >= 0) {
            editable.replace(Math.min(start, end), Math.max(start, end), insertText);
            int qIdx = insertText.indexOf("\"\"");
            if (qIdx >= 0)
              _input_editor.setSelection(Math.min(start, end) + qIdx + 1);
            else
              _input_editor.setSelection(Math.min(start, end) + insertText.length());

            if (l != null) {
              int line = l.getLineForOffset(_input_editor.getSelectionStart());
              highlight_key_for_line(line);
            }
          }
        }
      });

      b.setOnLongClickListener(new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
          Toast toast = Toast.makeText(LayoutEditorActivity.this, desc, Toast.LENGTH_SHORT);
          int[] pos = new int[2];
          v.getLocationOnScreen(pos);
          toast.setGravity(Gravity.TOP | Gravity.START, Math.max(dp(8), pos[0] - dp(16)), Math.max(0, pos[1] - dp(45)));
          toast.show();
          return true;
        }
      });

      chipsBar.addView(b);
      _quickChips.add(new QuickChip(b, finalAttr, insertText, label, finalPrefix, desc));
    }
  }

  private void update_quick_chips_highlight(String lineText)
  {
    int normalTextColor = ContextCompat.getColor(this, R.color.settings_primary);
    int normalBgColor = ContextCompat.getColor(this, R.color.settings_background);
    int dividerColor = ContextCompat.getColor(this, R.color.settings_divider);
    int activeBgColor = Color.rgb(56, 189, 248); // Glowing cyan
    int activeTextColor = Color.rgb(4, 43, 89);  // Contrast deep navy

    for (QuickChip chip : _quickChips)
    {
      boolean isActive = false;
      String val = null;

      if (lineText != null && chip.attrName != null && !chip.attrName.isEmpty())
      {
        if (chip.attrName.startsWith("<") || chip.attrName.startsWith("/"))
        {
          isActive = lineText.contains(chip.attrName);
        }
        else
        {
          Pattern pattern = Pattern.compile("(?:\\s|^)" + Pattern.quote(chip.attrName) + "\\s*=\\s*([\"'])(.*?)\\1");
          Matcher m = pattern.matcher(lineText);
          if (m.find())
          {
            isActive = true;
            val = m.group(2);
          }
        }
      }

      if (isActive && val != null && chip.labelPrefix != null)
      {
        String displayVal = (val.length() > 10) ? (val.substring(0, 8) + "…") : val;
        chip.button.setText(chip.labelPrefix + displayVal + "\"");
      }
      else
      {
        chip.button.setText(chip.defaultLabel);
      }

      GradientDrawable gd = new GradientDrawable();
      gd.setCornerRadius(dp(8));
      if (isActive)
      {
        gd.setColor(activeBgColor);
        gd.setStroke(dp(1.2f), Color.rgb(224, 242, 254));
        chip.button.setBackground(gd);
        chip.button.setTextColor(activeTextColor);
        chip.button.setTypeface(null, Typeface.BOLD);
      }
      else
      {
        gd.setColor(normalBgColor);
        gd.setStroke(dp(1), dividerColor);
        chip.button.setBackground(gd);
        chip.button.setTextColor(normalTextColor);
        chip.button.setTypeface(null, Typeface.NORMAL);
      }
    }
  }

  private void schedule_deck_update(final String xml)
  {
    if (_deckUpdateRunnable != null)
      _deckHandler.removeCallbacks(_deckUpdateRunnable);
    _deckUpdateRunnable = new Runnable() {
      @Override
      public void run() { validate_and_refresh_deck(xml); }
    };
    _deckHandler.postDelayed(_deckUpdateRunnable, 80);
  }

  private void validate_and_refresh_deck(String xml)
  {
    try
    {
      KeyboardData kd = KeyboardData.load_string_exn(xml);
      _error_view.setVisibility(View.GONE);
      KeyboardData full_kd = LayoutModifier.modify_layout(kd, false, false);
      _keyboard_view.setKeyboard(full_kd);
      _visual_deck_row_info.setText(full_kd.rows.size() + " Rows • Live");
    }
    catch (Exception e)
    {
      _error_view.setText("XML Error: " + e.getMessage());
      _error_view.setVisibility(View.VISIBLE);
    }
  }

  private void format_xml_code()
  {
    try
    {
      String input = _input_editor.getText().toString();
      Source xmlInput = new StreamSource(new StringReader(input));
      StringWriter stringWriter = new StringWriter();
      StreamResult xmlOutput = new StreamResult(stringWriter);
      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      Transformer transformer = transformerFactory.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
      transformer.transform(xmlInput, xmlOutput);
      String formatted = stringWriter.toString().trim();
      _input_editor.setText(formatted);
      Toast.makeText(this, "XML formatted", Toast.LENGTH_SHORT).show();
    }
    catch (Exception e)
    {
      Toast.makeText(this, "Could not format: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }
  }

  private void save_and_exit()
  {
    String xml = _input_editor.getText().toString();
    try
    {
      KeyboardData.load_string_exn(xml);
    }
    catch (Exception e)
    {
      Toast.makeText(this, "Cannot save invalid XML: " + e.getMessage(), Toast.LENGTH_LONG).show();
      return;
    }

    if (_layout_index >= 0)
      LayoutsPreference.save_custom_layout_at_index(this, _layout_index, xml);
    else
      LayoutsPreference.add_custom_layout_to_preferences(this, xml);

    Toast.makeText(this, "Layout saved successfully", Toast.LENGTH_SHORT).show();
    setResult(RESULT_OK);
    finish();
  }

  private void remove_and_exit()
  {
    if (_layout_index >= 0)
      LayoutsPreference.remove_layout_at_index(this, _layout_index);

    Toast.makeText(this, "Layout removed", Toast.LENGTH_SHORT).show();
    setResult(RESULT_OK);
    finish();
  }

  private void highlight_key_in_editor(final KeyboardData.Key key)
  {
    if (key == null) return;

    final Editable text = _input_editor.getText();
    if (text == null) return;
    final Layout layout = _input_editor.getLayout();
    final int lineCount = _input_editor.getLineCount();

    int targetLine = key.sourceLineNumber;
    if (targetLine < 0 || targetLine >= lineCount || !is_key_on_line(text, layout, targetLine, key))
    {
      targetLine = find_line_for_key(text, layout, lineCount, key);
    }

    if (targetLine >= 0 && targetLine < lineCount && layout != null)
    {
      _isSelectingFromPreview = true;
      int start = layout.getLineStart(targetLine);
      int end = layout.getLineEnd(targetLine);

      while (end > start && (text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r'))
        end--;

      _input_editor.setHighlightedLine(targetLine);
      _input_editor.setSelection(start);
      update_quick_chips_highlight(text.subSequence(start, end).toString());
      _input_editor.post(new Runnable() {
        @Override
        public void run() { _input_editor.scrollTo(0, _input_editor.getScrollY()); }
      });

      if (_code_scroll != null)
      {
        int lineTop = layout.getLineTop(targetLine);
        int scrollH = _code_scroll.getHeight();
        if (scrollH <= 0) scrollH = dp(360);
        int targetScrollY = Math.max(0, lineTop - (scrollH / 3));
        _code_scroll.smoothScrollTo(0, targetScrollY);
      }

      InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      if (imm != null)
        imm.hideSoftInputFromWindow(_input_editor.getWindowToken(), 0);

      _deckHandler.postDelayed(new Runnable() {
        @Override
        public void run() { _isSelectingFromPreview = false; }
      }, 150);
    }
    else if (key.sourceLineNumber < 0)
    {
      String label = (key.keys != null && key.keys[0] != null) ? key.keys[0].getString() : null;
      if (label != null)
        Toast.makeText(this, "Key '" + label + "' is provided by modifier/bottom row", Toast.LENGTH_SHORT).show();
    }
  }

  private boolean is_key_on_line(CharSequence text, Layout layout, int line, KeyboardData.Key key)
  {
    if (layout == null || line < 0 || line >= layout.getLineCount()) return false;
    int start = layout.getLineStart(line);
    int end = layout.getLineEnd(line);
    String lineStr = text.subSequence(start, end).toString().trim();
    if (!lineStr.contains("<key")) return false;
    if (key.keys != null && key.keys[0] != null)
    {
      String mainChar = key.keys[0].getString();
      if (mainChar != null && !mainChar.isEmpty())
      {
        return lineStr.contains("\"" + mainChar + "\"");
      }
    }
    return true;
  }

  private int find_line_for_key(CharSequence text, Layout layout, int lineCount, KeyboardData.Key key)
  {
    if (key == null || layout == null) return -1;
    String mainChar = (key.keys != null && key.keys[0] != null) ? key.keys[0].getString() : null;
    int bestLine = -1;
    int minDistance = Integer.MAX_VALUE;
    int preferredLine = key.sourceLineNumber >= 0 ? key.sourceLineNumber : 0;

    for (int l = 0; l < lineCount; l++)
    {
      int start = layout.getLineStart(l);
      int end = layout.getLineEnd(l);
      String lineStr = text.subSequence(start, end).toString();
      if (!lineStr.contains("<key")) continue;
      if (mainChar != null && !mainChar.isEmpty())
      {
        if (lineStr.contains("\"" + mainChar + "\""))
        {
          int dist = Math.abs(l - preferredLine);
          if (dist < minDistance)
          {
            minDistance = dist;
            bestLine = l;
          }
        }
      }
    }
    return bestLine;
  }

  private void highlight_key_for_line(int lineIndex)
  {
    _input_editor.setHighlightedLine(lineIndex);
    Layout layout = _input_editor.getLayout();
    Editable text = _input_editor.getText();
    if (layout != null && text != null && lineIndex >= 0 && lineIndex < layout.getLineCount())
    {
      int start = layout.getLineStart(lineIndex);
      int end = layout.getLineEnd(lineIndex);
      while (end > start && (text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r'))
        end--;
      update_quick_chips_highlight(text.subSequence(start, end).toString());
    }
    else
    {
      update_quick_chips_highlight(null);
    }

    if (_keyboard_view == null) return;
    KeyboardData kd = _keyboard_view.getKeyboard();
    if (kd == null || kd.rows == null) return;
    KeyboardData.Key match = null;
    for (KeyboardData.Row r : kd.rows)
    {
      for (KeyboardData.Key k : r.keys)
      {
        if (k.sourceLineNumber == lineIndex)
        {
          match = k;
          break;
        }
      }
      if (match != null) break;
    }
    _keyboard_view.setHighlightedKey(match);
  }

  private int dp(int val)
  {
    return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics());
  }

  private int dp(float val)
  {
    return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics());
  }
}
