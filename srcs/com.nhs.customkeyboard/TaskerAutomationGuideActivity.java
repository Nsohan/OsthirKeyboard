package com.nhs.customkeyboard;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.WebView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/** Read-only, self-contained "how do I use this" screen for the Tasker
 Automation feature (see TaskerAutomationConfig / TaskerTriggerEngine /
 TaskerBridge). Reachable from Settings > Tasker Automation > "How to
 use Tasker Automation".

 The guide itself is a static HTML+CSS page bundled at
 assets/tasker_automation_guide.html rather than a native layout: it's
 long, section-heavy, cross-referencing content that's far easier to
 keep readable (and to edit later) as one styled document than as a
 tree of TextViews/CardViews, and a plain WebView pointed at an
 in-package asset needs no network access or JS to render it nicely. */
public class TaskerAutomationGuideActivity extends AppCompatActivity
{
  @SuppressLint("SetJavaScriptEnabled")
  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
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

    setContentView(R.layout.tasker_automation_guide_activity);

    android.view.View root = findViewById(R.id.tasker_guide_root_layout);
    if (root != null)
    {
      androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
        androidx.core.graphics.Insets insets = windowInsets.getInsets(
            androidx.core.view.WindowInsetsCompat.Type.systemBars());
        v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
        return windowInsets;
      });
    }

    Toolbar toolbar = findViewById(R.id.tasker_guide_toolbar);
    setSupportActionBar(toolbar);
    setTitle(R.string.tasker_guide_activity_title);
    if (getSupportActionBar() != null)
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    WebView web_view = findViewById(R.id.tasker_guide_webview);
    // Static, bundled content only (no navigation away from it, no
    // remote/user-controlled content) - JS stays off since the page
    // doesn't need it.
    web_view.getSettings().setJavaScriptEnabled(false);
    web_view.getSettings().setDisplayZoomControls(false);
    web_view.getSettings().setBuiltInZoomControls(true);
    web_view.loadUrl("file:///android_asset/tasker_automation_guide.html");
  }

  @Override
  public boolean onSupportNavigateUp()
  {
    finish();
    return true;
  }
}
