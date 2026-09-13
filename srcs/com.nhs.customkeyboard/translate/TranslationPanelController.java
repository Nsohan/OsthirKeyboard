package com.nhs.customkeyboard.translate;

import android.view.ViewGroup;
import com.nhs.customkeyboard.R;

public class TranslationPanelController {

  private TranslationBarView _translation_bar_view;

  public void setup(ViewGroup containerView) {
    if (containerView == null) return;
    _translation_bar_view = containerView.findViewById(R.id.translation_bar_view);
    if (_translation_bar_view == null) {
      _translation_bar_view = containerView.findViewById(R.id.translation_bar);
    }
  }

  public TranslationBarView getView() {
    return _translation_bar_view;
  }

  public boolean isOpen() {
    return _translation_bar_view != null && _translation_bar_view.isOpen();
  }

  public void toggle() {
    if (_translation_bar_view != null) {
      if (_translation_bar_view.isOpen()) {
        _translation_bar_view.close();
      } else {
        _translation_bar_view.open();
      }
    }
  }

  public void open() {
    if (_translation_bar_view != null && !_translation_bar_view.isOpen()) {
      _translation_bar_view.open();
    }
  }

  public void close() {
    if (_translation_bar_view != null && _translation_bar_view.isOpen()) {
      _translation_bar_view.close();
    }
  }

  public String getSourceLang() {
    return _translation_bar_view != null ? _translation_bar_view.getSourceLang() : "auto";
  }

  public void swapLanguages() {
    if (_translation_bar_view != null) {
      _translation_bar_view.swap_languages();
    }
  }

  public void appendString(String text) {
    if (_translation_bar_view != null) {
      _translation_bar_view.append_string(text);
    }
  }
}
