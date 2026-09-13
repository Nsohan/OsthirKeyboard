package com.nhs.customkeyboard;

import android.os.Build.VERSION;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.SurroundingText;
import java.util.List;

/** Keep track of the word being typed. This also tracks whether the selection
 is empty. */
public final class CurrentlyTypedWord
{
  InputConnection _ic = null;
  Handler _handler;
  Callback _callback;

  /** The currently typed word. */
  StringBuilder _w = new StringBuilder();
  /** This can be disabled if the editor doesn't support looking at the text
   before the cursor. */
  boolean _enabled = false;
  /** The current word is empty while the selection is ongoing. */
  boolean _has_selection = false;
  /** Used to avoid concurrent refreshes in [delayed_refresh()]. */
  boolean _refresh_pending = false;

  /** The estimated cursor position in code points. Used to avoid expensive IPC
   calls when the typed word can be estimated locally with [typed]. When the
   cursor position gets out of sync, the text before the cursor is queried
   again to the editor. */
  int _cursor;
  /** The cursor position within the current word relative to the end of the
   word in chars. Equal to [0] when the cursor is at the end of the word. */
  int _w_cursor;

  public CurrentlyTypedWord(Handler h, Callback cb)
  {
    _handler = h;
    _callback = cb;
  }

  public String get()
  {
    return _w.toString();
  }

  public boolean is_selection_not_empty()
  {
    return _has_selection;
  }

  /** The cursor position relative to the end of the word. */
  public int cursor_relative()
  {
    return _w_cursor;
  }

  public void started(Config conf, InputConnection ic)
  {
    _ic = ic;
    _enabled = true;
    EditorConfig e = conf.editor_config;
    _has_selection = e.initial_sel_start != e.initial_sel_end;
    _cursor = e.initial_sel_start;
    _w_cursor = 0;
    if (!_has_selection)
    {
      set_current_word(e.initial_text_before_cursor);
      if (_w.length() > 0 && e.initial_text_after_cursor != null)
      {
        _w_cursor = -append_chars(e.initial_text_after_cursor);
      }
    }
  }

  public void typed(String s)
  {
    if (!_enabled)
      return;
    _has_selection = false;
    type_chars(s);
    callback();
  }

  public void selection_updated(int oldSelStart, int newSelStart, int newSelEnd)
  {
    if (!_enabled)
      return;
    boolean new_has_sel = newSelStart != newSelEnd;
    if (new_has_sel || _has_selection)
    {
      _cursor = newSelStart;
      _has_selection = new_has_sel;
      refresh_current_word();
    }
    else if (newSelStart != _cursor)
    {
      _cursor = newSelStart;
      _w_cursor = 0;
      refresh_current_word();
    }
  }

  public void event_sent(int code, int meta)
  {
    if (!_enabled)
      return;
    switch (code)
    {
      case KeyEvent.KEYCODE_DEL:
        if (meta == 0)
        {
          remove_surrounding_text(1, 0);
          refresh_current_word();
        }
        else
        {
          delayed_refresh();
        }
        break;
      case KeyEvent.KEYCODE_ENTER:
      case KeyEvent.KEYCODE_NUMPAD_ENTER:
        _w.setLength(0);
        _w_cursor = 0;
        delayed_refresh();
        break;
      default:
        delayed_refresh();
        break;
    }
  }

  public void clear()
  {
    _w.setLength(0);
    _w_cursor = 0;
    callback();
  }

  public void remove_surrounding_text(int remove_before, int remove_after)
  {
    if (!_enabled)
      return;
    int len = _w.length();
    int c = len + _w_cursor;
    int del_start = Math.max(c - remove_before, 0);
    int del_end = Math.max(Math.min(c + remove_after, len), del_start);
    _w.delete(del_start, del_end);
    _cursor -= remove_before;
    _w_cursor -= Math.min(remove_after, 0);
    if (_w.length() == 0)
    {
      refresh_current_word();
    }
    else
    {
      callback();
    }
  }

  void callback()
  {
    String w = _w.toString();
    _callback.currently_typed_word(w);
  }

  /** Estimate the currently typed word after [chars] has been typed. */
  void type_chars(CharSequence s, int start, int end)
  {
    int insert_start = 0;
    // Iterate over code points as that's the unit of [_cursor].
    for (int i = start; i < end;)
    {
      int c = Character.codePointAt(s, i);
      i += Character.charCount(c);
      _cursor++;
      if (!is_word_char(c) && i <= end)
        insert_start = i;
    }
    if (insert_start > 0)
    {
      int split = Math.max(Math.min(_w.length() + _w_cursor, _w.length()), 0);
      _w.delete(0, split);
      _w_cursor = -_w.length();
    }
    _w.insert(Math.max(_w.length() + _w_cursor, 0), s, insert_start, end);
  }

  void type_chars(CharSequence s)
  {
    type_chars(s, 0, s.length());
  }

  int append_chars(CharSequence s, int start, int end)
  {
    int i = start;
    while (i < end)
    {
      int c = Character.codePointAt(s, i);
      if (!is_word_char(c))
        break;
      _w.appendCodePoint(c);
      i += Character.charCount(c);
    }
    return i - start;
  }

