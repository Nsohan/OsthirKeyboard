package com.nhs.customkeyboard.prefs;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.nhs.customkeyboard.Logs;
import com.nhs.customkeyboard.R;
import com.nhs.customkeyboard.suggestions.UserLearningDatabase;
import com.nhs.customkeyboard.suggestions.UserLearningEngine;

import java.io.InputStream;

public class UserDictionaryBackupActivity extends AppCompatActivity
{
  private static final int REQUEST_CODE_IMPORT = 1001;
  private static final int REQUEST_CODE_EXPORT = 1002;

  private UserLearningDatabase _db;
  private UserLearningEngine _engine;
  private TextView _tvStats;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState)
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

    setContentView(R.layout.activity_user_dictionary_backup);

    View root = findViewById(R.id.backup_root_layout);
    if (root != null)
    {
      androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
        androidx.core.graphics.Insets insets = windowInsets.getInsets(
            androidx.core.view.WindowInsetsCompat.Type.systemBars());
        v.setPadding(insets.left, insets.top, insets.right, 0);
        return windowInsets;
      });
    }

    View scrollView = findViewById(R.id.backup_scroll_view);
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

    MaterialToolbar toolbar = findViewById(R.id.backup_toolbar);
    if (toolbar != null)
    {
      setSupportActionBar(toolbar);
      if (getSupportActionBar() != null)
      {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      }
      toolbar.setNavigationOnClickListener(v -> finish());
    }
    setTitle(R.string.pref_backup_restore_title);

    _db = UserLearningDatabase.getInstance(this);
    _engine = UserLearningEngine.getInstance(this);

    _tvStats = findViewById(R.id.tv_stats);
    View btnExport = findViewById(R.id.btn_export);
    View btnImport = findViewById(R.id.btn_import);
    View btnSync = findViewById(R.id.btn_sync_system);
    View btnClear = findViewById(R.id.btn_clear_history);

    updateStats();

    if (btnExport != null) btnExport.setOnClickListener(v -> exportBackup());
    if (btnImport != null) btnImport.setOnClickListener(v -> pickBackupFile());
    if (btnSync != null) btnSync.setOnClickListener(v -> syncSystemDictionary());
    if (btnClear != null) btnClear.setOnClickListener(v -> confirmClearHistory());
  }

  @Override
  public boolean onSupportNavigateUp()
  {
    finish();
    return true;
  }

  private void updateStats()
  {
    int words = _db.getLearnedWordCount();
    int bigrams = _db.getLearnedBigramCount();
    int emails = _db.getLearnedEmailCount();
    String text = getString(R.string.user_dict_stats_format, words, bigrams);
    if (emails > 0)
    {
      text += "\nLearned Emails: " + emails;
    }
    _tvStats.setText(text);
  }

  private void exportBackup()
  {
    try
    {
      Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
      intent.addCategory(Intent.CATEGORY_OPENABLE);
      intent.setType("application/json");
      intent.putExtra(Intent.EXTRA_TITLE, "OsthirKeyboard_User_Dictionary_Backup.json");
      startActivityForResult(intent, REQUEST_CODE_EXPORT);
    }
    catch (Exception e)
    {
      Logs.exn("UserDictBackup", e);
      Toast.makeText(this, "Export error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }
  }

  private void pickBackupFile()
  {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("*/*");
    startActivityForResult(intent, REQUEST_CODE_IMPORT);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
  {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == RESULT_OK && data != null && data.getData() != null)
    {
      Uri uri = data.getData();
      if (requestCode == REQUEST_CODE_EXPORT)
      {
        exportToUri(uri);
      }
      else if (requestCode == REQUEST_CODE_IMPORT)
      {
        importFromUri(uri);
      }
    }
  }

  private void exportToUri(Uri uri)
  {
    try (java.io.OutputStream os = getContentResolver().openOutputStream(uri))
    {
      if (os != null)
      {
        boolean success = _db.exportToJson(os);
        if (success)
        {
          Toast.makeText(this, "Backup exported successfully!", Toast.LENGTH_LONG).show();
        }
        else
        {
          Toast.makeText(this, "Export failed.", Toast.LENGTH_SHORT).show();
        }
      }
    }
    catch (Exception e)
    {
      Logs.exn("UserDictBackup", e);
      Toast.makeText(this, "Export error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }
  }

  private void importFromUri(Uri uri)
  {
    try (InputStream is = getContentResolver().openInputStream(uri))
    {
      if (is != null)
      {
        boolean success = _db.importFromJson(is);
        if (success)
        {
          updateStats();
          Toast.makeText(this, "Backup imported and merged successfully!", Toast.LENGTH_SHORT).show();
        }
        else
        {
          Toast.makeText(this, "Import failed. Invalid JSON format.", Toast.LENGTH_SHORT).show();
        }
      }
    }
    catch (Exception e)
    {
      Logs.exn("UserDictBackup", e);
      Toast.makeText(this, "Import error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }
  }

  private void syncSystemDictionary()
  {
    int count = _db.syncWithSystemUserDictionary(this);
    updateStats();
    Toast.makeText(this, "Synced " + count + " words with Android User Dictionary.", Toast.LENGTH_SHORT).show();
  }

  private void confirmClearHistory()
  {
    new AlertDialog.Builder(this)
        .setTitle(R.string.pref_clear_learned_title)
        .setMessage(R.string.pref_clear_learned_confirm)
        .setPositiveButton("Clear", (dialog, which) -> {
          _engine.clearAll();
          updateStats();
          Toast.makeText(this, "Learned history cleared.", Toast.LENGTH_SHORT).show();
        })
        .setNegativeButton("Cancel", null)
        .show();
  }
}
