package com.nhs.customkeyboard.voice;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.nhs.customkeyboard.Config;
import com.nhs.customkeyboard.KeyboardData;
import com.nhs.customkeyboard.R;
import com.nhs.customkeyboard.VoicePermissionActivity;
import com.nhs.customkeyboard.dict.Dictionaries;
import com.nhs.customkeyboard.suggestions.CandidatesView;
import com.nhs.customkeyboard.translate.TranslationBarView;

import java.util.ArrayList;

public class VoiceTypingController {
  private static final String TAG = "VoiceTypingController";

  public interface HostProvider {
    Context getContext();
    InputConnection getCurrentInputConnection();
    KeyboardData getCurrentLayout();
    Dictionaries getDictionaries();
    Config getConfig();
    TranslationBarView getTranslationBarView();
    CandidatesView getCandidatesView();
    void onVoiceStateChanged(boolean active);
  }

  private final HostProvider _host;
  private View _voice_typing_bar_view;
  private TextView _tv_voice_status;
  private TextView _tv_voice_lang_badge;
  private ImageButton _btn_voice_back;
  private ImageButton _btn_voice_mic;

  private String _current_voice_lang = "en-US";
  private boolean _is_voice_typing_active = false;
  private SpeechRecognizer _speech_recognizer;

  public VoiceTypingController(HostProvider host) {
    _host = host;
  }

  public void setup(ViewGroup containerView) {
    if (containerView == null) return;
    _voice_typing_bar_view = containerView.findViewById(R.id.voice_typing_bar_view);
    if (_voice_typing_bar_view == null) {
      _voice_typing_bar_view = containerView.findViewById(R.id.voice_typing_bar);
    }
    if (_voice_typing_bar_view == null) return;

    _btn_voice_back = _voice_typing_bar_view.findViewById(R.id.btn_voice_back);
    _tv_voice_status = _voice_typing_bar_view.findViewById(R.id.tv_voice_status);
    _tv_voice_lang_badge = _voice_typing_bar_view.findViewById(R.id.tv_voice_lang_badge);
    _btn_voice_mic = _voice_typing_bar_view.findViewById(R.id.btn_voice_mic);

    if (_btn_voice_back != null) {
      _btn_voice_back.setOnClickListener(v -> stop_voice_typing());
    }
    if (_tv_voice_lang_badge != null) {
      _tv_voice_lang_badge.setOnClickListener(v -> toggle_voice_language());
    }
    if (_btn_voice_mic != null) {
      _btn_voice_mic.setOnClickListener(v -> {
        if (_is_voice_typing_active) {
          stop_voice_typing();
        } else {
          start_dynamic_voice_typing();
        }
      });
    }
  }

  public boolean isVoiceTypingActive() {
    return _is_voice_typing_active;
  }

  public void start_dynamic_voice_typing() {
    Context ctx = _host.getContext();
    if (ctx == null) return;

    if (VERSION.SDK_INT >= 23) {
      if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        Intent permIntent = new Intent(ctx, VoicePermissionActivity.class);
        permIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(permIntent);
        Toast.makeText(ctx, "Microphone permission required for voice typing", Toast.LENGTH_SHORT).show();
        return;
      }
    }