  int append_chars(CharSequence s)
  {
    return append_chars(s, 0, s.length());
  }

  /** Refresh the current word by immediately querying the editor. */
  void refresh_current_word()
  {
    Logs.debug("Refresh current word");
    _refresh_pending = false;
    _w_cursor = 0;
    if (_ic == null)
      return;
    if (_has_selection)
    {
      set_current_word("");
      return;
    }
    if (VERSION.SDK_INT >= 31)
    {
      SurroundingText st = _ic.getSurroundingText(100, 50, 0);
      if (st != null && st.getText() != null)
      {
        set_current_word(st);
        return;
      }
    }
    set_current_word(_ic.getTextBeforeCursor(100, 0));
  }

  public static boolean is_sentence_terminator(char c)
  {
    return c == '.' || c == '\u0964' || c == '\u0965' || c == '?' || c == '!' || c == '\n' || c == '\r';
  }

  public static boolean is_space_char(char c)
  {
    return (c == ' ' || c == '\t' || c == '\u00A0' || Character.isWhitespace(c)) && !is_sentence_terminator(c);
  }

  public static String find_preceding_word(CharSequence text)
  {
    if (text == null || text.length() == 0)
      return null;
    int i = text.length() - 1;
    // Skip all trailing horizontal space(s) on the current line (never skip \n or \r)
    while (i >= 0 && is_space_char(text.charAt(i)))
    {
      i--;
    }
    if (i < 0)
      return null;

    char c = text.charAt(i);
    // If preceded by sentence terminator (including \n, \r, ., ?, !), terminate
    if (is_sentence_terminator(c))
      return null;

    // Collect word characters walking backwards
    int end = i + 1;
    while (i >= 0 && is_word_char(text.charAt(i)) && !is_sentence_terminator(text.charAt(i)))
    {
      i--;
    }
    int start = i + 1;
    if (start >= end)
      return null;

    String word = text.subSequence(start, end).toString().trim();
    if (word.isEmpty())
      return null;
    for (int j = 0; j < word.length(); j++)
    {
      if (is_sentence_terminator(word.charAt(j)))
        return null;
    }
    return word;
  }

  /** Refresh the current word by immediately querying the editor. */
  void set_current_word(CharSequence text_before_cursor)
  {
    _w.setLength(0);
    _w_cursor = 0;
    if (text_before_cursor == null || text_before_cursor.length() == 0)
    {
      _callback.cursor_in_whitespace(null);
      return;
    }
    char lastChar = text_before_cursor.charAt(text_before_cursor.length() - 1);
    if (is_sentence_terminator(lastChar))
    {
      // Newline, carriage return, period, question mark, etc. terminate predictions immediately
      _callback.cursor_in_whitespace(null);
      return;
    }
    if (is_space_char(lastChar))
    {
      // Cursor is preceded by horizontal space(s) (Scenario 1: e.g. "Hello   how   are   |")
      String precedingWord = find_preceding_word(text_before_cursor);
      _callback.cursor_in_whitespace(precedingWord);
      return;
    }
    int saved_cursor = _cursor;
    type_chars(text_before_cursor.toString());
    _cursor = saved_cursor;
    callback();
  }

  /** Like above but take the text after the cursor into account. */
  void set_current_word(SurroundingText st)
  {
    _w.setLength(0);
    _w_cursor = 0;
    if (st == null || st.getText() == null || st.getText().length() == 0)
    {
      _callback.cursor_in_whitespace(null);
      return;
    }
    int st_sel = st.getSelectionStart();
    CharSequence st_text = st.getText();
    if (st_sel <= 0)
    {
      _callback.cursor_in_whitespace(null);
      return;
    }
    CharSequence textBeforeCursor = st_text.subSequence(0, st_sel);
    char lastChar = textBeforeCursor.charAt(textBeforeCursor.length() - 1);
    if (is_sentence_terminator(lastChar))
    {
      // Newline, carriage return, period, question mark, etc. terminate predictions immediately
      _callback.cursor_in_whitespace(null);
      return;
    }
    if (is_space_char(lastChar))
    {
      // Cursor is preceded by horizontal space(s)
      String precedingWord = find_preceding_word(textBeforeCursor);
      _callback.cursor_in_whitespace(precedingWord);
      return;
    }
    int saved_cursor = _cursor;
    type_chars(st_text, 0, st_sel);
    _w_cursor = -append_chars(st_text, st_sel, st_text.length());
    _cursor = saved_cursor;
    callback();
  }

  void delayed_refresh()
  {
    _refresh_pending = true;
    _handler.postDelayed(delayed_refresh_run, 50);
  }

  Runnable delayed_refresh_run = new Runnable()
  {
    public void run()
    {
      if (_refresh_pending)
        refresh_current_word();
    }
  };

  public static boolean is_word_char(int c)
  {
    if (Character.isLetterOrDigit(c) || c == '\'')
      return true;
    int type = Character.getType(c);
    return type == Character.NON_SPACING_MARK
            || type == Character.COMBINING_SPACING_MARK;
  }

  public static interface Callback
  {
    public void currently_typed_word(String word);
    public void cursor_in_whitespace(String precedingWord);
  }
}
