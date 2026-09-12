package com.nhs.customkeyboard.translate;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputConnection;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.List;
import com.nhs.customkeyboard.R;

public class TranslationBarView extends LinearLayout
{
  public interface OnTranslationBarListener
  {
    void onCloseTranslation();
    InputConnection getInputConnection();
    void onLanguagesChanged(String sourceLang, String targetLang);
  }

  private OnTranslationBarListener _listener;
  private ImageButton _btn_back;
  private TextView _btn_source_lang;
  private ImageView _btn_swap;
  private TextView _btn_target_lang;
  private TextView _tv_input;
  private ImageView _btn_clear;

  private final StringBuilder _current_text = new StringBuilder();
  private int _last_committed_length = 0;
  private String _source_lang = "en";
  private String _target_lang = "bn";

  private final Handler _debounce_handler = new Handler(Looper.getMainLooper());
  private Runnable _debounce_runnable;
  private static final long DEBOUNCE_MS = 350;

  public TranslationBarView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
  }

  public void setOnTranslationBarListener(OnTranslationBarListener listener)
  {
    _listener = listener;
  }

  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();
    _btn_back = findViewById(R.id.btn_translate_back);
    _btn_source_lang = findViewById(R.id.btn_source_lang);
    _btn_swap = findViewById(R.id.btn_swap_lang);
    _btn_target_lang = findViewById(R.id.btn_target_lang);
    _tv_input = findViewById(R.id.tv_translate_input);
    _btn_clear = findViewById(R.id.btn_translate_clear);

    init_views();
  }

  private void init_views()
  {
    if (_btn_back != null)
    {
      _btn_back.setOnClickListener(new OnClickListener()
      {
        @Override
        public void onClick(View v)
        {
          close();
        }
      });
    }

    if (_btn_source_lang != null)
    {
      _btn_source_lang.setOnClickListener(new OnClickListener()
      {
        @Override
        public void onClick(View v)
        {
          open_language_picker(true);
        }
      });
    }

    if (_btn_target_lang != null)
    {
      _btn_target_lang.setOnClickListener(new OnClickListener()
      {
        @Override
        public void onClick(View v)
        {
          open_language_picker(false);
        }
      });
    }

    if (_btn_swap != null)
    {
      _btn_swap.setOnClickListener(new OnClickListener()
      {
        @Override
        public void onClick(View v)
        {
          swap_languages();
        }
      });
    }

    if (_btn_clear != null)
    {
      _btn_clear.setOnClickListener(new OnClickListener()
      {
        @Override
        public void onClick(View v)
        {
          clear_input();
        }
      });
    }
  }

  public void open()
  {
    _source_lang = TranslationService.getSourceLang(getContext());
    _target_lang = TranslationService.getTargetLang(getContext());
    update_lang_pills();

    _current_text.setLength(0);
    _last_committed_length = 0;
    update_input_display();

    setVisibility(View.VISIBLE);
  }

  public void close()
  {
    setVisibility(View.GONE);
    if (_debounce_runnable != null)
    {
      _debounce_handler.removeCallbacks(_debounce_runnable);
    }
    _current_text.setLength(0);
    _last_committed_length = 0;
    update_input_display();

    if (_listener != null)
    {
      _listener.onCloseTranslation();
    }
  }

  public boolean isOpen()
  {
    return getVisibility() == View.VISIBLE;
  }

  public String getSourceLang()
  {
    return _source_lang;
  }

  public String getTargetLang()
  {
    return _target_lang;
  }

  public void append_char(char c)
  {
    _current_text.append(c);
    update_input_display();
    schedule_translation();
  }

  public void append_string(String str)
  {
    if (str != null && !str.isEmpty())
    {
      if (_current_text.length() > 0 && !_current_text.toString().endsWith(" "))
      {
        _current_text.append(" ");
      }
      _current_text.append(str);
      update_input_display();
      schedule_translation();
    }
  }

  public void delete_char()
  {
    if (_current_text.length() > 0)
    {
      _current_text.deleteCharAt(_current_text.length() - 1);
      update_input_display();
      schedule_translation();
    }
    else
    {
      // If translation buffer is already empty, reset in app
      delete_committed_in_app();
    }
  }

  public void replace_last_word(int oldLength, String newText)
  {
    if (newText == null) return;
    int len = _current_text.length();
    if (oldLength > 0 && len >= oldLength)
    {
      _current_text.delete(len - oldLength, len);
    }
    _current_text.append(newText);
    update_input_display();
    schedule_translation();
  }

  public void handle_enter()
  {
    if (_debounce_runnable != null)
    {
      _debounce_handler.removeCallbacks(_debounce_runnable);
    }
    _current_text.setLength(0);
    _last_committed_length = 0;
    update_input_display();
  }

  public void clear_input()
  {
    if (_debounce_runnable != null)
    {
      _debounce_handler.removeCallbacks(_debounce_runnable);
    }
    _current_text.setLength(0);
    update_input_display();
    delete_committed_in_app();
  }

  private void update_input_display()
  {
    if (_tv_input != null)
    {
      if (_current_text.length() > 0)
      {
        _tv_input.setText(_current_text.toString() + "|");
        if (_btn_clear != null) _btn_clear.setVisibility(View.VISIBLE);
      }
      else
      {
        _tv_input.setText("");
        if (_btn_clear != null) _btn_clear.setVisibility(View.GONE);
      }
    }
  }

  private void update_lang_pills()
  {
    if (_btn_source_lang != null)
    {
      _btn_source_lang.setText(TranslationService.getLanguageName(_source_lang));
    }
    if (_btn_target_lang != null)
    {
      _btn_target_lang.setText(TranslationService.getLanguageName(_target_lang));
    }
  }

  public void swap_languages()
  {
    String oldSource = _source_lang;
    String oldTarget = _target_lang;

    if ("auto".equals(oldSource))
    {
      _source_lang = oldTarget;
      _target_lang = "en";
    }
    else
    {
      _source_lang = oldTarget;
      _target_lang = oldSource;
    }

    TranslationService.setSourceLang(getContext(), _source_lang);
    TranslationService.setTargetLang(getContext(), _target_lang);
    update_lang_pills();

    if (_listener != null)
    {
      _listener.onLanguagesChanged(_source_lang, _target_lang);
    }

    if (_current_text.length() > 0)
    {
      schedule_translation();
    }
  }

  private void schedule_translation()
  {
    if (_debounce_runnable != null)
    {
      _debounce_handler.removeCallbacks(_debounce_runnable);
    }

    final String query = _current_text.toString().trim();
    if (query.isEmpty())
    {
      delete_committed_in_app();
      return;
    }

    _debounce_runnable = new Runnable()
    {
      @Override
      public void run()
      {
        TranslationService.translate(query, _source_lang, _target_lang, new TranslationService.Callback()
        {
          @Override
          public void onTranslated(String original, String translated)
          {
            // Verify query hasn't changed in the meantime
            if (original.equals(_current_text.toString().trim()))
            {
              commit_translated_text(translated);
            }
          }

          @Override
          public void onError(Exception error)
          {
            // Keep existing or handle silently
          }
        });
      }
    };

    _debounce_handler.postDelayed(_debounce_runnable, DEBOUNCE_MS);
  }

  private void commit_translated_text(String translated)
  {
    if (_listener == null || translated == null) return;
    InputConnection ic = _listener.getInputConnection();
    if (ic == null) return;

    try
    {
      ic.beginBatchEdit();
      if (_last_committed_length > 0)
      {
        ic.deleteSurroundingText(_last_committed_length, 0);
      }
      ic.commitText(translated, 1);
      _last_committed_length = translated.length();
      ic.endBatchEdit();
    }
    catch (Exception ignored) {}
  }

  private void delete_committed_in_app()
  {
    if (_listener == null || _last_committed_length <= 0) return;
    InputConnection ic = _listener.getInputConnection();
    if (ic != null)
    {
      try
      {
        ic.beginBatchEdit();
        ic.deleteSurroundingText(_last_committed_length, 0);
        _last_committed_length = 0;
        ic.endBatchEdit();
      }
      catch (Exception ignored) {}
    }
  }

  private void open_language_picker(final boolean isSource)
  {
    final Context ctx = getContext();
    final List<TranslationService.Language> langs = TranslationService.getLanguages(isSource);
    final String currentCode = isSource ? _source_lang : _target_lang;

    View dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_language_picker, null);
    TextView tvTitle = dialogView.findViewById(R.id.tv_picker_title);
    TextView tvSelectedName = dialogView.findViewById(R.id.tv_selected_lang_name);
    View layoutSelected = dialogView.findViewById(R.id.layout_selected_lang);
    ListView lvLangs = dialogView.findViewById(R.id.lv_languages);

    tvTitle.setText(isSource ? "Translate from" : "Translate to");
    tvSelectedName.setText(TranslationService.getLanguageName(currentCode));

    AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
    builder.setView(dialogView);
    final AlertDialog dialog = builder.create();

    layoutSelected.setOnClickListener(new OnClickListener()
    {
      @Override
      public void onClick(View v)
      {
        dialog.dismiss();
      }
    });

    lvLangs.setAdapter(new BaseAdapter()
    {
      @Override
      public int getCount() { return langs.size(); }

      @Override
      public Object getItem(int position) { return langs.get(position); }

      @Override
      public long getItemId(int position) { return position; }

      @Override
      public View getView(int position, View convertView, ViewGroup parent)
      {
        if (convertView == null)
        {
          convertView = LayoutInflater.from(ctx).inflate(R.layout.item_language_picker, parent, false);
        }
        final TranslationService.Language item = langs.get(position);
        TextView tv = convertView.findViewById(R.id.tv_lang_item_name);
        tv.setText(item.name);

        convertView.setOnClickListener(new OnClickListener()
        {
          @Override
          public void onClick(View v)
          {
            if (isSource)
            {
              _source_lang = item.code;
              TranslationService.setSourceLang(ctx, _source_lang);
            }
            else
            {
              _target_lang = item.code;
              TranslationService.setTargetLang(ctx, _target_lang);
            }
            update_lang_pills();
            dialog.dismiss();

            if (_listener != null)
            {
              _listener.onLanguagesChanged(_source_lang, _target_lang);
            }

            if (_current_text.length() > 0)
            {
              schedule_translation();
            }
          }
        });

        return convertView;
      }
    });

    Window w = dialog.getWindow();
    if (w != null)
    {
      w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    }

    android.os.IBinder token = getWindowToken();
    if (token == null && getRootView() != null)
    {
      token = getRootView().getWindowToken();
    }

    if (token != null)
    {
      com.nhs.customkeyboard.Utils.show_dialog_on_ime(dialog, token);
    }
    else
    {
      dialog.show();
    }
  }
}
