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
    super.onCreate(savedInstanceState);
    setContentView(R.layout.dictionaries_activity);

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
