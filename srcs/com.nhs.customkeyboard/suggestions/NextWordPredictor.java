package com.nhs.customkeyboard.suggestions;

import android.content.Context;
import android.util.Log;
import com.nhs.customkeyboard.Logs;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * High-performance binary search index reader for bn_bigrams.bin.
 * Performs sub-millisecond next-word lookups using a memory-mapped or direct ByteBuffer.
 */
public final class NextWordPredictor
{
  private static final String TAG = "NextWordPredictor";
  private static final String ASSET_FILE = "dictionaries/bn_bigrams.bin";
  private static volatile NextWordPredictor sInstance;

  private ByteBuffer _buffer;
  private int _totalEntries;
  private int _indexOffset;
  private int _succArrayOffset;
  private int _stringPoolOffset;
  private boolean _loaded = false;

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
    loadFromAsset(context);
  }

  private synchronized void loadFromAsset(Context context)
  {
    try (InputStream is = context.getAssets().open(ASSET_FILE))
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
      if (magic[0] != 'B' || magic[1] != 'G' || magic[2] != 'B' || magic[3] != 'N')
      {
        Logs.exn(TAG, new IllegalStateException("Invalid magic in bn_bigrams.bin"));
        return;
      }

      int version = _buffer.getShort() & 0xFFFF;
      int reserved = _buffer.getShort() & 0xFFFF;
      _totalEntries = _buffer.getInt();
      _indexOffset = _buffer.getInt();
      _succArrayOffset = _buffer.getInt();
      _stringPoolOffset = _buffer.getInt();

      _loaded = true;
      Log.i(TAG, "Loaded bn_bigrams.bin successfully: " + _totalEntries + " entries, version " + version);
    }
    catch (Exception e)
    {
      Logs.exn(TAG, e);
      _loaded = false;
    }
  }

  public boolean isLoaded()
  {
    return _loaded && _buffer != null;
  }

  /**
   * Predicts top next words following [word].
   * Returns an array of predicted strings, or empty array if none found.
   */
  public String[] predict(String word)
  {
    if (!_loaded || _buffer == null || word == null || word.isEmpty())
      return new String[0];

    byte[] targetBytes = word.trim().getBytes(StandardCharsets.UTF_8);
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
