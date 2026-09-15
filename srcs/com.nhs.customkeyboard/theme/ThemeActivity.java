package com.nhs.customkeyboard.theme;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.nhs.customkeyboard.DirectBootAwarePreferences;
import com.nhs.customkeyboard.R;

import java.util.ArrayList;
import java.util.List;

public class ThemeActivity extends AppCompatActivity
{
  private String _activeThemeId = "monet";
  private SharedPreferences _prefs;

  private ThemeAdapter _myThemesAdapter;
  private ThemeAdapter _defaultThemesAdapter;
  private ThemeAdapter _colorsAdapter;

  private boolean _isColorsExpanded = true;
  private ImageView _chevronColors;
  private RecyclerView _recyclerColors;

  private final ActivityResultLauncher<Intent> _cropLauncher =
      registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK && result.getData() != null)
        {
          String newThemeId = result.getData().getStringExtra(ThemeCropActivity.EXTRA_RESULT_THEME_ID);
          if (newThemeId != null)
          {
            refreshMyThemes();
            openPreview(newThemeId);
          }
        }
      });

  private final ActivityResultLauncher<String> _pickImageLauncher =
      registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
        if (uri != null)
        {
          Intent intent = new Intent(ThemeActivity.this, ThemeCropActivity.class);
          intent.putExtra(ThemeCropActivity.EXTRA_IMAGE_URI, uri);
          _cropLauncher.launch(intent);
        }
      });

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
    if (insetsController != null)
    {
      boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
      insetsController.setAppearanceLightStatusBars(!isNight);
      insetsController.setAppearanceLightNavigationBars(!isNight);
    }

    setContentView(R.layout.activity_theme);

    View root = findViewById(R.id.theme_root_layout);
    if (root != null)
    {
      androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
        androidx.core.graphics.Insets insets = windowInsets.getInsets(
            androidx.core.view.WindowInsetsCompat.Type.systemBars());
        v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
        return windowInsets;
      });
    }

    Toolbar toolbar = findViewById(R.id.theme_toolbar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null)
    {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setDisplayShowHomeEnabled(true);
    }
    toolbar.setNavigationOnClickListener(v -> finish());

    _prefs = PreferenceManager.getDefaultSharedPreferences(this);
    _activeThemeId = _prefs.getString("theme", "monet");

    setupMyThemes();
    setupDefaultThemes();
    setupColors();
  }

  private void setupMyThemes()
  {
    RecyclerView recycler = findViewById(R.id.recycler_my_themes);
    recycler.setLayoutManager(new GridLayoutManager(this, 3));

    List<ThemeModel> customThemes = ThemeRepository.getCustomThemes(this);
    _myThemesAdapter = new ThemeAdapter(customThemes, true, false, _activeThemeId, new ThemeAdapter.OnThemeClickListener() {
      @Override
      public void onThemeClick(ThemeModel model)
      {
        openPreview(model.id);
      }

      @Override
      public void onAddClick()
      {
        _pickImageLauncher.launch("image/*");
      }

      @Override
      public void onThemeLongClick(ThemeModel model)
      {
        confirmDeleteCustomTheme(model);
      }
    });
    recycler.setAdapter(_myThemesAdapter);
  }

  private void setupDefaultThemes()
  {
    RecyclerView recycler = findViewById(R.id.recycler_default);
    recycler.setLayoutManager(new GridLayoutManager(this, 3));

    List<ThemeModel> defaultThemes = ThemeRepository.getDefaultThemes();
    _defaultThemesAdapter = new ThemeAdapter(defaultThemes, false, true, _activeThemeId, new ThemeAdapter.OnThemeClickListener() {
      @Override
      public void onThemeClick(ThemeModel model)
      {
        openPreview(model.id);
      }

      @Override
      public void onAddClick() {}

      @Override
      public void onThemeLongClick(ThemeModel model) {}
    });
    recycler.setAdapter(_defaultThemesAdapter);
  }

  private void setupColors()
  {
    View headerContainer = findViewById(R.id.header_colors_container);
    _chevronColors = findViewById(R.id.chevron_colors);
    _recyclerColors = findViewById(R.id.recycler_colors);
    _recyclerColors.setLayoutManager(new GridLayoutManager(this, 3));

    List<ThemeModel> colorThemes = ThemeRepository.getColorThemes();
    _colorsAdapter = new ThemeAdapter(colorThemes, false, false, _activeThemeId, new ThemeAdapter.OnThemeClickListener() {
      @Override
      public void onThemeClick(ThemeModel model)
      {
        openPreview(model.id);
      }

      @Override
      public void onAddClick() {}

      @Override
      public void onThemeLongClick(ThemeModel model) {}
    });
    _recyclerColors.setAdapter(_colorsAdapter);

    headerContainer.setOnClickListener(v -> {
      _isColorsExpanded = !_isColorsExpanded;
      _recyclerColors.setVisibility(_isColorsExpanded ? View.VISIBLE : View.GONE);
      _chevronColors.setImageResource(_isColorsExpanded ? R.drawable.ic_chevron_down : R.drawable.ic_chevron_right);
    });
  }

  private void openPreview(String themeId)
  {
    ThemePreviewBottomSheet sheet = ThemePreviewBottomSheet.newInstance(themeId);
    sheet.setOnThemeActionListener(new ThemePreviewBottomSheet.OnThemeActionListener() {
      @Override
      public void onThemeApplied(String appliedId, boolean keyBorders)
      {
        _activeThemeId = appliedId;
        if (_myThemesAdapter != null) _myThemesAdapter.setActiveThemeId(appliedId);
        if (_defaultThemesAdapter != null) _defaultThemesAdapter.setActiveThemeId(appliedId);
        if (_colorsAdapter != null) _colorsAdapter.setActiveThemeId(appliedId);

        DirectBootAwarePreferences.copy_preferences_to_protected_storage(ThemeActivity.this, _prefs);
      }

      @Override
      public void onThemeEdit(ThemeModel model)
      {
        Intent intent = new Intent(ThemeActivity.this, ThemeCropActivity.class);
        intent.putExtra(ThemeCropActivity.EXTRA_IMAGE_PATH, model.imagePath);
        intent.putExtra(ThemeCropActivity.EXTRA_EDIT_THEME_ID, model.id);
        intent.putExtra(ThemeCropActivity.EXTRA_INITIAL_DARKNESS, model.darknessOverlay);
        intent.putExtra(ThemeCropActivity.EXTRA_INITIAL_KEY_OPACITY, model.keyOpacity);
        intent.putExtra(ThemeCropActivity.EXTRA_INITIAL_BLUR, model.blur);
        intent.putExtra(ThemeCropActivity.EXTRA_INITIAL_KEY_SHADOW, model.keyShadow);
        _cropLauncher.launch(intent);
      }

      @Override
      public void onThemeDelete(ThemeModel model)
      {
        ThemeRepository.deleteCustomTheme(ThemeActivity.this, model.id);
        if (model.id.equals(_activeThemeId))
        {
          _activeThemeId = "monet";
          _prefs.edit().putString("theme", "monet").apply();
          if (_defaultThemesAdapter != null) _defaultThemesAdapter.setActiveThemeId("monet");
        }
        refreshMyThemes();
      }
    });
    sheet.show(getSupportFragmentManager(), "theme_preview");
  }

  private void refreshMyThemes()
  {
    if (_myThemesAdapter != null)
    {
      List<ThemeModel> customThemes = ThemeRepository.getCustomThemes(this);
      _myThemesAdapter.setItems(customThemes);
    }
  }

  private void confirmDeleteCustomTheme(ThemeModel model)
  {
    new AlertDialog.Builder(this)
        .setTitle(R.string.theme_custom_theme_title)
        .setMessage(R.string.theme_delete_confirm)
        .setPositiveButton(R.string.theme_btn_delete, (dialog, which) -> {
          ThemeRepository.deleteCustomTheme(ThemeActivity.this, model.id);
          if (model.id.equals(_activeThemeId))
          {
            _activeThemeId = "monet";
            _prefs.edit().putString("theme", "monet").apply();
            if (_defaultThemesAdapter != null) _defaultThemesAdapter.setActiveThemeId("monet");
          }
          refreshMyThemes();
        })
        .setNegativeButton(R.string.theme_cancel, null)
        .show();
  }

  @Override
  protected void onResume()
  {
    super.onResume();
    String current = _prefs.getString("theme", "monet");
    if (!current.equals(_activeThemeId))
    {
      _activeThemeId = current;
      if (_myThemesAdapter != null) _myThemesAdapter.setActiveThemeId(current);
      if (_defaultThemesAdapter != null) _defaultThemesAdapter.setActiveThemeId(current);
      if (_colorsAdapter != null) _colorsAdapter.setActiveThemeId(current);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(android.view.Menu menu)
  {
    getMenuInflater().inflate(R.menu.theme_menu, menu);
    android.view.MenuItem item = menu.findItem(R.id.action_appearance_settings);
    if (item != null)
    {
      androidx.core.view.MenuItemCompat.setIconTintList(item,
          androidx.core.content.ContextCompat.getColorStateList(this, R.color.settings_on_surface));
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item)
  {
    if (item.getItemId() == R.id.action_appearance_settings)
    {
      Intent intent = new Intent(this, com.nhs.customkeyboard.SettingsActivity.class);
      intent.putExtra(com.nhs.customkeyboard.SettingsActivity.EXTRA_START_SCREEN, "screen_style_advanced");
      startActivity(intent);
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  // Adapter for theme cards
  private static class ThemeAdapter extends RecyclerView.Adapter<ThemeAdapter.ViewHolder>
  {
    public interface OnThemeClickListener
    {
      void onThemeClick(ThemeModel model);
      void onAddClick();
      void onThemeLongClick(ThemeModel model);
    }

    private final List<ThemeModel> _items = new ArrayList<>();
    private final boolean _hasAddButton;
    private final boolean _showLabels;
    private String _activeThemeId;
    private final OnThemeClickListener _listener;

    public ThemeAdapter(List<ThemeModel> items, boolean hasAddButton, boolean showLabels,
                        String activeThemeId, OnThemeClickListener listener)
    {
      if (items != null) _items.addAll(items);
      _hasAddButton = hasAddButton;
      _showLabels = showLabels;
      _activeThemeId = activeThemeId;
      _listener = listener;
    }

    public void setItems(List<ThemeModel> items)
    {
      _items.clear();
      if (items != null) _items.addAll(items);
      notifyDataSetChanged();
    }

    public void setActiveThemeId(String activeId)
    {
      _activeThemeId = activeId;
      notifyDataSetChanged();
    }

    @Override
    public int getItemCount()
    {
      return _items.size() + (_hasAddButton ? 1 : 0);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
      View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_theme_card, parent, false);
      return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position)
    {
      if (_hasAddButton && position == 0)
      {
        holder.cardView.setAddButton();
        holder.label.setVisibility(View.GONE);
        holder.cardView.setOnClickListener(v -> {
          if (_listener != null) _listener.onAddClick();
        });
        holder.cardView.setOnLongClickListener(null);
        return;
      }

      int itemIndex = _hasAddButton ? position - 1 : position;
      ThemeModel model = _items.get(itemIndex);

      boolean isSelected = model.id.equals(_activeThemeId);
      holder.cardView.setThemeModel(model, isSelected);

      if (_showLabels && model.titleRes != 0)
      {
        holder.label.setText(model.titleRes);
        holder.label.setVisibility(View.VISIBLE);
      }
      else
      {
        holder.label.setVisibility(View.GONE);
      }

      holder.cardView.setOnClickListener(v -> {
        if (_listener != null) _listener.onThemeClick(model);
      });

      holder.cardView.setOnLongClickListener(v -> {
        if (model.isCustomPhoto && _listener != null)
        {
          _listener.onThemeLongClick(model);
          return true;
        }
        return false;
      });
    }

    static class ViewHolder extends RecyclerView.ViewHolder
    {
      final ThemeCardView cardView;
      final TextView label;

      ViewHolder(View v)
      {
        super(v);
        cardView = v.findViewById(R.id.theme_card_preview);
        label = v.findViewById(R.id.theme_card_label);
      }
    }
  }
}
