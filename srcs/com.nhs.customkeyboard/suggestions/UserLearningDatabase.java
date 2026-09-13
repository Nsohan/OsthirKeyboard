package com.nhs.customkeyboard.suggestions;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.UserDictionary;
import android.util.Log;
import com.nhs.customkeyboard.Logs;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SQLite Database Helper managing on-device user learned words and bigram transitions.
 * Supports JSON Export/Import and synchronization with Android's System UserDictionary.
 */
public final class UserLearningDatabase extends SQLiteOpenHelper
{
  private static final String TAG = "UserLearningDB";
  private static final String DB_NAME = "UserLearning.db";
  private static final int DB_VERSION = 1;

  private static volatile UserLearningDatabase sInstance;

  public static UserLearningDatabase getInstance(Context context)
  {
    if (sInstance == null)
    {
      synchronized (UserLearningDatabase.class)
      {
        if (sInstance == null)
        {
          sInstance = new UserLearningDatabase(context.getApplicationContext());
        }
      }
    }
    return sInstance;
  }

  private UserLearningDatabase(Context context)
  {
    super(context, DB_NAME, null, DB_VERSION);
  }

  @Override
  public void onCreate(SQLiteDatabase db)
  {
    db.execSQL("CREATE TABLE IF NOT EXISTS user_words ("
        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
        + "word TEXT UNIQUE NOT NULL, "
        + "frequency INTEGER DEFAULT 1, "
        + "last_used INTEGER"
        + ");");

    db.execSQL("CREATE TABLE IF NOT EXISTS user_bigrams ("
        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
        + "w1 TEXT NOT NULL, "
        + "w2 TEXT NOT NULL, "
        + "frequency INTEGER DEFAULT 1, "
        + "last_used INTEGER, "
        + "UNIQUE(w1, w2)"
        + ");");

    db.execSQL("CREATE INDEX IF NOT EXISTS idx_user_words_word ON user_words(word);");
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_user_bigrams_w1 ON user_bigrams(w1);");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
  {
    // Schema version 1
  }

  public synchronized void recordWord(String word)
  {
    if (word == null || word.length() < 2) return;
    long now = System.currentTimeMillis();
    SQLiteDatabase db = getWritableDatabase();
    try
    {
      db.execSQL("INSERT OR IGNORE INTO user_words (word, frequency, last_used) VALUES (?, 0, ?);",
          new Object[]{word, now});
      db.execSQL("UPDATE user_words SET frequency = frequency + 1, last_used = ? WHERE word = ?;",
          new Object[]{now, word});
    }
    catch (Exception e)
    {
      Logs.exn(TAG, e);
    }
  }

  public synchronized void recordBigram(String w1, String w2)
  {
    if (w1 == null || w2 == null || w1.isEmpty() || w2.isEmpty() || w1.equals(w2)) return;
    long now = System.currentTimeMillis();
    SQLiteDatabase db = getWritableDatabase();
    try
    {
      db.execSQL("INSERT OR IGNORE INTO user_bigrams (w1, w2, frequency, last_used) VALUES (?, ?, 0, ?);",
          new Object[]{w1, w2, now});
      db.execSQL("UPDATE user_bigrams SET frequency = frequency + 1, last_used = ? WHERE w1 = ? AND w2 = ?;",
          new Object[]{now, w1, w2});
    }
    catch (Exception e)
    {
      Logs.exn(TAG, e);
    }
  }

  public synchronized List<String> getTopWords(int limit)
  {
    List<String> list = new ArrayList<>();
    SQLiteDatabase db = getReadableDatabase();
    try (Cursor c = db.rawQuery(
        "SELECT word FROM user_words ORDER BY frequency DESC, last_used DESC LIMIT ?",
        new String[]{String.valueOf(limit)}))
    {
      while (c.moveToNext())
      {
        list.add(c.getString(0));
      }
    }
    catch (Exception e)
    {
      Logs.exn(TAG, e);
    }
    return list;
  }

  public synchronized List<String[]> getTopBigrams(int limit)
  {
    List<String[]> list = new ArrayList<>();
    SQLiteDatabase db = getReadableDatabase();
    try (Cursor c = db.rawQuery(
        "SELECT w1, w2, frequency FROM user_bigrams ORDER BY frequency DESC, last_used DESC LIMIT ?",
        new String[]{String.valueOf(limit)}))
    {
      while (c.moveToNext())
      {
        list.add(new String[]{c.getString(0), c.getString(1), String.valueOf(c.getInt(2))});
      }
    }
    catch (Exception e)
    {
      Logs.exn(TAG, e);
    }
    return list;
  }

  public synchronized List<String> getWordCompletions(String prefix, int limit)
  {
    List<String> list = new ArrayList<>();
    if (prefix == null || prefix.isEmpty()) return list;
    SQLiteDatabase db = getReadableDatabase();
    try (Cursor c = db.rawQuery(
        "SELECT word FROM user_words WHERE word LIKE ? ORDER BY frequency DESC, last_used DESC LIMIT ?",
        new String[]{prefix + "%", String.valueOf(limit)}))
    {
      while (c.moveToNext())
      {
        list.add(c.getString(0));
      }
    }
    catch (Exception e)
    {
      Logs.exn(TAG, e);
    }
    return list;
  }

  public synchronized List<String> getNextWords(String w1, int limit)
  {
    List<String> list = new ArrayList<>();
    if (w1 == null || w1.isEmpty()) return list;
    SQLiteDatabase db = getReadableDatabase();
    try (Cursor c = db.rawQuery(
        "SELECT w2 FROM user_bigrams WHERE w1 = ? ORDER BY frequency DESC, last_used DESC LIMIT ?",
        new String[]{w1, String.valueOf(limit)}))
    {
      while (c.moveToNext())
      {
        list.add(c.getString(0));
      }
    }
    catch (Exception e)
    {
      Logs.exn(TAG, e);
    }
    return list;
  }

  public synchronized void clearAllLearnedData()
  {
    SQLiteDatabase db = getWritableDatabase();
    try
    {
      db.execSQL("DELETE FROM user_words;");
      db.execSQL("DELETE FROM user_bigrams;");
      db.execSQL("VACUUM;");
      Log.i(TAG, "Cleared all user learned words and transitions.");
    }
    catch (Exception e)
    {
      Logs.exn(TAG, e);
    }
  }

  public synchronized int getLearnedWordCount()
  {
    SQLiteDatabase db = getReadableDatabase();
    try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM user_words", null))
    {
      if (c.moveToFirst()) return c.getInt(0);
    }
    catch (Exception e)
    {
      Logs.exn(TAG, e);
    }
    return 0;
  }

  public synchronized int getLearnedBigramCount()
  {
    SQLiteDatabase db = getReadableDatabase();
    try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM user_bigrams", null))
    {
      if (c.moveToFirst()) return c.getInt(0);
    }
    catch (Exception e)
    {
      Logs.exn(TAG, e);
    }
    return 0;
  }

  /**
   * Exports learned words and transitions into a JSON file for backup.
   */
  public synchronized boolean exportToJson(File destFile)
  {
    try
    {
      JSONObject root = new JSONObject();
      root.put("version", 1);
      root.put("timestamp", System.currentTimeMillis());

      // Export Words
      JSONArray wordsArray = new JSONArray();
      SQLiteDatabase db = getReadableDatabase();
      try (Cursor c = db.rawQuery("SELECT word, frequency, last_used FROM user_words", null))
      {
        while (c.moveToNext())
        {
          JSONObject obj = new JSONObject();
          obj.put("word", c.getString(0));
          obj.put("frequency", c.getInt(1));
          obj.put("last_used", c.getLong(2));
          wordsArray.put(obj);
        }
      }
      root.put("user_words", wordsArray);

      // Export Bigrams
      JSONArray bigramsArray = new JSONArray();
      try (Cursor c = db.rawQuery("SELECT w1, w2, frequency, last_used FROM user_bigrams", null))
      {
        while (c.moveToNext())
        {
          JSONObject obj = new JSONObject();
          obj.put("w1", c.getString(0));
          obj.put("w2", c.getString(1));
          obj.put("frequency", c.getInt(2));
          obj.put("last_used", c.getLong(3));
          bigramsArray.put(obj);
        }
      }
      root.put("user_bigrams", bigramsArray);

      try (FileOutputStream fos = new FileOutputStream(destFile);
           OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8))
      {
        writer.write(root.toString(2));
      }
      return true;
    }
    catch (Exception e)
    {
      Logs.exn(TAG, e);
      return false;
    }
  }

