package com.nhs.customkeyboard.prefs;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.nhs.customkeyboard.Logs;
import com.nhs.customkeyboard.R;
import com.nhs.customkeyboard.suggestions.UserLearningDatabase;
import com.nhs.customkeyboard.suggestions.UserLearningEngine;

import java.io.File;
import java.io.InputStream;

public class UserDictionaryBackupActivity extends AppCompatActivity
{
  private static final int REQUEST_CODE_IMPORT = 1001;

  private UserLearningDatabase _db;
  private UserLearningEngine _engine;
  private TextView _tvStats;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_user_dictionary_backup);
    setTitle(R.string.pref_backup_restore_title);

    _db = UserLearningDatabase.getInstance(this);
    _engine = UserLearningEngine.getInstance(this);

    _tvStats = findViewById(R.id.tv_stats);
    Button btnExport = findViewById(R.id.btn_export);
    Button btnImport = findViewById(R.id.btn_import);
    Button btnSync = findViewById(R.id.btn_sync_system);
    Button btnClear = findViewById(R.id.btn_clear_history);

    updateStats();

    btnExport.setOnClickListener(v -> exportBackup());
    btnImport.setOnClickListener(v -> pickBackupFile());
    btnSync.setOnClickListener(v -> syncSystemDictionary());
    btnClear.setOnClickListener(v -> confirmClearHistory());
  }

  private void updateStats()
  {
    int words = _db.getLearnedWordCount();
    int bigrams = _db.getLearnedBigramCount();
    _tvStats.setText(getString(R.string.user_dict_stats_format, words, bigrams));
  }

  private void exportBackup()
  {
    try
    {
      File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
      if (!downloadsDir.exists()) downloadsDir.mkdirs();
      File backupFile = new File(downloadsDir, "OsthirKeyboard_User_Dictionary_Backup.json");

      boolean success = _db.exportToJson(backupFile);
      if (success)
      {
        Toast.makeText(this, "Exported successfully to:\n" + backupFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
      }
      else
      {
        Toast.makeText(this, "Export failed.", Toast.LENGTH_SHORT).show();
      }
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
    if (requestCode == REQUEST_CODE_IMPORT && resultCode == RESULT_OK && data != null)
    {
      Uri uri = data.getData();
      if (uri != null)
      {
        importFromUri(uri);
      }
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
