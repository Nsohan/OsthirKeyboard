package com.nhs.customkeyboard.clipboard;

import android.content.Context;
import android.net.Uri;
import android.util.Patterns;
import com.nhs.customkeyboard.Config;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;

public class ClipboardSuggestionHelper
{
  public static final long SUGGESTION_RECENCY_WINDOW_MS = 2 * 60 * 1000L; // 2 minutes

  public enum ChipType
  {
    TEXT,
    PHONE,
    URL,
    EMAIL,
    SCREENSHOT,
    IMAGE
  }

  public static class ChipItem
  {
    public final ChipType type;
    public final String displayText;
    public final String pasteText;
    public final Uri imageUri;
    public final String mimeType;
    public final long timestamp;

    public ChipItem(ChipType type, String displayText, String pasteText, Uri imageUri, String mimeType, long timestamp)
    {
      this.type = type;
      this.displayText = displayText;
      this.pasteText = pasteText;
      this.imageUri = imageUri;
      this.mimeType = mimeType;
      this.timestamp = timestamp;
    }

    public static ChipItem forText(String text, long timestamp)
    {
      return new ChipItem(ChipType.TEXT, text, text, null, null, timestamp);
    }

    public static ChipItem forEntity(ChipType type, String entity, long timestamp)
    {
      return new ChipItem(type, entity, entity, null, null, timestamp);
    }

    public static ChipItem forScreenshot(Uri uri, String mimeType, String label, long timestamp)
    {
      return new ChipItem(ChipType.SCREENSHOT, label, null, uri, mimeType, timestamp);
    }

    public static ChipItem forImage(Uri uri, String mimeType, String label, long timestamp)
    {
      return new ChipItem(ChipType.IMAGE, label, null, uri, mimeType, timestamp);
    }
  }

  private static ClipboardSuggestionHelper _instance;

  public static synchronized ClipboardSuggestionHelper get()
  {
    if (_instance == null)
    {
      _instance = new ClipboardSuggestionHelper();
    }
    return _instance;
  }

  private ChipItem _latestItem;
  private final List<ChipItem> _activeChips = new ArrayList<>();
  private boolean _dismissed = false;
  private long _lastDismissedClipTimestamp = 0;
  private OnSuggestionChangedListener _listener;

  public interface OnSuggestionChangedListener
  {
    void onSuggestionChanged(List<ChipItem> chips);
  }

  public void setListener(OnSuggestionChangedListener listener)
  {
    _listener = listener;
  }

  public synchronized void onNewTextClip(String text)
  {
    onNewTextClip(text, System.currentTimeMillis());
  }

  public synchronized void onNewTextClip(String text, long timestamp)
  {
    if (text == null || text.trim().isEmpty()) return;

    long now = System.currentTimeMillis();
    if (timestamp > 0 && (now - timestamp) > SUGGESTION_RECENCY_WINDOW_MS)
    {
      return;
    }

    if (timestamp <= _lastDismissedClipTimestamp)
    {
      return;
    }

    _dismissed = false;
    _activeChips.clear();

    Config config = Config.globalConfig();
    if (config == null || !config.clipboard_suggestion_bar_enabled)
    {
      notifyListener();
      return;
    }

    ChipItem primary = ChipItem.forText(text, timestamp);
    _latestItem = primary;
    _activeChips.add(primary);

    if (config.clipboard_extract_entities_enabled)
    {
      extractEntities(text, timestamp, _activeChips);
    }

    notifyListener();
  }

  public synchronized void onNewScreenshot(Uri uri, String mimeType, String label)
  {
    onNewScreenshot(uri, mimeType, label, System.currentTimeMillis());
  }

  public synchronized void onNewScreenshot(Uri uri, String mimeType, String label, long timestamp)
  {
    if (uri == null) return;

    Config config = Config.globalConfig();
    if (config == null || !config.clipboard_suggestion_bar_enabled || !config.clipboard_save_screenshots_enabled)
    {
      return;
    }

    long now = System.currentTimeMillis();
    if (timestamp > 0 && (now - timestamp) > SUGGESTION_RECENCY_WINDOW_MS)
    {
      return;
    }

    if (timestamp <= _lastDismissedClipTimestamp)
    {
      return;
    }

    _dismissed = false;
    _activeChips.clear();

    ChipItem item = ChipItem.forScreenshot(uri, mimeType, label != null ? label : "Screenshot", timestamp);
    _latestItem = item;
    _activeChips.add(item);

    notifyListener();
  }

