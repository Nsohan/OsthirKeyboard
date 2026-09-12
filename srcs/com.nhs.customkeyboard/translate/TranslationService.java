package com.nhs.customkeyboard.translate;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import com.nhs.customkeyboard.Logs;

public final class TranslationService
{
  public interface Callback
  {
    void onTranslated(String original, String translated);
    void onError(Exception error);
  }

  public static class Language
  {
    public final String code;
    public final String name;

    public Language(String code, String name)
    {
      this.code = code;
      this.name = name;
    }

    @Override
    public String toString() { return name; }
  }

  private static final String PREF_NAME = "translation_prefs";
  private static final String KEY_SOURCE = "source_lang";
  private static final String KEY_TARGET = "target_lang";

  private static final ExecutorService _executor = Executors.newSingleThreadExecutor();
  private static final Handler _mainHandler = new Handler(Looper.getMainLooper());

  private static final List<Language> ALL_LANGUAGES = new ArrayList<>();
  private static final Map<String, String> CODE_TO_NAME = new HashMap<>();

  private static final Pattern RESULT_CONTAINER_PATTERN =
      Pattern.compile("class=\"result-container\">(.*?)</div>", Pattern.DOTALL);
  private static final Pattern T0_PATTERN =
      Pattern.compile("class=\"t0\">(.*?)</div>", Pattern.DOTALL);

  static
  {
    addLang("auto", "Detect language");
    addLang("af", "Afrikaans");
    addLang("ak", "Akan");
    addLang("sq", "Albanian");
    addLang("am", "Amharic");
    addLang("ar", "Arabic");
    addLang("hy", "Armenian");
    addLang("as", "Assamese");
    addLang("az", "Azerbaijani");
    addLang("bn", "Bangla");
    addLang("eu", "Basque");
    addLang("be", "Belarusian");
    addLang("bs", "Bosnian");
    addLang("bg", "Bulgarian");
    addLang("my", "Burmese (Myanmar)");
    addLang("ca", "Catalan");
    addLang("zh-CN", "Chinese (Simplified)");
    addLang("zh-TW", "Chinese (Traditional)");
    addLang("hr", "Croatian");
    addLang("cs", "Czech");
    addLang("da", "Danish");
    addLang("nl", "Dutch");
    addLang("en", "English");
    addLang("eo", "Esperanto");
    addLang("et", "Estonian");
    addLang("fil", "Filipino");
    addLang("fi", "Finnish");
    addLang("fr", "French");
    addLang("gl", "Galician");
    addLang("ka", "Georgian");
    addLang("de", "German");
    addLang("el", "Greek");
    addLang("gu", "Gujarati");
    addLang("ht", "Haitian Creole");
    addLang("ha", "Hausa");
    addLang("he", "Hebrew");
    addLang("hi", "Hindi");
    addLang("hu", "Hungarian");
    addLang("is", "Icelandic");
    addLang("ig", "Igbo");
    addLang("id", "Indonesian");
    addLang("ga", "Irish");
    addLang("it", "Italian");
    addLang("ja", "Japanese");
    addLang("jv", "Javanese");
    addLang("kn", "Kannada");
    addLang("kk", "Kazakh");
    addLang("km", "Khmer");
    addLang("ko", "Korean");
    addLang("ku", "Kurdish");
    addLang("ky", "Kyrgyz");
    addLang("lo", "Lao");
    addLang("la", "Latin");
    addLang("lv", "Latvian");
    addLang("lt", "Lithuanian");
    addLang("lb", "Luxembourgish");
    addLang("mk", "Macedonian");
    addLang("mg", "Malagasy");
    addLang("ms", "Malay");
    addLang("ml", "Malayalam");
    addLang("mt", "Maltese");
    addLang("mi", "Maori");
    addLang("mr", "Marathi");
    addLang("mn", "Mongolian");
    addLang("ne", "Nepali");
    addLang("no", "Norwegian");
    addLang("or", "Odia");
    addLang("ps", "Pashto");
    addLang("fa", "Persian");
    addLang("pl", "Polish");
    addLang("pt", "Portuguese");
    addLang("pa", "Punjabi");
    addLang("ro", "Romanian");
    addLang("ru", "Russian");
    addLang("sa", "Sanskrit");
    addLang("sr", "Serbian");
    addLang("sd", "Sindhi");
    addLang("si", "Sinhala");
    addLang("sk", "Slovak");
    addLang("sl", "Slovenian");
    addLang("so", "Somali");
    addLang("es", "Spanish");
    addLang("su", "Sundanese");
    addLang("sw", "Swahili");
    addLang("sv", "Swedish");
    addLang("tg", "Tajik");
    addLang("ta", "Tamil");
    addLang("te", "Telugu");
    addLang("th", "Thai");
    addLang("tr", "Turkish");
    addLang("uk", "Ukrainian");
    addLang("ur", "Urdu");
    addLang("uz", "Uzbek");
    addLang("vi", "Vietnamese");
    addLang("cy", "Welsh");
    addLang("xh", "Xhosa");
    addLang("yi", "Yiddish");
    addLang("yo", "Yoruba");
    addLang("zu", "Zulu");
  }

