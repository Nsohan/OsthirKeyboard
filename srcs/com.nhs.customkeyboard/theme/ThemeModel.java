package com.nhs.customkeyboard.theme;

import androidx.annotation.StringRes;

public class ThemeModel
{
  public static final int CATEGORY_MY_THEMES = 0;
  public static final int CATEGORY_DEFAULT = 1;
  public static final int CATEGORY_COLORS = 2;

  public final String id;
  public final int category;
  @StringRes public final int titleRes;
  public final String customTitle;
  public final int themeResId;
  public final int themeNightResId;
  public final int previewBgColor;
  public final int previewKeyColor;
  public final int previewAccentColor;
  public final boolean isDynamicColor;
  public final boolean isSystemAuto;
  public final boolean isCustomPhoto;
  public final String imagePath;
  public final float darknessOverlay;
  public final float keyOpacity;
  public final float blur;
  public final float keyShadow;

  public ThemeModel(String id, int category, int titleRes, int themeResId,
                    int themeNightResId, int previewBgColor, int previewKeyColor,
                    int previewAccentColor, boolean isDynamicColor,
                    boolean isSystemAuto, boolean isCustomPhoto,
                    String imagePath, float darknessOverlay, float keyOpacity, float blur, float keyShadow)
  {
    this.id = id;
    this.category = category;
    this.titleRes = titleRes;
    this.customTitle = null;
    this.themeResId = themeResId;
    this.themeNightResId = themeNightResId;
    this.previewBgColor = previewBgColor;
    this.previewKeyColor = previewKeyColor;
    this.previewAccentColor = previewAccentColor;
    this.isDynamicColor = isDynamicColor;
    this.isSystemAuto = isSystemAuto;
    this.isCustomPhoto = isCustomPhoto;
    this.imagePath = imagePath;
    this.darknessOverlay = darknessOverlay;
    this.keyOpacity = keyOpacity;
    this.blur = blur;
    this.keyShadow = keyShadow;
  }

  public ThemeModel(String id, int category, int titleRes, int themeResId,
                    int themeNightResId, int previewBgColor, int previewKeyColor,
                    int previewAccentColor, boolean isDynamicColor,
                    boolean isSystemAuto, boolean isCustomPhoto,
                    String imagePath, float darknessOverlay, float keyOpacity, float blur)
  {
    this(id, category, titleRes, themeResId, themeNightResId, previewBgColor,
        previewKeyColor, previewAccentColor, isDynamicColor, isSystemAuto,
        isCustomPhoto, imagePath, darknessOverlay, keyOpacity, blur, 0.0f);
  }

  public ThemeModel(String id, int category, int titleRes, int themeResId,
                    int themeNightResId, int previewBgColor, int previewKeyColor,
                    int previewAccentColor, boolean isDynamicColor,
                    boolean isSystemAuto, boolean isCustomPhoto,
                    String imagePath, float darknessOverlay, float keyOpacity)
  {
    this(id, category, titleRes, themeResId, themeNightResId, previewBgColor,
        previewKeyColor, previewAccentColor, isDynamicColor, isSystemAuto,
        isCustomPhoto, imagePath, darknessOverlay, keyOpacity, 0f, 0.0f);
  }

  public ThemeModel(String id, int category, int titleRes, int themeResId,
                    int themeNightResId, int previewBgColor, int previewKeyColor,
                    int previewAccentColor, boolean isDynamicColor,
                    boolean isSystemAuto, boolean isCustomPhoto,
                    String imagePath, float darknessOverlay)
  {
    this(id, category, titleRes, themeResId, themeNightResId, previewBgColor,
        previewKeyColor, previewAccentColor, isDynamicColor, isSystemAuto,
        isCustomPhoto, imagePath, darknessOverlay, 1.0f, 0f, 0.0f);
  }

  public ThemeModel(String id, String customTitle, String imagePath, float darknessOverlay, float keyOpacity, float blur, float keyShadow)
  {
    this.id = id;
    this.category = CATEGORY_MY_THEMES;
    this.titleRes = 0;
    this.customTitle = customTitle;
    this.themeResId = 0;
    this.themeNightResId = 0;
    this.previewBgColor = 0xFF212121;
    this.previewKeyColor = 0x33FFFFFF;
    this.previewAccentColor = 0xFF4285F4;
    this.isDynamicColor = false;
    this.isSystemAuto = false;
    this.isCustomPhoto = true;
    this.imagePath = imagePath;
    this.darknessOverlay = darknessOverlay;
    this.keyOpacity = keyOpacity;
    this.blur = blur;
    this.keyShadow = keyShadow;
  }

  public ThemeModel(String id, String customTitle, String imagePath, float darknessOverlay, float keyOpacity, float blur)
  {
    this(id, customTitle, imagePath, darknessOverlay, keyOpacity, blur, 0.0f);
  }

  public ThemeModel(String id, String customTitle, String imagePath, float darknessOverlay, float keyOpacity)
  {
    this(id, customTitle, imagePath, darknessOverlay, keyOpacity, 0f, 0.0f);
  }

  public ThemeModel(String id, String customTitle, String imagePath, float darknessOverlay)
  {
    this(id, customTitle, imagePath, darknessOverlay, 1.0f, 0f, 0.0f);
  }
}
