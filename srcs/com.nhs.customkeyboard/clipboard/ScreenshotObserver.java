package com.nhs.customkeyboard.clipboard;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import androidx.core.content.ContextCompat;
import com.nhs.customkeyboard.ClipboardHistoryService;
import com.nhs.customkeyboard.Config;
import com.nhs.customkeyboard.Logs;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ScreenshotObserver extends ContentObserver
{
  private static ScreenshotObserver _instance;
  private final Context _context;
  private boolean _isRegistered = false;
  private long _lastProcessedId = -1;
  private final ScheduledExecutorService _executor = Executors.newSingleThreadScheduledExecutor();

  public static synchronized ScreenshotObserver getInstance(Context context)
  {
    if (_instance == null)
    {
      _instance = new ScreenshotObserver(context.getApplicationContext());
    }
    return _instance;
  }

  private ScreenshotObserver(Context context)
  {
    super(new Handler(Looper.getMainLooper()));
    _context = context;
  }

  public boolean hasStoragePermission()
  {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
    {
      return ContextCompat.checkSelfPermission(_context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
    }
    else
    {
      return ContextCompat.checkSelfPermission(_context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }
  }

  public synchronized void register()
  {
    if (_isRegistered) return;
    Config config = Config.globalConfig();
    if (config == null || !config.clipboard_save_screenshots_enabled)
    {
      return;
    }
    if (!hasStoragePermission())
    {
      return;
    }

    try
    {
      _context.getContentResolver().registerContentObserver(
          MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
          true,
          this
      );
      _isRegistered = true;
    }
    catch (Throwable e)
    {
      Logs.exn("ScreenshotObserver", e);
    }
  }

  public synchronized void unregister()
  {
    if (!_isRegistered) return;
    try
    {
      _context.getContentResolver().unregisterContentObserver(this);
      _isRegistered = false;
    }
    catch (Throwable e)
    {
      Logs.exn("ScreenshotObserver", e);
    }
  }

  @Override
  public void onChange(boolean selfChange)
  {
    handleScreenshotChange();
  }

  @Override
  public void onChange(boolean selfChange, Uri uri)
  {
    handleScreenshotChange();
  }

  private void handleScreenshotChange()
  {
    Config config = Config.globalConfig();
    if (config == null || !config.clipboard_save_screenshots_enabled)
    {
      return;
    }
    if (!hasStoragePermission())
    {
      return;
    }

    _executor.execute(() -> {
      try
      {
        boolean found = checkLatestScreenshot();
        if (!found)
        {
          _executor.schedule(() -> {
            try
            {
              checkLatestScreenshot();
            }
            catch (Throwable t)
            {
              Logs.exn("ScreenshotObserver", t);
            }
          }, 400, TimeUnit.MILLISECONDS);
        }
      }
      catch (Throwable t)
      {
        Logs.exn("ScreenshotObserver", t);
      }
    });
  }

  private boolean checkLatestScreenshot()
  {
    Cursor cursor = null;
    try
    {
      String[] projection = new String[]{
          MediaStore.Images.Media._ID,
          MediaStore.Images.Media.DISPLAY_NAME,
          MediaStore.Images.Media.MIME_TYPE,
          MediaStore.Images.Media.DATE_ADDED
      };

      String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";
      cursor = _context.getContentResolver().query(
          MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
          projection,
          null,
          null,
          sortOrder
      );

      if (cursor != null && cursor.moveToFirst())
      {
        int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
        int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
        int mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE);
        int dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED);

        long id = cursor.getLong(idCol);
        String name = cursor.getString(nameCol);
        String mimeType = cursor.getString(mimeCol);
        long dateAddedSeconds = cursor.getLong(dateCol);

        if (id == _lastProcessedId)
        {
          return false;
        }

        long nowSeconds = System.currentTimeMillis() / 1000L;
        // Verify it was added within the last 60 seconds
        if (Math.abs(nowSeconds - dateAddedSeconds) > 60)
        {
          return false;
        }

        if (isScreenshot(name))
        {
          _lastProcessedId = id;
          Uri contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
          if (mimeType == null || mimeType.isEmpty())
          {
            mimeType = "image/png";
          }

          ClipboardHistoryService service = ClipboardHistoryService.get_service(_context);
          if (service != null)
          {
            service.add_image_clip(contentUri, mimeType, "Screenshot");
          }
          return true;
        }
      }
    }
    catch (Throwable e)
    {
      Logs.exn("ScreenshotObserver", e);
    }
    finally
    {
      if (cursor != null)
      {
        try { cursor.close(); } catch (Throwable ignored) {}
      }
    }
    return false;
  }

  private boolean isScreenshot(String name)
  {
    if (name == null) return false;
    String lower = name.toLowerCase();
    return lower.contains("screenshot") || lower.startsWith("screen_") || lower.contains("screencap");
  }
}

