package com.nhs.customkeyboard.suggestions;

import android.content.Context;
import java.util.Arrays;
import java.util.List;
import nhs.cdict.Cdict;
import com.nhs.customkeyboard.dict.Dictionaries;
import com.nhs.customkeyboard.Config;
import com.nhs.customkeyboard.ComposeKey;
import com.nhs.customkeyboard.ComposeKeyData;

/**
 * Keep track of the word being typed and provide suggestions for [CandidatesView].
 * Supports:
 *  1. Dynamic user learning (custom words & bigram transitions).
 *  2. Static next-word prediction via NextWordPredictor (bn_bigrams.bin).
 *  3. Static dictionary prefix autocomplete via cdict (bn.dict).
 *  4. Bilingual & phonetic emoji shortcut suggestions.
 *  5. Sentence boundary suppression (., ।, ?, !).
 */
public final class Suggestions
{
  Callback _callback;
  Config _config;
  Context _context;
  boolean _enabled;

  /** Current suggestions. The best suggestion is at index [0]. */
  public String[] suggestions = new String[MAX_COUNT];
  /** Number of suggestions at the beginning of the [suggestions] array that are not [null]. */
  public int count = 0;
  public String emoji_suggestion = null;
  /** Tracks the word for which next-word predictions are currently active. */
  private String _active_predicted_word = null;
  /** Number of suggestions in [suggestions]. */
  public static final int MAX_COUNT = 20;

  public Suggestions(Callback c, Config conf)
  {
    this(c, conf, null);
  }

  public Suggestions(Callback c, Config conf, Context context)
  {
    _callback = c;
    _config = conf;
    _context = (context != null) ? context.getApplicationContext() : null;
  }

  public void setContext(Context context)
  {
    if (context != null && _context == null)
    {
      _context = context.getApplicationContext();
    }
  }

  private int _batch_count = 0;
  private boolean _pending_callback = false;

  public void begin_batch()
  {
    _batch_count++;
  }

  public void end_batch()
  {
    if (_batch_count > 0)
    {
      _batch_count--;
      if (_batch_count == 0 && _pending_callback)
      {
        _pending_callback = false;
        _callback.set_suggestions(this);
      }
    }
  }

  private void notify_callback()
  {
    if (_batch_count > 0)
    {
      _pending_callback = true;
    }
    else
    {
      _callback.set_suggestions(this);
    }
  }

  public void started()
  {
    _enabled = _config.editor_config.should_show_candidates_view;
    clear();
  }

  public void currently_typed_word(String word)
  {
    if (!_enabled)
      return;
    if (word == null || word.isEmpty() || _config.current_dictionary == null)
    {
      // If next-word predictions are active for a word, do not wipe them out
      // merely because the active typing buffer is empty (e.g. cursor is in whitespace after a word).
      if (_active_predicted_word != null && count > 0)
      {
        return;
      }
      clear();
    }
    else
    {
      _active_predicted_word = null;
      query_suggestions(word);
    }
    notify_callback();
  }

  public void clear()
  {
    count = 0;
    for (int i = 0; i < MAX_COUNT; i++)
      suggestions[i] = null;
    emoji_suggestion = null;
    _active_predicted_word = null;
  }

  public void clear_predictions()
  {
    clear();
    notify_callback();
  }

  /**
   * Triggered when spacebar is pressed after completing [lastWord], or when the cursor
   * is positioned after whitespace following a word.
   * Predicts next likely words unless suppressed by a sentence boundary (. or ।).
   */
  public void on_space_pressed(String lastWord)
  {
    if (!_enabled || !_config.next_word_prediction_enabled)
    {
      clear_predictions();
      return;
    }

    if (lastWord == null || lastWord.isEmpty())
    {
      clear_predictions();
      return;
    }

    String clean = lastWord.trim();
    // Check sentence boundary punctuation (. or । or ॥ or ? or ! or newline)
    if (clean.endsWith(".") || clean.endsWith("\u0964") || clean.endsWith("\u0965") ||
        clean.endsWith("?") || clean.endsWith("!") || clean.contains("\n") || clean.contains("\r"))
    {
      clear_predictions();
      return;
    }

    // Ignore redundant/subsequent space presses for the same word.
    if (clean.equalsIgnoreCase(_active_predicted_word))
    {
      return;
    }

    clear();
    _active_predicted_word = clean;
    int i = 0;

    // Priority 1: User's personalized learned transitions
    if (_config.user_learning_enabled && _context != null)
    {
      UserLearningEngine engine = UserLearningEngine.getInstance(_context);
      List<String> userBigrams = engine.get_next_word_predictions(clean, 3);
      if (userBigrams != null)
      {
        for (String w : userBigrams)
        {
          if (i < MAX_COUNT && !contains(suggestions, i, w))
          {
            suggestions[i++] = w;
          }
        }
      }
    }

    // Priority 2: Static language bigram predictions (bn_bigrams.bin)
    if (_context != null)
    {
      NextWordPredictor predictor = NextWordPredictor.getInstance(_context);
      String[] staticBigrams = predictor.predict(clean);
      if (staticBigrams != null)
      {
        for (String w : staticBigrams)
        {
          if (i < MAX_COUNT && !contains(suggestions, i, w))
          {
            suggestions[i++] = w;
          }
        }
      }
    }

    count = i;
    emoji_suggestion = null;
    notify_callback();
  }

