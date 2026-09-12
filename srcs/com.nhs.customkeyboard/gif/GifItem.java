package com.nhs.customkeyboard.gif;

public class GifItem
{
  public final String id;
  public final String title;
  public final String previewUrl;
  public final String contentUrl;
  public final int width;
  public final int height;

  public GifItem(String id, String title, String previewUrl, String contentUrl, int width, int height)
  {
    this.id = id;
    this.title = title != null ? title : "";
    this.previewUrl = previewUrl;
    this.contentUrl = contentUrl != null ? contentUrl : previewUrl;
    this.width = width > 0 ? width : 200;
    this.height = height > 0 ? height : 200;
  }
}
