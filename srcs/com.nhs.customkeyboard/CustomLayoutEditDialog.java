package com.nhs.customkeyboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.text.InputType;
import android.text.Layout;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;

import com.nhs.customkeyboard.prefs.KeymapManager;

import java.util.ArrayList;
import java.util.List;

public class CustomLayoutEditDialog
{
  public interface OpenInBuilder
  {
    void open(String current_text);
  }

  public static void show(Context ctx, String initial_text,
                          boolean allow_remove, final Callback callback)
  {
    show(ctx, initial_text, allow_remove,
            R.string.pref_custom_layout_title,
            R.string.pref_layouts_remove_custom,
            callback);
  }

  public static void show(Context ctx, String initial_text,
                          boolean allow_remove, int title_res, int remove_label_res,
                          final Callback callback)
  {
    show(ctx, initial_text, allow_remove, title_res, remove_label_res, null, callback);
  }

  /** Used by KeymapEditDialog. No keymap-selector row. */
  public static void show(Context ctx, String initial_text,
                          boolean allow_remove, int title_res, int remove_label_res,
                          final OpenInBuilder on_open_in_builder,
                          final Callback callback)
  {
    show(ctx, initial_text, allow_remove, title_res, remove_label_res, false, on_open_in_builder, callback);
  }

  /** Full version. [show_keymap_selector], if true, adds a Spinner
   (listing every saved keymap plus "(No keymap)") and a "Swipe"
   checkbox above the input box - used only for the Layout dialog
   (LayoutsPreference.select_custom()), never for the Keymap JSON
   dialog. Picking a keymap rewrites the "keymap"/"swipekeymap"
   attributes on the input's <keyboard> tag; conversely, manually
   typing those attributes updates the Spinner/checkbox to match -
   both directions are guarded against re-entrant loops.

   [on_open_in_builder], if non-null, adds a "Keymap Builder" button
   below the input box, with an inline red error row next to it
   (never both true - Layout and Keymap dialogs are mutually
   exclusive uses of this method). */
  public static void show(Context ctx, String initial_text,
                          boolean allow_remove, int title_res, int remove_label_res,
                          final boolean show_keymap_selector,
                          final OpenInBuilder on_open_in_builder,
                          final Callback callback)
  {
    final LayoutEntryEditText input = new LayoutEntryEditText(ctx);
    input.setText(initial_text);

    MaxHeightScrollView input_scroll = new MaxHeightScrollView(ctx);
    input_scroll.set_max_height(dp(ctx, 320));
    input_scroll.addView(input, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT,
            ScrollView.LayoutParams.WRAP_CONTENT));

    LinearLayout container = new LinearLayout(ctx);
    container.setOrientation(LinearLayout.VERTICAL);

    final TextView error_view = new TextView(ctx);
    error_view.setTextColor(Color.rgb(200, 40, 40));
    error_view.setTextSize(12f);
    error_view.setVisibility(View.GONE);

    final boolean[] syncing = { false };
    final Spinner[] spinner_holder = { null };
    final CheckBox[] swipe_cb_holder = { null };
    final List<String> keymap_names = new ArrayList<>();
    final EditText[] name_input_holder = { null };