  int query_suggestions(String word)
  {
    Cdict dict = _config.current_dictionary;
    boolean first_char_upper = Character.isUpperCase(word.charAt(0));
    word = apply_substitutions(word);
    int i = 0;

    // Priority 1: Check user's learned custom words matching prefix
    if (_config.user_learning_enabled && _context != null)
    {
      UserLearningEngine engine = UserLearningEngine.getInstance(_context);
      List<String> learnedWords = engine.get_word_completions(word, 3);
      if (learnedWords != null)
      {
        for (String w : learnedWords)
        {
          if (i < MAX_COUNT && !contains(suggestions, i, w))
          {
            suggestions[i++] = w;
          }
        }
      }
    }

    // Priority 2: Static dictionary exact match and suffixes
    Cdict.Result r = dict.find(word);
    if (r.found && !contains(suggestions, i, dict.word(r.index)))
      suggestions[i++] = dict.word(r.index);

    int[] suffixes = dict.suffixes(r, MAX_COUNT);
    int[] dist = (word.length() < 3) ? NO_RESULTS :
      dict.distance(word, 1, MAX_COUNT);

    for (int j = 0; j < suffixes.length && i < MAX_COUNT; j++)
    {
      String w = dict.word(suffixes[j]);
      if (!contains(suggestions, i, w))
        suggestions[i++] = w;
    }
    for (int j = 0; j < dist.length && i < MAX_COUNT; j++)
    {
      String w = dict.word(dist[j]);
      if (!contains(suggestions, i, w))
        suggestions[i++] = w;
    }

    // If first character is uppercase, also query lowercase in dictionary
    if (first_char_upper && i < MAX_COUNT)
    {
      String lower = word.toLowerCase(java.util.Locale.ROOT);
      if (!lower.equals(word))
      {
        Cdict.Result rLower = dict.find(lower);
        if (rLower.found && !contains(suggestions, i, dict.word(rLower.index)))
          suggestions[i++] = dict.word(rLower.index);

        int[] suffixesLower = dict.suffixes(rLower, MAX_COUNT);
        for (int j = 0; j < suffixesLower.length && i < MAX_COUNT; j++)
        {
          String w = dict.word(suffixesLower[j]);
          if (!contains(suggestions, i, w))
            suggestions[i++] = w;
        }
      }
    }

    count = i;
    if (first_char_upper)
      capitalize_results();

    // Priority 3: Emoji shortcut lookup
    emoji_suggestion = query_emoji(word);
    return i;
  }

  private static boolean contains(String[] arr, int len, String target)
  {
    for (int k = 0; k < len; k++)
    {
      if (arr[k] != null && arr[k].equalsIgnoreCase(target))
        return true;
    }
    return false;
  }

  void capitalize_results()
  {
    for (int i = 0; i < count; i++)
    {
      if (suggestions[i] != null && !suggestions[i].isEmpty())
      {
        suggestions[i] = suggestions[i].substring(0, 1).toUpperCase()
          + suggestions[i].substring(1);
      }
    }
  }

  String query_emoji(String word)
  {
    Cdict dict = _config.emoji_dictionary;
    // Disable emoji suggestion for short words
    if (dict == null || word.length() < 2)
      return null;
    // 1. Exact match (Works for 2+ char complete words like "চা", "বউ", "মা")
    Cdict.Result r = dict.find(word);
    if (r.found)
      return dict.word(r.index);
    int[] s = dict.suffixes(r, 1);
    if (s.length > 0)
      return dict.word(s[0]);
    return null;
  }

  /** Apply the same substitutions that were used when building the
      dictionaries to find word aliases. This catches missing diacritics for
      example. */
  String apply_substitutions(String w)
  {
    StringBuilder b = new StringBuilder(w);
    int len = w.length();
    for (int i = 0; i < len; i++)
    {
      char r =
        ComposeKey.transform_char(ComposeKeyData.substitutions, b.charAt(i));
      if (r != 0) b.setCharAt(i, r);
    }
    return b.toString();
  }

  static final int[] NO_RESULTS = new int[0];

  public static interface Callback
  {
    public void set_suggestions(Suggestions suggestions);
  }
}
