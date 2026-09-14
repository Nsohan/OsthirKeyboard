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

  public static final class LearnedEmail
  {
    public final String email;
    public final String username;
    public final String domain;
    public int frequency;

    public LearnedEmail(String email, String username, String domain, int frequency)
    {
      this.email = email;
      this.username = username;
      this.domain = domain;
      this.frequency = frequency;
    }
  }

  // In-memory caches for microsecond lookups
  private final Map<String, Integer> _wordFrequencyCache = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Integer>> _bigramCache = new ConcurrentHashMap<>();
  private final List<LearnedEmail> _emailCache = new java.util.concurrent.CopyOnWriteArrayList<>();
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
        _db.cleanupCorruptedUserWords();

        // Load frequent words into memory with real frequencies
        List<String[]> topWords = _db.getTopWordsWithFrequency(1000);
        for (String[] row : topWords)
        {
          try
          {
            String w = row[0];
            int freq = Integer.parseInt(row[1]);
            _wordFrequencyCache.put(w, freq);
          }
          catch (Exception ignored) {}
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

        // Load frequent emails into memory
        List<String[]> topEmails = _db.getTopEmails(100);
        for (String[] row : topEmails)
        {
          try
          {
            int freq = Integer.parseInt(row[3]);
            _emailCache.add(new LearnedEmail(row[0], row[1], row[2], freq));
          }
          catch (Exception ignored) {}
        }

        _initialized = true;
        Log.i(TAG, "UserLearningEngine cache warmed up with " + topWords.size() + " words, " + topBigrams.size() + " bigrams, and " + topEmails.size() + " emails.");
      }
      catch (Exception e)
      {
        Logs.exn(TAG, e);
      }
    });
  }

  /**
   * Learns a newly typed or selected email address.
   */
  public void record_email(String email)
  {
    if (email == null) return;
    String clean = email.trim();
    int atIdx = clean.indexOf('@');
    if (atIdx <= 0 || atIdx >= clean.length() - 1) return;
    String username = clean.substring(0, atIdx).trim();
    String domain = clean.substring(atIdx).trim();
    if (username.isEmpty() || !domain.contains(".")) return;

    // Update memory cache immediately
    boolean found = false;
    for (LearnedEmail le : _emailCache)
    {
      if (le.email.equalsIgnoreCase(clean))
      {
        le.frequency++;
        found = true;
        break;
      }
    }
    if (!found)
    {
      _emailCache.add(0, new LearnedEmail(clean, username, domain, 1));
    }

    // Queue asynchronous DB write
    _executor.execute(() -> {
      _db.recordEmail(clean);
    });
  }

  /**
   * Learns a newly typed or selected word.
   */
  public void record_word(String word)
  {
    if (word == null || word.length() < 2 || word.contains("@")) return;
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
    List<Map.Entry<String, Integer>> candidates = new ArrayList<>();
    for (Map.Entry<String, Integer> entry : _wordFrequencyCache.entrySet())
    {
      String w = entry.getKey();
      if (w.contains("@")) continue;
      if (w.toLowerCase(Locale.ROOT).startsWith(pLower))
      {
        candidates.add(entry);
      }
    }

    if (!candidates.isEmpty())
    {
      // Sort: exact match first, then frequency DESC, then shorter length
      candidates.sort((a, b) -> {
        boolean aExact = a.getKey().equalsIgnoreCase(p);
        boolean bExact = b.getKey().equalsIgnoreCase(p);
        if (aExact != bExact) return aExact ? -1 : 1;
        int cmp = Integer.compare(b.getValue(), a.getValue());
        if (cmp != 0) return cmp;
        return Integer.compare(a.getKey().length(), b.getKey().length());
      });

      List<String> res = new ArrayList<>();
      for (int i = 0; i < Math.min(maxCount, candidates.size()); i++)
      {
        res.add(candidates.get(i).getKey());
      }
      return res;
    }

    return _db.getWordCompletions(p, maxCount);
  }

  /**
   * Gets personalized learned email suggestions matching prefix.
   */
  public List<String> get_matching_emails(String prefix, int maxCount)
  {
    if (prefix == null || prefix.isEmpty()) return Collections.emptyList();
    String p = prefix.trim().toLowerCase(Locale.ROOT);
    if (p.isEmpty()) return Collections.emptyList();

    List<String> res = new ArrayList<>();
    for (LearnedEmail le : _emailCache)
    {
      String u = le.username.toLowerCase(Locale.ROOT);
      String em = le.email.toLowerCase(Locale.ROOT);
      if (u.startsWith(p) || em.startsWith(p))
      {
        res.add(le.email);
        if (res.size() >= maxCount) return res;
      }
    }
    if (!res.isEmpty()) return res;

    return _db.getMatchingEmails(prefix, maxCount);
  }

  public boolean is_learned_word(String word)
  {
    if (word == null || word.isEmpty()) return false;
    String clean = word.trim();
    if (_wordFrequencyCache.containsKey(clean)) return true;
    for (LearnedEmail le : _emailCache)
    {
      if (le.email.equalsIgnoreCase(clean)) return true;
    }
    if (_bigramCache.containsKey(clean)) return true;
    for (Map<String, Integer> succs : _bigramCache.values())
    {
      if (succs.containsKey(clean)) return true;
    }
    return _db.isLearnedWord(clean);
  }

  public void delete_learned_word(String word)
  {
    if (word == null || word.isEmpty()) return;
    String clean = word.trim();
    _wordFrequencyCache.remove(clean);
    _emailCache.removeIf(le -> le.email.equalsIgnoreCase(clean));
    _bigramCache.remove(clean);
    for (Map<String, Integer> succs : _bigramCache.values())
    {
      succs.remove(clean);
    }
    _executor.execute(() -> {
      _db.deleteWord(clean);
    });
  }

  public void clearAll()
  {
    _wordFrequencyCache.clear();
    _bigramCache.clear();
    _emailCache.clear();
    _executor.execute(_db::clearAllLearnedData);
  }
}
