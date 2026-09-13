package com.nhs.customkeyboard.gif;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.nhs.customkeyboard.Config;
import com.nhs.customkeyboard.R;
import com.nhs.customkeyboard.suggestions.CandidatesView;

public class GifPanelController {

  public interface HostProvider {
    Context getContext();
    ViewGroup getKeyboardContainerView();
    CandidatesView getCandidatesView();
    int getKeyboardHeight();
    void setInputView(View view);
    Config getConfig();
  }

  private final HostProvider _host;
  private ViewGroup _gif_pane;
  private View _gif_search_bar_view;
  private TextView _tv_gif_search_query;
  private View _btn_gif_search_clear;
  private final StringBuilder _gif_search_text = new StringBuilder();
  private boolean _gif_search_active = false;

  public GifPanelController(HostProvider host) {
    _host = host;
  }

  public void setup(ViewGroup containerView) {
    if (containerView == null) return;
    _gif_search_bar_view = containerView.findViewById(R.id.gif_search_bar_view);
    if (_gif_search_bar_view == null) {
      _gif_search_bar_view = containerView.findViewById(R.id.gif_search_bar);
    }
    if (_gif_search_bar_view != null) {
      _tv_gif_search_query = _gif_search_bar_view.findViewById(R.id.tv_gif_search_query);
      _btn_gif_search_clear = _gif_search_bar_view.findViewById(R.id.btn_gif_search_clear);

      View btnBack = _gif_search_bar_view.findViewById(R.id.btn_gif_search_back);
      if (btnBack != null) {
        btnBack.setOnClickListener(v -> close_gif_search(true));
      }

      View btnSubmit = _gif_search_bar_view.findViewById(R.id.btn_gif_search_submit);
      if (btnSubmit != null) {
        btnSubmit.setOnClickListener(v -> submit_gif_search());
      }

      if (_btn_gif_search_clear != null) {
        _btn_gif_search_clear.setOnClickListener(v -> {
          _gif_search_text.setLength(0);
          update_gif_search_display();
        });
      }
    }
  }

  public boolean isGifSearchActive() {
    return _gif_search_active;
  }

  public void resetPane() {
    _gif_pane = null;
  }

  public void show_gif_pane() {
    close_gif_search(false);
    Context ctx = _host.getContext();
    if (ctx == null) return;

    if (_gif_pane == null) {
      Config config = _host.getConfig();
      int theme = (config != null) ? config.theme : 0;
      Context wrappedCtx = (theme != 0) ? new ContextThemeWrapper(ctx, theme) : ctx;
      _gif_pane = (ViewGroup) View.inflate(wrappedCtx, R.layout.gif_pane, null);
    }
    if (_gif_pane instanceof GifManagerView) {
      GifManagerView gmv = (GifManagerView) _gif_pane;
      gmv.setOnSearchClickListener(this::open_gif_search);
      gmv.onShow(_host.getKeyboardHeight());
    }
    _host.setInputView(_gif_pane);
  }

  public void open_gif_search(String initialQuery) {
    _gif_search_active = true;
    _gif_search_text.setLength(0);
    if (initialQuery != null && !initialQuery.isEmpty()) {
      _gif_search_text.append(initialQuery);
    }
    update_gif_search_display();
    if (_gif_search_bar_view != null) {
      _gif_search_bar_view.setVisibility(View.VISIBLE);
    }
    CandidatesView candidatesView = _host.getCandidatesView();
    if (candidatesView != null) {
      candidatesView.setVisibility(View.GONE);
    }
    ViewGroup container = _host.getKeyboardContainerView();
    if (container != null) {
      _host.setInputView(container);
    }
  }

  public void close_gif_search(boolean returnToGifPane) {
    _gif_search_active = false;
    if (_gif_search_bar_view != null) {
      _gif_search_bar_view.setVisibility(View.GONE);
    }
    CandidatesView candidatesView = _host.getCandidatesView();
    if (candidatesView != null) {
      candidatesView.setVisibility(View.VISIBLE);
    }
    if (returnToGifPane) {
      show_gif_pane();
    }
  }

  public void submit_gif_search() {
    String query = _gif_search_text.toString().trim();
    close_gif_search(true);
    if (_gif_pane instanceof GifManagerView && !query.isEmpty()) {
      ((GifManagerView) _gif_pane).perform_search(query);
    }
  }

  public void append_gif_search_char(char c) {
    _gif_search_text.append(c);
    update_gif_search_display();
  }

  public void append_gif_search_string(String s) {
    _gif_search_text.append(s);
    update_gif_search_display();
  }

  public void delete_gif_search_char() {
    if (_gif_search_text.length() > 0) {
      _gif_search_text.deleteCharAt(_gif_search_text.length() - 1);
      update_gif_search_display();
    }
  }

  private void update_gif_search_display() {
    if (_tv_gif_search_query != null) {
      _tv_gif_search_query.setText(_gif_search_text.toString());
    }
    if (_btn_gif_search_clear != null) {
      _btn_gif_search_clear.setVisibility(_gif_search_text.length() > 0 ? View.VISIBLE : View.GONE);
    }
  }

  public void onDismiss() {
    close_gif_search(false);
    Context ctx = _host.getContext();
    if (ctx != null) {
      try {
        Glide.get(ctx).clearMemory();
      } catch (Exception ignored) {}
    }
  }
}
