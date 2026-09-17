package com.nhs.customkeyboard.theme;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.preference.PreferenceManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.nhs.customkeyboard.Config;
import com.nhs.customkeyboard.Keyboard2View;
import com.nhs.customkeyboard.KeyboardData;
import com.nhs.customkeyboard.LayoutModifier;
import com.nhs.customkeyboard.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

public class ThemeCropActivity extends AppCompatActivity
{
  public static final String EXTRA_IMAGE_URI = "extra_image_uri";
  public static final String EXTRA_IMAGE_PATH = "extra_image_path";
  public static final String EXTRA_EDIT_THEME_ID = "extra_edit_theme_id";
  public static final String EXTRA_INITIAL_DARKNESS = "extra_initial_darkness";
  public static final String EXTRA_INITIAL_KEY_OPACITY = "extra_initial_key_opacity";
  public static final String EXTRA_INITIAL_BLUR = "extra_initial_blur";
  public static final String EXTRA_INITIAL_KEY_SHADOW = "extra_initial_key_shadow";
  public static final String EXTRA_RESULT_THEME_ID = "extra_result_theme_id";

  private CropImageView _cropImageView;
  private FrameLayout _cropKeyboardOverlay;
  private FrameLayout _keyboardHolder;
  private View _touchInterceptor;
  private ImageView _btnCropBack;
  private MaterialButton _btnCropDone;

  private Slider _sliderBrightness;
  private TextView _tvBrightnessValue;
  private Slider _sliderKeyOpacity;
  private TextView _tvKeyOpacityValue;
  private Slider _sliderBlur;
  private TextView _tvBlurValue;
  private Slider _sliderKeyShadow;
  private TextView _tvKeyShadowValue;

  private Keyboard2View _previewKeyboardView;

  private Bitmap _sourceBitmap;
  private String _editThemeId = null;
  private int _brightnessPercent = 80;
  private int _keyOpacityPercent = 100;
  private int _blurPercent = 0;
  private float _keyShadowDp = 0.0f;

