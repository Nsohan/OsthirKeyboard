package com.nhs.customkeyboard.theme;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.nhs.customkeyboard.Config;
import com.nhs.customkeyboard.Keyboard2View;
import com.nhs.customkeyboard.KeyboardData;
import com.nhs.customkeyboard.LayoutModifier;
import com.nhs.customkeyboard.R;

public class ThemePreviewBottomSheet extends BottomSheetDialogFragment
{
  public interface OnThemeAppliedListener
  {
    void onThemeApplied(String themeId, boolean keyBorders);
  }

  public interface OnThemeActionListener extends OnThemeAppliedListener
  {
    void onThemeEdit(ThemeModel model);
    void onThemeDelete(ThemeModel model);
  }

  private static final String ARG_THEME_ID = "arg_theme_id";

  private ThemeModel _model;
  private OnThemeAppliedListener _listener;
  private OnThemeActionListener _actionListener;

  private Keyboard2View _singleKeyboardView;
  private Keyboard2View _dualLightKeyboardView;
  private Keyboard2View _dualDarkKeyboardView;

  private SwitchMaterial _switchKeyBorders;
  private boolean _currentKeyBorders = true;
  private boolean _applied = false;

  public static ThemePreviewBottomSheet newInstance(String themeId)
  {
    ThemePreviewBottomSheet fragment = new ThemePreviewBottomSheet();
    Bundle args = new Bundle();
    args.putString(ARG_THEME_ID, themeId);
    fragment.setArguments(args);
    return fragment;
  }

  public void setOnThemeAppliedListener(OnThemeAppliedListener listener)
  {
    _listener = listener;
    if (listener instanceof OnThemeActionListener)
    {
      _actionListener = (OnThemeActionListener) listener;
    }
  }

  public void setOnThemeActionListener(OnThemeActionListener listener)
  {
    _listener = listener;
    _actionListener = listener;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    String id = getArguments() != null ? getArguments().getString(ARG_THEME_ID) : "system";
    _model = ThemeRepository.findThemeById(requireContext(), id);

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
    _currentKeyBorders = prefs.getBoolean("key_borders", true);
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle savedInstanceState)
  {
    BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
    if (dialog.getWindow() != null)
    {
      if (android.os.Build.VERSION.SDK_INT >= 29)
      {
        dialog.getWindow().setNavigationBarContrastEnforced(false);
      }
      boolean isNight = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
      androidx.core.view.WindowInsetsControllerCompat insetsController = androidx.core.view.WindowCompat.getInsetsController(
          dialog.getWindow(), dialog.getWindow().getDecorView());
      if (insetsController != null)
      {
        insetsController.setAppearanceLightNavigationBars(!isNight);
      }
    }
    dialog.setOnShowListener(d -> {
      BottomSheetDialog bd = (BottomSheetDialog) d;
      FrameLayout bottomSheet = bd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
      if (bottomSheet != null)
      {
        bottomSheet.setBackgroundColor(Color.TRANSPARENT);
        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setSkipCollapsed(true);
      }
    });
    return dialog;
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.bottom_sheet_theme_preview, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);

    FrameLayout singleContainer = view.findViewById(R.id.preview_single_container);
    LinearLayout dualContainer = view.findViewById(R.id.preview_dual_container);
    androidx.cardview.widget.CardView singleCard = view.findViewById(R.id.preview_single_card);
    FrameLayout keyboardHolder = view.findViewById(R.id.preview_keyboard_holder);