    if (show_keymap_selector)
    {
      keymap_names.add(ctx.getString(R.string.layout_keymap_none));
      for (KeymapManager.StoredKeymap k : KeymapManager.load(ctx))
        keymap_names.add(k.name);

      final boolean isDark = is_dark_theme(ctx);

      // "Keyboard Attributes" card: bordered container with three
      // labeled rows (Name / Keymap / Swipekeymap), each a TextView
      // label on the left and its control on the right, matching the
      // visual style used elsewhere (rounded corners, light border).
      LinearLayout attrs_card = new LinearLayout(ctx);
      attrs_card.setOrientation(LinearLayout.VERTICAL);
      int card_pad = dp(ctx, 10);
      attrs_card.setPadding(card_pad, card_pad, card_pad, card_pad);
      GradientDrawable card_bg = new GradientDrawable();
      card_bg.setShape(GradientDrawable.RECTANGLE);
      int card_fill = isDark ? Color.rgb(33, 37, 43) : Color.rgb(248, 250, 252);
      int card_stroke = isDark ? Color.rgb(65, 72, 83) : Color.rgb(224, 228, 233);
      card_bg.setColor(card_fill);
      card_bg.setCornerRadius(dp(ctx, 10));
      card_bg.setStroke(dp(ctx, 1), card_stroke);
      attrs_card.setBackground(card_bg);

      TextView card_title = new TextView(ctx);
      card_title.setText(R.string.layout_attributes_title);
      card_title.setTextColor(isDark ? Color.rgb(148, 163, 184) : Color.rgb(102, 112, 133));
      card_title.setTextSize(11f);
      card_title.setTypeface(null, android.graphics.Typeface.BOLD);
      LinearLayout.LayoutParams card_title_params = new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      card_title_params.bottomMargin = dp(ctx, 8);
      attrs_card.addView(card_title, card_title_params);

      int label_width = dp(ctx, 90);
      int label_color = isDark ? Color.rgb(203, 213, 225) : Color.rgb(71, 84, 103);
      final int primary_text_color = isDark ? Color.rgb(241, 245, 249) : Color.rgb(15, 23, 42);
      int hint_color = isDark ? Color.rgb(100, 116, 139) : Color.rgb(148, 163, 184);

      // Name row.
      LinearLayout name_row = new LinearLayout(ctx);
      name_row.setOrientation(LinearLayout.HORIZONTAL);
      name_row.setGravity(Gravity.CENTER_VERTICAL);
      TextView name_label = new TextView(ctx);
      name_label.setText(R.string.layout_name_label);
      name_label.setTextColor(label_color);
      name_label.setTextSize(13f);
      name_row.addView(name_label, new LinearLayout.LayoutParams(
              label_width, LinearLayout.LayoutParams.WRAP_CONTENT));
      final EditText name_input = new EditText(ctx);
      name_input.setHint(R.string.layout_name_hint);
      name_input.setSingleLine(true);
      name_input.setTextSize(13f);
      name_input.setTextColor(primary_text_color);
      name_input.setHintTextColor(hint_color);
      name_row.addView(name_input, new LinearLayout.LayoutParams(
              0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
      LinearLayout.LayoutParams name_row_params = new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      name_row_params.bottomMargin = dp(ctx, 8);
      attrs_card.addView(name_row, name_row_params);
      name_input_holder[0] = name_input;

      // Keymap row.
      LinearLayout keymap_row = new LinearLayout(ctx);
      keymap_row.setOrientation(LinearLayout.HORIZONTAL);
      keymap_row.setGravity(Gravity.CENTER_VERTICAL);
      TextView keymap_label = new TextView(ctx);
      keymap_label.setText(R.string.layout_keymap_label);
      keymap_label.setTextColor(label_color);
      keymap_label.setTextSize(13f);
      keymap_row.addView(keymap_label, new LinearLayout.LayoutParams(
              label_width, LinearLayout.LayoutParams.WRAP_CONTENT));
      final Spinner spinner = new Spinner(ctx);
      ArrayAdapter<String> adapter = new ArrayAdapter<String>(ctx,
              android.R.layout.simple_spinner_item, keymap_names) {
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
          View v = super.getView(position, convertView, parent);
          if (v instanceof TextView) {
            ((TextView) v).setTextColor(primary_text_color);
            ((TextView) v).setTextSize(13f);
          }
          return v;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
          View v = super.getDropDownView(position, convertView, parent);
          if (v instanceof TextView) {
            ((TextView) v).setTextColor(primary_text_color);
            ((TextView) v).setTextSize(14f);
          }
          return v;
        }
      };
      adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
      spinner.setAdapter(adapter);
      keymap_row.addView(spinner, new LinearLayout.LayoutParams(
              0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
      LinearLayout.LayoutParams keymap_row_params = new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      keymap_row_params.bottomMargin = dp(ctx, 4);
      attrs_card.addView(keymap_row, keymap_row_params);
      spinner_holder[0] = spinner;

      // Swipekeymap row.
      LinearLayout swipe_row = new LinearLayout(ctx);
      swipe_row.setOrientation(LinearLayout.HORIZONTAL);
      swipe_row.setGravity(Gravity.CENTER_VERTICAL);
      TextView swipe_label = new TextView(ctx);
      swipe_label.setText(R.string.layout_swipekeymap_row_label);
      swipe_label.setTextColor(label_color);
      swipe_label.setTextSize(13f);
      swipe_row.addView(swipe_label, new LinearLayout.LayoutParams(
              label_width, LinearLayout.LayoutParams.WRAP_CONTENT));
      final CheckBox swipe_cb = new CheckBox(ctx);
      swipe_row.addView(swipe_cb, new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
      attrs_card.addView(swipe_row, new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
      swipe_cb_holder[0] = swipe_cb;

      LinearLayout.LayoutParams attrs_card_params = new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      attrs_card_params.bottomMargin = dp(ctx, 10);
      container.addView(attrs_card, attrs_card_params);

      // Initial state from the XML, guarded so it doesn't immediately
      // rewrite the text back at itself.
      syncing[0] = true;
      name_input.setText(KeymapXmlAttrUtils.get_name_attr(initial_text));
      String current_keymap = KeymapXmlAttrUtils.get_keymap_attr(initial_text);
      int initial_index = (current_keymap != null)
              ? Math.max(0, keymap_names.indexOf(current_keymap)) : 0;
      spinner.setSelection(initial_index);
      swipe_cb.setChecked(KeymapXmlAttrUtils.get_swipekeymap_attr(initial_text));
      swipe_cb.setEnabled(initial_index != 0);
      syncing[0] = false;

      name_input.addTextChangedListener(new android.text.TextWatcher()
      {
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        public void onTextChanged(CharSequence s, int a, int b, int c) {}
        public void afterTextChanged(android.text.Editable s)
        {
          if (syncing[0]) return;
          syncing[0] = true;
          String new_text = KeymapXmlAttrUtils.set_name_attr(
                  input.getText().toString(), s.toString());
          int cursor = input.getSelectionStart();
          input.setText(new_text);
          input.setSelection(Math.min(cursor, new_text.length()));
          syncing[0] = false;
          update_error_view(error_view, safe_validate(callback, new_text));
        }
      });

      spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
      {
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
        {
          if (syncing[0]) return;
          syncing[0] = true;
          String new_text;
          if (position == 0)
          {
            swipe_cb.setChecked(false);
            swipe_cb.setEnabled(false);
            new_text = KeymapXmlAttrUtils.remove_keymap_attrs(input.getText().toString());
          }
          else
          {
            swipe_cb.setEnabled(true);
            String t = KeymapXmlAttrUtils.set_keymap_attr(
                    input.getText().toString(), keymap_names.get(position));
            t = KeymapXmlAttrUtils.set_swipekeymap_attr(t, swipe_cb.isChecked());
            new_text = t;
          }
          input.setText(new_text);
          input.setSelection(new_text.length());
          syncing[0] = false;
          update_error_view(error_view, safe_validate(callback, new_text));
        }
        public void onNothingSelected(AdapterView<?> parent) {}
      });

      swipe_cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
      {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
          if (syncing[0]) return;
          syncing[0] = true;
          String new_text = KeymapXmlAttrUtils.set_swipekeymap_attr(
                  input.getText().toString(), isChecked);
          input.setText(new_text);
          input.setSelection(new_text.length());
          syncing[0] = false;
          update_error_view(error_view, safe_validate(callback, new_text));
        }
      });
    }

    container.addView(input_scroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

    final Button[] builder_button_holder = new Button[1];

    if (on_open_in_builder != null)
    {
//      View divider = new View(ctx);
//      divider.setBackgroundColor(Color.rgb(52, 120, 246));
//      LinearLayout.LayoutParams divider_params = new LinearLayout.LayoutParams(
//              LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 2));
//      divider_params.topMargin = dp(ctx, 4);
//      divider_params.bottomMargin = dp(ctx, 8);
//      container.addView(divider, divider_params);

      LinearLayout bottom_row = new LinearLayout(ctx);
      bottom_row.setOrientation(LinearLayout.HORIZONTAL);
      bottom_row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

      LinearLayout.LayoutParams error_params = new LinearLayout.LayoutParams(
              0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
      bottom_row.addView(error_view, error_params);

      Button builder_btn = new Button(ctx);
      builder_button_holder[0] = builder_btn;
      builder_btn.setText(R.string.pref_keymap_open_builder);
      builder_btn.setTextColor(Color.rgb(52, 120, 246));
      builder_btn.setBackgroundColor(Color.TRANSPARENT);
      bottom_row.addView(builder_btn, new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.WRAP_CONTENT,
              LinearLayout.LayoutParams.WRAP_CONTENT));

      container.addView(bottom_row, new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT,
              LinearLayout.LayoutParams.WRAP_CONTENT));
    }
    else
    {
      LinearLayout.LayoutParams error_params = new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT,
              LinearLayout.LayoutParams.WRAP_CONTENT);
      error_params.topMargin = dp(ctx, 6);
      container.addView(error_view, error_params);
    }

    LinearLayout dialog_margin_container = new LinearLayout(ctx);
    dialog_margin_container.setOrientation(LinearLayout.VERTICAL);
    dialog_margin_container.setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8));
    dialog_margin_container.addView(container, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

    AlertDialog.Builder builder = new AlertDialog.Builder(ctx)
            .setTitle(title_res)
            .setView(dialog_margin_container)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null);
    if (allow_remove)
      builder.setNeutralButton(remove_label_res, new DialogInterface.OnClickListener(){
        public void onClick(DialogInterface _dialog, int _which)
        {
          callback.select(null);
        }
      });

    final AlertDialog dialog = builder.create();

    if (on_open_in_builder != null && builder_button_holder[0] != null)
    {
      builder_button_holder[0].setOnClickListener(new View.OnClickListener()
      {
        public void onClick(View v)
        {
          String current_text = input.getText().toString();
          dialog.dismiss();
          on_open_in_builder.open(current_text);
        }
      });
    }

    update_error_view(error_view, safe_validate(callback, initial_text));

    dialog.setOnShowListener(new DialogInterface.OnShowListener()
    {
      public void onShow(DialogInterface d)
      {
        Button ok_btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        ok_btn.setOnClickListener(new View.OnClickListener()
        {
          public void onClick(View v)
          {
            String text = input.getText().toString();
            String error = safe_validate(callback, text);
            update_error_view(error_view, error);
            if (error == null)
            {
              callback.select(text);
              dialog.dismiss();
            }
          }
        });
      }
    });

    input.set_on_text_change(new LayoutEntryEditText.OnChangeListener()
    {
      public void on_change()
      {
        String text = input.getText().toString();
        update_error_view(error_view, safe_validate(callback, text));

        if (show_keymap_selector && spinner_holder[0] != null && !syncing[0])
        {
          syncing[0] = true;
          if (name_input_holder[0] != null)
            name_input_holder[0].setText(KeymapXmlAttrUtils.get_name_attr(text));
          String cur = KeymapXmlAttrUtils.get_keymap_attr(text);
          int idx = (cur != null) ? Math.max(0, keymap_names.indexOf(cur)) : 0;
          spinner_holder[0].setSelection(idx);
          swipe_cb_holder[0].setChecked(KeymapXmlAttrUtils.get_swipekeymap_attr(text));
          swipe_cb_holder[0].setEnabled(idx != 0);
          syncing[0] = false;
        }
      }
    });
    dialog.show();
  }

  /** Wraps [callback.validate] so a bug in some Callback implementation
   (or a not-yet-handled edge case in whatever it parses) shows up as
   a normal inline error message, same as any other invalid input,
   rather than crashing the whole app - this runs on every keystroke,
   including from a Handler-posted callback with no other surrounding
   try/catch, so nothing here may be allowed to escape uncaught. */
  private static String safe_validate(Callback callback, String text)
  {
    try
    {
      return callback.validate(text);
    }
    catch (Exception e)
    {
      String msg = e.getMessage();
      return (msg != null && !msg.isEmpty()) ? msg : ("Invalid input (" + e.getClass().getSimpleName() + ")");
    }
  }

  private static void update_error_view(TextView error_view, String error)
  {
    if (error == null || error.isEmpty())
    {
      error_view.setVisibility(View.GONE);
      error_view.setText("");
    }
    else
    {
      error_view.setText("\u26A0 " + error);
      error_view.setVisibility(View.VISIBLE);
    }
  }

  public interface Callback
  {
    public void select(String text);
    public String validate(String text);
  }

  private static int dp(Context ctx, int value)
  {
    return (int)(value * ctx.getResources().getDisplayMetrics().density);
  }

  private static int dp(Context ctx, float value)
  {
    return (int)(value * ctx.getResources().getDisplayMetrics().density);
  }

  public static class MaxHeightScrollView extends NestedScrollView
  {
    private int _max_height = Integer.MAX_VALUE;
    private float _lastY = 0f;

    public MaxHeightScrollView(Context ctx)
    {
      super(ctx);
      setNestedScrollingEnabled(true);
    }

    public void set_max_height(int max_height_px) { _max_height = max_height_px; }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev)
    {
      ViewParent parent = getParent();
      switch (ev.getActionMasked())
      {
        case MotionEvent.ACTION_DOWN:
          _lastY = ev.getY();
          if (parent != null)
            parent.requestDisallowInterceptTouchEvent(true);
          break;

        case MotionEvent.ACTION_MOVE:
          float currentY = ev.getY();
          float deltaY = currentY - _lastY;
          _lastY = currentY;

          if (parent != null)
          {
            // deltaY < 0: dragging finger UP (scrolling DOWN towards bottom of code)
            // deltaY > 0: dragging finger DOWN (scrolling UP towards top of code)
            if (deltaY < 0 && !canScrollVertically(1))
            {
              // Reached bottom of code, allow main page to scroll down
              parent.requestDisallowInterceptTouchEvent(false);
            }
            else if (deltaY > 0 && !canScrollVertically(-1))
            {
              // Reached top/head of code, allow main page to scroll up
              parent.requestDisallowInterceptTouchEvent(false);
            }
            else
            {
              // Still room to scroll within code in this direction
              parent.requestDisallowInterceptTouchEvent(true);
            }
          }
          break;

        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          if (parent != null)
            parent.requestDisallowInterceptTouchEvent(false);
          break;
      }
      return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
      int height_mode = MeasureSpec.getMode(heightMeasureSpec);
      int height_size = MeasureSpec.getSize(heightMeasureSpec);
      int capped_size = Math.min(height_size == 0 ? _max_height : height_size, _max_height);
      int new_height_spec;
      if (height_mode == MeasureSpec.UNSPECIFIED)
        new_height_spec = MeasureSpec.makeMeasureSpec(_max_height, MeasureSpec.AT_MOST);
      else
        new_height_spec = MeasureSpec.makeMeasureSpec(capped_size, MeasureSpec.AT_MOST);
      super.onMeasure(widthMeasureSpec, new_height_spec);
    }
  }

  public static class LayoutEntryEditText extends EditText
  {
    Paint _ln_paint;
    Paint _gutterBgPaint;
    Paint _gutterDividerPaint;
    OnChangeListener _on_change_listener = null;
    Handler _on_change_throttler;
    Runnable _on_change_delayed = new Runnable()
    {
      public void run()
      {
        OnChangeListener l = LayoutEntryEditText.this._on_change_listener;
        if (l != null)
          l.on_change();
      }
    };

    private int _gutter_width = 0;
    private int _prev_digits = -1;
    private Integer _gutterBgColor = null;
    private float _downX = 0f;
    private float _downY = 0f;
    private int _touchSlop;

    public LayoutEntryEditText(Context ctx)
    {
      super(ctx);
      if (_ln_paint == null)
      {
        _ln_paint = new Paint(getPaint());
        _ln_paint.setTextSize(_ln_paint.getTextSize() * 0.8f);
      }
      _touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
      setHorizontallyScrolling(true);
      setHorizontalScrollBarEnabled(true);
      setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
      setGravity(Gravity.TOP | Gravity.START);
      setMinLines(3);
      setMaxLines(Integer.MAX_VALUE);
      style_input_box(this);
      _on_change_throttler = new Handler(ctx.getMainLooper());
      checkAndUpdateGutterWidth();
    }

    public void setGutterBackgroundColor(int color)
    {
      _gutterBgColor = color;
      invalidate();
    }

    private void checkAndUpdateGutterWidth()
    {
      if (_ln_paint == null)
      {
        Paint p = getPaint();
        if (p == null) return;
        _ln_paint = new Paint(p);
        _ln_paint.setTextSize(_ln_paint.getTextSize() * 0.8f);
      }
      int line_count = Math.max(1, getLineCount());
      int digits = Math.max(2, (int) Math.log10(line_count) + 1);
      if (digits != _prev_digits)
      {
        _prev_digits = digits;
        float digit_width = _ln_paint.measureText("0");
        _gutter_width = (int) (digits * digit_width + dp(getContext(), 14));
        setPadding(_gutter_width + dp(getContext(), 6), dp(getContext(), 4), dp(getContext(), 16), dp(getContext(), 4));
      }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
      checkAndUpdateGutterWidth();
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public interface OnLineSelectListener {
      void onLineSelected(int lineIndex);
    }
    private OnLineSelectListener _line_select_listener = null;
    public void setOnLineSelectListener(OnLineSelectListener l) { _line_select_listener = l; }

    private int _highlightedLine = -1;
    private Paint _highlightLineBgPaint;
    private Paint _highlightLineBarPaint;

    public void setHighlightedLine(int lineIndex)
    {
      if (_highlightedLine != lineIndex)
      {
        _highlightedLine = lineIndex;
        invalidate();
      }
    }

    public int getHighlightedLine()
    {
      return _highlightedLine;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd)
    {
      super.onSelectionChanged(selStart, selEnd);
      if (_line_select_listener != null && getLayout() != null)
      {
        int line = getLayout().getLineForOffset(selStart);
        _line_select_listener.onLineSelected(line);
      }
    }

    public void set_on_text_change(OnChangeListener l) { _on_change_listener = l; }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
      switch (event.getActionMasked())
      {
        case MotionEvent.ACTION_DOWN:
          _downX = event.getX();
          _downY = event.getY();
          break;
        case MotionEvent.ACTION_UP:
          float dx = Math.abs(event.getX() - _downX);
          float dy = Math.abs(event.getY() - _downY);
          if (dx < _touchSlop && dy < _touchSlop && event.getX() <= _gutter_width + dp(getContext(), 4))
          {
            Layout layout = getLayout();
            if (layout != null)
            {
              float y = event.getY() + getScrollY();
              int line = layout.getLineForVertical((int) y);
              if (line >= 0 && line < getLineCount())
              {
                setHighlightedLine(line);
                int start = layout.getLineStart(line);
                int end = layout.getLineEnd(line);
                CharSequence txt = getText();
                if (txt != null)
                {
                  while (end > start && (txt.charAt(end - 1) == '\n' || txt.charAt(end - 1) == '\r'))
                    end--;
                }
                setSelection(start, end);
                if (_line_select_listener != null)
                  _line_select_listener.onLineSelected(line);
                return true;
              }
            }
          }
          break;
      }
      return super.onTouchEvent(event);
    }

    Rect _clip_bounds = new Rect();

    @Override
    protected void onDraw(Canvas canvas)
    {
      final Context ctx = getContext();
      final boolean isDark = is_dark_theme(ctx);
      final int sx = Math.max(0, getScrollX());
      final int line_count = getLineCount();
      final Layout layout = getLayout();

      if (_gutterBgPaint == null)
      {
        _gutterBgPaint = new Paint();
        _gutterBgPaint.setStyle(Paint.Style.FILL);
        _gutterDividerPaint = new Paint();
        _gutterDividerPaint.setStyle(Paint.Style.STROKE);
        _gutterDividerPaint.setStrokeWidth(dp(ctx, 1));
      }

      int bgColor;
      if (_gutterBgColor != null)
      {
        bgColor = _gutterBgColor;
      }
      else
      {
        try
        {
          bgColor = ContextCompat.getColor(ctx, R.color.settings_background);
        }
        catch (Exception e)
        {
          bgColor = isDark ? Color.rgb(0, 0, 0) : Color.rgb(246, 248, 252);
        }
      }

      int dividerColor;
      try
      {
        dividerColor = ContextCompat.getColor(ctx, R.color.settings_divider);
      }
      catch (Exception e)
      {
        dividerColor = isDark ? Color.rgb(44, 46, 51) : Color.rgb(225, 227, 232);
      }

      _gutterBgPaint.setColor(bgColor);
      _gutterDividerPaint.setColor(dividerColor);

      // 1. Draw active line background highlight (BEFORE super.onDraw so text is on top)
      if (layout != null && _highlightedLine >= 0 && _highlightedLine < line_count)
      {
        if (_highlightLineBgPaint == null)
        {
          _highlightLineBgPaint = new Paint();
          _highlightLineBgPaint.setStyle(Paint.Style.FILL);
        }
        _highlightLineBgPaint.setColor(isDark ? Color.argb(60, 56, 189, 248) : Color.argb(40, 52, 120, 246));

        int top = layout.getLineTop(_highlightedLine);
        int bottom = layout.getLineBottom(_highlightedLine);
        int lineRight = (int) layout.getLineRight(_highlightedLine);
        int right = Math.max(sx + getWidth(), lineRight + dp(ctx, 32));
        canvas.drawRect(sx + _gutter_width, top, right, bottom, _highlightLineBgPaint);
      }

      // 2. Draw the EditText text, cursor, and text selection
      super.onDraw(canvas);

      // 3. Draw the pinned gutter OVER any scrolled text
      canvas.getClipBounds(_clip_bounds);
      if (layout == null) return;

      int clipTop = _clip_bounds.isEmpty() ? 0 : _clip_bounds.top;
      int clipBottom = _clip_bounds.isEmpty() ? getHeight() : _clip_bounds.bottom;

      // 3a. Solid gutter background (pinned to sx)
      canvas.drawRect(sx, clipTop, sx + _gutter_width, clipBottom, _gutterBgPaint);

      // 3b. Vertical divider line (pinned to sx + _gutter_width)
      float divX = sx + _gutter_width;
      canvas.drawLine(divX, clipTop, divX, clipBottom, _gutterDividerPaint);

      // 3c. Active line indicator bar on the left edge of the gutter
      if (_highlightedLine >= 0 && _highlightedLine < line_count)
      {
        if (_highlightLineBarPaint == null)
        {
          _highlightLineBarPaint = new Paint();
          _highlightLineBarPaint.setStyle(Paint.Style.FILL);
        }
        _highlightLineBarPaint.setColor(isDark ? Color.rgb(56, 189, 248) : Color.rgb(52, 120, 246));
        int hlTop = layout.getLineTop(_highlightedLine);
        int hlBottom = layout.getLineBottom(_highlightedLine);
        canvas.drawRect(sx, hlTop, sx + dp(ctx, 3.5f), hlBottom, _highlightLineBarPaint);
      }

      // 3d. Line numbers (right-aligned in the pinned gutter)
      int startLine = layout.getLineForVertical(clipTop);
      int endLine = layout.getLineForVertical(clipBottom);
      float rightMargin = dp(ctx, 6);

      for (int line = startLine; line <= Math.min(endLine, line_count - 1); line++)
      {
        int baseline = getLineBounds(line, null);
        String lineStr = String.valueOf(line);
        float numWidth = _ln_paint.measureText(lineStr);
        float textX = sx + _gutter_width - rightMargin - numWidth;

        if (line == _highlightedLine)
        {
          _ln_paint.setColor(isDark ? Color.rgb(56, 189, 248) : Color.rgb(52, 120, 246));
          _ln_paint.setFakeBoldText(true);
          canvas.drawText(lineStr, textX, baseline, _ln_paint);
          _ln_paint.setFakeBoldText(false);
        }
        else
        {
          _ln_paint.setColor(isDark ? Color.rgb(100, 116, 139) : Color.rgb(152, 162, 171));
          canvas.drawText(lineStr, textX, baseline, _ln_paint);
        }
      }
    }

    @Override
    protected void onTextChanged(CharSequence text, int _s, int _lb, int _la)
    {
      super.onTextChanged(text, _s, _lb, _la);
      checkAndUpdateGutterWidth();
      if (_on_change_throttler != null)
      {
        _on_change_throttler.removeCallbacks(_on_change_delayed);
        _on_change_throttler.postDelayed(_on_change_delayed, 1000);
      }
    }

    public static interface OnChangeListener { public void on_change(); }
  }

  private static void style_input_box(final EditText input)
  {
    final Context ctx = input.getContext();
    final boolean isDark = is_dark_theme(ctx);
    final int text_color = isDark ? Color.rgb(241, 245, 249) : Color.rgb(23, 32, 42);
    final int hint_color = isDark ? Color.rgb(100, 116, 139) : Color.rgb(152, 162, 171);
    final int bg_normal = isDark ? Color.rgb(24, 26, 31) : Color.WHITE;
    final int bg_focused = isDark ? Color.rgb(30, 33, 40) : Color.rgb(248, 251, 255);
    final int border_normal = isDark ? Color.rgb(65, 72, 83) : Color.rgb(208, 213, 221);
    final int border_focused = Color.rgb(52, 120, 246);

    input.setTextColor(text_color);
    input.setHintTextColor(hint_color);
    input.setTextSize(14f);
    input.setGravity(Gravity.TOP | Gravity.START);
    input.setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12));
    input.setBackground(create_input_background(bg_normal, border_normal, dp(ctx, 1), dp(ctx, 10)));
    input.setOnFocusChangeListener(new View.OnFocusChangeListener()
    {
      @Override
      public void onFocusChange(View view, boolean has_focus)
      {
        if (has_focus)
          input.setBackground(create_input_background(bg_focused, border_focused, dp(ctx, 2), dp(ctx, 10)));
        else
          input.setBackground(create_input_background(bg_normal, border_normal, dp(ctx, 1), dp(ctx, 10)));
      }
    });
  }

  private static GradientDrawable create_input_background(int fill_color, int border_color, int border_width, int corner_radius)
  {
    GradientDrawable background = new GradientDrawable();
    background.setShape(GradientDrawable.RECTANGLE);
    background.setColor(fill_color);
    background.setCornerRadius(corner_radius);
    background.setStroke(border_width, border_color);
    return background;
  }

  public static boolean is_dark_theme(Context ctx)
  {
    int nightMode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    if (nightMode == Configuration.UI_MODE_NIGHT_YES)
      return true;
    if (nightMode == Configuration.UI_MODE_NIGHT_NO)
      return false;
    android.util.TypedValue tv = new android.util.TypedValue();
    if (ctx.getTheme().resolveAttribute(android.R.attr.isLightTheme, tv, true))
      return tv.data == 0;
    return false;
  }
}