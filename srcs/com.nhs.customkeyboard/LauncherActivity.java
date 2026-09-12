package com.nhs.customkeyboard;

import android.content.Intent;
import android.graphics.drawable.Animatable;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.ArrayList;
import java.util.List;
import com.nhs.customkeyboard.dict.DictionariesActivity;
import com.nhs.customkeyboard.R;

public class LauncherActivity extends AppCompatActivity implements Handler.Callback
{
  /** Text is replaced when receiving key events. */
  TextView _tryhere_text;
  EditText _tryhere_area;
  /** Periodically restart the animations. */
  List<Animatable> _animations;
  Handler _handler;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.launcher_activity);

    MaterialToolbar toolbar = findViewById(R.id.launcher_toolbar);
    if (toolbar != null)
    {
      setSupportActionBar(toolbar);
    }

    _tryhere_text = findViewById(R.id.launcher_tryhere_text);
    _tryhere_area = findViewById(R.id.launcher_tryhere_area);
    if (VERSION.SDK_INT >= 28 && _tryhere_area != null)
      _tryhere_area.addOnUnhandledKeyEventListener(
          this.new Tryhere_OnUnhandledKeyEventListener());
    _handler = new Handler(getMainLooper(), this);
  }

  @Override
  protected void onStart()
  {
    super.onStart();
    _animations = new ArrayList<Animatable>();
    add_anim(R.id.launcher_anim_swipe);
    add_anim(R.id.launcher_anim_round_trip);
    add_anim(R.id.launcher_anim_circle);
    _handler.removeMessages(0);
    _handler.sendEmptyMessageDelayed(0, 500);
  }

  @Override
  protected void onStop()
  {
    super.onStop();
    if (_handler != null)
      _handler.removeMessages(0);
  }

  private void add_anim(int id)
  {
    Animatable anim = find_anim(id);
    if (anim != null)
      _animations.add(anim);
  }

  @Override
  public boolean handleMessage(Message _msg)
  {
    if (_animations != null)
    {
      for (Animatable anim : _animations)
      {
        if (anim != null)
          anim.start();
      }
    }
    _handler.sendEmptyMessageDelayed(0, 3000);
    return true;
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
    getMenuInflater().inflate(R.menu.launcher_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
    if (item.getItemId() == R.id.btnLaunchSettingsActivity)
    {
      Intent intent = new Intent(LauncherActivity.this, SettingsActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  public void launch_imesettings(View _btn)
  {
    startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
  }

  public void launch_imepicker(View v)
  {
    InputMethodManager imm =
      (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
    if (imm != null)
      imm.showInputMethodPicker();
  }

  public void launch_dictionaries_activity(View v)
  {
    startActivity(new Intent(this, DictionariesActivity.class));
  }

  Animatable find_anim(int id)
  {
    ImageView img = findViewById(id);
    if (img == null) return null;
    return (Animatable)img.getDrawable();
  }

  final class Tryhere_OnUnhandledKeyEventListener implements View.OnUnhandledKeyEventListener
  {
    public boolean onUnhandledKeyEvent(View v, KeyEvent ev)
    {
      // Don't handle the back key
      if (ev.getKeyCode() == KeyEvent.KEYCODE_BACK)
        return false;
      // Key release of modifiers would erase interesting data
      if (KeyEvent.isModifierKey(ev.getKeyCode()))
        return false;
      StringBuilder s = new StringBuilder();
      if (ev.isAltPressed()) s.append("Alt+");
      if (ev.isShiftPressed()) s.append("Shift+");
      if (ev.isCtrlPressed()) s.append("Ctrl+");
      if (ev.isMetaPressed()) s.append("Meta+");
      // s.append(ev.getDisplayLabel());
      String kc = KeyEvent.keyCodeToString(ev.getKeyCode());
      s.append(kc.replaceFirst("^KEYCODE_", ""));
      if (_tryhere_text != null)
        _tryhere_text.setText(s.toString());
      return false;
    }
  }
}
