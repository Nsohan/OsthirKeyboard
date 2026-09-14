package com.nhs.customkeyboard.theme;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
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
import java.util.UUID;

public class ThemeCropActivity extends AppCompatActivity
{
  public static final String EXTRA_IMAGE_URI = "extra_image_uri";
  public static final String EXTRA_IMAGE_PATH = "extra_image_path";
  public static final String EXTRA_EDIT_THEME_ID = "extra_edit_theme_id";
  public static final String EXTRA_INITIAL_DARKNESS = "extra_initial_darkness";
  public static final String EXTRA_RESULT_THEME_ID = "extra_result_theme_id";

  private View _layoutStepCrop;
  private View _layoutStepBrightness;

  private CropImageView _cropImageView;
  private ImageView _btnCropBack;
  private MaterialButton _btnCropNext;

  private ImageView _btnBrightnessBack;
  private Slider _sliderBrightness;
  private TextView _tvBrightnessValue;
  private ImageView _ivCroppedPreview;
  private View _darknessOverlay;
  private FrameLayout _keyboardHolder;
  private MaterialButton _btnBrightnessDone;

  private Bitmap _sourceBitmap;
  private Bitmap _croppedBitmap;
  private String _editThemeId = null;
  private int _brightnessPercent = 80;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_theme_crop);

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

    _layoutStepCrop = findViewById(R.id.layout_step_crop);
    _layoutStepBrightness = findViewById(R.id.layout_step_brightness);

    // Step 1 Views
    _cropImageView = findViewById(R.id.crop_image_view);
    _btnCropBack = findViewById(R.id.btn_crop_back);
    _btnCropNext = findViewById(R.id.btn_crop_next);

    // Step 2 Views
    _btnBrightnessBack = findViewById(R.id.btn_brightness_back);
    _sliderBrightness = findViewById(R.id.slider_brightness);
    _tvBrightnessValue = findViewById(R.id.tv_brightness_value);
    _ivCroppedPreview = findViewById(R.id.iv_cropped_preview);
    _darknessOverlay = findViewById(R.id.brightness_darkness_overlay);
    _keyboardHolder = findViewById(R.id.brightness_keyboard_holder);
    _btnBrightnessDone = findViewById(R.id.btn_brightness_done);

    _editThemeId = getIntent().getStringExtra(EXTRA_EDIT_THEME_ID);
    float initialDarkness = getIntent().getFloatExtra(EXTRA_INITIAL_DARKNESS, 0.20f);
    _brightnessPercent = Math.max(0, Math.min(100, Math.round((1f - initialDarkness) * 100f)));

    _btnCropBack.setOnClickListener(v -> finish());
    _btnCropNext.setOnClickListener(v -> goToBrightnessStep());

    _btnBrightnessBack.setOnClickListener(v -> goToCropStep());
    _btnBrightnessDone.setOnClickListener(v -> saveCustomThemeAndFinish());

    setupBrightnessSlider();
    loadInputBitmap();
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
      else if (imagePath != null && new File(imagePath).exists())
      {
        _sourceBitmap = BitmapFactory.decodeFile(imagePath);
        ExifInterface exif = new ExifInterface(imagePath);
        _sourceBitmap = rotateBitmapIfNeeded(_sourceBitmap, exif);
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
    if (_editThemeId != null)
    {
      _croppedBitmap = _sourceBitmap;
      _ivCroppedPreview.setImageBitmap(_croppedBitmap);
      updateBrightnessDisplay(_brightnessPercent);
      setupSuperimposedKeyboard();
      _layoutStepCrop.setVisibility(View.GONE);
      _layoutStepBrightness.setVisibility(View.VISIBLE);
    }
    else
    {
      goToCropStep();
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
      Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
      bitmap.recycle();
      return rotated;
    }
    return bitmap;
  }

  private void goToCropStep()
  {
    _layoutStepBrightness.setVisibility(View.GONE);
    _layoutStepCrop.setVisibility(View.VISIBLE);
  }

  private void goToBrightnessStep()
  {
    _croppedBitmap = _cropImageView.getCroppedBitmap();
    if (_croppedBitmap == null)
    {
      _croppedBitmap = _sourceBitmap;
    }

    _ivCroppedPreview.setImageBitmap(_croppedBitmap);
    updateBrightnessDisplay(_brightnessPercent);
    setupSuperimposedKeyboard();

    _layoutStepCrop.setVisibility(View.GONE);
    _layoutStepBrightness.setVisibility(View.VISIBLE);
  }

  private void setupBrightnessSlider()
  {
    _sliderBrightness.setValue((float) _brightnessPercent);
    _sliderBrightness.addOnChangeListener((slider, value, fromUser) -> {
      updateBrightnessDisplay(Math.round(value));
    });
  }

  private void updateBrightnessDisplay(int brightness)
  {
    _brightnessPercent = brightness;
    _tvBrightnessValue.setText(brightness + "%");
    float darkness = (100f - brightness) / 100f;
    _darknessOverlay.setAlpha(darkness);
  }

  private void setupSuperimposedKeyboard()
  {
    Context themeContext = new ContextThemeWrapper(this, R.style.Dark);
    Keyboard2View kv = new Keyboard2View(themeContext);
    kv.setThemePreviewMode(true);
    kv.setBackgroundColor(Color.TRANSPARENT);
    kv.setKeyboard(loadPreviewKeyboard());

    _keyboardHolder.removeAllViews();
    _keyboardHolder.addView(kv, new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
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

  private void saveCustomThemeAndFinish()
  {
    try
    {
      File dir = new File(getFilesDir(), "themes");
      if (!dir.exists()) dir.mkdirs();

      String themeId = (_editThemeId != null) ? _editThemeId : ("custom_" + UUID.randomUUID().toString());
      File destFile = new File(dir, themeId + ".jpg");

      Bitmap bitmapToSave = (_croppedBitmap != null) ? _croppedBitmap : _sourceBitmap;
      FileOutputStream fos = new FileOutputStream(destFile);
      bitmapToSave.compress(Bitmap.CompressFormat.JPEG, 90, fos);
      fos.close();

      float darkness = (100f - _brightnessPercent) / 100f;
      ThemeModel model = new ThemeModel(themeId, getString(R.string.theme_custom_theme_title), destFile.getAbsolutePath(), darkness);
      ThemeRepository.saveCustomTheme(this, model);

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
  public void onBackPressed()
  {
    if (_layoutStepBrightness != null && _layoutStepBrightness.getVisibility() == View.VISIBLE)
    {
      goToCropStep();
    }
    else
    {
      super.onBackPressed();
    }
  }

  @Override
  protected void onDestroy()
  {
    if (_sourceBitmap != null && !_sourceBitmap.isRecycled())
    {
      _sourceBitmap.recycle();
      _sourceBitmap = null;
    }
    if (_croppedBitmap != null && !_croppedBitmap.isRecycled())
    {
      _croppedBitmap.recycle();
      _croppedBitmap = null;
    }
    super.onDestroy();
  }
}