    _switchKeyBorders = view.findViewById(R.id.switch_key_borders);
    _switchKeyBorders.setChecked(_currentKeyBorders);

    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
      androidx.core.graphics.Insets insets = windowInsets.getInsets(
          androidx.core.view.WindowInsetsCompat.Type.navigationBars());
      v.setPadding(
          (int) dp(20),
          (int) dp(20),
          (int) dp(20),
          (int) (dp(20) + insets.bottom)
      );
      return windowInsets;
    });

    View customActionsLayout = view.findViewById(R.id.layout_custom_actions);
    ImageView btnEdit = view.findViewById(R.id.btn_action_edit);
    ImageView btnDelete = view.findViewById(R.id.btn_action_delete);

    if (_model != null && _model.isCustomPhoto)
    {
      if (customActionsLayout != null) customActionsLayout.setVisibility(View.VISIBLE);
      if (btnEdit != null)
      {
        btnEdit.setOnClickListener(v -> {
          dismiss();
          if (_actionListener != null)
          {
            _actionListener.onThemeEdit(_model);
          }
        });
      }
      if (btnDelete != null)
      {
        btnDelete.setOnClickListener(v -> {
          new androidx.appcompat.app.AlertDialog.Builder(requireContext())
              .setTitle(R.string.theme_custom_theme_title)
              .setMessage(R.string.theme_delete_confirm)
              .setPositiveButton(R.string.theme_btn_delete, (dialog, which) -> {
                dismiss();
                if (_actionListener != null)
                {
                  _actionListener.onThemeDelete(_model);
                }
              })
              .setNegativeButton(R.string.theme_cancel, null)
              .show();
        });
      }
    }
    else
    {
      if (customActionsLayout != null) customActionsLayout.setVisibility(View.GONE);
    }

    MaterialButton btnCancel = view.findViewById(R.id.btn_cancel);
    MaterialButton btnApply = view.findViewById(R.id.btn_apply);

    btnCancel.setOnClickListener(v -> dismiss());
    btnApply.setOnClickListener(v -> applyTheme());

    if (_model.isSystemAuto)
    {
      singleContainer.setVisibility(View.GONE);
      dualContainer.setVisibility(View.VISIBLE);
      setupDualPreview(view);
    }
    else
    {
      singleContainer.setVisibility(View.VISIBLE);
      dualContainer.setVisibility(View.GONE);
      setupSinglePreview(singleCard, keyboardHolder, view);
    }

    _switchKeyBorders.setOnCheckedChangeListener((buttonView, isChecked) -> {
      _currentKeyBorders = isChecked;
      Config.globalConfig().keyBorders = isChecked;
      if (_singleKeyboardView != null)
      {
        _singleKeyboardView.reset();
        _singleKeyboardView.invalidate();
      }
      if (_dualLightKeyboardView != null)
      {
        _dualLightKeyboardView.reset();
        _dualLightKeyboardView.invalidate();
      }
      if (_dualDarkKeyboardView != null)
      {
        _dualDarkKeyboardView.reset();
        _dualDarkKeyboardView.invalidate();
      }
    });
  }

  private void setupSinglePreview(androidx.cardview.widget.CardView singleCard, FrameLayout keyboardHolder, View rootView)
  {
    Context themeContext;
    if (_model.isCustomPhoto)
    {
      themeContext = new ContextThemeWrapper(requireContext(), R.style.CustomImageTheme);
    }
    else
    {
      int resId = (_model.themeResId != 0) ? _model.themeResId : R.style.Light;
      themeContext = new ContextThemeWrapper(requireContext(), resId);
    }

    // Configure card container appearance
    TypedArray a = themeContext.getTheme().obtainStyledAttributes(new int[]{
        R.attr.colorKeyboard,
        R.attr.colorLabel,
        R.attr.colorKeyAction
    });
    int colorKeyboard = a.getColor(0, _model.previewBgColor);
    int colorLabel = a.getColor(1, 0xFF333333);
    int colorKeyAction = a.getColor(2, _model.previewAccentColor);
    a.recycle();

    ImageView customBg = rootView.findViewById(R.id.preview_custom_bg);
    View darknessOverlay = rootView.findViewById(R.id.preview_darkness_overlay);
    ImageView menuIcon = rootView.findViewById(R.id.preview_menu_icon);
    ImageView micIcon = rootView.findViewById(R.id.preview_mic_icon);

    if (_model.isCustomPhoto && _model.imagePath != null)
    {
      if (customBg != null)
      {
        customBg.setVisibility(View.VISIBLE);
        try
        {
          android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(_model.imagePath);
          customBg.setImageBitmap(bmp);
        }
        catch (Throwable ignored) {}
      }
      if (darknessOverlay != null)
      {
        darknessOverlay.setVisibility(View.VISIBLE);
        darknessOverlay.setAlpha(_model.darknessOverlay);
      }
      singleCard.setCardBackgroundColor(0xFF1E1F22);
      if (menuIcon != null) menuIcon.setColorFilter(0xFFE3E3E3);
      if (micIcon != null) micIcon.setColorFilter(0xFFE3E3E3);
    }
    else
    {
      if (customBg != null) customBg.setVisibility(View.GONE);
      if (darknessOverlay != null) darknessOverlay.setVisibility(View.GONE);
      singleCard.setCardBackgroundColor(colorKeyboard);
      if (menuIcon != null) menuIcon.setColorFilter(colorLabel);
      if (micIcon != null) micIcon.setColorFilter(colorLabel);
    }

    if (Config.globalConfig() != null)
    {
      Config.globalConfig().keyBorders = _currentKeyBorders;
      if (_model.isCustomPhoto)
      {
        Config.globalConfig().keyOpacity = Math.max(0, Math.min(255, Math.round(_model.keyOpacity * 255)));
        Config.globalConfig().keyShadow = dp(_model.keyShadow);
      }
      else
      {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(requireContext());
        Config.globalConfig().keyOpacity = p.getInt("key_opacity", 100) * 255 / 100;
        Config.globalConfig().keyShadow = dp(p.getFloat("key_shadow", 3.0f));
      }
    }
    _singleKeyboardView = new Keyboard2View(themeContext);
    _singleKeyboardView.setThemePreviewMode(true);
    _singleKeyboardView.setBackgroundColor(Color.TRANSPARENT);
    _singleKeyboardView.setKeyboard(loadPreviewKeyboard());

    keyboardHolder.removeAllViews();
    keyboardHolder.addView(_singleKeyboardView, new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
  }

  private KeyboardData loadPreviewKeyboard()
  {
    try
    {
      if (Config.globalConfig() == null)
      {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        Config.initGlobalConfig(prefs, getResources(), false, com.nhs.customkeyboard.dict.Dictionaries.instance(requireContext()));
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

  private void setupDualPreview(View rootView)
  {
    FrameLayout lightHolder = rootView.findViewById(R.id.preview_dual_light_holder);
    FrameLayout darkHolder = rootView.findViewById(R.id.preview_dual_dark_holder);

    // Light variant card
    Context lightContext = new ContextThemeWrapper(requireContext(), R.style.Light);
    LinearLayout lightCard = createMiniPreviewCard(lightContext, true);
    lightHolder.removeAllViews();
    lightHolder.addView(lightCard);

    // Dark variant card
    Context darkContext = new ContextThemeWrapper(requireContext(), R.style.Dark);
    LinearLayout darkCard = createMiniPreviewCard(darkContext, false);
    darkHolder.removeAllViews();
    darkHolder.addView(darkCard);
  }

  private LinearLayout createMiniPreviewCard(Context themeContext, boolean isLight)
  {
    LinearLayout card = new LinearLayout(themeContext);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding((int) dp(4), (int) dp(4), (int) dp(4), (int) dp(4));

    GradientDrawable gd = new GradientDrawable();
    gd.setCornerRadius(dp(18));
    gd.setColor(isLight ? 0xFFE2E7EC : 0xFF1E1F22);
    if (isLight)
    {
      gd.setStroke((int) dp(1), ContextCompat.getColor(requireContext(), R.color.settings_divider));
    }
    card.setBackground(gd);

    // Toolbar simulation
    LinearLayout tb = new LinearLayout(themeContext);
    tb.setOrientation(LinearLayout.HORIZONTAL);
    tb.setGravity(android.view.Gravity.CENTER_VERTICAL);
    tb.setLayoutParams(new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, (int) dp(24)));
    tb.setPadding((int) dp(10), 0, (int) dp(10), 0);

    ImageView menuIcon = new ImageView(themeContext);
    menuIcon.setImageResource(R.drawable.ic_grid_menu);
    menuIcon.setColorFilter(isLight ? 0xFF444746 : 0xFFE3E3E3);
    tb.addView(menuIcon, new LinearLayout.LayoutParams((int) dp(18), (int) dp(18)));

    View spacer = new View(themeContext);
    LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0, 1, 1f);
    tb.addView(spacer, spLp);

    ImageView micIcon = new ImageView(themeContext);
    micIcon.setImageResource(R.drawable.ic_mic);
    micIcon.setColorFilter(isLight ? 0xFF444746 : 0xFFE3E3E3);
    tb.addView(micIcon, new LinearLayout.LayoutParams((int) dp(18), (int) dp(18)));

    card.addView(tb);

    // Keyboard view
    Config.globalConfig().keyBorders = _currentKeyBorders;
    Keyboard2View kv = new Keyboard2View(themeContext);
    kv.setThemePreviewMode(true);
    kv.setKeyboard(loadPreviewKeyboard());

    if (isLight)
      _dualLightKeyboardView = kv;
    else
      _dualDarkKeyboardView = kv;

    card.addView(kv, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    return card;
  }

  private void applyTheme()
  {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
    SharedPreferences.Editor editor = prefs.edit();

    editor.putString("theme", _model.id);
    editor.putBoolean("key_borders", _currentKeyBorders);

    if (_model.isCustomPhoto)
    {
      editor.putString("custom_theme_image_path", _model.imagePath);
      editor.putFloat("custom_theme_darkness", _model.darknessOverlay);
      editor.putFloat("custom_theme_key_opacity", _model.keyOpacity);
      editor.putFloat("custom_theme_key_shadow", _model.keyShadow);
    }

    _applied = true;
    editor.apply();

    // Notify listeners & refresh
    if (_listener != null)
    {
      _listener.onThemeApplied(_model.id, _currentKeyBorders);
    }

    Toast.makeText(requireContext(), R.string.theme_applied_toast, Toast.LENGTH_SHORT).show();
    dismiss();
  }

  @Override
  public void onDismiss(@NonNull DialogInterface dialog)
  {
    super.onDismiss(dialog);
    if (!_applied && Config.globalConfig() != null && getContext() != null)
    {
      SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(requireContext());
      Config.globalConfig().keyBorders = p.getBoolean("key_borders", true);
      String themeName = p.getString("theme", "monet");
      if (themeName != null && themeName.startsWith("custom_"))
      {
        Config.globalConfig().keyOpacity = Math.max(0, Math.min(255, Math.round(p.getFloat("custom_theme_key_opacity", 1.0f) * 255)));
        Config.globalConfig().keyShadow = dp(p.getFloat("custom_theme_key_shadow", 0.0f));
      }
      else
      {
        Config.globalConfig().keyOpacity = p.getInt("key_opacity", 100) * 255 / 100;
        Config.globalConfig().keyShadow = dp(p.getFloat("key_shadow", 3.0f));
      }
    }
  }

  private float dp(float v)
  {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
  }
}
