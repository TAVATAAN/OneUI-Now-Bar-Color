package io.github.tavataan.nowbarcolor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    // Wide 24-color curated palette (One UI vibrant, pastel, neon & deep dark tones)
    private static final int[] PRESET_PALETTE = new int[]{
            0xFF1E88E5, // One UI Blue
            0xFF00ACC1, // Cyan
            0xFF00897B, // Teal
            0xFF43A047, // Emerald Green
            0xFF7CB342, // Lime Green
            0xFFFDD835, // Amber Gold
            0xFFFB8C00, // Vivid Orange
            0xFFE53935, // Crimson Red
            0xFFD81B60, // Rose Pink
            0xFF8E24AA, // Deep Purple
            0xFF5E35B1, // Royal Violet
            0xFF3949AB, // Indigo
            0xFF0D47A1, // Midnight Blue
            0xFF004D40, // Forest Pine
            0xFF1B5E20, // Dark Moss
            0xFF880E4F, // Burgundy Wine
            0xFF4A148C, // Cosmic Purple
            0xFF311B92, // Deep Space
            0xFF263238, // Slate Charcoal
            0xFF1C1C1E, // Pure Dark Graphite
            0xFF000000, // True Pitch Black
            0xFF607D8B, // Cool Steel
            0xFF795548, // Warm Espresso
            0xFFFF4081  // Neon Magenta
    };

    private ConfigManager.Config mConfig;
    private WallpaperColorHelper.PaletteResult mWallpaperPalette;

    // UI References for live updates
    private OneUIMediaCapsuleView mPreviewMediaCapsule;
    private FrameLayout mLockscreenStage;
    private TextView mPreviewModeTag;

    private Button mBtnFixedMode;
    private Button mBtnWallpaperMode;
    private Button mBtnGradientMode;
    private LinearLayout mFixedPaletteContainer;
    private LinearLayout mWallpaperModeContainer;
    private LinearLayout mGradientModeContainer;
    private final List<View> mPaletteSwatchViews = new ArrayList<>();
    private final List<View> mGradientPaletteSwatchViews = new ArrayList<>();
    private final List<LinearLayout> mWallpaperVariantCards = new ArrayList<>();

    // Global gradient subtabs
    private LinearLayout mBtnGlobalGradStart;
    private LinearLayout mBtnGlobalGradEnd;
    private View mDotGlobalGradStart;
    private View mDotGlobalGradEnd;
    private boolean mGlobalGradEditingEnd = false; // false = Primer color, true = Color final

    private Button mBtnStyleSolid;
    private Button mBtnStyleBlur;
    private SeekBar mOpacitySeekBar;
    private TextView mOpacityValueText;

    private LinearLayout mCategoriesListContainer;
    private String mExpandedCategoryId = null;

    private LinearLayout mMainCol;

    private void showLanguageSelectionDialog() {
        final String[] langCodes = new String[]{
                I18n.LANG_AUTO,
                I18n.LANG_EN,
                I18n.LANG_ES,
                I18n.LANG_PT
        };
        final String[] langNames = new String[]{
                I18n.get(this, "lang_auto"),
                I18n.get(this, "lang_en"),
                I18n.get(this, "lang_es"),
                I18n.get(this, "lang_pt")
        };
        String current = I18n.getLanguage(this);
        int checkedItem = 0;
        for (int i = 0; i < langCodes.length; i++) {
            if (langCodes[i].equals(current)) {
                checkedItem = i;
                break;
            }
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(I18n.get(this, "select_lang_title"))
                .setSingleChoiceItems(langNames, checkedItem, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        I18n.setLanguage(MainActivity.this, langCodes[which]);
                        dialog.dismiss();
                        rebuildFullUi();
                    }
                })
                .setNegativeButton(I18n.get(this, "cancel"), null)
                .show();
    }

    private void rebuildFullUi() {
        if (mMainCol == null) return;
        mMainCol.removeAllViews();
        mMainCol.addView(buildHeaderSection());
        mMainCol.addView(buildMasterSwitchCard());
        mMainCol.addView(buildLivePreviewCard());
        mMainCol.addView(buildColorSelectionCard());
        mMainCol.addView(buildStyleAndOpacityCard());
        mMainCol.addView(buildCategoriesCatalogCard());
        refreshEntireUi(false);
    }

    private static class CategoryRowHolder {
        String categoryId;
        LinearLayout rootCard;
        View expandSection;
        TextView arrowView;
    }
    private final List<CategoryRowHolder> mCategoryRowHolders = new ArrayList<>();

    // Hooked by NowBarHook or verified via SystemUI active flag
    public boolean isModuleActiveInLsposed() {
        try {
            return android.provider.Settings.System.getInt(getContentResolver(), "nowbar_color_hook_active", 0) == 1;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupDarkWindow();

        mConfig = ConfigManager.loadFromAppPrefs(this);
        mWallpaperPalette = WallpaperColorHelper.extractLockWallpaperPalette(this, true);
        updateCachedWallpaperInConfig();

        ScrollView rootScroll = new ScrollView(this);
        rootScroll.setFillViewport(true);
        rootScroll.setBackgroundColor(0xFF000000);

        mMainCol = new LinearLayout(this);
        mMainCol.setOrientation(LinearLayout.VERTICAL);
        int padH = dp(18);
        mMainCol.setPadding(padH, dp(16), padH, dp(36));
        rootScroll.addView(mMainCol);

        // 1. Header + Status Pill
        mMainCol.addView(buildHeaderSection());

        // 2. Master Enable Switch Card (Activador del módulo arriba)
        mMainCol.addView(buildMasterSwitchCard());

        // 3. Live Preview Card (Vista previa abajo)
        mMainCol.addView(buildLivePreviewCard());

        // 4. Color Source Selector (Wide Palette + Lockscreen Wallpaper Button side-by-side)
        mMainCol.addView(buildColorSelectionCard());

        // 5. Visual Style (Solid vs Blur) + Opacity Slider
        mMainCol.addView(buildStyleAndOpacityCard());

        // 6. Catalog of All 9 Now Bar Capsule Types (Individual Toggles + Per-Capsule Color Override)
        mMainCol.addView(buildCategoriesCatalogCard());

        setContentView(rootScroll);
        refreshEntireUi(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mPreviewMediaCapsule != null) {
            mPreviewMediaCapsule.startWaveAnimation();
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final WallpaperColorHelper.PaletteResult fresh =
                        WallpaperColorHelper.extractLockWallpaperPalette(MainActivity.this, true);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mWallpaperPalette = fresh;
                        updateCachedWallpaperInConfig();
                        refreshEntireUi(false);
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mPreviewMediaCapsule != null) {
            mPreviewMediaCapsule.stopWaveAnimation();
        }
    }

    private void updateCachedWallpaperInConfig() {
        if (mWallpaperPalette == null || mConfig == null) return;
        mConfig.cachedWallpaperPrimary = mWallpaperPalette.primaryColor;
        mConfig.cachedWallpaperSecondary = mWallpaperPalette.secondaryColor;
        mConfig.cachedWallpaperAccent = mWallpaperPalette.monetAccentColor;
        mConfig.cachedWallpaperBottom = mWallpaperPalette.bottomAreaColor;
    }

    private int resolveWallpaperBottomColor() {
        if (mWallpaperPalette != null && mWallpaperPalette.bottomAreaColor != 0) {
            return mWallpaperPalette.bottomAreaColor | 0xFF000000;
        }
        if (mConfig != null && mConfig.cachedWallpaperBottom != 0) {
            return mConfig.cachedWallpaperBottom | 0xFF000000;
        }
        return 0xFF1565C0;
    }

    private void setupDarkWindow() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = getWindow();
        if (w != null) {
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(0xFF000000);
            w.setNavigationBarColor(0xFF000000);
        }
    }

    private View buildHeaderSection() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(4), dp(6), dp(4), dp(14));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(I18n.get(this, "app_title"));
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        topRow.addView(title, titleLp);

        TextView langBtn = new TextView(this);
        String curPref = I18n.getLanguage(this);
        String langDisplay;
        if (I18n.LANG_AUTO.equals(curPref)) {
            langDisplay = "Auto (" + I18n.getEffectiveLanguage(this).toUpperCase(Locale.ROOT) + ")";
        } else {
            langDisplay = curPref.toUpperCase(Locale.ROOT);
        }
        langBtn.setText(langDisplay);
        langBtn.setTextColor(0xFFFFFFFF);
        langBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        langBtn.setTypeface(Typeface.DEFAULT_BOLD);
        langBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        langBtn.setBackground(roundRect(0x22FFFFFF, dp(18), 1, 0x44FFFFFF));
        LinearLayout.LayoutParams langLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        langLp.rightMargin = dp(8);
        langBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLanguageSelectionDialog();
            }
        });
        topRow.addView(langBtn, langLp);

        boolean active = isModuleActiveInLsposed();
        String effLang = I18n.getEffectiveLanguage(this);
        String badgeText;
        if (active) {
            badgeText = "es".equals(effLang) ? "● LSPosed Activo" : ("pt".equals(effLang) ? "● LSPosed Ativo" : "● LSPosed Active");
        } else {
            badgeText = "es".equals(effLang) ? "● Sincronización Root Activa" : ("pt".equals(effLang) ? "● Sincronização Root Ativa" : "● Root Sync Active");
        }
        TextView badge = new TextView(this);
        badge.setText(badgeText);
        badge.setTextColor(active ? 0xFF69F0AE : 0xFF82B1FF);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setPadding(dp(12), dp(6), dp(12), dp(6));
        badge.setBackground(roundRect(active ? 0x2200E676 : 0x22448AFF, dp(20), 1, active ? 0x5500E676 : 0x55448AFF));
        topRow.addView(badge);

        box.addView(topRow);

        TextView sub = new TextView(this);
        sub.setText(I18n.get(this, "app_subtitle"));
        sub.setTextColor(0xFF9AA0B4);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        sub.setPadding(0, dp(6), 0, 0);
        box.addView(sub);

        return box;
    }

    private View buildLivePreviewCard() {
        LinearLayout card = createSectionCard();
        card.setPadding(dp(16), dp(14), dp(16), dp(16));

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView lbl = new TextView(this);
        lbl.setText(I18n.get(this, "preview_title"));
        lbl.setTextColor(0xFF82B1FF);
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        headerRow.addView(lbl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        mPreviewModeTag = new TextView(this);
        mPreviewModeTag.setTextColor(0xFFB0BEC5);
        mPreviewModeTag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        headerRow.addView(mPreviewModeTag);
        card.addView(headerRow);

        // Simulated Lockscreen Wallpaper Backdrop
        mLockscreenStage = new FrameLayout(this) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF r = new RectF();

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                int bottomColor = resolveWallpaperBottomColor();
                p.setStyle(Paint.Style.FILL);
                p.setShader(null);
                p.setColor(bottomColor);
                r.set(0, 0, getWidth(), getHeight());
                canvas.drawRoundRect(r, dp(22), dp(22), p);

                // Subtle delicate border so the card is cleanly defined regardless of wallpaper brightness
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(1));
                p.setColor(0x22FFFFFF);
                float half = dp(0.5f);
                r.set(half, half, getWidth() - half, getHeight() - half);
                canvas.drawRoundRect(r, dp(22), dp(22), p);
                p.setStyle(Paint.Style.FILL);
            }
        };
        mLockscreenStage.setWillNotDraw(false);
        LinearLayout.LayoutParams stageLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(132));
        stageLp.topMargin = dp(10);
        mLockscreenStage.setPadding(dp(14), dp(18), dp(14), dp(18));

        // Pixel-Perfect Samsung One UI 7 Media Capsule
        mPreviewMediaCapsule = new OneUIMediaCapsuleView(this);
        FrameLayout.LayoutParams capLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        mLockscreenStage.addView(mPreviewMediaCapsule, capLp);
        card.addView(mLockscreenStage, stageLp);

        return card;
    }

    private View buildMasterSwitchCard() {
        LinearLayout card = createSectionCard();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(14), dp(18), dp(14));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(this);
        t.setText(I18n.get(this, "enable_title"));
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        textCol.addView(t);

        TextView s = new TextView(this);
        s.setText(I18n.get(this, "enable_sub"));
        s.setTextColor(0xFF9AA0B4);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        textCol.addView(s);

        card.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        OneUISwitch masterSwitch = new OneUISwitch(this);
        masterSwitch.setChecked(mConfig.masterEnabled);
        masterSwitch.setOnCheckedChangeListener(new OneUISwitch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(OneUISwitch view, boolean isChecked) {
                mConfig.masterEnabled = isChecked;
                refreshEntireUi(true);
            }
        });
        card.addView(masterSwitch);

        return card;
    }

    private View buildColorSelectionCard() {
        LinearLayout card = createSectionCard();

        TextView secTitle = new TextView(this);
        secTitle.setText(I18n.get(this, "sec_color_mode"));
        secTitle.setTextColor(0xFF82B1FF);
        secTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        secTitle.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(secTitle);

        // Top side-by-side buttons: [ Paleta de Color Fijo ] | [ Fondo de Bloqueo ] | [ Degradado ]
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams modeRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        modeRowLp.topMargin = dp(10);
        modeRowLp.bottomMargin = dp(14);

        mBtnFixedMode = new Button(this);
        mBtnFixedMode.setAllCaps(false);
        mBtnFixedMode.setText(I18n.get(this, "tab_solid"));
        mBtnFixedMode.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        mBtnFixedMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mConfig.colorMode = ConfigManager.MODE_FIXED_COLOR;
                refreshEntireUi(true);
            }
        });

        View spacer1 = new View(this);

        mBtnWallpaperMode = new Button(this);
        mBtnWallpaperMode.setAllCaps(false);
        mBtnWallpaperMode.setText(I18n.get(this, "tab_wallpaper"));
        mBtnWallpaperMode.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        mBtnWallpaperMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mConfig.colorMode = ConfigManager.MODE_LOCK_WALLPAPER;
                mWallpaperPalette = WallpaperColorHelper.extractLockWallpaperPalette(MainActivity.this, true);
                updateCachedWallpaperInConfig();
                refreshEntireUi(true);
            }
        });

        View spacer2 = new View(this);

        mBtnGradientMode = new Button(this);
        mBtnGradientMode.setAllCaps(false);
        mBtnGradientMode.setText(I18n.get(this, "tab_gradient"));
        mBtnGradientMode.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        mBtnGradientMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mConfig.colorMode = ConfigManager.MODE_GRADIENT;
                refreshEntireUi(true);
            }
        });

        modeRow.addView(mBtnFixedMode, new LinearLayout.LayoutParams(0, dp(44), 1f));
        modeRow.addView(spacer1, new LinearLayout.LayoutParams(dp(6), 1));
        modeRow.addView(mBtnWallpaperMode, new LinearLayout.LayoutParams(0, dp(44), 1f));
        modeRow.addView(spacer2, new LinearLayout.LayoutParams(dp(6), 1));
        modeRow.addView(mBtnGradientMode, new LinearLayout.LayoutParams(0, dp(44), 1f));
        card.addView(modeRow, modeRowLp);

        // Container A: Wide Fixed Color Palette Grid + Custom HSV/HEX Picker Button
        mFixedPaletteContainer = new LinearLayout(this);
        mFixedPaletteContainer.setOrientation(LinearLayout.VERTICAL);

        TextView palLabel = new TextView(this);
        palLabel.setText(I18n.get(this, "palette_label"));
        palLabel.setTextColor(0xFFB0B8C8);
        palLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        palLabel.setPadding(0, 0, 0, dp(10));
        mFixedPaletteContainer.addView(palLabel);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(6);
        mPaletteSwatchViews.clear();

        for (int i = 0; i < PRESET_PALETTE.length; i++) {
            final int presetColor = PRESET_PALETTE[i];
            FrameLayout swatchWrap = new FrameLayout(this);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = 0;
            glp.height = dp(46);
            glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            glp.setMargins(dp(4), dp(4), dp(4), dp(4));
            swatchWrap.setLayoutParams(glp);

            final View circle = new View(this);
            swatchWrap.addView(circle, new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));
            circle.setTag(presetColor);
            mPaletteSwatchViews.add(circle);

            swatchWrap.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mConfig.colorMode = ConfigManager.MODE_FIXED_COLOR;
                    mConfig.globalColor = presetColor;
                    refreshEntireUi(true);
                }
            });
            grid.addView(swatchWrap);
        }
        mFixedPaletteContainer.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button btnCustomHex = new Button(this);
        btnCustomHex.setAllCaps(false);
        btnCustomHex.setText(I18n.get(this, "picker_advanced"));
        btnCustomHex.setTextColor(0xFFFFFFFF);
        btnCustomHex.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btnCustomHex.setBackground(roundRect(0xFF232325, dp(14), 1, 0xFF38383B));
        LinearLayout.LayoutParams customLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        customLp.topMargin = dp(12);
        btnCustomHex.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCustomColorPickerDialog(
                        I18n.get(MainActivity.this, "dialog_global_color"),
                        mConfig.globalColor,
                        new OnColorPickedListener() {
                            @Override
                            public void onColorPicked(int color) {
                                mConfig.colorMode = ConfigManager.MODE_FIXED_COLOR;
                                mConfig.globalColor = color;
                                refreshEntireUi(true);
                            }
                        }
                );
            }
        });
        mFixedPaletteContainer.addView(btnCustomHex, customLp);
        card.addView(mFixedPaletteContainer);

        // Container B: Dynamic Lockscreen Wallpaper Color Selector
        mWallpaperModeContainer = new LinearLayout(this);
        mWallpaperModeContainer.setOrientation(LinearLayout.VERTICAL);

        TextView wallInfo = new TextView(this);
        wallInfo.setText(I18n.get(this, "wall_info"));
        wallInfo.setTextColor(0xFFB0B8C8);
        wallInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        wallInfo.setPadding(0, 0, 0, dp(10));
        mWallpaperModeContainer.addView(wallInfo);

        String[] variantTitles = new String[]{
                I18n.get(this, "wall_primary_title"),
                I18n.get(this, "wall_secondary_title"),
                I18n.get(this, "wall_monet_title"),
                I18n.get(this, "wall_bottom_title")
        };
        String[] variantSubs = new String[]{
                I18n.get(this, "wall_primary_desc"),
                I18n.get(this, "wall_secondary_desc"),
                I18n.get(this, "wall_monet_desc"),
                I18n.get(this, "wall_bottom_desc")
        };

        mWallpaperVariantCards.clear();
        for (int i = 0; i < 4; i++) {
            final int variantIndex = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dp(8);
            row.setLayoutParams(rlp);

            View colorPreviewDot = new View(this);
            colorPreviewDot.setTag("dot");
            row.addView(colorPreviewDot, new LinearLayout.LayoutParams(dp(34), dp(34)));

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(12), 0, dp(8), 0);

            TextView vt = new TextView(this);
            vt.setText(variantTitles[i]);
            vt.setTextColor(0xFFFFFFFF);
            vt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            vt.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            col.addView(vt);

            TextView vs = new TextView(this);
            vs.setText(variantSubs[i]);
            vs.setTextColor(0xFF9AA0B4);
            vs.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            col.addView(vs);

            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mConfig.colorMode = ConfigManager.MODE_LOCK_WALLPAPER;
                    mConfig.wallpaperVariant = variantIndex;
                    refreshEntireUi(true);
                }
            });

            mWallpaperVariantCards.add(row);
            mWallpaperModeContainer.addView(row);
        }

        Button btnRefreshWall = new Button(this);
        btnRefreshWall.setAllCaps(false);
        btnRefreshWall.setText(I18n.get(this, "wall_refresh_btn"));
        btnRefreshWall.setTextColor(0xFFFFFFFF);
        btnRefreshWall.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        btnRefreshWall.setBackground(roundRect(0xFF232325, dp(14), 1, 0xFF38383B));
        btnRefreshWall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mWallpaperPalette = WallpaperColorHelper.extractLockWallpaperPalette(MainActivity.this, true);
                updateCachedWallpaperInConfig();
                refreshEntireUi(true);
                Toast.makeText(MainActivity.this, I18n.get(MainActivity.this, "wall_updated_toast"), Toast.LENGTH_SHORT).show();
            }
        });
        mWallpaperModeContainer.addView(btnRefreshWall, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        card.addView(mWallpaperModeContainer);

        // Container C: Two-Stop Horizontal Linear Gradient Selector
        mGradientModeContainer = new LinearLayout(this);
        mGradientModeContainer.setOrientation(LinearLayout.VERTICAL);

        TextView gradInfo = new TextView(this);
        gradInfo.setText(I18n.get(this, "grad_info"));
        gradInfo.setTextColor(0xFFB0B8C8);
        gradInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        gradInfo.setPadding(0, 0, 0, dp(10));
        mGradientModeContainer.addView(gradInfo);

        // Sub-tabs: [● Primer color]  [● Color final]
        LinearLayout subtabsRow = new LinearLayout(this);
        subtabsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        stlp.bottomMargin = dp(12);

        mBtnGlobalGradStart = new LinearLayout(this);
        mBtnGlobalGradStart.setOrientation(LinearLayout.HORIZONTAL);
        mBtnGlobalGradStart.setGravity(Gravity.CENTER);
        mDotGlobalGradStart = new View(this);
        mBtnGlobalGradStart.addView(mDotGlobalGradStart, new LinearLayout.LayoutParams(dp(16), dp(16)));
        TextView txtGradStart = new TextView(this);
        txtGradStart.setText(" " + I18n.get(this, "grad_color_start"));
        txtGradStart.setTextColor(0xFFFFFFFF);
        txtGradStart.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        txtGradStart.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        mBtnGlobalGradStart.addView(txtGradStart);

        View subGap = new View(this);

        mBtnGlobalGradEnd = new LinearLayout(this);
        mBtnGlobalGradEnd.setOrientation(LinearLayout.HORIZONTAL);
        mBtnGlobalGradEnd.setGravity(Gravity.CENTER);
        mDotGlobalGradEnd = new View(this);
        mBtnGlobalGradEnd.addView(mDotGlobalGradEnd, new LinearLayout.LayoutParams(dp(16), dp(16)));
        TextView txtGradEnd = new TextView(this);
        txtGradEnd.setText(" " + I18n.get(this, "grad_color_end"));
        txtGradEnd.setTextColor(0xFFFFFFFF);
        txtGradEnd.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        txtGradEnd.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        mBtnGlobalGradEnd.addView(txtGradEnd);

        mBtnGlobalGradStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mGlobalGradEditingEnd = false;
                refreshEntireUi(false);
            }
        });

        mBtnGlobalGradEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mGlobalGradEditingEnd = true;
                refreshEntireUi(false);
            }
        });

        subtabsRow.addView(mBtnGlobalGradStart, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        subtabsRow.addView(subGap, new LinearLayout.LayoutParams(dp(8), 1));
        subtabsRow.addView(mBtnGlobalGradEnd, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        mGradientModeContainer.addView(subtabsRow, stlp);

        // Gradient preset palette grid
        GridLayout gradGrid = new GridLayout(this);
        gradGrid.setColumnCount(6);
        mGradientPaletteSwatchViews.clear();

        for (int i = 0; i < PRESET_PALETTE.length; i++) {
            final int presetColor = PRESET_PALETTE[i];
            FrameLayout swatchWrap = new FrameLayout(this);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = 0;
            glp.height = dp(46);
            glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            glp.setMargins(dp(4), dp(4), dp(4), dp(4));
            swatchWrap.setLayoutParams(glp);

            final View circle = new View(this);
            swatchWrap.addView(circle, new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));
            circle.setTag(presetColor);
            mGradientPaletteSwatchViews.add(circle);

            swatchWrap.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mConfig.colorMode = ConfigManager.MODE_GRADIENT;
                    if (mGlobalGradEditingEnd) {
                        mConfig.gradientEndColor = presetColor;
                    } else {
                        mConfig.gradientStartColor = presetColor;
                    }
                    refreshEntireUi(true);
                }
            });
            gradGrid.addView(swatchWrap);
        }
        mGradientModeContainer.addView(gradGrid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button btnGradCustomHex = new Button(this);
        btnGradCustomHex.setAllCaps(false);
        btnGradCustomHex.setText(I18n.get(this, "picker_advanced"));
        btnGradCustomHex.setTextColor(0xFFFFFFFF);
        btnGradCustomHex.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btnGradCustomHex.setBackground(roundRect(0xFF232325, dp(14), 1, 0xFF38383B));
        LinearLayout.LayoutParams gradCustomLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        gradCustomLp.topMargin = dp(12);
        btnGradCustomHex.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String title = mGlobalGradEditingEnd ? I18n.get(MainActivity.this, "grad_color_end") : I18n.get(MainActivity.this, "grad_color_start");
                int curCol = mGlobalGradEditingEnd ? mConfig.gradientEndColor : mConfig.gradientStartColor;
                showCustomColorPickerDialog(
                        title,
                        curCol,
                        new OnColorPickedListener() {
                            @Override
                            public void onColorPicked(int color) {
                                mConfig.colorMode = ConfigManager.MODE_GRADIENT;
                                if (mGlobalGradEditingEnd) {
                                    mConfig.gradientEndColor = color;
                                } else {
                                    mConfig.gradientStartColor = color;
                                }
                                refreshEntireUi(true);
                            }
                        }
                );
            }
        });
        mGradientModeContainer.addView(btnGradCustomHex, gradCustomLp);
        card.addView(mGradientModeContainer);

        return card;
    }

    private View buildStyleAndOpacityCard() {
        LinearLayout card = createSectionCard();

        TextView secTitle = new TextView(this);
        secTitle.setText(I18n.get(this, "sec_finish_opacity"));
        secTitle.setTextColor(0xFF82B1FF);
        secTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        secTitle.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(secTitle);

        LinearLayout styleRow = new LinearLayout(this);
        styleRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams srlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        srlp.topMargin = dp(10);
        srlp.bottomMargin = dp(16);

        mBtnStyleSolid = new Button(this);
        mBtnStyleSolid.setAllCaps(false);
        mBtnStyleSolid.setText(I18n.get(this, "finish_solid_title"));
        mBtnStyleSolid.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        mBtnStyleSolid.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mConfig.styleMode = ConfigManager.STYLE_SOLID;
                refreshEntireUi(true);
            }
        });

        View gap = new View(this);

        mBtnStyleBlur = new Button(this);
        mBtnStyleBlur.setAllCaps(false);
        mBtnStyleBlur.setText(I18n.get(this, "finish_blur_title"));
        mBtnStyleBlur.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        mBtnStyleBlur.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mConfig.styleMode = ConfigManager.STYLE_BLUR;
                refreshEntireUi(true);
            }
        });

        styleRow.addView(mBtnStyleSolid, new LinearLayout.LayoutParams(0, dp(44), 1f));
        styleRow.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        styleRow.addView(mBtnStyleBlur, new LinearLayout.LayoutParams(0, dp(44), 1f));
        card.addView(styleRow, srlp);

        // Opacity Header Row
        LinearLayout opHeader = new LinearLayout(this);
        opHeader.setOrientation(LinearLayout.HORIZONTAL);
        opHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView opTitle = new TextView(this);
        opTitle.setText(I18n.get(this, "opacity_title"));
        opTitle.setTextColor(0xFFFFFFFF);
        opTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        opHeader.addView(opTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        mOpacityValueText = new TextView(this);
        mOpacityValueText.setText(mConfig.opacity + "%");
        mOpacityValueText.setTextColor(0xFF82B1FF);
        mOpacityValueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        mOpacityValueText.setTypeface(Typeface.DEFAULT_BOLD);
        opHeader.addView(mOpacityValueText);
        card.addView(opHeader);

        mOpacitySeekBar = new SeekBar(this);
        mOpacitySeekBar.setMax(100);
        mOpacitySeekBar.setProgress(mConfig.opacity);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sbLp.topMargin = dp(6);
        sbLp.bottomMargin = dp(8);
        mOpacitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mConfig.opacity = progress;
                mOpacityValueText.setText(progress + "%");
                if (fromUser) {
                    refreshPreviewOnly();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                refreshEntireUi(true);
            }
        });
        card.addView(mOpacitySeekBar, sbLp);

        return card;
    }

    private View buildCategoriesCatalogCard() {
        LinearLayout card = createSectionCard();

        TextView secTitle = new TextView(this);
        secTitle.setText(I18n.get(this, "sec_catalog_title"));
        secTitle.setTextColor(0xFF82B1FF);
        secTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        secTitle.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(secTitle);

        TextView secSub = new TextView(this);
        secSub.setText(I18n.get(this, "sec_catalog_desc"));
        secSub.setTextColor(0xFF9AA0B4);
        secSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        secSub.setPadding(0, dp(4), 0, dp(12));
        card.addView(secSub);

        mCategoriesListContainer = new LinearLayout(this);
        mCategoriesListContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(mCategoriesListContainer);

        rebuildCategoriesList();
        return card;
    }

    private void applyCategoryExpansionWithTransition() {
        try {
            AutoTransition transition = new AutoTransition();
            transition.setDuration(220);
            transition.setInterpolator(new PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f));
            TransitionManager.beginDelayedTransition(mCategoriesListContainer, transition);
        } catch (Throwable ignored) {}

        for (CategoryRowHolder holder : mCategoryRowHolders) {
            boolean isExpanded = holder.categoryId.equals(mExpandedCategoryId);
            holder.expandSection.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            if (holder.arrowView != null) {
                holder.arrowView.setText(isExpanded ? "▲" : "▼");
                holder.arrowView.setTextColor(isExpanded ? 0xFF82B1FF : 0xFF7D8597);
            }
            holder.rootCard.setBackground(roundRect(
                    isExpanded ? 0xFF2A2A2E : 0xFF232325,
                    dp(28),
                    1,
                    isExpanded ? 0xFF1477FF : 0xFF38383B
            ));
        }
        refreshPreviewOnly();
    }

    private void rebuildCategoriesList() {
        if (mCategoriesListContainer == null) return;
        mCategoriesListContainer.removeAllViews();
        mCategoryRowHolders.clear();

        Integer liveWall = WallpaperColorHelper.resolveForVariant(
                mWallpaperPalette, mConfig.wallpaperVariant);

        for (int i = 0; i < ConfigManager.ALL_CATEGORIES.size(); i++) {
            final ConfigManager.CategoryMeta meta = ConfigManager.ALL_CATEGORIES.get(i);
            final ConfigManager.CategoryConfig cc = mConfig.getCategory(meta.id);
            final boolean isExpanded = meta.id.equals(mExpandedCategoryId);

            // 1. Root Island Container (Vertical container with pill rounded corners)
            LinearLayout rootIsland = new LinearLayout(this);
            rootIsland.setOrientation(LinearLayout.VERTICAL);
            rootIsland.setBackground(roundRect(
                    isExpanded ? 0xFF2A2A2E : 0xFF232325,
                    dp(28),
                    1,
                    isExpanded ? 0xFF1477FF : 0xFF38383B
            ));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i < ConfigManager.ALL_CATEGORIES.size() - 1) {
                rlp.bottomMargin = dp(10);
            }
            rootIsland.setLayoutParams(rlp);

            // 2. Main Row (Icon, Title, Expand indicator, Color chip, Switch)
            LinearLayout mainRow = new LinearLayout(this);
            mainRow.setOrientation(LinearLayout.HORIZONTAL);
            mainRow.setGravity(Gravity.CENTER_VERTICAL);
            mainRow.setPadding(dp(12), dp(10), dp(14), dp(10));

            // Category Icon Badge
            FrameLayout iconWrap = new FrameLayout(this);
            View iconBg = new View(this);
            iconBg.setBackground(roundRect(meta.accentColor, dp(20), 0, 0));
            iconWrap.addView(iconBg, new FrameLayout.LayoutParams(dp(40), dp(40)));

            TextView iconTxt = new TextView(this);
            iconTxt.setText(meta.badgeText);
            iconTxt.setTextColor(0xFFFFFFFF);
            iconTxt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            iconTxt.setGravity(Gravity.CENTER);
            iconWrap.addView(iconTxt, new FrameLayout.LayoutParams(dp(40), dp(40)));
            mainRow.addView(iconWrap, new LinearLayout.LayoutParams(dp(40), dp(40)));

            // Title Column (Only title visible initially + subtle chevron)
            LinearLayout titleCol = new LinearLayout(this);
            titleCol.setOrientation(LinearLayout.HORIZONTAL);
            titleCol.setGravity(Gravity.CENTER_VERTICAL);
            titleCol.setPadding(dp(12), 0, dp(8), 0);

            TextView title = new TextView(this);
            title.setText(meta.getTitle(this));
            title.setTextColor(cc.enabled ? 0xFFFFFFFF : 0xFF7D8597);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            titleCol.addView(title, tlp);

            TextView arrowView = new TextView(this);
            arrowView.setText(isExpanded ? "▲" : "▼");
            arrowView.setTextColor(isExpanded ? 0xFF82B1FF : 0xFF7D8597);
            arrowView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
            arrowView.setPadding(dp(4), 0, dp(4), 0);
            titleCol.addView(arrowView);

            mainRow.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            // Color Override Chip (Global vs Propio)
            int resolvedRgb = mConfig.resolveBaseRgbForCategory(meta.id, liveWall);
            LinearLayout colorChip = new LinearLayout(this);
            colorChip.setOrientation(LinearLayout.HORIZONTAL);
            colorChip.setGravity(Gravity.CENTER_VERTICAL);
            colorChip.setPadding(dp(8), dp(6), dp(10), dp(6));
            colorChip.setBackground(roundRect(
                    cc.useCustomColor ? 0xFF263248 : 0xFF1E1E20,
                    dp(14),
                    1,
                    cc.useCustomColor ? 0xFF3367B8 : 0xFF38383B
            ));

            View miniDot = new View(this);
            boolean isCatGrad = cc.useCustomColor && (cc.customColorMode == ConfigManager.MODE_GRADIENT);
            boolean isGlobGrad = !cc.useCustomColor && (mConfig.colorMode == ConfigManager.MODE_GRADIENT);
            if (isCatGrad) {
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{cc.customGradientStart | 0xFF000000, cc.customGradientEnd | 0xFF000000});
                gd.setCornerRadius(dp(7));
                miniDot.setBackground(gd);
            } else if (isGlobGrad) {
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{mConfig.gradientStartColor | 0xFF000000, mConfig.gradientEndColor | 0xFF000000});
                gd.setCornerRadius(dp(7));
                miniDot.setBackground(gd);
            } else {
                miniDot.setBackground(roundRect(resolvedRgb | 0xFF000000, dp(7), 1, 0x66FFFFFF));
            }
            colorChip.addView(miniDot, new LinearLayout.LayoutParams(dp(14), dp(14)));

            TextView chipText = new TextView(this);
            String chipLabel;
            if (cc.useCustomColor) {
                if (cc.customColorMode == ConfigManager.MODE_GRADIENT) {
                    chipLabel = " " + I18n.get(this, "tab_gradient") + (cc.useCustomOpacity ? " • " + cc.customOpacity + "%" : "");
                } else {
                    String curLang = I18n.getEffectiveLanguage(this);
                    String customWord = "es".equals(curLang) ? "Propio" : ("pt".equals(curLang) ? "Próprio" : "Custom");
                    chipLabel = " " + customWord + (cc.useCustomOpacity ? " • " + cc.customOpacity + "%" : "");
                }
            } else {
                chipLabel = isGlobGrad ? (" Global • " + I18n.get(this, "tab_gradient")) : " Global";
            }
            chipText.setText(chipLabel);
            chipText.setTextColor(cc.useCustomColor ? 0xFF90CAF9 : 0xFFB0B8C8);
            chipText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            chipText.setTypeface(Typeface.DEFAULT_BOLD);
            colorChip.addView(chipText);

            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipLp.rightMargin = dp(8);
            colorChip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showIndividualCategoryColorPanel(meta, cc);
                }
            });
            mainRow.addView(colorChip, chipLp);

            // Individual ON/OFF One UI Switch
            OneUISwitch sw = new OneUISwitch(this);
            sw.setChecked(cc.enabled);
            sw.setOnCheckedChangeListener(new OneUISwitch.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(OneUISwitch view, boolean isChecked) {
                    cc.enabled = isChecked;
                    refreshEntireUi(true);
                }
            });
            mainRow.addView(sw);

            rootIsland.addView(mainRow);

            // 3. Expandable Section (Shows which apps and services are affected)
            LinearLayout expandSection = new LinearLayout(this);
            expandSection.setOrientation(LinearLayout.VERTICAL);
            expandSection.setPadding(dp(16), 0, dp(16), dp(14));
            expandSection.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

            View divider = new View(this);
            divider.setBackgroundColor(0x22FFFFFF);
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            divLp.topMargin = dp(2);
            divLp.bottomMargin = dp(8);
            expandSection.addView(divider, divLp);

            TextView appLabel = new TextView(this);
            appLabel.setText(I18n.get(this, "affected_services"));
            appLabel.setTextColor(0xFF82B1FF);
            appLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            appLabel.setTypeface(Typeface.DEFAULT_BOLD);
            expandSection.addView(appLabel);

            TextView appDesc = new TextView(this);
            appDesc.setText(meta.getSubtitle(this));
            appDesc.setTextColor(0xFFE0E0E5);
            appDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            appDesc.setPadding(0, dp(3), 0, 0);
            expandSection.addView(appDesc);

            rootIsland.addView(expandSection);

            // Tapping the island expands/collapses this category
            View.OnClickListener islandClick = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (meta.id.equals(mExpandedCategoryId)) {
                        mExpandedCategoryId = null; // collapse
                    } else {
                        mExpandedCategoryId = meta.id; // expand this, collapse others
                    }
                    applyCategoryExpansionWithTransition();
                }
            };
            mainRow.setOnClickListener(islandClick);
            expandSection.setOnClickListener(islandClick);

            CategoryRowHolder holder = new CategoryRowHolder();
            holder.categoryId = meta.id;
            holder.rootCard = rootIsland;
            holder.expandSection = expandSection;
            holder.arrowView = arrowView;
            mCategoryRowHolders.add(holder);

            mCategoriesListContainer.addView(rootIsland);
        }
    }

    private void showIndividualCategoryColorPanel(final ConfigManager.CategoryMeta meta, final ConfigManager.CategoryConfig cc) {
        final Integer liveWall = WallpaperColorHelper.resolveForVariant(mWallpaperPalette, mConfig.wallpaperVariant);
        final int initialGlobalRgb = mConfig.resolveBaseRgbForCategory(meta.id, liveWall);
        final boolean[] isUsingGlobal = new boolean[]{ !cc.useCustomColor };
        final int[] selectedMode = new int[]{ cc.useCustomColor ? cc.customColorMode : (mConfig.colorMode == ConfigManager.MODE_GRADIENT ? ConfigManager.MODE_GRADIENT : ConfigManager.MODE_FIXED_COLOR) };
        final int[] selectedColor = new int[]{ cc.useCustomColor ? cc.customColor : initialGlobalRgb };
        final int[] gradStart = new int[]{ cc.useCustomColor ? cc.customGradientStart : mConfig.gradientStartColor };
        final int[] gradEnd = new int[]{ cc.useCustomColor ? cc.customGradientEnd : mConfig.gradientEndColor };
        final boolean[] gradEditingEnd = new boolean[]{ false };
        final boolean[] hasCustomOpacity = new boolean[]{ cc.useCustomOpacity };
        final int[] selectedOpacity = new int[]{ cc.useCustomOpacity ? cc.customOpacity : mConfig.opacity };
        final int[] currentTab = new int[]{ cc.useCustomColor ? cc.customColorMode : (mConfig.colorMode == ConfigManager.MODE_GRADIENT ? 2 : (mConfig.colorMode == ConfigManager.MODE_LOCK_WALLPAPER ? 1 : 0)) };

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final AlertDialog dialog = builder.create();
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(true);
        scroll.setPadding(dp(8), dp(8), dp(8), dp(8));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundRect(0xFF232325, dp(28), 1, 0xFF38383B));
        int pad = dp(18);
        card.setPadding(pad, pad, pad, pad);
        scroll.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 1. Header Row (Category Icon + Title + Subtitle)
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout iconWrap = new FrameLayout(this);
        View iconBg = new View(this);
        iconBg.setBackground(roundRect(meta.accentColor, dp(18), 0, 0));
        iconWrap.addView(iconBg, new FrameLayout.LayoutParams(dp(36), dp(36)));
        TextView iconTxt = new TextView(this);
        iconTxt.setText(meta.badgeText);
        iconTxt.setTextColor(0xFFFFFFFF);
        iconTxt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        iconTxt.setGravity(Gravity.CENTER);
        iconWrap.addView(iconTxt, new FrameLayout.LayoutParams(dp(36), dp(36)));
        titleRow.addView(iconWrap, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setPadding(dp(12), 0, 0, 0);

        TextView titleTxt = new TextView(this);
        titleTxt.setText(meta.getTitle(this));
        titleTxt.setTextColor(0xFFFFFFFF);
        titleTxt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        titleTxt.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleCol.addView(titleTxt);

        TextView subTxt = new TextView(this);
        subTxt.setText(I18n.get(this, "individual_color_title"));
        subTxt.setTextColor(0xFF82B1FF);
        subTxt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        titleCol.addView(subTxt);

        titleRow.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(titleRow);

        // 2. "Restablecer a Color Global" Button
        final Button btnResetGlobal = new Button(this);
        btnResetGlobal.setAllCaps(false);
        btnResetGlobal.setText(I18n.get(this, "reset_global_btn"));
        btnResetGlobal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        resetLp.topMargin = dp(12);
        resetLp.bottomMargin = dp(12);
        card.addView(btnResetGlobal, resetLp);

        // 3. Own Live Preview Capsule
        TextView previewHeader = new TextView(this);
        previewHeader.setText(I18n.get(this, "individual_preview_title"));
        previewHeader.setTextColor(0xFF82B1FF);
        previewHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        previewHeader.setTypeface(Typeface.DEFAULT_BOLD);
        previewHeader.setPadding(0, 0, 0, dp(6));
        card.addView(previewHeader);

        FrameLayout dialogStage = new FrameLayout(this) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF r = new RectF();

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                int bottomColor = resolveWallpaperBottomColor();
                p.setStyle(Paint.Style.FILL);
                p.setShader(null);
                p.setColor(bottomColor);
                r.set(0, 0, getWidth(), getHeight());
                canvas.drawRoundRect(r, dp(20), dp(20), p);

                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(1));
                p.setColor(0x22FFFFFF);
                float half = dp(0.5f);
                r.set(half, half, getWidth() - half, getHeight() - half);
                canvas.drawRoundRect(r, dp(20), dp(20), p);
                p.setStyle(Paint.Style.FILL);
            }
        };
        dialogStage.setWillNotDraw(false);
        dialogStage.setPadding(dp(12), dp(16), dp(12), dp(16));
        LinearLayout.LayoutParams dStageLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dStageLp.bottomMargin = dp(14);

        final OneUIMediaCapsuleView dialogCapsule = new OneUIMediaCapsuleView(this);
        FrameLayout.LayoutParams dclp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        dialogStage.addView(dialogCapsule, dclp);
        card.addView(dialogStage, dStageLp);
        dialogCapsule.startWaveAnimation();

        // 4. Mode Row Buttons [ Paleta Fija ] | [ Fondo Bloqueo ] | [ Degradado ]
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams modeRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        modeRowLp.bottomMargin = dp(12);

        final Button btnTabFixed = new Button(this);
        btnTabFixed.setAllCaps(false);
        btnTabFixed.setText(I18n.get(this, "tab_solid"));
        btnTabFixed.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);

        View tabSpacer1 = new View(this);

        final Button btnTabWallpaper = new Button(this);
        btnTabWallpaper.setAllCaps(false);
        btnTabWallpaper.setText(I18n.get(this, "tab_wallpaper"));
        btnTabWallpaper.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);

        View tabSpacer2 = new View(this);

        final Button btnTabGradient = new Button(this);
        btnTabGradient.setAllCaps(false);
        btnTabGradient.setText(I18n.get(this, "tab_gradient"));
        btnTabGradient.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);

        modeRow.addView(btnTabFixed, new LinearLayout.LayoutParams(0, dp(42), 1f));
        modeRow.addView(tabSpacer1, new LinearLayout.LayoutParams(dp(6), 1));
        modeRow.addView(btnTabWallpaper, new LinearLayout.LayoutParams(0, dp(42), 1f));
        modeRow.addView(tabSpacer2, new LinearLayout.LayoutParams(dp(6), 1));
        modeRow.addView(btnTabGradient, new LinearLayout.LayoutParams(0, dp(42), 1f));
        card.addView(modeRow, modeRowLp);

        // 5. Container A: Fixed Palette Grid
        final LinearLayout palContainer = new LinearLayout(this);
        palContainer.setOrientation(LinearLayout.VERTICAL);

        TextView palLabel = new TextView(this);
        palLabel.setText(I18n.get(this, "palette_label"));
        palLabel.setTextColor(0xFFB0B8C8);
        palLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        palLabel.setPadding(0, 0, 0, dp(8));
        palContainer.addView(palLabel);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(6);
        final List<View> swatchViews = new ArrayList<>();

        for (int i = 0; i < PRESET_PALETTE.length; i++) {
            final int presetCol = PRESET_PALETTE[i];
            FrameLayout wrap = new FrameLayout(this);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = 0;
            glp.height = dp(44);
            glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            glp.setMargins(dp(3), dp(3), dp(3), dp(3));
            wrap.setLayoutParams(glp);

            final View circle = new View(this);
            wrap.addView(circle, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER));
            circle.setTag(presetCol);
            swatchViews.add(circle);

            grid.addView(wrap);
        }
        palContainer.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(palContainer);

        // 6. Container B: Wallpaper Variants
        final LinearLayout wallContainer = new LinearLayout(this);
        wallContainer.setOrientation(LinearLayout.VERTICAL);
        wallContainer.setVisibility(View.GONE);

        TextView wallInfo = new TextView(this);
        wallInfo.setText(I18n.get(this, "wall_info"));
        wallInfo.setTextColor(0xFFB0B8C8);
        wallInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        wallInfo.setPadding(0, 0, 0, dp(8));
        wallContainer.addView(wallInfo);

        final int[] wallColors = new int[]{
                mWallpaperPalette != null ? mWallpaperPalette.primaryColor : mConfig.cachedWallpaperPrimary,
                mWallpaperPalette != null ? mWallpaperPalette.secondaryColor : mConfig.cachedWallpaperSecondary,
                mWallpaperPalette != null ? mWallpaperPalette.monetAccentColor : mConfig.cachedWallpaperAccent,
                mWallpaperPalette != null ? mWallpaperPalette.bottomAreaColor : mConfig.cachedWallpaperBottom
        };

        String[] variantTitles = new String[]{
                I18n.get(this, "wall_primary_title"),
                I18n.get(this, "wall_secondary_title"),
                I18n.get(this, "wall_monet_title"),
                I18n.get(this, "wall_bottom_title")
        };

        final List<LinearLayout> wallVariantRows = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            final int variantCol = wallColors[i];
            final LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dp(6);
            row.setLayoutParams(rlp);
            row.setTag(variantCol);

            View dot = new View(this);
            dot.setBackground(roundRect(variantCol | 0xFF000000, dp(15), 1, 0x66FFFFFF));
            row.addView(dot, new LinearLayout.LayoutParams(dp(30), dp(30)));

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setPadding(dp(10), 0, dp(6), 0);

            TextView title = new TextView(this);
            title.setText(variantTitles[i]);
            title.setTextColor(0xFFFFFFFF);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            textCol.addView(title);

            row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            wallVariantRows.add(row);
            wallContainer.addView(row);
        }
        card.addView(wallContainer);

        // 7. Container C: Gradient (Degradado) Selector with Subtabs & Dedicated Opacity Slider
        final LinearLayout gradContainer = new LinearLayout(this);
        gradContainer.setOrientation(LinearLayout.VERTICAL);
        gradContainer.setVisibility(View.GONE);

        TextView gradInfo = new TextView(this);
        gradInfo.setText(I18n.get(this, "grad_info"));
        gradInfo.setTextColor(0xFFB0B8C8);
        gradInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        gradInfo.setPadding(0, 0, 0, dp(8));
        gradContainer.addView(gradInfo);

        // Subtabs: [● Primer color]  [● Color final]
        LinearLayout subtabsRow = new LinearLayout(this);
        subtabsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        stlp.bottomMargin = dp(10);

        final LinearLayout btnDialogGradStart = new LinearLayout(this);
        btnDialogGradStart.setOrientation(LinearLayout.HORIZONTAL);
        btnDialogGradStart.setGravity(Gravity.CENTER);
        final View dotDialogGradStart = new View(this);
        btnDialogGradStart.addView(dotDialogGradStart, new LinearLayout.LayoutParams(dp(14), dp(14)));
        TextView txtDGradStart = new TextView(this);
        txtDGradStart.setText(" " + I18n.get(this, "grad_color_start"));
        txtDGradStart.setTextColor(0xFFFFFFFF);
        txtDGradStart.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        txtDGradStart.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btnDialogGradStart.addView(txtDGradStart);

        View dSubGap = new View(this);

        final LinearLayout btnDialogGradEnd = new LinearLayout(this);
        btnDialogGradEnd.setOrientation(LinearLayout.HORIZONTAL);
        btnDialogGradEnd.setGravity(Gravity.CENTER);
        final View dotDialogGradEnd = new View(this);
        btnDialogGradEnd.addView(dotDialogGradEnd, new LinearLayout.LayoutParams(dp(14), dp(14)));
        TextView txtDGradEnd = new TextView(this);
        txtDGradEnd.setText(" " + I18n.get(this, "grad_color_end"));
        txtDGradEnd.setTextColor(0xFFFFFFFF);
        txtDGradEnd.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        txtDGradEnd.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btnDialogGradEnd.addView(txtDGradEnd);

        subtabsRow.addView(btnDialogGradStart, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        subtabsRow.addView(dSubGap, new LinearLayout.LayoutParams(dp(8), 1));
        subtabsRow.addView(btnDialogGradEnd, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        gradContainer.addView(subtabsRow, stlp);

        // Gradient preset palette grid
        GridLayout dGradGrid = new GridLayout(this);
        dGradGrid.setColumnCount(6);
        final List<View> dialogGradSwatchViews = new ArrayList<>();

        for (int i = 0; i < PRESET_PALETTE.length; i++) {
            final int presetCol = PRESET_PALETTE[i];
            FrameLayout wrap = new FrameLayout(this);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = 0;
            glp.height = dp(44);
            glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            glp.setMargins(dp(3), dp(3), dp(3), dp(3));
            wrap.setLayoutParams(glp);

            final View circle = new View(this);
            wrap.addView(circle, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER));
            circle.setTag(presetCol);
            dialogGradSwatchViews.add(circle);

            dGradGrid.addView(wrap);
        }
        gradContainer.addView(dGradGrid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button btnDialogGradAdvanced = new Button(this);
        btnDialogGradAdvanced.setAllCaps(false);
        btnDialogGradAdvanced.setText(I18n.get(this, "picker_advanced"));
        btnDialogGradAdvanced.setTextColor(0xFFFFFFFF);
        btnDialogGradAdvanced.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        btnDialogGradAdvanced.setBackground(roundRect(0xFF2E2E32, dp(14), 1, 0xFF45454A));
        LinearLayout.LayoutParams dGradAdvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        dGradAdvLp.topMargin = dp(8);
        dGradAdvLp.bottomMargin = dp(10);
        gradContainer.addView(btnDialogGradAdvanced, dGradAdvLp);

        // Opacity Section in Degradado (below palette & advanced)
        LinearLayout opHeader = new LinearLayout(this);
        opHeader.setOrientation(LinearLayout.HORIZONTAL);
        opHeader.setGravity(Gravity.CENTER_VERTICAL);
        opHeader.setPadding(0, dp(4), 0, dp(2));

        TextView opTitle = new TextView(this);
        opTitle.setText(I18n.get(this, "opacity_title"));
        opTitle.setTextColor(0xFFFFFFFF);
        opTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        opHeader.addView(opTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView gradOpacityText = new TextView(this);
        gradOpacityText.setText(selectedOpacity[0] + "%");
        gradOpacityText.setTextColor(0xFF82B1FF);
        gradOpacityText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        gradOpacityText.setTypeface(Typeface.DEFAULT_BOLD);
        opHeader.addView(gradOpacityText);
        gradContainer.addView(opHeader);

        final SeekBar gradOpacitySeekBar = new SeekBar(this);
        gradOpacitySeekBar.setMax(100);
        gradOpacitySeekBar.setProgress(selectedOpacity[0]);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sbLp.topMargin = dp(4);
        sbLp.bottomMargin = dp(6);
        gradContainer.addView(gradOpacitySeekBar, sbLp);

        card.addView(gradContainer);

        // 8. State Update Runnable
        final Runnable updateDialogState = new Runnable() {
            @Override
            public void run() {
                int effOpacity = (hasCustomOpacity[0] && !isUsingGlobal[0]) ? selectedOpacity[0] : mConfig.opacity;
                int clampedOpacity = Math.max(0, Math.min(100, effOpacity));
                int alpha = Math.round((clampedOpacity / 100.0f) * 255.0f);
                boolean isBlur = (mConfig.styleMode == ConfigManager.STYLE_BLUR);
                int strokeColor = isBlur ? 0x55FFFFFF : 0x22FFFFFF;

                if (isUsingGlobal[0]) {
                    if (mConfig.colorMode == ConfigManager.MODE_GRADIENT) {
                        int[] g = mConfig.resolveGlobalGradientArgb();
                        if (g != null) {
                            dialogCapsule.setCapsuleGradient(g[0], g[1], isBlur, strokeColor);
                        }
                    } else {
                        int baseRgb = mConfig.resolveBaseRgbForCategory(meta.id, liveWall);
                        int finalArgb = (alpha << 24) | (baseRgb & 0x00FFFFFF);
                        dialogCapsule.setCapsuleColor(finalArgb, isBlur, strokeColor);
                    }
                } else {
                    if (selectedMode[0] == ConfigManager.MODE_GRADIENT) {
                        int startArgb = (alpha << 24) | (gradStart[0] & 0x00FFFFFF);
                        int endArgb = (alpha << 24) | (gradEnd[0] & 0x00FFFFFF);
                        dialogCapsule.setCapsuleGradient(startArgb, endArgb, isBlur, strokeColor);
                    } else {
                        int finalArgb = (alpha << 24) | (selectedColor[0] & 0x00FFFFFF);
                        dialogCapsule.setCapsuleColor(finalArgb, isBlur, strokeColor);
                    }
                }

                // Update Reset button
                if (isUsingGlobal[0]) {
                    btnResetGlobal.setBackground(roundRect(0xFF1E3A66, dp(14), 2, 0xFF1477FF));
                    btnResetGlobal.setTextColor(0xFF82B1FF);
                    String globDesc = (mConfig.colorMode == ConfigManager.MODE_GRADIENT)
                            ? I18n.get(MainActivity.this, "tab_gradient")
                            : String.format("#%06X", (mConfig.resolveBaseRgbForCategory(meta.id, liveWall) & 0x00FFFFFF));
                    btnResetGlobal.setText("✓ " + I18n.get(MainActivity.this, "using_global_color") + " (" + globDesc + " • " + mConfig.opacity + "%)");
                } else {
                    btnResetGlobal.setBackground(roundRect(0xFF2E2E32, dp(14), 1, 0xFF45454A));
                    btnResetGlobal.setTextColor(0xFFE0E0E5);
                    btnResetGlobal.setText(I18n.get(MainActivity.this, "reset_global_btn"));
                }

                // Update Swatches in Palette
                for (View swatch : swatchViews) {
                    int col = (int) swatch.getTag();
                    boolean isSel = !isUsingGlobal[0] && (selectedMode[0] == ConfigManager.MODE_FIXED_COLOR) && (col == (selectedColor[0] | 0xFF000000));
                    swatch.setBackground(roundRect(col, dp(18), isSel ? dp(3) : 1, isSel ? 0xFFFFFFFF : 0x44FFFFFF));
                }

                // Update Wallpaper Variant rows
                for (LinearLayout row : wallVariantRows) {
                    int col = (int) row.getTag();
                    boolean isSel = !isUsingGlobal[0] && (selectedMode[0] == ConfigManager.MODE_LOCK_WALLPAPER) && (col == (selectedColor[0] | 0xFF000000));
                    row.setBackground(roundRect(
                            isSel ? 0xFF353538 : 0xFF232325,
                            dp(12),
                            isSel ? 2 : 1,
                            isSel ? 0xFF1477FF : 0xFF38383B
                    ));
                }

                // Update Gradient Subtabs
                boolean editStart = !gradEditingEnd[0];
                btnDialogGradStart.setBackground(roundRect(
                        editStart ? 0xFF333338 : 0xFF232325,
                        dp(12),
                        editStart ? 2 : 1,
                        editStart ? 0xFF1477FF : 0xFF38383B
                ));
                dotDialogGradStart.setBackground(roundRect(gradStart[0] | 0xFF000000, dp(7), 1, 0x88FFFFFF));

                boolean editEnd = gradEditingEnd[0];
                btnDialogGradEnd.setBackground(roundRect(
                        editEnd ? 0xFF333338 : 0xFF232325,
                        dp(12),
                        editEnd ? 2 : 1,
                        editEnd ? 0xFF1477FF : 0xFF38383B
                ));
                dotDialogGradEnd.setBackground(roundRect(gradEnd[0] | 0xFF000000, dp(7), 1, 0x88FFFFFF));

                int activeGradCol = gradEditingEnd[0] ? gradEnd[0] : gradStart[0];
                for (View swatch : dialogGradSwatchViews) {
                    int col = (int) swatch.getTag();
                    boolean isSel = !isUsingGlobal[0] && (selectedMode[0] == ConfigManager.MODE_GRADIENT) && ((col & 0x00FFFFFF) == (activeGradCol & 0x00FFFFFF));
                    swatch.setBackground(roundRect(col, dp(18), isSel ? dp(3) : 1, isSel ? 0xFFFFFFFF : 0x44FFFFFF));
                }

                // Update opacity texts and seekbar
                gradOpacityText.setText(clampedOpacity + "%");
                if (gradOpacitySeekBar.getProgress() != clampedOpacity) {
                    gradOpacitySeekBar.setProgress(clampedOpacity);
                }

                // Update Tab buttons & container visibility
                palContainer.setVisibility(currentTab[0] == 0 ? View.VISIBLE : View.GONE);
                wallContainer.setVisibility(currentTab[0] == 1 ? View.VISIBLE : View.GONE);
                gradContainer.setVisibility(currentTab[0] == 2 ? View.VISIBLE : View.GONE);
                styleSelectableButton(btnTabFixed, currentTab[0] == 0);
                styleSelectableButton(btnTabWallpaper, currentTab[0] == 1);
                styleSelectableButton(btnTabGradient, currentTab[0] == 2);
            }
        };

        // Wire swatch click in Fixed Palette
        for (final View swatch : swatchViews) {
            final int col = (int) swatch.getTag();
            ((View) swatch.getParent()).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    isUsingGlobal[0] = false;
                    selectedMode[0] = ConfigManager.MODE_FIXED_COLOR;
                    selectedColor[0] = col;
                    updateDialogState.run();
                }
            });
        }

        // Wire wallpaper row click
        for (final LinearLayout row : wallVariantRows) {
            final int col = (int) row.getTag();
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    isUsingGlobal[0] = false;
                    selectedMode[0] = ConfigManager.MODE_LOCK_WALLPAPER;
                    selectedColor[0] = col;
                    updateDialogState.run();
                }
            });
        }

        // Wire swatch click in Gradient Palette
        for (final View swatch : dialogGradSwatchViews) {
            final int col = (int) swatch.getTag();
            ((View) swatch.getParent()).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    isUsingGlobal[0] = false;
                    selectedMode[0] = ConfigManager.MODE_GRADIENT;
                    if (gradEditingEnd[0]) {
                        gradEnd[0] = col;
                    } else {
                        gradStart[0] = col;
                    }
                    updateDialogState.run();
                }
            });
        }

        btnDialogGradStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gradEditingEnd[0] = false;
                updateDialogState.run();
            }
        });

        btnDialogGradEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gradEditingEnd[0] = true;
                updateDialogState.run();
            }
        });

        btnDialogGradAdvanced.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String title = gradEditingEnd[0] ? (I18n.get(MainActivity.this, "grad_color_end") + ": " + meta.getTitle(MainActivity.this)) : (I18n.get(MainActivity.this, "grad_color_start") + ": " + meta.getTitle(MainActivity.this));
                int cur = gradEditingEnd[0] ? gradEnd[0] : gradStart[0];
                showCustomColorPickerDialog(title, cur, new OnColorPickedListener() {
                    @Override
                    public void onColorPicked(int color) {
                        isUsingGlobal[0] = false;
                        selectedMode[0] = ConfigManager.MODE_GRADIENT;
                        if (gradEditingEnd[0]) {
                            gradEnd[0] = color;
                        } else {
                            gradStart[0] = color;
                        }
                        updateDialogState.run();
                    }
                });
            }
        });

        gradOpacitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    selectedOpacity[0] = progress;
                    hasCustomOpacity[0] = true;
                    isUsingGlobal[0] = false;
                    updateDialogState.run();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnTabFixed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentTab[0] = 0;
                selectedMode[0] = ConfigManager.MODE_FIXED_COLOR;
                isUsingGlobal[0] = false;
                updateDialogState.run();
            }
        });

        btnTabWallpaper.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentTab[0] = 1;
                selectedMode[0] = ConfigManager.MODE_LOCK_WALLPAPER;
                isUsingGlobal[0] = false;
                updateDialogState.run();
            }
        });

        btnTabGradient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentTab[0] = 2;
                selectedMode[0] = ConfigManager.MODE_GRADIENT;
                isUsingGlobal[0] = false;
                updateDialogState.run();
            }
        });

        btnResetGlobal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isUsingGlobal[0] = true;
                hasCustomOpacity[0] = false;
                selectedOpacity[0] = mConfig.opacity;
                selectedMode[0] = mConfig.colorMode;
                updateDialogState.run();
            }
        });

        // 9. Bottom Actions Row: [ ✨ Avanzado ] [ Cancelar ] [ Aplicar ]
        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        actionsLp.topMargin = dp(14);

        Button btnAdvanced = new Button(this);
        btnAdvanced.setAllCaps(false);
        btnAdvanced.setText(I18n.get(this, "btn_advanced"));
        btnAdvanced.setTextColor(0xFFFFFFFF);
        btnAdvanced.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        btnAdvanced.setBackground(roundRect(0xFF2E2E32, dp(14), 1, 0xFF45454A));

        View s1 = new View(this);

        Button btnCancel = new Button(this);
        btnCancel.setAllCaps(false);
        btnCancel.setText(I18n.get(this, "cancel"));
        btnCancel.setTextColor(0xFFB0B8C8);
        btnCancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        btnCancel.setBackground(roundRect(0xFF232325, dp(14), 1, 0xFF38383B));

        View s2 = new View(this);

        Button btnApply = new Button(this);
        btnApply.setAllCaps(false);
        btnApply.setText(I18n.get(this, "apply"));
        btnApply.setTextColor(0xFFFFFFFF);
        btnApply.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        btnApply.setTypeface(Typeface.DEFAULT_BOLD);
        btnApply.setBackground(roundRect(0xFF1477FF, dp(14), 1, 0xFF3367B8));

        actionsRow.addView(btnAdvanced, new LinearLayout.LayoutParams(0, dp(44), 1.05f));
        actionsRow.addView(s1, new LinearLayout.LayoutParams(dp(6), 1));
        actionsRow.addView(btnCancel, new LinearLayout.LayoutParams(0, dp(44), 0.95f));
        actionsRow.addView(s2, new LinearLayout.LayoutParams(dp(6), 1));
        actionsRow.addView(btnApply, new LinearLayout.LayoutParams(0, dp(44), 1.15f));
        card.addView(actionsRow, actionsLp);

        btnAdvanced.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentTab[0] == 2) {
                    String title = gradEditingEnd[0] ? (I18n.get(MainActivity.this, "grad_color_end") + ": " + meta.getTitle(MainActivity.this)) : (I18n.get(MainActivity.this, "grad_color_start") + ": " + meta.getTitle(MainActivity.this));
                    int cur = gradEditingEnd[0] ? gradEnd[0] : gradStart[0];
                    showCustomColorPickerDialog(title, cur, new OnColorPickedListener() {
                        @Override
                        public void onColorPicked(int color) {
                            isUsingGlobal[0] = false;
                            selectedMode[0] = ConfigManager.MODE_GRADIENT;
                            if (gradEditingEnd[0]) {
                                gradEnd[0] = color;
                            } else {
                                gradStart[0] = color;
                            }
                            updateDialogState.run();
                        }
                    });
                } else {
                    showCustomColorPickerDialog(
                            I18n.get(MainActivity.this, "dialog_color_opacity_prefix") + meta.getTitle(MainActivity.this),
                            selectedColor[0],
                            hasCustomOpacity[0] ? selectedOpacity[0] : mConfig.opacity,
                            true,
                            new OnColorAndOpacityPickedListener() {
                                @Override
                                public void onColorAndOpacityPicked(int color, int opacityPercent) {
                                    isUsingGlobal[0] = false;
                                    selectedMode[0] = (currentTab[0] == 1) ? ConfigManager.MODE_LOCK_WALLPAPER : ConfigManager.MODE_FIXED_COLOR;
                                    selectedColor[0] = color;
                                    hasCustomOpacity[0] = true;
                                    selectedOpacity[0] = opacityPercent;
                                    updateDialogState.run();
                                }
                            }
                    );
                }
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnApply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isUsingGlobal[0]) {
                    cc.useCustomColor = false;
                    cc.useCustomOpacity = false;
                    cc.customColorMode = ConfigManager.MODE_FIXED_COLOR;
                } else {
                    cc.useCustomColor = true;
                    int finalMode = (currentTab[0] == 2) ? ConfigManager.MODE_GRADIENT
                            : ((currentTab[0] == 1) ? ConfigManager.MODE_LOCK_WALLPAPER : ConfigManager.MODE_FIXED_COLOR);
                    cc.customColorMode = finalMode;
                    cc.customColor = selectedColor[0];
                    cc.customGradientStart = gradStart[0];
                    cc.customGradientEnd = gradEnd[0];
                    cc.useCustomOpacity = hasCustomOpacity[0];
                    cc.customOpacity = selectedOpacity[0];
                    cc.enabled = true;
                }
                ConfigManager.saveAndSync(MainActivity.this, mConfig);
                refreshEntireUi(true);
                dialog.dismiss();
            }
        });

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                dialogCapsule.stopWaveAnimation();
            }
        });

        dialog.setView(scroll);
        dialog.show();
        updateDialogState.run();
    }

    private interface OnColorPickedListener {
        void onColorPicked(int color);
    }

    private interface OnColorAndOpacityPickedListener {
        void onColorAndOpacityPicked(int color, int opacityPercent);
    }

    private void showCustomColorPickerDialog(String title, int initialColor, final OnColorPickedListener listener) {
        showCustomColorPickerDialog(title, initialColor, mConfig.opacity, false, new OnColorAndOpacityPickedListener() {
            @Override
            public void onColorAndOpacityPicked(int color, int opacityPercent) {
                if (listener != null) {
                    listener.onColorPicked(color);
                }
            }
        });
    }

    private void showCustomColorPickerDialog(
            String title,
            int initialColor,
            int initialOpacity,
            boolean showOpacity,
            final OnColorAndOpacityPickedListener listener) {
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(10), dp(16), dp(10));

        final HsvColorPickerView pickerView = new HsvColorPickerView(this);
        pickerView.setColor(initialColor);
        pickerView.setOpacitySliderVisible(showOpacity);
        if (showOpacity) {
            pickerView.setOpacity(initialOpacity);
        }

        container.addView(pickerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.addView(container);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton(I18n.get(this, "apply_color"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int finalColor = pickerView.getColor();
                        int finalOpacity = pickerView.getOpacity();
                        if (listener != null) {
                            listener.onColorAndOpacityPicked(finalColor, finalOpacity);
                        }
                    }
                })
                .setNegativeButton(I18n.get(this, "cancel"), null)
                .show();
    }

    private void refreshPreviewOnly() {
        Integer liveWall = WallpaperColorHelper.resolveForVariant(
                mWallpaperPalette, mConfig.wallpaperVariant);

        if (!mConfig.masterEnabled) {
            mPreviewModeTag.setText(I18n.get(this, "preview_original"));
            if (mPreviewMediaCapsule != null) {
                mPreviewMediaCapsule.setCapsuleColor(0x99222630, true, 0x33FFFFFF);
            }
            return;
        }

        boolean isBlur = (mConfig.styleMode == ConfigManager.STYLE_BLUR);
        int strokeColor = isBlur ? 0x55FFFFFF : 0x22FFFFFF;

        if (mConfig.colorMode == ConfigManager.MODE_GRADIENT) {
            int[] grad = mConfig.resolveGlobalGradientArgb();
            String bTag = isBlur ? (I18n.get(this, "preview_blur") + " • ") : (I18n.get(this, "preview_solid") + " • ");
            mPreviewModeTag.setText(bTag + I18n.get(this, "preview_gradient") + " • " + mConfig.opacity + "%");
            if (mPreviewMediaCapsule != null && grad != null) {
                mPreviewMediaCapsule.setCapsuleGradient(grad[0], grad[1], isBlur, strokeColor);
            }
        } else {
            int finalArgb = mConfig.resolveGlobalFinalArgb(liveWall);
            String bTag2 = isBlur ? (I18n.get(this, "preview_blur") + " • ") : (I18n.get(this, "preview_solid") + " • ");
            mPreviewModeTag.setText(bTag2 + mConfig.opacity + "%");
            if (mPreviewMediaCapsule != null) {
                mPreviewMediaCapsule.setCapsuleColor(finalArgb, isBlur, strokeColor);
            }
        }

        if (mLockscreenStage != null) {
            mLockscreenStage.invalidate();
        }
    }

    private void refreshEntireUi(boolean saveAndBroadcast) {
        if (saveAndBroadcast) {
            updateCachedWallpaperInConfig();
            ConfigManager.saveAndSync(this, mConfig);
        }

        // 1. Update Mode Buttons
        boolean isFixed = (mConfig.colorMode == ConfigManager.MODE_FIXED_COLOR);
        boolean isWall = (mConfig.colorMode == ConfigManager.MODE_LOCK_WALLPAPER);
        boolean isGrad = (mConfig.colorMode == ConfigManager.MODE_GRADIENT);
        styleSelectableButton(mBtnFixedMode, isFixed);
        styleSelectableButton(mBtnWallpaperMode, isWall);
        styleSelectableButton(mBtnGradientMode, isGrad);

        mFixedPaletteContainer.setVisibility(isFixed ? View.VISIBLE : View.GONE);
        mWallpaperModeContainer.setVisibility(isWall ? View.VISIBLE : View.GONE);
        mGradientModeContainer.setVisibility(isGrad ? View.VISIBLE : View.GONE);

        // Update Gradient subtabs & swatches
        if (mBtnGlobalGradStart != null) {
            boolean editStart = !mGlobalGradEditingEnd;
            mBtnGlobalGradStart.setBackground(roundRect(
                    editStart ? 0xFF333338 : 0xFF232325,
                    dp(14),
                    editStart ? 2 : 1,
                    editStart ? 0xFF1477FF : 0xFF38383B
            ));
            mDotGlobalGradStart.setBackground(roundRect(mConfig.gradientStartColor | 0xFF000000, dp(8), 1, 0x88FFFFFF));

            boolean editEnd = mGlobalGradEditingEnd;
            mBtnGlobalGradEnd.setBackground(roundRect(
                    editEnd ? 0xFF333338 : 0xFF232325,
                    dp(14),
                    editEnd ? 2 : 1,
                    editEnd ? 0xFF1477FF : 0xFF38383B
            ));
            mDotGlobalGradEnd.setBackground(roundRect(mConfig.gradientEndColor | 0xFF000000, dp(8), 1, 0x88FFFFFF));

            int activeGradCol = mGlobalGradEditingEnd ? mConfig.gradientEndColor : mConfig.gradientStartColor;
            for (View swatch : mGradientPaletteSwatchViews) {
                int col = (Integer) swatch.getTag();
                boolean selected = ((col & 0x00FFFFFF) == (activeGradCol & 0x00FFFFFF));
                swatch.setBackground(roundRect(col, dp(19), selected ? dp(3) : 1, selected ? 0xFFFFFFFF : 0x44FFFFFF));
            }
        }

        // 2. Update Palette Swatches selection border
        for (View swatch : mPaletteSwatchViews) {
            int col = (Integer) swatch.getTag();
            boolean selected = ((col & 0x00FFFFFF) == (mConfig.globalColor & 0x00FFFFFF));
            swatch.setBackground(roundRect(col, dp(19), selected ? dp(3) : 1, selected ? 0xFFFFFFFF : 0x44FFFFFF));
        }

        // 3. Update Wallpaper Variant rows
        int[] wallColors = new int[]{
                mWallpaperPalette != null ? mWallpaperPalette.primaryColor : mConfig.cachedWallpaperPrimary,
                mWallpaperPalette != null ? mWallpaperPalette.secondaryColor : mConfig.cachedWallpaperSecondary,
                mWallpaperPalette != null ? mWallpaperPalette.monetAccentColor : mConfig.cachedWallpaperAccent,
                mWallpaperPalette != null ? mWallpaperPalette.bottomAreaColor : mConfig.cachedWallpaperBottom
        };
        for (int i = 0; i < mWallpaperVariantCards.size(); i++) {
            LinearLayout row = mWallpaperVariantCards.get(i);
            boolean sel = (mConfig.wallpaperVariant == i);
            row.setBackground(roundRect(
                    sel ? 0xFF353538 : 0xFF232325,
                    dp(14),
                    sel ? 2 : 1,
                    sel ? 0xFF1477FF : 0xFF38383B
            ));
            View dot = row.findViewWithTag("dot");
            if (dot != null) {
                dot.setBackground(roundRect(wallColors[i] | 0xFF000000, dp(17), 2, 0x88FFFFFF));
            }
        }

        // 4. Update Style Buttons (Solid vs Blur)
        boolean isBlur = (mConfig.styleMode == ConfigManager.STYLE_BLUR);
        styleSelectableButton(mBtnStyleSolid, !isBlur);
        styleSelectableButton(mBtnStyleBlur, isBlur);

        // 5. Rebuild Category Catalog rows & Preview
        rebuildCategoriesList();
        refreshPreviewOnly();
    }

    private void styleSelectableButton(Button btn, boolean selected) {
        if (btn == null) return;
        btn.setTextColor(selected ? 0xFFFFFFFF : 0xFF9AA0B4);
        btn.setTypeface(Typeface.create("sans-serif-medium", selected ? Typeface.BOLD : Typeface.NORMAL));
        btn.setBackground(roundRect(
                selected ? 0xFF1477FF : 0xFF232325,
                dp(14),
                1,
                selected ? 0xFF3367B8 : 0xFF38383B
        ));
    }

    private LinearLayout createSectionCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(roundRect(0xFF2B2B2D, dp(26), 1, 0xFF38383B));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        card.setLayoutParams(lp);
        return card;
    }

    private GradientDrawable roundRect(int fillColor, int radiusPx, int strokeWidthPx, int strokeColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(fillColor);
        gd.setCornerRadius(radiusPx);
        if (strokeWidthPx > 0) {
            gd.setStroke(strokeWidthPx, strokeColor);
        }
        return gd;
    }

    private int shiftAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private int dp(float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }
}
