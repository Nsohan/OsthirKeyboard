package com.nhs.customkeyboard.avro;

import android.view.inputmethod.InputConnection;
import com.nhs.customkeyboard.KeymapEngine.WordTrackerCallback;

/**
 * Manages the active typing session for the Avro layout.
 * Buffers English phonetic keystrokes, triggers AvroParser,
 * updates the input field via InputConnection batch edits,
 * and maintains proper stepwise backspace behavior.
 */
public final class AvroEngine
{
  private static final AvroEngine INSTANCE = new AvroEngine();

  public static AvroEngine get()
  {
    return INSTANCE;
  }

  private final StringBuilder rawBuffer = new StringBuilder();
  private int lastCommittedLen = 0;
  private String lastParsed = "";
  private boolean active = false;
  private int selfEditCount = 0;
  private int lastConsumedSelfEditCount = 0;

  private AvroEngine() {}

  public void set_active(boolean active)
  {
    this.active = active;
    if (!active)
    {
      reset();
    }
  }

  public boolean is_active()
  {
    return active;
  }

  public boolean has_pending()
  {
    return rawBuffer.length() > 0;
  }

  public boolean consume_self_edit()
  {
    if (selfEditCount != lastConsumedSelfEditCount)
    {
      lastConsumedSelfEditCount = selfEditCount;
      return true;
    }
    return false;
  }

  /**
   * Resets the active buffer without altering text in the editor.
   * Typically invoked when cursor moves, layout changes, or word finishes.
   */
  public void reset()
  {
    rawBuffer.setLength(0);
    lastCommittedLen = 0;
    lastParsed = "";
    lastConsumedSelfEditCount = selfEditCount;
  }

  /**
   * Handles a character typed while Avro layout is active.
   *
   * @return true if character was consumed by the Avro engine, false otherwise.
   */
  public boolean handle_char(InputConnection conn, char c, WordTrackerCallback wt)
  {
    if (!active || conn == null)
      return false;

    // Check if character is a valid phonetic input token (Latin letter, caret for chandrabindu, or backtick escape)
    if (isPhoneticChar(c))
    {
      rawBuffer.append(c);
      String parsed = AvroParser.parse(rawBuffer.toString());

      selfEditCount++;
      conn.beginBatchEdit();
      if (lastCommittedLen > 0)
      {
        conn.deleteSurroundingText(lastCommittedLen, 0);
      }
      conn.commitText(parsed, 1);
      conn.endBatchEdit();

      if (wt != null)
      {
        if (lastCommittedLen > 0)
        {
          wt.remove_surrounding_text(lastCommittedLen, 0);
        }
        wt.typed(parsed);
      }

      lastCommittedLen = parsed.length();
      lastParsed = parsed;
      return true;
    }

    // Whitespace, numbers, or punctuation: finalize current word
    if (rawBuffer.length() > 0)
    {
      reset();
    }

    return false;
  }

  /**
   * Handles Backspace when Avro engine has pending characters in buffer.
   *
   * @return true if backspace was consumed and handled, false to fall back to normal delete.
   */
  public boolean handle_backspace(InputConnection conn, WordTrackerCallback wt)
  {
    if (!active || rawBuffer.length() == 0 || conn == null)
      return false;

    rawBuffer.deleteCharAt(rawBuffer.length() - 1);

    selfEditCount++;
    conn.beginBatchEdit();
    if (lastCommittedLen > 0)
    {
      conn.deleteSurroundingText(lastCommittedLen, 0);
    }

    if (rawBuffer.length() == 0)
    {
      conn.endBatchEdit();
      if (wt != null && lastCommittedLen > 0)
      {
        wt.remove_surrounding_text(lastCommittedLen, 0);
      }
      reset();
    }
    else
    {
      String parsed = AvroParser.parse(rawBuffer.toString());
      conn.commitText(parsed, 1);
      conn.endBatchEdit();

      if (wt != null)
      {
        if (lastCommittedLen > 0)
        {
          wt.remove_surrounding_text(lastCommittedLen, 0);
        }
        wt.typed(parsed);
      }

      lastCommittedLen = parsed.length();
      lastParsed = parsed;
    }

    return true;
  }

  private static boolean isPhoneticChar(char c)
  {
    return (c >= 'a' && c <= 'z') ||
           (c >= 'A' && c <= 'Z') ||
           c == '^' || c == '`';
  }
}
