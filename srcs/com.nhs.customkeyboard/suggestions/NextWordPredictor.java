package com.nhs.customkeyboard.suggestions;

import android.content.Context;
import android.util.Log;
import com.nhs.customkeyboard.Logs;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * High-performance binary search index reader for next-word prediction.
 * Supports both Bengali (bn_bigrams.bin) and English (en_bigrams.bin).
 * Performs sub-millisecond next-word lookups using direct ByteBuffers.
 */
public final class NextWordPredictor
{
  private static final String TAG = "NextWordPredictor";
  private static final String BN_ASSET_FILE = "dictionaries/bn_bigrams.bin";
  private static final String EN_ASSET_FILE = "dictionaries/en_bigrams.bin";
  private static volatile NextWordPredictor sInstance;

  private final BinaryIndex _bnIndex;
  private final BinaryIndex _enIndex;

  public static NextWordPredictor getInstance(Context context)
  {
    if (sInstance == null)
    {
      synchronized (NextWordPredictor.class)
      {
        if (sInstance == null)
        {
          sInstance = new NextWordPredictor(context.getApplicationContext());
        }
      }
    }
    return sInstance;
  }

  private NextWordPredictor(Context context)
  {
    _bnIndex = new BinaryIndex(context, BN_ASSET_FILE);
    _enIndex = new BinaryIndex(context, EN_ASSET_FILE);
  }

  public boolean isLoaded()
  {
    return (_bnIndex != null && _bnIndex.isLoaded()) || (_enIndex != null && _enIndex.isLoaded());
  }

  public boolean isBengaliLoaded()
  {
    return _bnIndex != null && _bnIndex.isLoaded();
  }

  public boolean isEnglishLoaded()
  {
    return _enIndex != null && _enIndex.isLoaded();
  }

  /**
   * Predicts top next words following [word].
   * Automatically routes to Bengali index (bn_bigrams.bin) if [word] contains
   * Bengali characters, otherwise routes to English index (en_bigrams.bin).
   */
  public String[] predict(String word)
  {
    if (word == null || word.isEmpty())
      return new String[0];

    String clean = word.trim();
    if (clean.isEmpty())
      return new String[0];

    if (isBengali(clean))
    {
      return (_bnIndex != null) ? _bnIndex.predict(clean) : new String[0];
    }
    else
    {
      // English prediction with lowercase normalization
      String lower = clean.toLowerCase(Locale.ROOT);
      return (_enIndex != null) ? _enIndex.predict(lower) : new String[0];
    }
  }

  /**
   * Predict explicitly specifying language/script ("bn" or "en").
   */
  public String[] predict(String word, String lang)
  {
    if (word == null || word.isEmpty())
      return new String[0];

    String clean = word.trim();
    if ("en".equalsIgnoreCase(lang) || "english".equalsIgnoreCase(lang) || "latin".equalsIgnoreCase(lang))
    {
      return (_enIndex != null) ? _enIndex.predict(clean.toLowerCase(Locale.ROOT)) : new String[0];
    }
    else if ("bn".equalsIgnoreCase(lang) || "bangla".equalsIgnoreCase(lang) || "bengali".equalsIgnoreCase(lang))
    {
      return (_bnIndex != null) ? _bnIndex.predict(clean) : new String[0];
    }
    return predict(word);
  }

  private static boolean isBengali(String s)
  {
    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);
      if (c >= 0x0980 && c <= 0x09FF)
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Encapsulated binary index holding a parsed bigram binary file.
   */
  private static final class BinaryIndex
  {
    private final String _assetName;
    private ByteBuffer _buffer;
    private int _totalEntries;
    private int _indexOffset;
    private int _succArrayOffset;
    private int _stringPoolOffset;
    private boolean _loaded = false;

    public BinaryIndex(Context context, String assetPath)
    {
      _assetName = assetPath;
      loadFromAsset(context, assetPath);
    }

    public boolean isLoaded()
    {
      return _loaded && _buffer != null;
    }

    private synchronized void loadFromAsset(Context context, String assetPath)
    {
      try (InputStream is = context.getAssets().open(assetPath))
      {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = is.read(buf)) != -1)
        {
          baos.write(buf, 0, n);
        }
        byte[] bytes = baos.toByteArray();

        _buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        // Read 24-byte header
        byte[] magic = new byte[4];
        _buffer.get(magic);
        boolean validMagic = (magic[0] == 'B' && magic[1] == 'G') &&
            ((magic[2] == 'B' && magic[3] == 'N') || (magic[2] == 'E' && magic[3] == 'N'));

        if (!validMagic)
        {
          Logs.exn(TAG, new IllegalStateException("Invalid magic in " + assetPath + ": " + new String(magic, StandardCharsets.US_ASCII)));
          return;
        }

        int version = _buffer.getShort() & 0xFFFF;
        int reserved = _buffer.getShort() & 0xFFFF;
        _totalEntries = _buffer.getInt();
        _indexOffset = _buffer.getInt();
        _succArrayOffset = _buffer.getInt();
        _stringPoolOffset = _buffer.getInt();

        _loaded = true;
        Log.i(TAG, "Loaded " + assetPath + " successfully: " + _totalEntries + " entries, version " + version);
      }
      catch (Exception e)
      {
        Logs.exn(TAG, e);
        _loaded = false;
      }
    }

    public String[] predict(String word)
    {
      if (!_loaded || _buffer == null || word == null || word.isEmpty())
        return new String[0];

      byte[] targetBytes = word.getBytes(StandardCharsets.UTF_8);
      int low = 0;
      int high = _totalEntries - 1;

      while (low <= high)
      {
        int mid = (low + high) >>> 1;
        int entryOffset = _indexOffset + mid * 12;

        int w1Off = _buffer.getInt(entryOffset);
        int succOff = _buffer.getInt(entryOffset + 4);
        int succCount = _buffer.getShort(entryOffset + 8) & 0xFFFF;

        int cmp = compareWord(w1Off, targetBytes);
        if (cmp == 0)
        {
          // Found match! Read successor strings
          String[] results = new String[succCount];
          for (int i = 0; i < succCount; i++)
          {
            int succPtrOff = _succArrayOffset + (succOff + i) * 4;
            int succStrOff = _buffer.getInt(succPtrOff);
            results[i] = readCString(succStrOff);
          }
          return results;
        }
        else if (cmp < 0)
        {
          low = mid + 1;
        }
        else
        {
          high = mid - 1;
        }
      }

      return new String[0];
    }

    private int compareWord(int stringPoolOffset, byte[] targetBytes)
    {
      int absPos = _stringPoolOffset + stringPoolOffset;
      int targetLen = targetBytes.length;
      int i = 0;

      while (true)
      {
        byte b = _buffer.get(absPos + i);
        if (b == 0)
        {
          // Mid string ended
          return (i < targetLen) ? -1 : 0;
        }
        if (i >= targetLen)
        {
          // Target ended first
          return 1;
        }
        int diff = (b & 0xFF) - (targetBytes[i] & 0xFF);
        if (diff != 0)
        {
          return diff;
        }
        i++;
      }
    }

    private String readCString(int poolOffset)
    {
      int absPos = _stringPoolOffset + poolOffset;
      int end = absPos;
      while (_buffer.get(end) != 0)
      {
        end++;
      }
      int len = end - absPos;
      byte[] strBytes = new byte[len];
      for (int i = 0; i < len; i++)
      {
        strBytes[i] = _buffer.get(absPos + i);
      }
      return new String(strBytes, StandardCharsets.UTF_8);
    }
  }
}
