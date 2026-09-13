package com.nhs.customkeyboard.suggestions;

import android.content.Context;
import android.util.Log;
import com.nhs.customkeyboard.Logs;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fast in-memory caching and background persistence manager for user learning.
 * Ensures zero lag / zero latency during keystroke dispatch.
 */
public final class UserLearningEngine
{
  private static final String TAG = "UserLearningEngine";
  private static volatile UserLearningEngine sInstance;

  private final Context _context;
  private final UserLearningDatabase _db;
  private final ExecutorService _executor = Executors.newSingleThreadExecutor();

  // In-memory caches for microsecond lookups
  private final Map<String, Integer> _wordFrequencyCache = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Integer>> _bigramCache = new ConcurrentHashMap<>();
  private boolean _initialized = false;

  public static UserLearningEngine getInstance(Context context)
  {
    if (sInstance == null)
    {
      synchronized (UserLearningEngine.class)
      {
        if (sInstance == null)
        {
          sInstance = new UserLearningEngine(context.getApplicationContext());
        }
      }
    }
    return sInstance;
  }

  private UserLearningEngine(Context context)
  {
    _context = context;
    _db = UserLearningDatabase.getInstance(context);
    warmUpCache();
  }

  private void warmUpCache()
  {
    _executor.execute(() -> {
      try
      {
        // Load frequent words into memory
        List<String> topWords = _db.getTopWords(500);
        for (String w : topWords)
        {
          _wordFrequencyCache.put(w, 1);
        }

        // Load frequent bigrams into memory
        List<String[]> topBigrams = _db.getTopBigrams(1000);
        for (String[] row : topBigrams)
        {
          String w1 = row[0];
          String w2 = row[1];
          try
          {
            int freq = Integer.parseInt(row[2]);
            _bigramCache.computeIfAbsent(w1, k -> new ConcurrentHashMap<>()).put(w2, freq);
          }
          catch (Exception ignored) {}
        }

        _initialized = true;
        Log.i(TAG, "UserLearningEngine cache warmed up with " + topWords.size() + " words and " + topBigrams.size() + " bigrams.");
      }
      catch (Exception e)
      {
        Logs.exn(TAG, e);
      }
    });
  }

  /**
   * Learns a newly typed or selected word.
   */
  public void record_word(String word)
  {
    if (word == null || word.length() < 2) return;
    String clean = word.trim();
    if (clean.isEmpty()) return;

    // Update memory cache immediately
    _wordFrequencyCache.put(clean, _wordFrequencyCache.getOrDefault(clean, 0) + 1);

    // Queue asynchronous DB write
    _executor.execute(() -> {
      _db.recordWord(clean);
    });
  }

  /**
   * Learns a transition pair (w1 -> w2).
   */
  public void record_transition(String w1, String w2)
  {
    if (w1 == null || w2 == null) return;
    String c1 = w1.trim();
    String c2 = w2.trim();
    if (c1.isEmpty() || c2.isEmpty() || c1.equals(c2)) return;

    // Update memory cache immediately
    Map<String, Integer> succs = _bigramCache.computeIfAbsent(c1, k -> new ConcurrentHashMap<>());
    succs.put(c2, succs.getOrDefault(c2, 0) + 1);

    // Also record individual words
    record_word(c1);
    record_word(c2);

    // Queue asynchronous DB write
    _executor.execute(() -> {
      _db.recordBigram(c1, c2);
    });
  }

  /**
   * Gets personalized learned next words for w1.
   */
  public List<String> get_next_word_predictions(String w1, int maxCount)
  {
    if (w1 == null || w1.isEmpty()) return Collections.emptyList();
    String c1 = w1.trim();

    Map<String, Integer> succs = _bigramCache.get(c1);
    if (succs != null && !succs.isEmpty())
    {
      List<Map.Entry<String, Integer>> entries = new ArrayList<>(succs.entrySet());
      entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
      List<String> res = new ArrayList<>();
      for (int i = 0; i < Math.min(maxCount, entries.size()); i++)
      {
        res.add(entries.get(i).getKey());
      }
      return res;
    }

    // If not in hot cache, query DB
    return _db.getNextWords(c1, maxCount);
  }

  /**
   * Gets personalized learned word completions matching prefix.
   */
  public List<String> get_word_completions(String prefix, int maxCount)
  {
    if (prefix == null || prefix.isEmpty()) return Collections.emptyList();
    String p = prefix.trim();
    if (p.isEmpty()) return Collections.emptyList();

    String pLower = p.toLowerCase(Locale.ROOT);
    List<String> matches = new ArrayList<>();
    for (String w : _wordFrequencyCache.keySet())
    {
      if (w.toLowerCase(Locale.ROOT).startsWith(pLower))
      {
        matches.add(w);
        if (matches.size() >= maxCount) break;
      }
    }
    if (!matches.isEmpty()) return matches;

    return _db.getWordCompletions(p, maxCount);
  }

  public void clearAll()
  {
    _wordFrequencyCache.clear();
    _bigramCache.clear();
    _executor.execute(_db::clearAllLearnedData);
  }
}
