package com.nhs.customkeyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build.VERSION;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ClipboardHistoryService
{
  /** Start the service on startup and start listening to clipboard changes. */
  public static void on_startup(Context ctx, ClipboardPasteCallback cb)
  {
    get_service(ctx);
    _paste_callback = cb;
  }

  /** Start the service if it hasn't been started before. Returns [null] if the
      feature is unsupported. */
  public static ClipboardHistoryService get_service(Context ctx)
  {
    if (VERSION.SDK_INT <= 11)
      return null;
    if (_service == null)
      _service = new ClipboardHistoryService(ctx);
    return _service;
  }

  public static void set_history_enabled(boolean e)
  {
    Config.globalConfig().set_clipboard_history_enabled(e);
    if (_service == null)
      return;
    if (e)
      _service.add_current_clip();
    else
      _service.clear_history();
  }

  /** Send the given string to the editor. */
  public static void paste(String clip)
  {
    if (_paste_callback != null)
      _paste_callback.paste_from_clipboard_pane(clip);
  }

  /** Send the given image to the editor. */
  public static void paste_image(android.net.Uri uri, String mimeType, String label)
  {
    if (_paste_callback != null)
      _paste_callback.paste_image_from_clipboard_pane(uri, mimeType, label);
  }

  /** The maximum size limits the amount of user data stored in memory but also
      gives a sense to the user that the history is not persisted and can be
      forgotten as soon as the app stops. */
  public static final int MAX_HISTORY_SIZE = 50;

  static ClipboardHistoryService _service = null;
  static ClipboardPasteCallback _paste_callback = null;

  private final Context _context;
  ClipboardManager _cm;
  List<HistoryEntry> _history;
  OnClipboardHistoryChange _listener = null;

  ClipboardHistoryService(Context ctx)
  {
    _context = ctx.getApplicationContext();
    _history = new ArrayList<HistoryEntry>();
    _cm = (ClipboardManager)ctx.getSystemService(Context.CLIPBOARD_SERVICE);
    _cm.addPrimaryClipChangedListener(this.new SystemListener());
  }

  public synchronized List<String> clear_expired_and_get_history()
  {
    long now_ms = System.currentTimeMillis();
    List<String> dst = new ArrayList<String>();
    Iterator<HistoryEntry> it = _history.iterator();
    while (it.hasNext())
    {
      HistoryEntry ent = it.next();
      if (ent.expiry_timestamp <= now_ms)
        it.remove();
      else
        dst.add(ent.content);
    }
    return dst;
  }

  public static boolean isUriAccessible(Context ctx, Uri uri)
  {
    if (uri == null || ctx == null) return false;
    try (java.io.InputStream in = ctx.getContentResolver().openInputStream(uri))
    {
      return in != null;
    }
    catch (Throwable t)
    {
      return false;
    }
  }

  public synchronized List<HistoryEntry> clear_expired_and_get_history_entries()
  {
    long now_ms = System.currentTimeMillis();
    List<HistoryEntry> dst = new ArrayList<>();
    Iterator<HistoryEntry> it = _history.iterator();
    while (it.hasNext())
    {
      HistoryEntry ent = it.next();
      if (ent.expiry_timestamp <= now_ms)
      {
        it.remove();
      }
      else if (ent.is_image)
      {
        if (ent.image_uri == null || !isUriAccessible(_context, ent.image_uri))
        {
          it.remove();
        }
        else
        {
          dst.add(ent);
        }
      }
      else
      {
        dst.add(ent);
      }
    }
    return dst;
  }

  /** This will call [on_clipboard_history_change]. */
  public synchronized void remove_history_entry(String clip)
  {
    remove_history_entry(clip, null);
  }

  public synchronized void remove_history_entry(String clip, Uri imageUri)
  {
    int last_pos = _history.size() - 1;
    boolean last_pos_changed = false;
    for (int pos = last_pos; pos >= 0; pos--)
    {
      HistoryEntry ent = _history.get(pos);
      boolean matches;
      if (imageUri != null)
      {
        matches = ent.is_image && (imageUri.equals(ent.image_uri) || imageUri.equals(ent.source_uri));
      }
      else
      {
        matches = !ent.is_image && ent.content != null && ent.content.equals(clip);
      }
      if (!matches)
        continue;

      // Clean up cached file if it exists in internal cache
      if (ent.is_image && ent.image_uri != null)
      {
        try
        {
          String path = ent.image_uri.getPath();
          if (path != null && path.contains("clipboard_images"))
          {
            File cacheDir = new File(_context.getCacheDir(), "clipboard_images");
            String fileName = ent.image_uri.getLastPathSegment();
            if (fileName != null)
            {
              File file = new File(cacheDir, fileName);
              if (file.exists()) file.delete();
            }
          }
        }
        catch (Throwable ignored) {}
      }

      // Removing the current clipboard, clear the system clipboard.
      if (pos == last_pos)
        last_pos_changed = true;
      _history.remove(pos);
    }
    if (last_pos_changed)
    {
      if (VERSION.SDK_INT >= 28)
        _cm.clearPrimaryClip();
      else
        _cm.setText("");
    }
    if (_listener != null)
      _listener.on_clipboard_history_change();
  }

  /** Add clipboard entries to the history, skipping consecutive duplicates and
      empty strings. */
  public synchronized void add_clip(String clip)
  {
    add_clip(clip, System.currentTimeMillis());
  }

  public synchronized void add_clip(String clip, long timestamp)
  {
    if (!Config.globalConfig().clipboard_history_enabled)
      return;
    if (clip.equals(""))
      return;
    int size = _history.size();
    boolean isConsecutiveDuplicate = (size > 0 && _history.get(0).content.equals(clip));
    if (!isConsecutiveDuplicate)
    {
      if (size >= MAX_HISTORY_SIZE)
        _history.remove(size - 1);
      _history.add(0, new HistoryEntry(clip));
      if (_listener != null)
        _listener.on_clipboard_history_change();
    }
    com.nhs.customkeyboard.clipboard.ClipboardSuggestionHelper.get().onNewTextClip(clip, timestamp);
  }

  public synchronized void add_image_clip(android.net.Uri uri, String mimeType, String label)
  {
    add_image_clip(uri, uri, mimeType, label, System.currentTimeMillis(), true);
  }

  public synchronized void add_image_clip(android.net.Uri uri, String mimeType, String label, long timestamp, boolean isScreenshot)
  {
    add_image_clip(uri, uri, mimeType, label, timestamp, isScreenshot);
  }

  public synchronized void add_image_clip(android.net.Uri uri, android.net.Uri sourceUri, String mimeType, String label, long timestamp, boolean isScreenshot)
  {
    if (!Config.globalConfig().clipboard_history_enabled)
      return;
    if (uri == null || !isUriAccessible(_context, uri))
      return;

    // Check if this image already exists in history
    int existingIndex = -1;
    for (int i = 0; i < _history.size(); i++)
    {
      HistoryEntry entry = _history.get(i);
      if (entry.is_image)
      {
        if ((sourceUri != null && (sourceUri.equals(entry.source_uri) || sourceUri.equals(entry.image_uri)))
            || (uri != null && (uri.equals(entry.image_uri) || uri.equals(entry.source_uri))))
        {
          existingIndex = i;
          break;
        }
      }
    }

    if (existingIndex == 0)
    {
      // Consecutive duplicate: already at top of history!
      return;
    }
    if (existingIndex > 0)
    {
      // Remove older duplicate so it moves to top
      _history.remove(existingIndex);
    }
    else if (_history.size() >= MAX_HISTORY_SIZE)
    {
      _history.remove(_history.size() - 1);
    }

    _history.add(0, new HistoryEntry(label, uri, sourceUri, mimeType, true));
    if (isScreenshot)
    {
      com.nhs.customkeyboard.clipboard.ClipboardSuggestionHelper.get().onNewScreenshot(uri, mimeType, label, timestamp);
    }
    else
    {
      com.nhs.customkeyboard.clipboard.ClipboardSuggestionHelper.get().onNewImageClip(uri, mimeType, label, timestamp);
    }
    if (_listener != null)
      _listener.on_clipboard_history_change();
  }

  private void cache_and_add_image_clip(Uri sourceUri, String mimeType, String label, long timestamp, boolean isScreenshot)
  {
    if (sourceUri == null) return;
    Uri finalUri = null;
    File cachedFile = null;
    try
    {
      File cacheDir = new File(_context.getCacheDir(), "clipboard_images");
      if (!cacheDir.exists())
      {
        cacheDir.mkdirs();
      }
      String ext = ".png";
      if (mimeType != null)
      {
        if (mimeType.contains("jpeg") || mimeType.contains("jpg")) ext = ".jpg";
        else if (mimeType.contains("webp")) ext = ".webp";
        else if (mimeType.contains("gif")) ext = ".gif";
        else if (mimeType.contains("avif")) ext = ".avif";
      }
      File[] existing = cacheDir.listFiles();
      if (existing != null && existing.length > 20)
      {
        for (File f : existing)
        {
          if (System.currentTimeMillis() - f.lastModified() > 24 * 60 * 60 * 1000L)
          {
            f.delete();
          }
        }
      }
      cachedFile = new File(cacheDir, "clip_" + System.currentTimeMillis() + ext);
      java.io.InputStream in = _context.getContentResolver().openInputStream(sourceUri);
      if (in == null)
      {
        return;
      }
      java.io.OutputStream out = new java.io.FileOutputStream(cachedFile);
      byte[] buffer = new byte[8192];
      int read;
      long totalRead = 0;
      while ((read = in.read(buffer)) != -1)
      {
        out.write(buffer, 0, read);
        totalRead += read;
      }
      out.flush();
      out.close();
      in.close();

      if (totalRead <= 0 || !cachedFile.exists() || cachedFile.length() <= 0)
      {
        if (cachedFile.exists()) cachedFile.delete();
        return;
      }

      finalUri = androidx.core.content.FileProvider.getUriForFile(
          _context,
          _context.getPackageName() + ".fileprovider",
          cachedFile
      );
    }
    catch (Throwable t)
    {
      Logs.exn("ClipboardHistoryService", t);
      if (cachedFile != null && cachedFile.exists())
      {
        try { cachedFile.delete(); } catch (Throwable ignored) {}
      }
      return;
    }

    if (finalUri != null)
    {
      add_image_clip(finalUri, sourceUri, mimeType, label, timestamp, isScreenshot);
    }
  }

  public synchronized void clear_history()
  {
    _history.clear();
    com.nhs.customkeyboard.clipboard.ClipboardSuggestionHelper.get().dismiss();
    if (_listener != null)
      _listener.on_clipboard_history_change();
  }

  public void set_on_clipboard_history_change(OnClipboardHistoryChange l) { _listener = l; }

  public static interface OnClipboardHistoryChange
  {
    public void on_clipboard_history_change();
  }

  /** Add what is currently in the system clipboard into the history. */
  public synchronized void add_current_clip()
  {
    ClipData clip = null;
    // getPrimaryClip might throw when the keyboard is disconnected.
    try { clip = _cm.getPrimaryClip(); } catch (Exception _e) {}
    if (clip == null)
      return;
    long clipTimestamp = System.currentTimeMillis();
    if (VERSION.SDK_INT >= 26 && clip.getDescription() != null)
    {
      long ts = clip.getDescription().getTimestamp();
      if (ts > 0)
      {
        clipTimestamp = ts;
      }
    }
    int count = clip.getItemCount();
    for (int i = 0; i < count; i++)
    {
      ClipData.Item item = clip.getItemAt(i);
      Uri uri = item.getUri();
      CharSequence text = item.getText();

      if (uri != null)
      {
        String mimeType = null;
        if (clip.getDescription() != null && clip.getDescription().getMimeTypeCount() > 0)
        {
          for (int m = 0; m < clip.getDescription().getMimeTypeCount(); m++)
          {
            String mType = clip.getDescription().getMimeType(m);
            if (mType != null && mType.startsWith("image/"))
            {
              mimeType = mType;
              break;
            }
          }
        }
        if (mimeType == null)
        {
          try
          {
            mimeType = _context.getContentResolver().getType(uri);
          }
          catch (Throwable ignored) {}
        }

        if (mimeType != null && mimeType.startsWith("image/"))
        {
          String label = (clip.getDescription() != null && clip.getDescription().getLabel() != null)
              ? clip.getDescription().getLabel().toString()
              : "Image";
          // Check if top history entry is already this exact image clip to avoid redundant caching & duplication
          if (!_history.isEmpty())
          {
            HistoryEntry top = _history.get(0);
            if (top.is_image && uri != null && (uri.equals(top.source_uri) || uri.equals(top.image_uri)))
            {
              continue;
            }
          }
          cache_and_add_image_clip(uri, mimeType, label, clipTimestamp, false);
          continue;
        }
      }

      if (text != null)
        add_clip(text.toString(), clipTimestamp);
    }
  }

  int get_history_ttl_minutes() {
    return Config.globalConfig().clipboard_history_duration;
  }

  final class SystemListener implements ClipboardManager.OnPrimaryClipChangedListener
  {
    public SystemListener() {}

    @Override
    public void onPrimaryClipChanged()
    {
      add_current_clip();
    }
  }

  public static final class HistoryEntry
  {
    public final String content;
    public final android.net.Uri image_uri;
    public final android.net.Uri source_uri;
    public final String mime_type;
    public final boolean is_image;

    /** Time at which the entry expires. */
    public final long expiry_timestamp;

    public HistoryEntry(String c)
    {
      this(c, null, null, null, false);
    }

    public HistoryEntry(String c, android.net.Uri imageUri, String mimeType, boolean isImage)
    {
      this(c, imageUri, imageUri, mimeType, isImage);
    }

    public HistoryEntry(String c, android.net.Uri imageUri, android.net.Uri sourceUri, String mimeType, boolean isImage)
    {
      content = c;
      image_uri = imageUri;
      source_uri = sourceUri != null ? sourceUri : imageUri;
      mime_type = mimeType;
      is_image = isImage;
      final int historyTtlMinutes = _service != null ? _service.get_history_ttl_minutes() : 60;
      if (historyTtlMinutes >= 0) {
          expiry_timestamp = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(historyTtlMinutes);
      } else {
          expiry_timestamp = Long.MAX_VALUE;
      }
    }
  }

  public interface ClipboardPasteCallback
  {
    public void paste_from_clipboard_pane(String content);
    public void paste_image_from_clipboard_pane(android.net.Uri uri, String mimeType, String label);
  }
}