  public synchronized void onNewImageClip(Uri uri, String mimeType, String label)
  {
    onNewImageClip(uri, mimeType, label, System.currentTimeMillis());
  }

  public synchronized void onNewImageClip(Uri uri, String mimeType, String label, long timestamp)
  {
    if (uri == null) return;

    Config config = Config.globalConfig();
    if (config == null || !config.clipboard_suggestion_bar_enabled)
    {
      return;
    }

    long now = System.currentTimeMillis();
    if (timestamp > 0 && (now - timestamp) > SUGGESTION_RECENCY_WINDOW_MS)
    {
      return;
    }

    if (timestamp <= _lastDismissedClipTimestamp)
    {
      return;
    }

    _dismissed = false;
    _activeChips.clear();

    ChipItem item = ChipItem.forImage(uri, mimeType, label != null ? label : "Image", timestamp);
    _latestItem = item;
    _activeChips.add(item);

    notifyListener();
  }

  public synchronized List<ChipItem> getActiveChips()
  {
    Config config = Config.globalConfig();
    if (config == null || !config.clipboard_suggestion_bar_enabled || _dismissed || _activeChips.isEmpty())
    {
      return Collections.emptyList();
    }

    long now = System.currentTimeMillis();
    if (_latestItem == null || (now - _latestItem.timestamp) > SUGGESTION_RECENCY_WINDOW_MS)
    {
      _activeChips.clear();
      _latestItem = null;
      return Collections.emptyList();
    }

    return new ArrayList<>(_activeChips);
  }

  public synchronized boolean hasActiveChips()
  {
    return !_dismissed && !_activeChips.isEmpty();
  }

  public synchronized void dismiss()
  {
    if (_latestItem != null)
    {
      _lastDismissedClipTimestamp = Math.max(_lastDismissedClipTimestamp, _latestItem.timestamp);
    }
    else
    {
      _lastDismissedClipTimestamp = System.currentTimeMillis();
    }
    _dismissed = true;
    _activeChips.clear();
    _latestItem = null;
    notifyListener();
  }

  private void notifyListener()
  {
    if (_listener != null)
    {
      _listener.onSuggestionChanged(getActiveChips());
    }
  }

  private void extractEntities(String text, long timestamp, List<ChipItem> dst)
  {
    if (text == null || text.length() > 2000) return;

    // 1. Phone numbers
    try
    {
      Matcher phoneMatcher = Patterns.PHONE.matcher(text);
      int phoneCount = 0;
      while (phoneMatcher.find() && phoneCount < 2)
      {
        String match = phoneMatcher.group().trim();
        if (match.length() >= 7 && !match.equals(text) && !containsDisplayText(dst, match))
        {
          dst.add(ChipItem.forEntity(ChipType.PHONE, match, timestamp));
          phoneCount++;
        }
      }
    }
    catch (Exception ignored) {}

    // 2. URLs / Links
    try
    {
      Matcher urlMatcher = Patterns.WEB_URL.matcher(text);
      int urlCount = 0;
      while (urlMatcher.find() && urlCount < 2)
      {
        String match = urlMatcher.group().trim();
        if (match.length() >= 4 && !match.equals(text) && !containsDisplayText(dst, match))
        {
          dst.add(ChipItem.forEntity(ChipType.URL, match, timestamp));
          urlCount++;
        }
      }
    }
    catch (Exception ignored) {}

    // 3. Email addresses
    try
    {
      Matcher emailMatcher = Patterns.EMAIL_ADDRESS.matcher(text);
      int emailCount = 0;
      while (emailMatcher.find() && emailCount < 2)
      {
        String match = emailMatcher.group().trim();
        if (!match.equals(text) && !containsDisplayText(dst, match))
        {
          dst.add(ChipItem.forEntity(ChipType.EMAIL, match, timestamp));
          emailCount++;
        }
      }
    }
    catch (Exception ignored) {}
  }

  private boolean containsDisplayText(List<ChipItem> list, String text)
  {
    for (ChipItem item : list)
    {
      if (item.displayText != null && item.displayText.equals(text))
      {
        return true;
      }
    }
    return false;
  }
}