  private final Handler _blurHandler = new Handler(Looper.getMainLooper());
  private Runnable _blurRunnable;
  private boolean _saved = false;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_theme_crop);

    if (getWindow() != null)
    {
      if (android.os.Build.VERSION.SDK_INT >= 29)
      {
        getWindow().setNavigationBarContrastEnforced(false);
        getWindow().setStatusBarContrastEnforced(false);
      }
      androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
      getWindow().setStatusBarColor(0xFF121316);
      getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
      androidx.core.view.WindowInsetsControllerCompat insetsController =
          androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
      if (insetsController != null)
      {
        insetsController.setAppearanceLightStatusBars(false);
        insetsController.setAppearanceLightNavigationBars(false);
      }
    }

    View root = findViewById(R.id.crop_root_layout);
    if (root != null)
    {
      androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
        androidx.core.graphics.Insets insets = windowInsets.getInsets(
            androidx.core.view.WindowInsetsCompat.Type.systemBars());
        v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
        return windowInsets;
      });
    }

    _cropImageView = findViewById(R.id.crop_image_view);
    _cropKeyboardOverlay = findViewById(R.id.crop_keyboard_overlay);
    _keyboardHolder = findViewById(R.id.brightness_keyboard_holder);
    _touchInterceptor = findViewById(R.id.crop_touch_interceptor);
    _btnCropBack = findViewById(R.id.btn_crop_back);
    _btnCropDone = findViewById(R.id.btn_crop_done);

    _sliderBrightness = findViewById(R.id.slider_brightness);
    _tvBrightnessValue = findViewById(R.id.tv_brightness_value);
    _sliderKeyOpacity = findViewById(R.id.slider_key_opacity);
    _tvKeyOpacityValue = findViewById(R.id.tv_key_opacity_value);
    _sliderBlur = findViewById(R.id.slider_blur);
    _tvBlurValue = findViewById(R.id.tv_blur_value);
    _sliderKeyShadow = findViewById(R.id.slider_key_shadow);
    _tvKeyShadowValue = findViewById(R.id.tv_key_shadow_value);

    _editThemeId = getIntent().getStringExtra(EXTRA_EDIT_THEME_ID);
    float initialDarkness = getIntent().getFloatExtra(EXTRA_INITIAL_DARKNESS, 0.20f);
    _brightnessPercent = Math.max(0, Math.min(100, Math.round((1f - initialDarkness) * 100f)));
    float initialKeyOpacity = getIntent().getFloatExtra(EXTRA_INITIAL_KEY_OPACITY, 1.0f);
    _keyOpacityPercent = Math.max(0, Math.min(100, Math.round(initialKeyOpacity * 100f)));
    float initialBlur = getIntent().getFloatExtra(EXTRA_INITIAL_BLUR, 0.0f);
    _blurPercent = Math.max(0, Math.min(100, Math.round(initialBlur * 100f)));
    float initialKeyShadow = getIntent().getFloatExtra(EXTRA_INITIAL_KEY_SHADOW, 0.0f);
    _keyShadowDp = Math.max(0.0f, Math.min(6.0f, Math.round(initialKeyShadow * 10.0f) / 10.0f));

    _btnCropBack.setOnClickListener(v -> finish());
    _btnCropDone.setOnClickListener(v -> saveCustomThemeAndFinish());

    setupKeyboardOverlay();
    setupTouchInterceptor();
    setupCropRectListener();
    setupBrightnessSlider();
    setupKeyOpacitySlider();
    setupBlurSlider();
    setupKeyShadowSlider();

    loadInputBitmap();
  }

  private void setupCropRectListener()
  {
    _cropImageView.setOnCropRectChangedListener(rect -> {
      if (rect == null || rect.isEmpty() || _cropKeyboardOverlay == null) return;
      int cropWidth = Math.round(rect.width());
      int cropHeight = Math.round(rect.height());
      int leftMargin = Math.round(rect.left);
      int topMargin = Math.round(rect.top);

      FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) _cropKeyboardOverlay.getLayoutParams();
      if (lp == null)
      {
        lp = new FrameLayout.LayoutParams(cropWidth, cropHeight);
      }
      else
      {
        lp.width = cropWidth;
        lp.height = cropHeight;
      }
      lp.leftMargin = leftMargin;
      lp.topMargin = topMargin;
      _cropKeyboardOverlay.setLayoutParams(lp);
      _cropKeyboardOverlay.setVisibility(View.VISIBLE);

      if (_previewKeyboardView != null)
      {
        _previewKeyboardView.reset();
      }

      _cropKeyboardOverlay.post(() -> {
        if (_previewKeyboardView != null)
        {
          _previewKeyboardView.reset();
          _previewKeyboardView.requestLayout();
          _previewKeyboardView.invalidate();
        }
      });
    });
  }

  private void setupTouchInterceptor()
  {
    if (_touchInterceptor != null)
    {
      _touchInterceptor.setOnTouchListener((v, event) -> {
        if (_cropImageView != null)
        {
          return _cropImageView.onTouchEvent(event);
        }
        return false;
      });
    }
  }

  private void setupKeyboardOverlay()
  {
    if (Config.globalConfig() != null)
    {
      Config.globalConfig().keyOpacity = Math.max(0, Math.min(255, Math.round(_keyOpacityPercent * 255 / 100f)));
      Config.globalConfig().keyShadow = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, _keyShadowDp, getResources().getDisplayMetrics());
    }
    Context themeContext = new ContextThemeWrapper(this, R.style.CustomImageTheme);
    _previewKeyboardView = new Keyboard2View(themeContext);
    _previewKeyboardView.setThemePreviewMode(true);
    _previewKeyboardView.setBackgroundColor(Color.TRANSPARENT);
    _previewKeyboardView.setKeyboard(loadPreviewKeyboard());

    _keyboardHolder.removeAllViews();
    _keyboardHolder.addView(_previewKeyboardView, new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
  }

  private KeyboardData loadPreviewKeyboard()
  {
    try
    {
      if (Config.globalConfig() == null)
      {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Config.initGlobalConfig(prefs, getResources(), false, com.nhs.customkeyboard.dict.Dictionaries.instance(this));
      }
      else
      {
        LayoutModifier.init(Config.globalConfig(), getResources());
      }
      KeyboardData raw = KeyboardData.load(getResources(), R.xml.latn_qwerty_us);
      return LayoutModifier.modify_layout(raw, false, true);
    }
    catch (Throwable t)
    {
      return KeyboardData.load(getResources(), R.xml.latn_qwerty_us);
    }
  }

  private void setupBrightnessSlider()
  {
    if (_sliderBrightness != null)
    {
      _sliderBrightness.setValue((float) _brightnessPercent);
      _sliderBrightness.addOnChangeListener((slider, value, fromUser) -> {
        updateBrightnessDisplay(Math.round(value));
      });
    }
    updateBrightnessDisplay(_brightnessPercent);
  }

  private void updateBrightnessDisplay(int brightness)
  {
    _brightnessPercent = brightness;
    if (_tvBrightnessValue != null)
    {
      _tvBrightnessValue.setText(brightness + "%");
    }
    float darkness = (100f - brightness) / 100f;
    if (_cropImageView != null)
    {
      _cropImageView.setDarkness(darkness);
    }
  }

  private void setupKeyOpacitySlider()
  {
    if (_sliderKeyOpacity != null)
    {
      _sliderKeyOpacity.setValue((float) _keyOpacityPercent);
      _sliderKeyOpacity.addOnChangeListener((slider, value, fromUser) -> {
        updateKeyOpacityDisplay(Math.round(value));
      });
    }
    updateKeyOpacityDisplay(_keyOpacityPercent);
  }

  private void updateKeyOpacityDisplay(int opacityPercent)
  {
    _keyOpacityPercent = opacityPercent;
    if (_tvKeyOpacityValue != null)
    {
      _tvKeyOpacityValue.setText(opacityPercent + "%");
    }
    if (Config.globalConfig() != null)
    {
      Config.globalConfig().keyOpacity = Math.max(0, Math.min(255, Math.round(opacityPercent * 255 / 100f)));
    }
    if (_previewKeyboardView != null)
    {
      _previewKeyboardView.reset();
      _previewKeyboardView.invalidate();
    }
  }

  private void setupBlurSlider()
  {
    if (_sliderBlur != null)
    {
      _sliderBlur.setValue((float) _blurPercent);
      _sliderBlur.addOnChangeListener((slider, value, fromUser) -> {
        updateBlurDisplay(Math.round(value));
      });
    }
    updateBlurDisplay(_blurPercent);
  }

  private void updateBlurDisplay(int blurPercent)
  {
    _blurPercent = blurPercent;
    if (_tvBlurValue != null)
    {
      _tvBlurValue.setText(blurPercent + "%");
    }
    if (_blurRunnable != null)
    {
      _blurHandler.removeCallbacks(_blurRunnable);
    }
    _blurRunnable = () -> {
      if (_blurPercent <= 0)
      {
        if (_cropImageView != null) _cropImageView.setBlurredBitmap(null);
      }
      else
      {
        new Thread(() -> {
          if (_sourceBitmap == null || _sourceBitmap.isRecycled()) return;
          int radius = Math.max(1, Math.min(25, Math.round(_blurPercent * 25f / 100f)));
          Bitmap blurred = ImageBlurUtils.fastBlur(_sourceBitmap, radius);
          runOnUiThread(() -> {
            if (_cropImageView != null)
            {
              _cropImageView.setBlurredBitmap(blurred);
            }
          });
        }).start();
      }
    };
    _blurHandler.postDelayed(_blurRunnable, 120);
  }

  private void setupKeyShadowSlider()
  {
    if (_sliderKeyShadow != null)
    {
      float valToSet = Math.max(0.0f, Math.min(6.0f, _keyShadowDp));
      _sliderKeyShadow.setValue(valToSet);
      _sliderKeyShadow.addOnChangeListener((slider, value, fromUser) -> {
        updateKeyShadowDisplay(value);
      });
    }
    updateKeyShadowDisplay(_keyShadowDp);
  }

  private void updateKeyShadowDisplay(float shadowDp)
  {
    _keyShadowDp = Math.max(0.0f, Math.min(6.0f, Math.round(shadowDp * 10.0f) / 10.0f));
    if (_tvKeyShadowValue != null)
    {
      _tvKeyShadowValue.setText(String.format(Locale.US, "%.1fdp", _keyShadowDp));
    }
    if (Config.globalConfig() != null)
    {
      float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, _keyShadowDp, getResources().getDisplayMetrics());
      Config.globalConfig().keyShadow = px;
    }
    if (_previewKeyboardView != null)
    {
      _previewKeyboardView.invalidate();
    }
  }

  private void loadInputBitmap()
  {
    Uri imageUri = getIntent().getParcelableExtra(EXTRA_IMAGE_URI);
    if (imageUri == null && getIntent().getData() != null)
    {
      imageUri = getIntent().getData();
    }
    String imagePath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);

    try
    {
      if (imageUri != null)
      {
        InputStream is = getContentResolver().openInputStream(imageUri);
        _sourceBitmap = decodeSampledBitmap(is);
        if (is != null) is.close();

        // Check orientation from URI
        InputStream exifStream = getContentResolver().openInputStream(imageUri);
        if (exifStream != null)
        {
          ExifInterface exif = new ExifInterface(exifStream);
          _sourceBitmap = rotateBitmapIfNeeded(_sourceBitmap, exif);
          exifStream.close();
        }
      }
      else if (imagePath != null)
      {
        String rawPath = imagePath.replace(".jpg", "_raw.jpg");
        File rawFile = new File(rawPath);
        String pathToLoad = rawFile.exists() ? rawPath : imagePath;
        if (new File(pathToLoad).exists())
        {
          _sourceBitmap = BitmapFactory.decodeFile(pathToLoad);
          ExifInterface exif = new ExifInterface(pathToLoad);
          _sourceBitmap = rotateBitmapIfNeeded(_sourceBitmap, exif);
        }
      }
    }
    catch (Exception e)
    {
      Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
      finish();
      return;
    }

    if (_sourceBitmap == null)
    {
      Toast.makeText(this, "Invalid image", Toast.LENGTH_SHORT).show();
      finish();
      return;
    }

    _cropImageView.setImageBitmap(_sourceBitmap);
    if (_blurPercent > 0)
    {
      updateBlurDisplay(_blurPercent);
    }
  }

  private Bitmap decodeSampledBitmap(InputStream is)
  {
    try
    {
      return BitmapFactory.decodeStream(is);
    }
    catch (Throwable t)
    {
      return null;
    }
  }

  private Bitmap rotateBitmapIfNeeded(Bitmap bitmap, ExifInterface exif)
  {
    if (bitmap == null || exif == null) return bitmap;
    int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
    int rotation = 0;
    if (orientation == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
    else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
    else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;

    if (rotation != 0)
    {
      Matrix matrix = new Matrix();
      matrix.postRotate(rotation);
      return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
    return bitmap;
  }

  private void saveCustomThemeAndFinish()
  {
    try
    {
      Bitmap cropped = _cropImageView.getCroppedBitmap();
      if (cropped == null)
      {
        Toast.makeText(this, "Failed to crop image", Toast.LENGTH_SHORT).show();
        return;
      }

      File dir = new File(getFilesDir(), "themes");
      if (!dir.exists()) dir.mkdirs();

      String themeId = (_editThemeId != null) ? _editThemeId : ("custom_" + UUID.randomUUID().toString());
      File outFile = new File(dir, themeId + ".jpg");

      FileOutputStream fos = new FileOutputStream(outFile);
      cropped.compress(Bitmap.CompressFormat.JPEG, 92, fos);
      fos.flush();
      fos.close();

      // Also save raw uncropped bitmap if new theme
      if (_sourceBitmap != null && _editThemeId == null)
      {
        File rawFile = new File(dir, themeId + "_raw.jpg");
        FileOutputStream rawFos = new FileOutputStream(rawFile);
        _sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 90, rawFos);
        rawFos.flush();
        rawFos.close();
      }

      float darkness = (100f - _brightnessPercent) / 100f;
      float keyOpacity = _keyOpacityPercent / 100f;
      float blur = _blurPercent / 100f;
      float keyShadow = _keyShadowDp;

      ThemeModel model = new ThemeModel(
          themeId,
          getString(R.string.theme_custom_theme_title),
          outFile.getAbsolutePath(),
          darkness,
          keyOpacity,
          blur,
          keyShadow
      );

      ThemeRepository.saveCustomTheme(this, model);

      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
      prefs.edit()
          .putString("theme", themeId)
          .putFloat("custom_theme_darkness", darkness)
          .putFloat("custom_theme_key_opacity", keyOpacity)
          .putFloat("custom_theme_key_shadow", keyShadow)
          .putString("custom_theme_image_path", outFile.getAbsolutePath())
          .apply();

      if (Config.globalConfig() != null)
      {
        Config.globalConfig().themeName = themeId;
        Config.globalConfig().customThemeDarkness = darkness;
        Config.globalConfig().customThemeKeyOpacity = keyOpacity;
        Config.globalConfig().customThemeKeyShadow = keyShadow;
        Config.globalConfig().customThemeImagePath = outFile.getAbsolutePath();
        Config.globalConfig().keyOpacity = Math.max(0, Math.min(255, Math.round(keyOpacity * 255)));
        Config.globalConfig().keyShadow = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, keyShadow, getResources().getDisplayMetrics());
      }

      _saved = true;
      Intent resultIntent = new Intent();
      resultIntent.putExtra(EXTRA_RESULT_THEME_ID, themeId);
      setResult(RESULT_OK, resultIntent);
      finish();
    }
    catch (Exception e)
    {
      Toast.makeText(this, "Failed to save theme", Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  protected void onDestroy()
  {
    if (_blurRunnable != null)
    {
      _blurHandler.removeCallbacks(_blurRunnable);
    }
    if (!_saved && Config.globalConfig() != null)
    {
      SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(this);
      String themeName = p.getString("theme", "monet");
      if (themeName != null && themeName.startsWith("custom_"))
      {
        Config.globalConfig().keyOpacity = Math.max(0, Math.min(255, Math.round(p.getFloat("custom_theme_key_opacity", 1.0f) * 255)));
        Config.globalConfig().keyShadow = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, p.getFloat("custom_theme_key_shadow", 0.0f), getResources().getDisplayMetrics());
      }
      else
      {
        Config.globalConfig().keyOpacity = p.getInt("key_opacity", 100) * 255 / 100;
        Config.globalConfig().keyShadow = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, p.getFloat("key_shadow", 3.0f), getResources().getDisplayMetrics());
      }
    }
    super.onDestroy();
  }
}