    if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
      Toast.makeText(ctx, R.string.toast_no_voice_input, Toast.LENGTH_SHORT).show();
      return;
    }

    TranslationBarView translationBar = _host.getTranslationBarView();
    boolean isTranslation = (translationBar != null && translationBar.isOpen());
    if (isTranslation) {
      _current_voice_lang = map_lang_to_speech_locale(translationBar.getSourceLang());
    } else {
      KeyboardData layout = _host.getCurrentLayout();
      boolean isBangla = is_bangla_active(layout);
      _current_voice_lang = isBangla ? "bn-BD" : "en-US";
    }
    _is_voice_typing_active = true;
    _host.onVoiceStateChanged(true);

    CandidatesView candidatesView = _host.getCandidatesView();
    if (candidatesView != null) {
      int candHeight = candidatesView.getHeight();
      if (candHeight > 0 && _voice_typing_bar_view != null) {
        ViewGroup.LayoutParams lp = _voice_typing_bar_view.getLayoutParams();
        if (lp != null) {
          lp.height = candHeight;
          _voice_typing_bar_view.setLayoutParams(lp);
        }
      }
      candidatesView.setVisibility(View.GONE);
    }

    if (_voice_typing_bar_view != null) {
      _voice_typing_bar_view.setVisibility(View.VISIBLE);
    }

    update_voice_ui_for_language();
    listen_speech();
  }

  public void update_voice_ui_for_language() {
    boolean isBangla = _current_voice_lang != null && _current_voice_lang.startsWith("bn");
    if (_tv_voice_lang_badge != null) {
      String badge = "EN";
      if (_current_voice_lang != null && _current_voice_lang.length() >= 2) {
        badge = _current_voice_lang.substring(0, 2).toUpperCase(java.util.Locale.US);
      }
      _tv_voice_lang_badge.setText(badge);
    }
    if (_tv_voice_status != null) {
      _tv_voice_status.setText(isBangla ? "এখনই বলুন" : "Speak now");
    }
  }

  public void toggle_voice_language() {
    TranslationBarView translationBar = _host.getTranslationBarView();
    if (translationBar != null && translationBar.isOpen()) {
      translationBar.swap_languages();
      return;
    }

    if (_current_voice_lang.startsWith("bn")) {
      _current_voice_lang = "en-US";
    } else {
      _current_voice_lang = "bn-BD";
    }
    update_voice_ui_for_language();
    listen_speech();
  }

  private void listen_speech() {
    if (!_is_voice_typing_active) return;
    Context ctx = _host.getContext();
    if (ctx == null) return;

    try {
      if (_speech_recognizer != null) {
        try {
          _speech_recognizer.cancel();
          _speech_recognizer.destroy();
        } catch (Exception ignored) {}
        _speech_recognizer = null;
      }

      _speech_recognizer = SpeechRecognizer.createSpeechRecognizer(ctx);
      Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
      intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
      intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, _current_voice_lang);
      intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, _current_voice_lang);
      intent.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{_current_voice_lang});
      intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
      intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
      intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.getPackageName());

      _speech_recognizer.setRecognitionListener(new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) {
          if (_tv_voice_status != null && _is_voice_typing_active) {
            _tv_voice_status.setText(_current_voice_lang.startsWith("bn") ? "এখনই বলুন" : "Speak now");
          }
        }

        @Override
        public void onBeginningOfSpeech() {
          if (_tv_voice_status != null && _is_voice_typing_active) {
            _tv_voice_status.setText(_current_voice_lang.startsWith("bn") ? "শুনছি..." : "Listening...");
          }
        }

        @Override
        public void onRmsChanged(float rmsdB) {
          if (_btn_voice_mic != null && _is_voice_typing_active && rmsdB > 2f) {
            float scale = 1.0f + Math.min(rmsdB / 20f, 0.25f);
            _btn_voice_mic.setScaleX(scale);
            _btn_voice_mic.setScaleY(scale);
          } else if (_btn_voice_mic != null) {
            _btn_voice_mic.setScaleX(1.0f);
            _btn_voice_mic.setScaleY(1.0f);
          }
        }

        @Override public void onBufferReceived(byte[] buffer) {}

        @Override
        public void onEndOfSpeech() {
          if (_btn_voice_mic != null) {
            _btn_voice_mic.setScaleX(1.0f);
            _btn_voice_mic.setScaleY(1.0f);
          }
        }

        @Override
        public void onError(int error) {
          Log.d(TAG, "SpeechRecognizer error: " + error);
          if (!_is_voice_typing_active) return;

          Context c = _host.getContext();
          if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS && c != null) {
            stop_voice_typing();
            Intent permIntent = new Intent(c, VoicePermissionActivity.class);
            permIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(permIntent);
            Toast.makeText(c, "Microphone permission required", Toast.LENGTH_SHORT).show();
          } else {
            stop_voice_typing();
          }
        }

        @Override
        public void onResults(Bundle results) {
          if (!_is_voice_typing_active) return;
          ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
          if (matches != null && !matches.isEmpty()) {
            String text = matches.get(0);
            if (text != null && !text.trim().isEmpty()) {
              TranslationBarView translationBar = _host.getTranslationBarView();
              if (translationBar != null && translationBar.isOpen()) {
                translationBar.append_string(text.trim());
              } else {
                InputConnection ic = _host.getCurrentInputConnection();
                if (ic != null) {
                  ic.commitText(text.trim() + " ", 1);
                }
              }
            }
          }
          stop_voice_typing();
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
          if (!_is_voice_typing_active) return;
          ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
          if (matches != null && !matches.isEmpty()) {
            String text = matches.get(0);
            if (_tv_voice_status != null && text != null && !text.isEmpty()) {
              _tv_voice_status.setText(text);
            }
          }
        }

        @Override public void onEvent(int eventType, Bundle params) {}
      });

      _speech_recognizer.startListening(intent);
    } catch (Exception e) {
      Log.e(TAG, "SpeechRecognizer startListening failed", e);
    }
  }

  public void stop_voice_typing() {
    if (!_is_voice_typing_active) {
      return;
    }
    _is_voice_typing_active = false;
    if (_btn_voice_mic != null) {
      _btn_voice_mic.setScaleX(1.0f);
      _btn_voice_mic.setScaleY(1.0f);
    }
    destroySpeechRecognizer();

    TranslationBarView translationBar = _host.getTranslationBarView();
    if (translationBar == null || !translationBar.isOpen()) {
      InputConnection ic = _host.getCurrentInputConnection();
      if (ic != null) {
        try {
          ic.finishComposingText();
        } catch (Exception ignored) {}
      }
    }
    if (_voice_typing_bar_view != null) {
      _voice_typing_bar_view.setVisibility(View.GONE);
    }
    _host.onVoiceStateChanged(false);
  }

  public void destroy() {
    _is_voice_typing_active = false;
    destroySpeechRecognizer();
  }

  private void destroySpeechRecognizer() {
    if (_speech_recognizer != null) {
      try {
        _speech_recognizer.stopListening();
        _speech_recognizer.cancel();
        _speech_recognizer.destroy();
      } catch (Exception ignored) {}
      _speech_recognizer = null;
    }
  }

  public boolean is_bangla_active(KeyboardData layout) {
    if (layout != null) {
      String name = layout.name != null ? layout.name.toLowerCase() : "";
      String script = layout.script != null ? layout.script.toLowerCase() : "";
      String keymap = layout.keymap != null ? layout.keymap.toLowerCase() : "";

      if (name.contains("বাংলা") || name.contains("bengali") || name.contains("bangla")
          || name.contains("probhat") || name.contains("provat") || name.contains("national")
          || name.contains("জাতীয়") || name.contains("জাতীয়") || name.contains("bn")
          || script.contains("beng") || script.contains("bangla") || script.contains("avro")
          || keymap.contains("bangla") || keymap.contains("beng") || keymap.contains("avro")
          || keymap.contains("probhat")) {
        return true;
      }
    }

    Dictionaries dictionaries = _host.getDictionaries();
    Config config = _host.getConfig();
    if (dictionaries != null && config != null) {
      String dict = dictionaries.get_selected(config);
      if (dict != null && (dict.toLowerCase().contains("bn") || dict.toLowerCase().contains("bangla") || dict.toLowerCase().contains("bengali"))) {
        return true;
      }
    }

    if (config != null && config.device_locales != null && config.device_locales.default_ != null) {
      String lang = config.device_locales.default_.lang_tag;
      if (lang != null && (lang.toLowerCase().startsWith("bn") || lang.toLowerCase().contains("beng"))) {
        return true;
      }
    }

    return false;
  }

  public String map_lang_to_speech_locale(String langCode) {
    if (langCode == null || langCode.isEmpty() || "auto".equalsIgnoreCase(langCode)) {
      KeyboardData layout = _host.getCurrentLayout();
      boolean isBangla = is_bangla_active(layout);
      return isBangla ? "bn-BD" : "en-US";
    }
    String code = langCode.toLowerCase(java.util.Locale.US).trim();
    if (code.equals("bn")) return "bn-BD";
    if (code.equals("en")) return "en-US";
    if (code.equals("hi")) return "hi-IN";
    if (code.equals("es")) return "es-ES";
    if (code.equals("fr")) return "fr-FR";
    if (code.equals("de")) return "de-DE";
    if (code.equals("ar")) return "ar-SA";
    if (code.equals("zh") || code.equals("zh-cn")) return "zh-CN";
    if (code.equals("zh-tw")) return "zh-TW";
    if (code.equals("ja")) return "ja-JP";
    if (code.equals("ru")) return "ru-RU";
    if (code.equals("pt")) return "pt-BR";
    if (code.equals("it")) return "it-IT";
    if (code.equals("ko")) return "ko-KR";
    if (code.equals("tr")) return "tr-TR";
    if (code.equals("id")) return "id-ID";
    if (code.equals("vi")) return "vi-VN";
    if (code.equals("th")) return "th-TH";
    if (code.equals("ur")) return "ur-PK";
    if (code.contains("-")) return code;
    return code;
  }
}