  private static void addLang(String code, String name)
  {
    ALL_LANGUAGES.add(new Language(code, name));
    CODE_TO_NAME.put(code, name);
  }

  public static List<Language> getLanguages(boolean allowAuto)
  {
    List<Language> list = new ArrayList<>();
    for (Language l : ALL_LANGUAGES)
    {
      if (!allowAuto && "auto".equals(l.code)) continue;
      list.add(l);
    }
    return list;
  }

  public static String getLanguageName(String code)
  {
    String name = CODE_TO_NAME.get(code);
    return (name != null) ? name : code;
  }

  public static String getSourceLang(Context ctx)
  {
    SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    return sp.getString(KEY_SOURCE, "en");
  }

  public static void setSourceLang(Context ctx, String code)
  {
    SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    sp.edit().putString(KEY_SOURCE, code).apply();
  }

  public static String getTargetLang(Context ctx)
  {
    SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    return sp.getString(KEY_TARGET, "bn");
  }

  public static void setTargetLang(Context ctx, String code)
  {
    SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    sp.edit().putString(KEY_TARGET, code).apply();
  }

  public static void translate(final String text, final String sourceLang, final String targetLang, final Callback callback)
  {
    if (text == null || text.trim().isEmpty())
    {
      if (callback != null) callback.onTranslated(text, "");
      return;
    }

    _executor.execute(new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          String q = URLEncoder.encode(text, "UTF-8");
          String sl = (sourceLang == null || sourceLang.isEmpty()) ? "auto" : sourceLang;
          String tl = (targetLang == null || targetLang.isEmpty() || "auto".equals(targetLang)) ? "bn" : targetLang;

          String result = null;

          // 1. Primary: User's requested endpoint https://translate.google.com/m?hl=BN-US&sl=...&tl=...&q=...
          try
          {
            String mEndpoint = "https://translate.google.com/m?hl=BN-US&sl=" + sl + "&tl=" + tl + "&ie=UTF-8&prev=_m&q=" + q;
            URL url = new URL(mEndpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile)");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK)
            {
              BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
              StringBuilder sb = new StringBuilder();
              String line;
              while ((line = reader.readLine()) != null)
              {
                sb.append(line);
              }
              reader.close();

              String html = sb.toString();
              Matcher m = RESULT_CONTAINER_PATTERN.matcher(html);
              if (m.find())
              {
                result = unescapeHtml(m.group(1));
              }
              else
              {
                Matcher m2 = T0_PATTERN.matcher(html);
                if (m2.find())
                {
                  result = unescapeHtml(m2.group(1));
                }
              }
            }
          }
          catch (Exception ex)
          {
            Logs.exn("TranslationService primary url failed, trying fallback", ex);
          }

          // 2. Fallback: JSON endpoint
          if (result == null)
          {
            String jsonEndpoint = "https://translate.googleapis.com/translate_a/single?client=gtx&sl="
                + sl + "&tl=" + tl + "&dt=t&q=" + q;

            URL url = new URL(jsonEndpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK)
            {
              BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
              StringBuilder sb = new StringBuilder();
              String line;
              while ((line = reader.readLine()) != null)
              {
                sb.append(line);
              }
              reader.close();

              JSONArray root = new JSONArray(sb.toString());
              JSONArray sentences = root.getJSONArray(0);
              StringBuilder translatedBuilder = new StringBuilder();
              for (int i = 0; i < sentences.length(); i++)
              {
                JSONArray part = sentences.optJSONArray(i);
                if (part != null && !part.isNull(0))
                {
                  translatedBuilder.append(part.getString(0));
                }
              }
              result = translatedBuilder.toString();
            }
          }

          if (result != null)
          {
            final String finalResult = result.trim();
            _mainHandler.post(new Runnable()
            {
              @Override
              public void run()
              {
                if (callback != null) callback.onTranslated(text, finalResult);
              }
            });
          }
          else
          {
            throw new Exception("Translation response was empty");
          }
        }
        catch (final Exception e)
        {
          Logs.exn("TranslationService", e);
          _mainHandler.post(new Runnable()
          {
            @Override
            public void run()
            {
              if (callback != null) callback.onError(e);
            }
          });
        }
      }
    });
  }

  @SuppressWarnings("deprecation")
  private static String unescapeHtml(String text)
  {
    if (text == null) return "";
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
    {
      return Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString();
    }
    else
    {
      return Html.fromHtml(text).toString();
    }
  }
}
