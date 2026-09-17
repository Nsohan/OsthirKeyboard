package com.nhs.customkeyboard.dict;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.nhs.customkeyboard.R;

public class DictionariesActivity extends AppCompatActivity
{
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    if (android.os.Build.VERSION.SDK_INT >= 29)
    {
      getWindow().setNavigationBarContrastEnforced(false);
      getWindow().setStatusBarContrastEnforced(false);
    }
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

    androidx.core.view.WindowInsetsControllerCompat insetsController =
        androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
    if (insetsController != null)
    {
      boolean isNight = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
      insetsController.setAppearanceLightStatusBars(!isNight);
      insetsController.setAppearanceLightNavigationBars(!isNight);
    }

    setContentView(R.layout.dictionaries_activity);

    android.view.View root = findViewById(R.id.dictionaries_root_layout);
    if (root != null)
    {
      androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
        androidx.core.graphics.Insets insets = windowInsets.getInsets(
            androidx.core.view.WindowInsetsCompat.Type.systemBars());
        v.setPadding(insets.left, insets.top, insets.right, 0);
        return windowInsets;
      });
    }

    android.view.View scrollView = findViewById(R.id.dictionaries_scroll_view);
    if (scrollView != null)
    {
      androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scrollView, (v, windowInsets) -> {
        androidx.core.graphics.Insets insets = windowInsets.getInsets(
            androidx.core.view.WindowInsetsCompat.Type.systemBars());
        int extraBottom = (int) (16 * getResources().getDisplayMetrics().density);
        v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), insets.bottom + extraBottom);
        return windowInsets;
      });
    }

    MaterialToolbar toolbar = findViewById(R.id.dictionaries_toolbar);
    if (toolbar != null)
    {
      setSupportActionBar(toolbar);
      if (getSupportActionBar() != null)
      {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      }
      toolbar.setNavigationOnClickListener(v -> finish());
    }
    setTitle(R.string.pref_screen_dictionary_title);
  }

  @Override
  public boolean onSupportNavigateUp()
  {
    finish();
    return true;
  }
}
