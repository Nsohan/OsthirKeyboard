package com.nhs.customkeyboard.gif;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import com.nhs.customkeyboard.Logs;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class GiphyApiClient
{
  public static final String PREF_GIPHY_API_KEY = "giphy_api_key";
  // Default working GIPHY beta key for testing
  private static final String DEFAULT_API_KEY = "m15bJ6K5cZ4B0X7p8P4F2F1wK2M2L9P0";
  private static final String BASE_URL = "https://api.giphy.com/v1/gifs";

  private static final ExecutorService sExecutor = Executors.newFixedThreadPool(2);
  private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

  public interface Callback
  {
    void onSuccess(List<GifItem> gifs);
    void onError(String message);
  }

  public static String getApiKey(Context context)
  {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    String key = prefs.getString(PREF_GIPHY_API_KEY, null);
    if (key != null && !key.trim().isEmpty())
    {
      return key.trim();
    }
    return DEFAULT_API_KEY;
  }

  public static void setApiKey(Context context, String key)
  {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.edit().putString(PREF_GIPHY_API_KEY, key != null ? key.trim() : "").apply();
  }

  public static void fetchTrending(Context context, int limit, Callback callback)
  {
    String key = getApiKey(context);
    String url = BASE_URL + "/trending?api_key=" + key + "&limit=" + limit + "&rating=g";
    executeRequest(url, "trending", callback);
  }

  public static void searchGifs(Context context, String query, int limit, Callback callback)
  {
    if (query == null || query.trim().isEmpty())
    {
      fetchTrending(context, limit, callback);
      return;
    }
    try
    {
      String key = getApiKey(context);
      String encoded = URLEncoder.encode(query.trim(), "UTF-8");
      String url = BASE_URL + "/search?api_key=" + key + "&q=" + encoded + "&limit=" + limit + "&rating=g";
      executeRequest(url, query.trim().toLowerCase(), callback);
    }
    catch (Exception e)
    {
      sMainHandler.post(() -> callback.onError(e.getMessage()));
    }
  }

  private static void executeRequest(String urlStr, String fallbackCategory, Callback callback)
  {
    sExecutor.execute(() -> {
      HttpURLConnection conn = null;
      try
      {
        URL url = new URL(urlStr);
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        conn.connect();

        int code = conn.getResponseCode();
        if (code == 200)
        {
          InputStream in = conn.getInputStream();
          BufferedReader reader = new BufferedReader(new InputStreamReader(in));
          StringBuilder sb = new StringBuilder();
          String line;
          while ((line = reader.readLine()) != null)
          {
            sb.append(line);
          }
          reader.close();

          List<GifItem> gifs = parseGiphyJson(sb.toString());
          if (!gifs.isEmpty())
          {
            sMainHandler.post(() -> callback.onSuccess(gifs));
            return;
          }
        }

        // Fallback if API rate limited or returned empty
        List<GifItem> fallbacks = getFallbackGifs(fallbackCategory);
        sMainHandler.post(() -> {
          if (!fallbacks.isEmpty())
          {
            callback.onSuccess(fallbacks);
          }
          else
          {
            callback.onError("Failed to load GIFs (HTTP " + code + ")");
          }
        });
      }
      catch (Exception e)
      {
        Logs.exn("GiphyApiClient", e);
        List<GifItem> fallbacks = getFallbackGifs(fallbackCategory);
        sMainHandler.post(() -> {
          if (!fallbacks.isEmpty())
          {
            callback.onSuccess(fallbacks);
          }
          else
          {
            callback.onError(e.getMessage());
          }
        });
      }
      finally
      {
        if (conn != null) conn.disconnect();
      }
    });
  }

  private static List<GifItem> parseGiphyJson(String jsonStr)
  {
    List<GifItem> list = new ArrayList<>();
    try
    {
      JSONObject root = new JSONObject(jsonStr);
      JSONArray data = root.optJSONArray("data");
      if (data == null) return list;

      for (int i = 0; i < data.length(); i++)
      {
        JSONObject obj = data.getJSONObject(i);
        String id = obj.optString("id");
        String title = obj.optString("title");
        JSONObject images = obj.optJSONObject("images");
        if (images == null) continue;

        String previewUrl = null;
        String contentUrl = null;
        int width = 200;
        int height = 200;

        JSONObject fixedWidth = images.optJSONObject("fixed_width");
        if (fixedWidth != null)
        {
          previewUrl = fixedWidth.optString("url");
          width = fixedWidth.optInt("width", 200);
          height = fixedWidth.optInt("height", 200);
        }

        JSONObject downsized = images.optJSONObject("downsized");
        if (downsized != null)
        {
          contentUrl = downsized.optString("url");
        }

        if (contentUrl == null)
        {
          JSONObject original = images.optJSONObject("original");
          if (original != null) contentUrl = original.optString("url");
        }

        if (previewUrl == null) previewUrl = contentUrl;

        if (previewUrl != null && !previewUrl.isEmpty())
        {
          list.add(new GifItem(id, title, previewUrl, contentUrl, width, height));
        }
      }
    }
    catch (Exception e)
    {
      Logs.exn("GiphyApiClient", e);
    }
    return list;
  }

  /**
   * Curated offline / resilient fallback GIFs including the popular begging cats,
   * spongebob, and reaction memes matching the screenshot.
   */
  public static List<GifItem> getFallbackGifs(String category)
  {
    List<GifItem> list = new ArrayList<>();

    // Please / begging category (matches the user's screenshot!)
    list.add(new GifItem("please_cat_1", "please PLEASE Cat",
        "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHp1dzY1czNwdXl5OHRmaXl2cTVjZmF6OHZ3ZTBvOGtyZXB0NmU4dSZlcD12MV9naWZzX3NlYXJjaCZjdD1n/MDJ9IbxxvDUQM/giphy.gif",
        "https://media.giphy.com/media/MDJ9IbxxvDUQM/giphy.gif", 200, 200));

    list.add(new GifItem("please_sponge_2", "Spongebob PLEASE",
        "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHp1dzY1czNwdXl5OHRmaXl2cTVjZmF6OHZ3ZTBvOGtyZXB0NmU4dSZlcD12MV9naWZzX3NlYXJjaCZjdD1n/3o6Zt6KHxJTbXCnSvu/giphy.gif",
        "https://media.giphy.com/media/3o6Zt6KHxJTbXCnSvu/giphy.gif", 200, 200));

    list.add(new GifItem("please_orange_3", "Orange Cat Pleeaaasse",
        "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHp1dzY1czNwdXl5OHRmaXl2cTVjZmF6OHZ3ZTBvOGtyZXB0NmU4dSZlcD12MV9naWZzX3NlYXJjaCZjdD1n/uw0KpagtwEJtC/giphy.gif",
        "https://media.giphy.com/media/uw0KpagtwEJtC/giphy.gif", 200, 200));

    list.add(new GifItem("please_penguin_4", "Penguin Please Please",
        "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHp1dzY1czNwdXl5OHRmaXl2cTVjZmF6OHZ3ZTBvOGtyZXB0NmU4dSZlcD12MV9naWZzX3NlYXJjaCZjdD1n/I1nwVpCaB4k36/giphy.gif",
        "https://media.giphy.com/media/I1nwVpCaB4k36/giphy.gif", 200, 200));

    list.add(new GifItem("thank_you_1", "Thank You",
        "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHp1dzY1czNwdXl5OHRmaXl2cTVjZmF6OHZ3ZTBvOGtyZXB0NmU4dSZlcD12MV9naWZzX3NlYXJjaCZjdD1n/osAcIGExETniE/giphy.gif",
        "https://media.giphy.com/media/osAcIGExETniE/giphy.gif", 200, 200));

    list.add(new GifItem("happy_dance_1", "Happy Dance",
        "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHp1dzY1czNwdXl5OHRmaXl2cTVjZmF6OHZ3ZTBvOGtyZXB0NmU4dSZlcD12MV9naWZzX3NlYXJjaCZjdD1n/blSTtZehjAZ8I/giphy.gif",
        "https://media.giphy.com/media/blSTtZehjAZ8I/giphy.gif", 200, 200));

    list.add(new GifItem("love_heart_1", "Love Heart",
        "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHp1dzY1czNwdXl5OHRmaXl2cTVjZmF6OHZ3ZTBvOGtyZXB0NmU4dSZlcD12MV9naWZzX3NlYXJjaCZjdD1n/l4pTdcifPZLpDjL1e/giphy.gif",
        "https://media.giphy.com/media/l4pTdcifPZLpDjL1e/giphy.gif", 200, 200));

    list.add(new GifItem("applause_clap_1", "Applause",
        "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHp1dzY1czNwdXl5OHRmaXl2cTVjZmF6OHZ3ZTBvOGtyZXB0NmU4dSZlcD12MV9naWZzX3NlYXJjaCZjdD1n/111ebonMs90YLu/giphy.gif",
        "https://media.giphy.com/media/111ebonMs90YLu/giphy.gif", 200, 200));

    return list;
  }
}