  /**
   * Imports and merges learned words and transitions from a backup JSON stream.
   */
  public synchronized boolean importFromJson(InputStream inputStream)
  {
    try
    {
      StringBuilder sb = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)))
      {
        String line;
        while ((line = reader.readLine()) != null)
        {
          sb.append(line);
        }
      }
      JSONObject root = new JSONObject(sb.toString());

      SQLiteDatabase db = getWritableDatabase();
      db.beginTransaction();
      try
      {
        if (root.has("user_words"))
        {
          JSONArray words = root.getJSONArray("user_words");
          for (int i = 0; i < words.length(); i++)
          {
            JSONObject obj = words.getJSONObject(i);
            String word = obj.getString("word");
            int freq = obj.optInt("frequency", 1);
            long lastUsed = obj.optLong("last_used", System.currentTimeMillis());
            db.execSQL("INSERT OR IGNORE INTO user_words (word, frequency, last_used) VALUES (?, ?, ?);",
                new Object[]{word, freq, lastUsed});
            db.execSQL("UPDATE user_words SET frequency = MAX(frequency, ?), last_used = MAX(last_used, ?) WHERE word = ?;",
                new Object[]{freq, lastUsed, word});
          }
        }

        if (root.has("user_bigrams"))
        {
          JSONArray bigrams = root.getJSONArray("user_bigrams");
          for (int i = 0; i < bigrams.length(); i++)
          {
            JSONObject obj = bigrams.getJSONObject(i);
            String w1 = obj.getString("w1");
            String w2 = obj.getString("w2");
            int freq = obj.optInt("frequency", 1);
            long lastUsed = obj.optLong("last_used", System.currentTimeMillis());
            db.execSQL("INSERT OR IGNORE INTO user_bigrams (w1, w2, frequency, last_used) VALUES (?, ?, ?, ?);",
                new Object[]{w1, w2, freq, lastUsed});
            db.execSQL("UPDATE user_bigrams SET frequency = MAX(frequency, ?), last_used = MAX(last_used, ?) WHERE w1 = ? AND w2 = ?;",
                new Object[]{freq, lastUsed, w1, w2});
          }
        }
        db.setTransactionSuccessful();
        return true;
      }
      finally
      {
        db.endTransaction();
      }
    }
    catch (Exception e)
    {
      Logs.exn(TAG, e);
      return false;
    }
  }

  /**
   * Bi-directionally synchronizes custom words with Android System UserDictionary.
   */
  public synchronized int syncWithSystemUserDictionary(Context context)
  {
    int syncedCount = 0;
    try
    {
      ContentResolver resolver = context.getContentResolver();
      // 1. Pull system words into our SQLite database
      Uri uri = UserDictionary.Words.CONTENT_URI;
      try (Cursor c = resolver.query(uri, new String[]{UserDictionary.Words.WORD, UserDictionary.Words.FREQUENCY}, null, null, null))
      {
        if (c != null)
        {
          while (c.moveToNext())
          {
            String w = c.getString(0);
            if (w != null && w.length() >= 2)
            {
              recordWord(w);
              syncedCount++;
            }
          }
        }
      }

      // 2. Push our SQLite words to system dictionary
      SQLiteDatabase db = getReadableDatabase();
      try (Cursor c = db.rawQuery("SELECT word, frequency FROM user_words", null))
      {
        while (c.moveToNext())
        {
          String word = c.getString(0);
          int freq = Math.min(255, Math.max(1, c.getInt(1)));
          try
          {
            UserDictionary.Words.addWord(context, word, freq, null, Locale.getDefault());
          }
          catch (Exception ignored) {}
        }
      }
    }
    catch (Exception e)
    {
      Logs.exn(TAG, e);
    }
    return syncedCount;
  }
}
