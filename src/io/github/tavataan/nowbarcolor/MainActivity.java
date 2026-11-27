package io.github.tavataan.nowbarcolor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private CapsulePreviewStage mLockscreenStage;
    private TextView mPreviewModeTag;

    private Button mBtnFixedMode;
    private Button mBtnWallpaperMode;
    private Button mBtnGradientMode;
    private Button mBtnImageMode;
    private LinearLayout mFixedPaletteContainer;
    private LinearLayout mWallpaperModeContainer;
    private LinearLayout mGradientModeContainer;
    private LinearLayout mImageModeContainer;
    private CapsuleImagePanelHelper.PanelController mGlobalImagePanel;
    private final List<View> mPaletteSwatchViews = new ArrayList<>();
    private final List<View> mGradientPaletteSwatchViews = new ArrayList<>();
    private final List<LinearLayout> mWallpaperVariantCards = new ArrayList<>();

    // Global gradient subtabs
    private LinearLayout mBtnGlobalGradStart;
    private LinearLayout mBtnGlobalGradEnd;
    private View mDotGlobalGradStart;
    private View mDotGlobalGradEnd;
    private boolean mGlobalGradEditingEnd = false; // false = Primer color, true = Color final

    private TextView mFinishCardTitle;
    private LinearLayout mColorFinishControlsContainer;
    private LinearLayout mImageFinishControlsContainer;
    private Button mBtnStyleSolid;
    private Button mBtnStyleBlur;
    private TextView mOpacityTitle;
    private TextView mFinishDescription;
    private SeekBar mOpacitySeekBar;
    private TextView mOpacityValueText;
    private LinearLayout mBlurTintContainer;
    private SeekBar mBlurTintSeekBar;
    private TextView mBlurTintValueText;
    private boolean mUpdatingSliders = false;
    private OneUISwitch mHideBgAodSwitch;

    private static final int REQ_PICK_CAPSULE_IMAGE = 7001;
    private final Map<String, Bitmap> mUiImageBitmaps = new HashMap<>();
    private final List<String> mUiImageLoadsInFlight = new ArrayList<>();
    private String mPendingPickSlot;
    private CapsuleImagePanelHelper.HostCallbacks mPendingPickCallbacks;

    private static final int TAB_GLOBAL = 0;
    private static final int TAB_INDIVIDUALS = 1;
    private static final String STATE_CURRENT_TAB = "state_current_tab";
    private static final PathInterpolator TAB_SWITCH_EASING =
            new PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f);

    private LinearLayout mCategoriesListContainer;
    private String mExpandedCategoryId = null;

    private ScrollView mGlobalScroll;
    private ScrollView mIndividualsScroll;
    private LinearLayout mGlobalCol;
    private LinearLayout mIndividualsCol;
    private OneUIFloatingBar mFloatingBar;
    private int mCurrentTab = TAB_GLOBAL;

    private final ExecutorService mScanExecutor = Executors.newSingleThreadExecutor();
    private TextView mCompatTitleText;
    private TextView mCompatDetailText;
    private volatile HookMap mCachedHookMap;
    private volatile String mLastScanError;
    private volatile boolean mScanningAod = false;

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
        if (mGlobalCol == null || mIndividualsCol == null) return;
        populateGlobalTab();
        populateIndividualsTab();
        if (mFloatingBar != null) {
            mFloatingBar.updateContentDescriptions(
                    I18n.get(this, "tab_global_cd"),
                    I18n.get(this, "tab_individual_cd"));
            mFloatingBar.setSelectedTab(mCurrentTab, false);
        }
        switchToTab(mCurrentTab, false);
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

        if (savedInstanceState != null) {
            mCurrentTab = savedInstanceState.getInt(STATE_CURRENT_TAB, TAB_GLOBAL);
        }

        mConfig = ConfigManager.loadFromAppPrefs(this);
        mWallpaperPalette = WallpaperColorHelper.extractLockWallpaperPalette(this, true);
        updateCachedWallpaperInConfig();

        final int padH = dp(18);
        final int baseGlobalTop = dp(16);
        final int baseIndividualsTop = dp(28);
        final int baseBottomScrollPad = dp(104);
        final int baseBarBottomMargin = dp(18);
        final int baseBottomScrimH = dp(96);

        FrameLayout rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(0xFF000000);

        mGlobalScroll = new ScrollView(this);
        mGlobalScroll.setFillViewport(true);
        mGlobalScroll.setBackgroundColor(0xFF000000);
        mGlobalCol = new LinearLayout(this);
        mGlobalCol.setOrientation(LinearLayout.VERTICAL);
        mGlobalCol.setPadding(padH, baseGlobalTop, padH, baseBottomScrollPad);
        mGlobalScroll.addView(mGlobalCol);
        rootFrame.addView(mGlobalScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mIndividualsScroll = new ScrollView(this);
        mIndividualsScroll.setFillViewport(true);
        mIndividualsScroll.setBackgroundColor(0xFF000000);
        mIndividualsCol = new LinearLayout(this);
        mIndividualsCol.setOrientation(LinearLayout.VERTICAL);
        mIndividualsCol.setPadding(padH, baseIndividualsTop, padH, baseBottomScrollPad);
        mIndividualsScroll.addView(mIndividualsCol);
        rootFrame.addView(mIndividualsScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        populateGlobalTab();
        populateIndividualsTab();

        final View topScrim = new View(this);
        topScrim.setClickable(false);
        topScrim.setFocusable(false);
        topScrim.setVisibility(View.GONE);
        topScrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xD9000000, 0x00000000}));
        FrameLayout.LayoutParams topScrimLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40), Gravity.TOP);
        rootFrame.addView(topScrim, topScrimLp);

        final View bottomScrim = new View(this);
        bottomScrim.setClickable(false);
        bottomScrim.setFocusable(false);
        bottomScrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xCC000000}));
        FrameLayout.LayoutParams scrimLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, baseBottomScrimH, Gravity.BOTTOM);
        rootFrame.addView(bottomScrim, scrimLp);

        mFloatingBar = new OneUIFloatingBar(this);
        mFloatingBar.updateContentDescriptions(
                I18n.get(this, "tab_global_cd"),
                I18n.get(this, "tab_individual_cd"));
        mFloatingBar.setOnTabSelectedListener(new OneUIFloatingBar.OnTabSelectedListener() {
            @Override
            public void onTabSelected(int tabIndex) {
                switchToTab(tabIndex, true);
            }
        });
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        barLp.bottomMargin = baseBarBottomMargin;
        rootFrame.addView(mFloatingBar, barLp);

        rootFrame.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                Insets sys = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                mGlobalCol.setPadding(
                        padH + sys.left,
                        baseGlobalTop + sys.top,
                        padH + sys.right,
                        baseBottomScrollPad + sys.bottom);
                mIndividualsCol.setPadding(
                        padH + sys.left,
                        baseIndividualsTop + sys.top,
                        padH + sys.right,
                        baseBottomScrollPad + sys.bottom);

                FrameLayout.LayoutParams tLp =
                        (FrameLayout.LayoutParams) topScrim.getLayoutParams();
                if (sys.top > 0) {
                    topScrim.setVisibility(View.VISIBLE);
                    tLp.height = sys.top + dp(10);
                    topScrim.setLayoutParams(tLp);
                } else {
                    topScrim.setVisibility(View.GONE);
                }

                FrameLayout.LayoutParams bScrimLp =
                        (FrameLayout.LayoutParams) bottomScrim.getLayoutParams();
                bScrimLp.height = baseBottomScrimH + sys.bottom;
                bottomScrim.setLayoutParams(bScrimLp);

                FrameLayout.LayoutParams bLp =
                        (FrameLayout.LayoutParams) mFloatingBar.getLayoutParams();
                bLp.bottomMargin = baseBarBottomMargin + sys.bottom;
                mFloatingBar.setLayoutParams(bLp);

                return insets;
            }
        });

        mFloatingBar.setSelectedTab(mCurrentTab, false);
        switchToTab(mCurrentTab, false);

        setContentView(rootFrame);
        rootFrame.requestApplyInsets();
        refreshEntireUi(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_CURRENT_TAB, mCurrentTab);
    }

    private void launchImagePicker(String slot, CapsuleImagePanelHelper.HostCallbacks cb) {
        mPendingPickSlot = slot;
        mPendingPickCallbacks = cb;
        Intent pickIntent = null;
        if (Build.VERSION.SDK_INT >= 33) {
            pickIntent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            pickIntent.setType("image/*");
        }
        if (pickIntent != null) {
            try {
                startActivityForResult(pickIntent, REQ_PICK_CAPSULE_IMAGE);
                return;
            } catch (Throwable ignored) {}
        }
        Intent docIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        docIntent.addCategory(Intent.CATEGORY_OPENABLE);
        docIntent.setType("image/*");
        try {
            startActivityForResult(docIntent, REQ_PICK_CAPSULE_IMAGE);
        } catch (Throwable ignored) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.setType("image/*");
            try {
                startActivityForResult(fallback, REQ_PICK_CAPSULE_IMAGE);
            } catch (Throwable t) {
                Toast.makeText(this, I18n.get(this, "crop_decoding_err"), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_CAPSULE_IMAGE && resultCode == RESULT_OK
                && data != null && data.getData() != null && mPendingPickCallbacks != null) {
            final Uri uri = data.getData();
            final String slot = mPendingPickSlot != null ? mPendingPickSlot : CapsuleImageProcessor.slotForGlobal();
            final CapsuleImagePanelHelper.HostCallbacks cb = mPendingPickCallbacks;
            CapsuleCropDialog.showForUri(
                    this, uri, slot,
                    cb.getStyle(), cb.getBlurPercent(), cb.getDuotonePreset(),
                    cb.getWallPrimary(), cb.getWallAccent(),
                    mScanExecutor,
                    new CapsuleCropDialog.Callback() {
                        @Override
                        public void onBaked(CapsuleImageProcessor.BakeResult result) {
                            cb.onBaked(result);
                        }
                    });
        }
    }

    private void cacheBakedBitmapInUi(String slot, long rev, Bitmap bmp) {
        if (slot == null || rev <= 0L || bmp == null) return;
        String prefix = slot + "@";
        Iterator<String> it = mUiImageBitmaps.keySet().iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(prefix)) it.remove();
        }
        mUiImageBitmaps.put(prefix + rev, bmp);
    }

    private Bitmap resolveBakedBitmapForUi(final String slot, final long rev, final Runnable onLoaded) {
        if (slot == null || rev <= 0L) return null;
        final String key = slot + "@" + rev;
        Bitmap cached = mUiImageBitmaps.get(key);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        if (mUiImageLoadsInFlight.contains(key)) {
            return null;
        }
        mUiImageLoadsInFlight.add(key);
        mScanExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap loaded = CapsuleImageProcessor.loadBakedBitmap(MainActivity.this, slot);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mUiImageLoadsInFlight.remove(key);
                        if (loaded != null) {
                            cacheBakedBitmapInUi(slot, rev, loaded);
                            if (onLoaded != null) onLoaded.run();
                        }
                    }
                });
            }
        });
        return null;
    }

    private int activeWallpaperPrimary() {
        return mWallpaperPalette != null ? mWallpaperPalette.primaryColor : mConfig.cachedWallpaperPrimary;
    }

    private int activeWallpaperAccent() {
        return mWallpaperPalette != null ? mWallpaperPalette.monetAccentColor : mConfig.cachedWallpaperAccent;
    }

    private CapsuleImagePanelHelper.HostCallbacks createGlobalImageCallbacks() {
        return new CapsuleImagePanelHelper.HostCallbacks() {
            @Override public int getStyle() { return mConfig.imageStyle; }
            @Override public void setStyle(int style) {
                mConfig.imageStyle = ConfigManager.clampImageStyle(style);
                refreshEntireUi(true);
            }
            @Override public int getBlurPercent() { return mConfig.imageBlurPercent; }
            @Override public void setBlurPercent(int blurPercent) { mConfig.imageBlurPercent = ConfigManager.clampImageBlurPercent(blurPercent); }
            @Override public int getScrimPercent() { return mConfig.imageScrimPercent; }
            @Override public void setScrimPercent(int scrimPercent) { mConfig.imageScrimPercent = ConfigManager.clampImageScrimPercent(scrimPercent); }
            @Override public int getScrimMode() { return mConfig.imageScrimMode; }
            @Override public void setScrimMode(int scrimMode) { mConfig.imageScrimMode = ConfigManager.clampScrimMode(scrimMode); }
            @Override public int getDuotonePreset() { return mConfig.imageDuotonePreset; }
            @Override public void setDuotonePreset(int preset) {
                mConfig.imageDuotonePreset = ConfigManager.clampDuotonePreset(preset);
                refreshEntireUi(true);
            }
            @Override public long getImageRev() { return mConfig.imageRev; }
            @Override public int getWallPrimary() { return activeWallpaperPrimary(); }
            @Override public int getWallAccent() { return activeWallpaperAccent(); }
            @Override
            public void onBaked(CapsuleImageProcessor.BakeResult result) {
                if (result == null) return;
                mConfig.colorMode = ConfigManager.MODE_IMAGE;
                mConfig.imageDomColor = result.dominantColor;
                mConfig.imageLuma = result.avgLuma;
                mConfig.imageRev = result.revision;
                cacheBakedBitmapInUi(CapsuleImageProcessor.slotForGlobal(), result.revision, result.bitmap);
                refreshEntireUi(true);
            }
            @Override
            public void onScrimChanged(boolean liveOnly) {
                if (liveOnly) refreshPreviewOnly();
                else refreshEntireUi(true);
            }
            @Override
            public void onRequestPickPhoto() {
                launchImagePicker(CapsuleImageProcessor.slotForGlobal(), this);
            }
        };
    }

    private void populateGlobalTab() {
        mGlobalCol.removeAllViews();
        mGlobalCol.addView(buildHeaderSection());
        mGlobalCol.addView(buildMasterSwitchCard());
        mGlobalCol.addView(buildCompatibilityCard());
        mGlobalCol.addView(buildLivePreviewCard());
        mGlobalCol.addView(buildColorSelectionCard());
        mGlobalCol.addView(buildStyleAndOpacityCard());
        updateCompatibilityUi();
    }

    private View buildCompatibilityCard() {
        LinearLayout card = createSectionCard();
        card.setPadding(dp(16), dp(12), dp(16), dp(12));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        mCompatTitleText = new TextView(this);
        mCompatTitleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        mCompatTitleText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        mCompatTitleText.setTextColor(0xFFFFFFFF);
        textCol.addView(mCompatTitleText);

        mCompatDetailText = new TextView(this);
        mCompatDetailText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        mCompatDetailText.setTextColor(0xFF9AA0B4);
        mCompatDetailText.setPadding(0, dp(2), 0, 0);
        textCol.addView(mCompatDetailText);

        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView diagBtn = new TextView(this);
        diagBtn.setText(I18n.get(this, "export_diag_btn"));
        diagBtn.setTextColor(0xFF82B1FF);
        diagBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        diagBtn.setTypeface(Typeface.DEFAULT_BOLD);
        diagBtn.setPadding(dp(12), dp(7), dp(12), dp(7));
        diagBtn.setBackground(roundRect(0x22448AFF, dp(16), 1, 0x55448AFF));
        diagBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDiagnosticsDialog();
            }
        });
        row.addView(diagBtn);

        card.addView(row);
        return card;
    }

    private void ensureAodHooksCached(final boolean forceRescan) {
        mScanExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    PackageInfo pi = getPackageManager().getPackageInfo(AodScanner.AOD_PKG, 0);
                    HookMap cached = HookMap.loadFromApp(MainActivity.this);
                    if (!forceRescan && cached != null
                            && cached.matchesTarget(pi.getLongVersionCode(), pi.lastUpdateTime)) {
                        mCachedHookMap = cached;
                        mLastScanError = null;
                        mScanningAod = false;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateCompatibilityUi();
                            }
                        });
                        return;
                    }
                } catch (Throwable ignored) {}

                mScanningAod = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateCompatibilityUi();
                    }
                });

                AodScanner.ScanResult res = AodScanner.scanInstalledAod(MainActivity.this);
                if (res.map != null) {
                    HookMap.saveFromApp(MainActivity.this, res.map);
                    mCachedHookMap = res.map;
                }
                mLastScanError = res.error;
                mScanningAod = false;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateCompatibilityUi();
                    }
                });
            }
        });
    }

    private void updateCompatibilityUi() {
        if (mCompatTitleText == null || mCompatDetailText == null) return;
        if (mScanningAod) {
            mCompatTitleText.setText(I18n.get(this, "aod_compat_scanning"));
            mCompatTitleText.setTextColor(0xFF82B1FF);
            mCompatDetailText.setText("DexKit");
            return;
        }
        if (mLastScanError != null) {
            mCompatTitleText.setText(I18n.get(this, "aod_compat_error"));
            mCompatTitleText.setTextColor(0xFFFF8A80);
            mCompatDetailText.setText(mLastScanError);
            return;
        }
        HookMap map = mCachedHookMap != null ? mCachedHookMap : HookMap.loadFromApp(this);
        if (map == null) {
            mCompatTitleText.setText(I18n.get(this, "aod_compat_scanning"));
            mCompatTitleText.setTextColor(0xFF9AA0B4);
            mCompatDetailText.setText("-");
            return;
        }
        int resolved = map.countResolvedSlots();
        int total = map.totalSlotCount();
        if (map.hasCoreHooks()) {
            mCompatTitleText.setText(I18n.get(this, "aod_compat_ok"));
            mCompatTitleText.setTextColor(0xFF69F0AE);
        } else {
            mCompatTitleText.setText(I18n.get(this, "aod_compat_partial"));
            mCompatTitleText.setTextColor(0xFFFFD54F);
        }
        mCompatDetailText.setText(map.aodVersionName + " (" + map.aodVersionCode + ") · " + resolved + "/" + total);
    }

    private void showDiagnosticsDialog() {
        final String report = buildDiagnosticReport();
        TextView body = new TextView(this);
        body.setText(report);
        body.setTextColor(0xFFE0E0E0);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        body.setTypeface(Typeface.MONOSPACE);
        body.setPadding(dp(18), dp(12), dp(18), dp(12));
        body.setTextIsSelectable(true);

        ScrollView sc = new ScrollView(this);
        sc.addView(body);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(I18n.get(this, "diag_dialog_title"))
                .setView(sc)
                .setPositiveButton(I18n.get(this, "copy_btn"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("NowBarColor Diagnostics", report));
                            Toast.makeText(MainActivity.this, I18n.get(MainActivity.this, "diag_copied_toast"), Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNeutralButton(I18n.get(this, "rescan_btn"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ensureAodHooksCached(true);
                    }
                })
                .setNegativeButton(I18n.get(this, "cancel"), null)
                .show();
    }

    private String buildDiagnosticReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== NowBarColor Diagnostics ===\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Module Active (LSPosed): ").append(isModuleActiveInLsposed()).append('\n');
        sb.append(describePkg("AOD_Service", AodScanner.AOD_PKG));
        sb.append(describePkg("SystemUI", "com.android.systemui"));
        sb.append('\n');

        HookMap map = mCachedHookMap != null ? mCachedHookMap : HookMap.loadFromApp(this);
        if (map != null) {
            sb.append(map.formatDiagnosticsLines());
        } else {
            sb.append("HookMap: <none> (error=").append(mLastScanError).append(")\n");
        }

        String treeDump = readSysUiDiagnosticsDump();
        if (!treeDump.isEmpty()) {
            sb.append('\n').append(treeDump);
        } else {
            sb.append("\n=== SystemUI Runtime Status ===\n<No SystemUI report received yet>\n");
            sb.append("\n=== Live NowBar View Tree ===\n<No lockscreen card captured yet>");
        }
        return sb.toString();
    }

    private String describePkg(String label, String pkg) {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(pkg, 0);
            return label + ": " + pi.versionName + " (" + pi.getLongVersionCode() + ")\n";
        } catch (Throwable t) {
            return label + ": <unavailable: " + t.getClass().getSimpleName() + ">\n";
        }
    }

    private String readSysUiDiagnosticsDump() {
        try {
            return getSharedPreferences(HookMap.PREF_NAME, Context.MODE_PRIVATE)
                    .getString("sysui_diag_dump", "");
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void populateIndividualsTab() {
        mIndividualsCol.removeAllViews();
        mIndividualsCol.addView(buildIndividualsHeaderSection());
        mIndividualsCol.addView(buildCategoriesCatalogCard());
    }

    private View buildIndividualsHeaderSection() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(4), dp(4), dp(4), dp(14));

        TextView title = new TextView(this);
        title.setText(I18n.get(this, "sec_catalog_title"));
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setLineSpacing(dp(2), 1.08f);
        box.addView(title);

        return box;
    }

    private void switchToTab(int tabIndex, boolean animate) {
        final int nextTab = (tabIndex == TAB_INDIVIDUALS) ? TAB_INDIVIDUALS : TAB_GLOBAL;
        final View incoming = (nextTab == TAB_INDIVIDUALS) ? mIndividualsScroll : mGlobalScroll;
        final View outgoing = (nextTab == TAB_INDIVIDUALS) ? mGlobalScroll : mIndividualsScroll;
        boolean changed = (mCurrentTab != nextTab);
        mCurrentTab = nextTab;

        updatePreviewAnimationState();

        if (incoming == null || outgoing == null) return;

        incoming.animate().setListener(null);
        outgoing.animate().setListener(null);
        incoming.animate().cancel();
        outgoing.animate().cancel();

        if (!animate || !changed) {
            incoming.setVisibility(View.VISIBLE);
            incoming.setAlpha(1f);
            incoming.setTranslationX(0f);
            outgoing.setVisibility(View.GONE);
            outgoing.setAlpha(1f);
            outgoing.setTranslationX(0f);
            return;
        }

        final float shift = dp(14) * (nextTab == TAB_INDIVIDUALS ? 1f : -1f);

        outgoing.setVisibility(View.VISIBLE);
        outgoing.animate()
                .alpha(0f)
                .translationX(-shift * 0.5f)
                .setDuration(150L)
                .setInterpolator(TAB_SWITCH_EASING)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (mCurrentTab == nextTab) {
                            outgoing.setVisibility(View.GONE);
                            outgoing.setAlpha(1f);
                            outgoing.setTranslationX(0f);
                        }
                    }
                })
                .start();

        incoming.setVisibility(View.VISIBLE);
        incoming.setAlpha(0f);
        incoming.setTranslationX(shift);
        incoming.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(220L)
                .setInterpolator(TAB_SWITCH_EASING)
                .setListener(null)
                .start();
    }

    private void updatePreviewAnimationState() {
        if (mPreviewMediaCapsule == null) return;
        if (mCurrentTab == TAB_GLOBAL) {
            mPreviewMediaCapsule.startWaveAnimation();
        } else {
            mPreviewMediaCapsule.stopWaveAnimation();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePreviewAnimationState();
        ensureAodHooksCached(false);
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
        mLockscreenStage = new CapsulePreviewStage(this, resolveWallpaperBottomColor());
        mLockscreenStage.setWillNotDraw(false);
        LinearLayout.LayoutParams stageLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stageLp.topMargin = dp(10);
        mLockscreenStage.setMinimumHeight(dp(120));
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
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(t);

        TextView s = new TextView(this);
        s.setText(I18n.get(this, "enable_sub"));
        s.setTextColor(0xFF9AA0B4);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        s.setMaxLines(3);
        s.setEllipsize(TextUtils.TruncateAt.END);
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

        // Top side-by-side buttons: [ Color ] | [ Wallpaper ] | [ Degradado ] | [ Imagen ]
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams modeRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        modeRowLp.topMargin = dp(10);
        modeRowLp.bottomMargin = dp(14);

        boolean isCompact = isCompactScreen();
        float tabSp = isCompact ? 10.5f : 11.5f;

        mBtnFixedMode = new Button(this);
        mBtnFixedMode.setAllCaps(false);
        mBtnFixedMode.setText(I18n.get(this, "tab_solid"));
        mBtnFixedMode.setTextSize(TypedValue.COMPLEX_UNIT_SP, tabSp);
        mBtnFixedMode.setMinimumHeight(dp(42));
        mBtnFixedMode.setPadding(dp(3), dp(6), dp(3), dp(6));
        mBtnFixedMode.setMaxLines(2);
        mBtnFixedMode.setEllipsize(TextUtils.TruncateAt.END);
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
        mBtnWallpaperMode.setTextSize(TypedValue.COMPLEX_UNIT_SP, isCompact ? 10f : 11f);
        mBtnWallpaperMode.setMinimumHeight(dp(42));
        mBtnWallpaperMode.setPadding(dp(3), dp(6), dp(3), dp(6));
        mBtnWallpaperMode.setMaxLines(2);
        mBtnWallpaperMode.setEllipsize(TextUtils.TruncateAt.END);
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
        mBtnGradientMode.setTextSize(TypedValue.COMPLEX_UNIT_SP, tabSp);
        mBtnGradientMode.setMinimumHeight(dp(42));
        mBtnGradientMode.setPadding(dp(3), dp(6), dp(3), dp(6));
        mBtnGradientMode.setMaxLines(2);
        mBtnGradientMode.setEllipsize(TextUtils.TruncateAt.END);
        mBtnGradientMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mConfig.colorMode = ConfigManager.MODE_GRADIENT;
                refreshEntireUi(true);
            }
        });

        View spacer3 = new View(this);

        mBtnImageMode = new Button(this);
        mBtnImageMode.setAllCaps(false);
        mBtnImageMode.setText(I18n.get(this, "tab_image"));
        mBtnImageMode.setTextSize(TypedValue.COMPLEX_UNIT_SP, tabSp);
        mBtnImageMode.setMinimumHeight(dp(42));
        mBtnImageMode.setPadding(dp(3), dp(6), dp(3), dp(6));
        mBtnImageMode.setMaxLines(2);
        mBtnImageMode.setEllipsize(TextUtils.TruncateAt.END);
        mBtnImageMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mConfig.colorMode = ConfigManager.MODE_IMAGE;
                refreshEntireUi(true);
            }
        });

        modeRow.addView(mBtnFixedMode, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        modeRow.addView(spacer1, new LinearLayout.LayoutParams(dp(4), 1));
        modeRow.addView(mBtnWallpaperMode, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f));
        modeRow.addView(spacer2, new LinearLayout.LayoutParams(dp(4), 1));
        modeRow.addView(mBtnGradientMode, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        modeRow.addView(spacer3, new LinearLayout.LayoutParams(dp(4), 1));
        modeRow.addView(mBtnImageMode, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f));
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
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stlp.bottomMargin = dp(12);

        mBtnGlobalGradStart = new LinearLayout(this);
        mBtnGlobalGradStart.setOrientation(LinearLayout.HORIZONTAL);
        mBtnGlobalGradStart.setGravity(Gravity.CENTER);
        mBtnGlobalGradStart.setMinimumHeight(dp(40));
        mBtnGlobalGradStart.setPadding(dp(8), dp(6), dp(8), dp(6));
        mDotGlobalGradStart = new View(this);
        mBtnGlobalGradStart.addView(mDotGlobalGradStart, new LinearLayout.LayoutParams(dp(14), dp(14)));
        TextView txtGradStart = new TextView(this);
        txtGradStart.setText(" " + I18n.get(this, "grad_color_start"));
        txtGradStart.setTextColor(0xFFFFFFFF);
        txtGradStart.setTextSize(TypedValue.COMPLEX_UNIT_SP, isCompact ? 11.5f : 12.5f);
        txtGradStart.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        txtGradStart.setSingleLine(true);
        txtGradStart.setEllipsize(TextUtils.TruncateAt.END);
        mBtnGlobalGradStart.addView(txtGradStart);

        View subGap = new View(this);

        mBtnGlobalGradEnd = new LinearLayout(this);
        mBtnGlobalGradEnd.setOrientation(LinearLayout.HORIZONTAL);
        mBtnGlobalGradEnd.setGravity(Gravity.CENTER);
        mBtnGlobalGradEnd.setMinimumHeight(dp(40));
        mBtnGlobalGradEnd.setPadding(dp(8), dp(6), dp(8), dp(6));
        mDotGlobalGradEnd = new View(this);
        mBtnGlobalGradEnd.addView(mDotGlobalGradEnd, new LinearLayout.LayoutParams(dp(14), dp(14)));
        TextView txtGradEnd = new TextView(this);
        txtGradEnd.setText(" " + I18n.get(this, "grad_color_end"));
        txtGradEnd.setTextColor(0xFFFFFFFF);
        txtGradEnd.setTextSize(TypedValue.COMPLEX_UNIT_SP, isCompact ? 11.5f : 12.5f);
        txtGradEnd.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        txtGradEnd.setSingleLine(true);
        txtGradEnd.setEllipsize(TextUtils.TruncateAt.END);
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

        subtabsRow.addView(mBtnGlobalGradStart, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        subtabsRow.addView(subGap, new LinearLayout.LayoutParams(dp(8), 1));
        subtabsRow.addView(mBtnGlobalGradEnd, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
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

        // Container D: Global Custom Photo & Artistic Styles
        mGlobalImagePanel = CapsuleImagePanelHelper.createController(
                this, CapsuleImageProcessor.slotForGlobal(), mScanExecutor, createGlobalImageCallbacks());
        mImageModeContainer = CapsuleImagePanelHelper.buildSelectorSection(this, mGlobalImagePanel);
        mImageModeContainer.setVisibility(View.GONE);
        card.addView(mImageModeContainer);

        return card;
    }

    private View buildStyleAndOpacityCard() {
        LinearLayout card = createSectionCard();

        TextView secTitle = new TextView(this);
        mFinishCardTitle = secTitle;
        secTitle.setText(I18n.get(this, "sec_finish_opacity"));
        secTitle.setTextColor(0xFF82B1FF);
        secTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        secTitle.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(secTitle);

        mColorFinishControlsContainer = new LinearLayout(this);
        mColorFinishControlsContainer.setOrientation(LinearLayout.VERTICAL);

        LinearLayout styleRow = new LinearLayout(this);
        styleRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams srlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        srlp.topMargin = dp(10);
        srlp.bottomMargin = dp(16);

        boolean isCompact = isCompactScreen();

        mBtnStyleSolid = new Button(this);
        mBtnStyleSolid.setAllCaps(false);
        mBtnStyleSolid.setText(I18n.get(this, "finish_solid_title"));
        mBtnStyleSolid.setTextSize(TypedValue.COMPLEX_UNIT_SP, isCompact ? 12f : 13f);
        mBtnStyleSolid.setMinimumHeight(dp(42));
        mBtnStyleSolid.setPadding(dp(6), dp(6), dp(6), dp(6));
        mBtnStyleSolid.setMaxLines(2);
        mBtnStyleSolid.setEllipsize(TextUtils.TruncateAt.END);
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
        mBtnStyleBlur.setTextSize(TypedValue.COMPLEX_UNIT_SP, isCompact ? 12f : 13f);
        mBtnStyleBlur.setMinimumHeight(dp(42));
        mBtnStyleBlur.setPadding(dp(6), dp(6), dp(6), dp(6));
        mBtnStyleBlur.setMaxLines(2);
        mBtnStyleBlur.setEllipsize(TextUtils.TruncateAt.END);
        mBtnStyleBlur.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mConfig.styleMode = ConfigManager.STYLE_BLUR;
                refreshEntireUi(true);
            }
        });

        styleRow.addView(mBtnStyleSolid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        styleRow.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        styleRow.addView(mBtnStyleBlur, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mColorFinishControlsContainer.addView(styleRow, srlp);

        // Primary Opacity / Blur Intensity Header Row
        boolean initialBlur = (mConfig.styleMode == ConfigManager.STYLE_BLUR);
        LinearLayout opHeader = new LinearLayout(this);
        opHeader.setOrientation(LinearLayout.HORIZONTAL);
        opHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView opTitle = new TextView(this);
        mOpacityTitle = opTitle;
        opTitle.setText(I18n.get(this, initialBlur ? "tint_strength_title" : "opacity_title"));
        opTitle.setTextColor(0xFFFFFFFF);
        opTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        opHeader.addView(opTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        mOpacityValueText = new TextView(this);
        mOpacityValueText.setText(formatPrimarySliderValue(initialBlur));
        mOpacityValueText.setTextColor(0xFF82B1FF);
        mOpacityValueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        mOpacityValueText.setTypeface(Typeface.DEFAULT_BOLD);
        opHeader.addView(mOpacityValueText);
        mColorFinishControlsContainer.addView(opHeader);

        mOpacitySeekBar = new SeekBar(this);
        mOpacitySeekBar.setMax(100);
        mOpacitySeekBar.setProgress(initialBlur ? mConfig.blurIntensity : mConfig.opacity);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sbLp.topMargin = dp(6);
        sbLp.bottomMargin = dp(8);
        mOpacitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mUpdatingSliders) return;
                boolean isBlurMode = (mConfig.styleMode == ConfigManager.STYLE_BLUR);
                if (isBlurMode) {
                    mConfig.blurIntensity = OneUiBlurSpec.clampBlurIntensity(progress);
                } else {
                    mConfig.opacity = progress;
                }
                mOpacityValueText.setText(formatPrimarySliderValue(isBlurMode));
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
        mColorFinishControlsContainer.addView(mOpacitySeekBar, sbLp);

        // Compact Second Slider: Tint Opacity (25%..85%, default 60%) for Color Blur mode
        mBlurTintContainer = new LinearLayout(this);
        mBlurTintContainer.setOrientation(LinearLayout.VERTICAL);
        mBlurTintContainer.setVisibility(initialBlur ? View.VISIBLE : View.GONE);

        LinearLayout tintHeader = new LinearLayout(this);
        tintHeader.setOrientation(LinearLayout.HORIZONTAL);
        tintHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams thLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        thLp.topMargin = dp(2);

        TextView tintTitle = new TextView(this);
        tintTitle.setText(I18n.get(this, "blur_tint_opacity_title"));
        tintTitle.setTextColor(0xFFE2E6F0);
        tintTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        tintHeader.addView(tintTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        mBlurTintValueText = new TextView(this);
        int clampedTint = OneUiBlurSpec.clampTintOpacity(mConfig.blurTintOpacity);
        mBlurTintValueText.setText(clampedTint + "%");
        mBlurTintValueText.setTextColor(0xFF82B1FF);
        mBlurTintValueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        mBlurTintValueText.setTypeface(Typeface.DEFAULT_BOLD);
        tintHeader.addView(mBlurTintValueText);
        mBlurTintContainer.addView(tintHeader, thLp);

        mBlurTintSeekBar = new SeekBar(this);
        mBlurTintSeekBar.setMax(OneUiBlurSpec.MAX_TINT_OPACITY - OneUiBlurSpec.MIN_TINT_OPACITY);
        mBlurTintSeekBar.setProgress(clampedTint - OneUiBlurSpec.MIN_TINT_OPACITY);
        LinearLayout.LayoutParams tsbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tsbLp.topMargin = dp(4);
        tsbLp.bottomMargin = dp(8);
        mBlurTintSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mUpdatingSliders) return;
                int tintOp = OneUiBlurSpec.clampTintOpacity(OneUiBlurSpec.MIN_TINT_OPACITY + progress);
                mConfig.blurTintOpacity = tintOp;
                if (mBlurTintValueText != null) {
                    mBlurTintValueText.setText(tintOp + "%");
                }
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
        mBlurTintContainer.addView(mBlurTintSeekBar, tsbLp);
        mColorFinishControlsContainer.addView(mBlurTintContainer);

        mFinishDescription = new TextView(this);
        mFinishDescription.setTextColor(0xFFB0B8C8);
        mFinishDescription.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        mFinishDescription.setText(I18n.get(this, initialBlur ? "finish_blur_desc" : "finish_solid_desc"));
        mColorFinishControlsContainer.addView(mFinishDescription);
        card.addView(mColorFinishControlsContainer);

        // Contextual Image Legibility & Glass Photo Blur controls (shown when MODE_IMAGE is selected)
        mImageFinishControlsContainer = CapsuleImagePanelHelper.buildLegibilitySection(
                this, mGlobalImagePanel, true);
        mImageFinishControlsContainer.setVisibility(View.GONE);
        card.addView(mImageFinishControlsContainer);

        View divider = new View(this);
        divider.setBackgroundColor(0xFF38383B);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        divLp.topMargin = dp(14);
        divLp.bottomMargin = dp(12);
        card.addView(divider, divLp);

        LinearLayout aodRow = new LinearLayout(this);
        aodRow.setOrientation(LinearLayout.HORIZONTAL);
        aodRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout aodTextCol = new LinearLayout(this);
        aodTextCol.setOrientation(LinearLayout.VERTICAL);
        aodTextCol.setPadding(0, 0, dp(12), 0);

        TextView aodTitle = new TextView(this);
        aodTitle.setText(I18n.get(this, "hide_bg_aod_title"));
        aodTitle.setTextColor(0xFFFFFFFF);
        aodTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, isCompact ? 13.5f : 14f);
        aodTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        aodTextCol.addView(aodTitle);

        TextView aodDesc = new TextView(this);
        aodDesc.setText(I18n.get(this, "hide_bg_aod_desc"));
        aodDesc.setTextColor(0xFF9AA0B4);
        aodDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        aodDesc.setPadding(0, dp(3), 0, 0);
        aodTextCol.addView(aodDesc);

        aodRow.addView(aodTextCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        mHideBgAodSwitch = new OneUISwitch(this);
        mHideBgAodSwitch.setChecked(mConfig.hideBgOnAod);
        mHideBgAodSwitch.setOnCheckedChangeListener(new OneUISwitch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(OneUISwitch view, boolean isChecked) {
                mConfig.hideBgOnAod = isChecked;
                refreshEntireUi(true);
            }
        });
        aodRow.addView(mHideBgAodSwitch);

        aodRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !mConfig.hideBgOnAod;
                mConfig.hideBgOnAod = next;
                if (mHideBgAodSwitch != null) {
                    mHideBgAodSwitch.setChecked(next, true);
                }
                refreshEntireUi(true);
            }
        });

        card.addView(aodRow);

        return card;
    }

    private View buildCategoriesCatalogCard() {
        LinearLayout card = createSectionCard();

        TextView secSub = new TextView(this);
        secSub.setText(I18n.get(this, "sec_catalog_desc"));
        secSub.setTextColor(0xFF9AA0B4);
        secSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        secSub.setPadding(0, 0, 0, dp(12));
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

        boolean isCompact = isCompactScreen();

        for (int i = 0; i < ConfigManager.ALL_CATEGORIES.size(); i++) {
            final ConfigManager.CategoryMeta meta = ConfigManager.ALL_CATEGORIES.get(i);
            final ConfigManager.CategoryConfig cc = mConfig.getCategory(meta.id);
            final boolean isExpanded = meta.id.equals(mExpandedCategoryId);

            // 1. Root Island Container (Vertical container with pill rounded corners)
            LinearLayout rootIsland = new LinearLayout(this);
            rootIsland.setOrientation(LinearLayout.VERTICAL);
            rootIsland.setBackground(roundRect(
                    isExpanded ? 0xFF2A2A2E : 0xFF232325,
                    dp(24),
                    1,
                    isExpanded ? 0xFF1477FF : 0xFF38383B
            ));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i < ConfigManager.ALL_CATEGORIES.size() - 1) {
                rlp.bottomMargin = isCompact ? dp(8) : dp(10);
            }
            rootIsland.setLayoutParams(rlp);

            // 2. Main Row (Icon, Center Column [Title row + Chip row], Switch)
            LinearLayout mainRow = new LinearLayout(this);
            mainRow.setOrientation(LinearLayout.HORIZONTAL);
            mainRow.setGravity(Gravity.CENTER_VERTICAL);
            int padH = isCompact ? dp(10) : dp(14);
            int padV = isCompact ? dp(8) : dp(10);
            mainRow.setPadding(padH, padV, padH, padV);

            // Resolved category color for background and chip
            int resolvedRgb = mConfig.resolveBaseRgbForCategory(meta.id, liveWall);

            // Category Icon Badge (Scales adaptively: 42dp on compact, 48dp on normal)
            int iconSize = isCompact ? dp(42) : dp(48);
            FrameLayout iconWrap = createCategoryIconBadge(meta, cc, resolvedRgb, iconSize);
            mainRow.addView(iconWrap, new LinearLayout.LayoutParams(iconSize, iconSize));

            // Center Column (Vertical layout: Title on top, Chip underneath)
            LinearLayout centerCol = new LinearLayout(this);
            centerCol.setOrientation(LinearLayout.VERTICAL);
            centerCol.setPadding(dp(12), 0, dp(8), 0);

            // Row 1: Title + Chevron
            LinearLayout titleRow = new LinearLayout(this);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView title = new TextView(this);
            title.setText(meta.getTitle(this));
            title.setTextColor(cc.enabled ? 0xFFFFFFFF : 0xFF7D8597);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, isCompact ? 13f : 14f);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView arrowView = new TextView(this);
            arrowView.setText(isExpanded ? "▲" : "▼");
            arrowView.setTextColor(isExpanded ? 0xFF82B1FF : 0xFF7D8597);
            arrowView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
            arrowView.setPadding(dp(4), 0, dp(4), 0);
            titleRow.addView(arrowView);

            centerCol.addView(titleRow);

            // Row 2: Color Override Chip
            LinearLayout colorChip = new LinearLayout(this);
            colorChip.setOrientation(LinearLayout.HORIZONTAL);
            colorChip.setGravity(Gravity.CENTER_VERTICAL);
            colorChip.setPadding(dp(7), dp(4), dp(9), dp(4));
            colorChip.setBackground(roundRect(
                    cc.useCustomColor ? 0xFF263248 : 0xFF1E1E20,
                    dp(12),
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
                gd.setCornerRadius(dp(6));
                miniDot.setBackground(gd);
            } else if (isGlobGrad) {
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{mConfig.gradientStartColor | 0xFF000000, mConfig.gradientEndColor | 0xFF000000});
                gd.setCornerRadius(dp(6));
                miniDot.setBackground(gd);
            } else {
                miniDot.setBackground(roundRect(resolvedRgb | 0xFF000000, dp(6), 1, 0x66FFFFFF));
            }
            colorChip.addView(miniDot, new LinearLayout.LayoutParams(dp(12), dp(12)));

            TextView chipText = new TextView(this);
            String chipLabel;
            if (cc.useCustomColor) {
                int effCatOp = (mConfig.styleMode == ConfigManager.STYLE_BLUR)
                        ? OneUiBlurSpec.clampTintOpacity(cc.customOpacity)
                        : cc.customOpacity;
                if (cc.customColorMode == ConfigManager.MODE_GRADIENT) {
                    chipLabel = " " + I18n.get(this, "tab_gradient") + (cc.useCustomOpacity ? " • " + effCatOp + "%" : "");
                } else {
                    String curLang = I18n.getEffectiveLanguage(this);
                    String customWord = "es".equals(curLang) ? "Propio" : ("pt".equals(curLang) ? "Próprio" : "Custom");
                    chipLabel = " " + customWord + (cc.useCustomOpacity ? " • " + effCatOp + "%" : "");
                }
            } else {
                chipLabel = isGlobGrad ? (" Global • " + I18n.get(this, "tab_gradient")) : " Global";
            }
            chipText.setText(chipLabel);
            chipText.setTextColor(cc.useCustomColor ? 0xFF90CAF9 : 0xFFB0B8C8);
            chipText.setTextSize(TypedValue.COMPLEX_UNIT_SP, isCompact ? 10.5f : 11.5f);
            chipText.setTypeface(Typeface.DEFAULT_BOLD);
            chipText.setMaxLines(1);
            chipText.setEllipsize(TextUtils.TruncateAt.END);
            colorChip.addView(chipText);

            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipLp.topMargin = dp(4);
            colorChip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showIndividualCategoryColorPanel(meta, cc);
                }
            });
            centerCol.addView(colorChip, chipLp);

            mainRow.addView(centerCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

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
        final int[] selectedMode = new int[]{ cc.useCustomColor ? cc.customColorMode : mConfig.colorMode };
        final int[] selectedColor = new int[]{ cc.useCustomColor ? cc.customColor : initialGlobalRgb };
        final int[] gradStart = new int[]{ cc.useCustomColor ? cc.customGradientStart : mConfig.gradientStartColor };
        final int[] gradEnd = new int[]{ cc.useCustomColor ? cc.customGradientEnd : mConfig.gradientEndColor };
        final boolean[] gradEditingEnd = new boolean[]{ false };
        final boolean[] hasCustomOpacity = new boolean[]{ cc.useCustomOpacity };
        final boolean isBlurDialog = (mConfig.styleMode == ConfigManager.STYLE_BLUR);
        final int defaultDialogOpacity = isBlurDialog
                ? OneUiBlurSpec.clampTintOpacity(mConfig.blurTintOpacity)
                : mConfig.opacity;
        final int[] selectedOpacity = new int[]{ cc.useCustomOpacity
                ? (isBlurDialog ? OneUiBlurSpec.clampTintOpacity(cc.customOpacity) : cc.customOpacity)
                : defaultDialogOpacity };
        final int[] currentTab = new int[]{ cc.useCustomColor
                ? cc.customColorMode
                : (mConfig.colorMode == ConfigManager.MODE_IMAGE ? 3
                    : (mConfig.colorMode == ConfigManager.MODE_GRADIENT ? 2
                        : (mConfig.colorMode == ConfigManager.MODE_LOCK_WALLPAPER ? 1 : 0))) };

        final String catSlot = CapsuleImageProcessor.slotForCategory(meta.id);
        final long[] catImgRev = new long[]{ cc.customImageRev };
        final int[] catImgStyle = new int[]{ cc.customImageStyle };
        final int[] catImgBlurPct = new int[]{ cc.customImageBlurPercent };
        final int[] catImgScrimPct = new int[]{ cc.customImageScrimPercent };
        final int[] catImgScrimMode = new int[]{ cc.customImageScrimMode };
        final int[] catImgDuotone = new int[]{ cc.customImageDuotonePreset };
        final int[] catImgDomColor = new int[]{ cc.customImageDomColor };
        final int[] catImgLuma = new int[]{ cc.customImageLuma };

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
        boolean isCompact = isCompactScreen();
        int pad = isCompact ? dp(14) : dp(18);
        card.setPadding(pad, pad, pad, pad);
        scroll.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 1. Header Row (Category Icon + Title + Subtitle)
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        int iconSize = isCompact ? dp(42) : dp(48);
        int iconInnerSize = isCompact ? dp(24) : dp(28);
        final View headerIconBg = new View(this);
        headerIconBg.setBackground(roundRect(meta.accentColor, isCompact ? dp(12) : dp(14), dp(1), 0x33FFFFFF));
        FrameLayout iconWrap = new FrameLayout(this);
        iconWrap.addView(headerIconBg, new FrameLayout.LayoutParams(iconSize, iconSize));

        ImageView headerIconImg = new ImageView(this);
        int resId = meta.getIconResId(this);
        if (resId != 0) {
            headerIconImg.setImageResource(resId);
        }
        headerIconImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        headerIconImg.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        FrameLayout.LayoutParams imgLp = new FrameLayout.LayoutParams(iconInnerSize, iconInnerSize);
        imgLp.gravity = Gravity.CENTER;
        iconWrap.addView(headerIconImg, imgLp);

        titleRow.addView(iconWrap, new LinearLayout.LayoutParams(iconSize, iconSize));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setPadding(dp(12), 0, 0, 0);

        TextView titleTxt = new TextView(this);
        titleTxt.setText(meta.getTitle(this));
        titleTxt.setTextColor(0xFFFFFFFF);
        titleTxt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        titleTxt.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleTxt.setMaxLines(2);
        titleTxt.setEllipsize(TextUtils.TruncateAt.END);
        titleCol.addView(titleTxt);

        TextView subTxt = new TextView(this);
        subTxt.setText(I18n.get(this, "individual_color_title"));
        subTxt.setTextColor(0xFF82B1FF);
        subTxt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        subTxt.setMaxLines(2);
        subTxt.setEllipsize(TextUtils.TruncateAt.END);
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

        CapsulePreviewStage dialogStage = new CapsulePreviewStage(this, resolveWallpaperBottomColor());
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

        // 4. Mode Row Buttons [ Color ] | [ Wallpaper ] | [ Degradado ] | [ Imagen ]
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams modeRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        modeRowLp.bottomMargin = dp(12);
        float mTabSp = isCompact ? 10f : 11f;

        final Button btnTabFixed = new Button(this);
        btnTabFixed.setAllCaps(false);
        btnTabFixed.setText(I18n.get(this, "tab_solid"));
        btnTabFixed.setTextSize(TypedValue.COMPLEX_UNIT_SP, mTabSp);
        btnTabFixed.setMinimumHeight(dp(40));
        btnTabFixed.setPadding(dp(3), dp(6), dp(3), dp(6));
        btnTabFixed.setMaxLines(2);
        btnTabFixed.setEllipsize(TextUtils.TruncateAt.END);

        View tabSpacer1 = new View(this);

        final Button btnTabWallpaper = new Button(this);
        btnTabWallpaper.setAllCaps(false);
        btnTabWallpaper.setText(I18n.get(this, "tab_wallpaper"));
        btnTabWallpaper.setTextSize(TypedValue.COMPLEX_UNIT_SP, isCompact ? 9.5f : 10.5f);
        btnTabWallpaper.setMinimumHeight(dp(40));
        btnTabWallpaper.setPadding(dp(3), dp(6), dp(3), dp(6));
        btnTabWallpaper.setMaxLines(2);
        btnTabWallpaper.setEllipsize(TextUtils.TruncateAt.END);

        View tabSpacer2 = new View(this);

        final Button btnTabGradient = new Button(this);
        btnTabGradient.setAllCaps(false);
        btnTabGradient.setText(I18n.get(this, "tab_gradient"));
        btnTabGradient.setTextSize(TypedValue.COMPLEX_UNIT_SP, mTabSp);
        btnTabGradient.setMinimumHeight(dp(40));
        btnTabGradient.setPadding(dp(3), dp(6), dp(3), dp(6));
        btnTabGradient.setMaxLines(2);
        btnTabGradient.setEllipsize(TextUtils.TruncateAt.END);

        View tabSpacer3 = new View(this);

        final Button btnTabImage = new Button(this);
        btnTabImage.setAllCaps(false);
        btnTabImage.setText(I18n.get(this, "tab_image"));
        btnTabImage.setTextSize(TypedValue.COMPLEX_UNIT_SP, mTabSp);
        btnTabImage.setMinimumHeight(dp(40));
        btnTabImage.setPadding(dp(3), dp(6), dp(3), dp(6));
        btnTabImage.setMaxLines(2);
        btnTabImage.setEllipsize(TextUtils.TruncateAt.END);

        modeRow.addView(btnTabFixed, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        modeRow.addView(tabSpacer1, new LinearLayout.LayoutParams(dp(4), 1));
        modeRow.addView(btnTabWallpaper, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f));
        modeRow.addView(tabSpacer2, new LinearLayout.LayoutParams(dp(4), 1));
        modeRow.addView(btnTabGradient, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        modeRow.addView(tabSpacer3, new LinearLayout.LayoutParams(dp(4), 1));
        modeRow.addView(btnTabImage, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f));
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
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stlp.bottomMargin = dp(10);

        final LinearLayout btnDialogGradStart = new LinearLayout(this);
        btnDialogGradStart.setOrientation(LinearLayout.HORIZONTAL);
        btnDialogGradStart.setGravity(Gravity.CENTER);
        btnDialogGradStart.setMinimumHeight(dp(40));
        btnDialogGradStart.setPadding(dp(8), dp(6), dp(8), dp(6));
        final View dotDialogGradStart = new View(this);
        btnDialogGradStart.addView(dotDialogGradStart, new LinearLayout.LayoutParams(dp(14), dp(14)));
        TextView txtDGradStart = new TextView(this);
        txtDGradStart.setText(" " + I18n.get(this, "grad_color_start"));
        txtDGradStart.setTextColor(0xFFFFFFFF);
        txtDGradStart.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        txtDGradStart.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        txtDGradStart.setSingleLine(true);
        txtDGradStart.setEllipsize(TextUtils.TruncateAt.END);
        btnDialogGradStart.addView(txtDGradStart);

        View dSubGap = new View(this);

        final LinearLayout btnDialogGradEnd = new LinearLayout(this);
        btnDialogGradEnd.setOrientation(LinearLayout.HORIZONTAL);
        btnDialogGradEnd.setGravity(Gravity.CENTER);
        btnDialogGradEnd.setMinimumHeight(dp(40));
        btnDialogGradEnd.setPadding(dp(8), dp(6), dp(8), dp(6));
        final View dotDialogGradEnd = new View(this);
        btnDialogGradEnd.addView(dotDialogGradEnd, new LinearLayout.LayoutParams(dp(14), dp(14)));
        TextView txtDGradEnd = new TextView(this);
        txtDGradEnd.setText(" " + I18n.get(this, "grad_color_end"));
        txtDGradEnd.setTextColor(0xFFFFFFFF);
        txtDGradEnd.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        txtDGradEnd.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        txtDGradEnd.setSingleLine(true);
        txtDGradEnd.setEllipsize(TextUtils.TruncateAt.END);
        btnDialogGradEnd.addView(txtDGradEnd);

        subtabsRow.addView(btnDialogGradStart, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        subtabsRow.addView(dSubGap, new LinearLayout.LayoutParams(dp(8), 1));
        subtabsRow.addView(btnDialogGradEnd, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
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
        opTitle.setText(I18n.get(this, isBlurDialog ? "blur_tint_opacity_title" : "opacity_title"));
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
        gradOpacitySeekBar.setMax(isBlurDialog
                ? (OneUiBlurSpec.MAX_TINT_OPACITY - OneUiBlurSpec.MIN_TINT_OPACITY)
                : 100);
        gradOpacitySeekBar.setProgress(isBlurDialog
                ? (OneUiBlurSpec.clampTintOpacity(selectedOpacity[0]) - OneUiBlurSpec.MIN_TINT_OPACITY)
                : selectedOpacity[0]);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sbLp.topMargin = dp(4);
        sbLp.bottomMargin = dp(6);
        gradContainer.addView(gradOpacitySeekBar, sbLp);

        card.addView(gradContainer);

        // Container D: Per-category Photo + Artistic Styles + Legibility
        final Runnable[] updateDialogRef = new Runnable[1];
        final CapsuleImagePanelHelper.PanelController[] catImageCtrlRef = new CapsuleImagePanelHelper.PanelController[1];
        final CapsuleImagePanelHelper.HostCallbacks catImageCallbacks = new CapsuleImagePanelHelper.HostCallbacks() {
            @Override public int getStyle() { return catImgStyle[0]; }
            @Override public void setStyle(int style) {
                isUsingGlobal[0] = false;
                selectedMode[0] = ConfigManager.MODE_IMAGE;
                currentTab[0] = 3;
                catImgStyle[0] = ConfigManager.clampImageStyle(style);
                if (updateDialogRef[0] != null) updateDialogRef[0].run();
            }
            @Override public int getBlurPercent() { return catImgBlurPct[0]; }
            @Override public void setBlurPercent(int blurPercent) {
                isUsingGlobal[0] = false;
                selectedMode[0] = ConfigManager.MODE_IMAGE;
                currentTab[0] = 3;
                catImgBlurPct[0] = ConfigManager.clampImageBlurPercent(blurPercent);
            }
            @Override public int getScrimPercent() { return catImgScrimPct[0]; }
            @Override public void setScrimPercent(int scrimPercent) {
                isUsingGlobal[0] = false;
                selectedMode[0] = ConfigManager.MODE_IMAGE;
                currentTab[0] = 3;
                catImgScrimPct[0] = ConfigManager.clampImageScrimPercent(scrimPercent);
            }
            @Override public int getScrimMode() { return catImgScrimMode[0]; }
            @Override public void setScrimMode(int scrimMode) {
                isUsingGlobal[0] = false;
                selectedMode[0] = ConfigManager.MODE_IMAGE;
                currentTab[0] = 3;
                catImgScrimMode[0] = ConfigManager.clampScrimMode(scrimMode);
            }
            @Override public int getDuotonePreset() { return catImgDuotone[0]; }
            @Override public void setDuotonePreset(int preset) {
                isUsingGlobal[0] = false;
                selectedMode[0] = ConfigManager.MODE_IMAGE;
                currentTab[0] = 3;
                catImgDuotone[0] = ConfigManager.clampDuotonePreset(preset);
                if (updateDialogRef[0] != null) updateDialogRef[0].run();
            }
            @Override public long getImageRev() { return catImgRev[0]; }
            @Override public int getWallPrimary() { return activeWallpaperPrimary(); }
            @Override public int getWallAccent() { return activeWallpaperAccent(); }
            @Override
            public void onBaked(CapsuleImageProcessor.BakeResult result) {
                if (result == null) return;
                isUsingGlobal[0] = false;
                selectedMode[0] = ConfigManager.MODE_IMAGE;
                currentTab[0] = 3;
                catImgDomColor[0] = result.dominantColor;
                catImgLuma[0] = result.avgLuma;
                catImgRev[0] = result.revision;
                cacheBakedBitmapInUi(catSlot, result.revision, result.bitmap);
                if (updateDialogRef[0] != null) updateDialogRef[0].run();
            }
            @Override
            public void onScrimChanged(boolean liveOnly) {
                isUsingGlobal[0] = false;
                selectedMode[0] = ConfigManager.MODE_IMAGE;
                currentTab[0] = 3;
                if (updateDialogRef[0] != null) updateDialogRef[0].run();
            }
            @Override
            public void onRequestPickPhoto() {
                launchImagePicker(catSlot, this);
            }
        };
        catImageCtrlRef[0] = CapsuleImagePanelHelper.createController(
                this, catSlot, mScanExecutor, catImageCallbacks);
        final LinearLayout imageContainer = new LinearLayout(this);
        imageContainer.setOrientation(LinearLayout.VERTICAL);
        imageContainer.setVisibility(View.GONE);
        imageContainer.addView(CapsuleImagePanelHelper.buildSelectorSection(this, catImageCtrlRef[0]));
        imageContainer.addView(CapsuleImagePanelHelper.buildLegibilitySection(this, catImageCtrlRef[0], false));
        card.addView(imageContainer);

        // 8. State Update Runnable
        final Button[] btnAdvancedRef = new Button[1];
        final View[] advGapRef = new View[1];
        final Runnable updateDialogState = new Runnable() {
            @Override
            public void run() {
                boolean isBlur = (mConfig.styleMode == ConfigManager.STYLE_BLUR);
                int rawEffOpacity = (hasCustomOpacity[0] && !isUsingGlobal[0])
                        ? selectedOpacity[0]
                        : (isBlur ? mConfig.blurTintOpacity : mConfig.opacity);
                int clampedOpacity = isBlur
                        ? OneUiBlurSpec.clampTintOpacity(rawEffOpacity)
                        : Math.max(0, Math.min(100, rawEffOpacity));
                int alpha = isBlur
                        ? OneUiBlurSpec.tintAlphaFromPercent(clampedOpacity)
                        : Math.round((clampedOpacity / 100.0f) * 255.0f);
                int blurRadiusPx = isBlur ? mConfig.resolveBlurRadiusPx() : 0;
                int strokeColor = isBlur ? 0x26FFFFFF : 0x22FFFFFF;

                final Runnable dialogSelfRef = this;
                if (isUsingGlobal[0]) {
                    if (mConfig.colorMode == ConfigManager.MODE_IMAGE && mConfig.imageRev > 0L) {
                        String gSlot = CapsuleImageProcessor.slotForGlobal();
                        Bitmap gBmp = resolveBakedBitmapForUi(gSlot, mConfig.imageRev, dialogSelfRef);
                        int gDom = mConfig.imageDomColor | 0xFF000000;
                        dialogCapsule.setCapsuleImage(
                                gBmp, gDom, mConfig.imageLuma, mConfig.imageScrimPercent, mConfig.imageScrimMode);
                        CapsuleImageDrawable badgeDrawable = new CapsuleImageDrawable();
                        badgeDrawable.configure(gBmp, gDom, dp(14), 35, ConfigManager.SCRIM_UNIFORM, true, dp(1), 1.0f);
                        headerIconBg.setBackground(badgeDrawable);
                    } else if (mConfig.colorMode == ConfigManager.MODE_GRADIENT) {
                        int[] g = isBlur
                                ? mConfig.resolveGlobalBlurGradientArgb()
                                : mConfig.resolveGlobalGradientArgb();
                        if (g != null) {
                            dialogCapsule.setCapsuleGradient(g[0], g[1], isBlur, blurRadiusPx, strokeColor);
                            GradientDrawable gd = new GradientDrawable(
                                    GradientDrawable.Orientation.LEFT_RIGHT,
                                    new int[]{mConfig.gradientStartColor | 0xFF000000, mConfig.gradientEndColor | 0xFF000000});
                            gd.setCornerRadius(dp(14));
                            gd.setStroke(dp(1), 0x33FFFFFF);
                            headerIconBg.setBackground(gd);
                        }
                    } else {
                        int baseRgb = mConfig.resolveBaseRgbForCategory(meta.id, liveWall);
                        int finalArgb = (alpha << 24) | (baseRgb & 0x00FFFFFF);
                        dialogCapsule.setCapsuleColor(finalArgb, isBlur, blurRadiusPx, strokeColor);
                        headerIconBg.setBackground(roundRect(baseRgb | 0xFF000000, dp(14), dp(1), 0x33FFFFFF));
                    }
                } else {
                    if (selectedMode[0] == ConfigManager.MODE_IMAGE) {
                        Bitmap cBmp = catImgRev[0] > 0L
                                ? resolveBakedBitmapForUi(catSlot, catImgRev[0], dialogSelfRef)
                                : null;
                        int cDom = catImgDomColor[0] | 0xFF000000;
                        dialogCapsule.setCapsuleImage(
                                cBmp, cDom, catImgLuma[0], catImgScrimPct[0], catImgScrimMode[0]);
                        CapsuleImageDrawable badgeDrawable = new CapsuleImageDrawable();
                        badgeDrawable.configure(cBmp, cDom, dp(14), 35, ConfigManager.SCRIM_UNIFORM, true, dp(1), 1.0f);
                        headerIconBg.setBackground(badgeDrawable);
                    } else if (selectedMode[0] == ConfigManager.MODE_GRADIENT) {
                        int startArgb = (alpha << 24) | (gradStart[0] & 0x00FFFFFF);
                        int endArgb = (alpha << 24) | (gradEnd[0] & 0x00FFFFFF);
                        dialogCapsule.setCapsuleGradient(startArgb, endArgb, isBlur, blurRadiusPx, strokeColor);
                        GradientDrawable gd = new GradientDrawable(
                                GradientDrawable.Orientation.LEFT_RIGHT,
                                new int[]{gradStart[0] | 0xFF000000, gradEnd[0] | 0xFF000000});
                        gd.setCornerRadius(dp(14));
                        gd.setStroke(dp(1), 0x33FFFFFF);
                        headerIconBg.setBackground(gd);
                    } else {
                        int finalArgb = (alpha << 24) | (selectedColor[0] & 0x00FFFFFF);
                        dialogCapsule.setCapsuleColor(finalArgb, isBlur, blurRadiusPx, strokeColor);
                        headerIconBg.setBackground(roundRect(selectedColor[0] | 0xFF000000, dp(14), dp(1), 0x33FFFFFF));
                    }
                }

                // Update Reset button
                if (isUsingGlobal[0]) {
                    btnResetGlobal.setBackground(roundRect(0xFF1E3A66, dp(14), 2, 0xFF1477FF));
                    btnResetGlobal.setTextColor(0xFF82B1FF);
                    String globDesc;
                    int globalOp;
                    if (mConfig.colorMode == ConfigManager.MODE_IMAGE) {
                        globDesc = I18n.get(MainActivity.this, "tab_image");
                        globalOp = mConfig.imageScrimPercent;
                    } else if (mConfig.colorMode == ConfigManager.MODE_GRADIENT) {
                        globDesc = I18n.get(MainActivity.this, "tab_gradient");
                        globalOp = isBlur ? OneUiBlurSpec.clampTintOpacity(mConfig.blurTintOpacity) : mConfig.opacity;
                    } else {
                        globDesc = String.format("#%06X", (mConfig.resolveBaseRgbForCategory(meta.id, liveWall) & 0x00FFFFFF));
                        globalOp = isBlur ? OneUiBlurSpec.clampTintOpacity(mConfig.blurTintOpacity) : mConfig.opacity;
                    }
                    btnResetGlobal.setText("✓ " + I18n.get(MainActivity.this, "using_global_color") + " (" + globDesc + " • " + globalOp + "%)");
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
                int targetBarProgress = isBlur
                        ? (clampedOpacity - OneUiBlurSpec.MIN_TINT_OPACITY)
                        : clampedOpacity;
                if (gradOpacitySeekBar.getProgress() != targetBarProgress) {
                    gradOpacitySeekBar.setProgress(targetBarProgress);
                }

                // Update Tab buttons & container visibility
                palContainer.setVisibility(currentTab[0] == 0 ? View.VISIBLE : View.GONE);
                wallContainer.setVisibility(currentTab[0] == 1 ? View.VISIBLE : View.GONE);
                gradContainer.setVisibility(currentTab[0] == 2 ? View.VISIBLE : View.GONE);
                imageContainer.setVisibility(currentTab[0] == 3 ? View.VISIBLE : View.GONE);
                styleSelectableButton(btnTabFixed, currentTab[0] == 0);
                styleSelectableButton(btnTabWallpaper, currentTab[0] == 1);
                styleSelectableButton(btnTabGradient, currentTab[0] == 2);
                styleSelectableButton(btnTabImage, currentTab[0] == 3);

                if (btnAdvancedRef[0] != null) {
                    btnAdvancedRef[0].setVisibility(currentTab[0] == 3 ? View.GONE : View.VISIBLE);
                }
                if (advGapRef[0] != null) {
                    advGapRef[0].setVisibility(currentTab[0] == 3 ? View.GONE : View.VISIBLE);
                }
                catImageCtrlRef[0].refresh();
            }
        };
        updateDialogRef[0] = updateDialogState;

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
                    selectedOpacity[0] = (mConfig.styleMode == ConfigManager.STYLE_BLUR)
                            ? OneUiBlurSpec.clampTintOpacity(OneUiBlurSpec.MIN_TINT_OPACITY + progress)
                            : progress;
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

        btnTabImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentTab[0] = 3;
                selectedMode[0] = ConfigManager.MODE_IMAGE;
                isUsingGlobal[0] = false;
                updateDialogState.run();
            }
        });

        btnResetGlobal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isUsingGlobal[0] = true;
                hasCustomOpacity[0] = false;
                selectedOpacity[0] = (mConfig.styleMode == ConfigManager.STYLE_BLUR)
                        ? OneUiBlurSpec.clampTintOpacity(mConfig.blurTintOpacity)
                        : mConfig.opacity;
                selectedMode[0] = mConfig.colorMode;
                updateDialogState.run();
            }
        });

        // 9. Bottom Actions Row: [ ✨ Avanzado ] [ Cancelar ] [ Aplicar ]
        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(14);

        Button btnAdvanced = new Button(this);
        btnAdvancedRef[0] = btnAdvanced;
        btnAdvanced.setAllCaps(false);
        btnAdvanced.setText(I18n.get(this, "btn_advanced"));
        btnAdvanced.setTextColor(0xFFFFFFFF);
        btnAdvanced.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        btnAdvanced.setMinimumHeight(dp(40));
        btnAdvanced.setPadding(dp(4), dp(4), dp(4), dp(4));
        btnAdvanced.setSingleLine(true);
        btnAdvanced.setEllipsize(TextUtils.TruncateAt.END);
        btnAdvanced.setBackground(roundRect(0xFF2E2E32, dp(14), 1, 0xFF45454A));

        View s1 = new View(this);
        advGapRef[0] = s1;

        Button btnCancel = new Button(this);
        btnCancel.setAllCaps(false);
        btnCancel.setText(I18n.get(this, "cancel"));
        btnCancel.setTextColor(0xFFB0B8C8);
        btnCancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        btnCancel.setMinimumHeight(dp(40));
        btnCancel.setPadding(dp(4), dp(4), dp(4), dp(4));
        btnCancel.setSingleLine(true);
        btnCancel.setEllipsize(TextUtils.TruncateAt.END);
        btnCancel.setBackground(roundRect(0xFF232325, dp(14), 1, 0xFF38383B));

        View s2 = new View(this);

        Button btnApply = new Button(this);
        btnApply.setAllCaps(false);
        btnApply.setText(I18n.get(this, "apply"));
        btnApply.setTextColor(0xFFFFFFFF);
        btnApply.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        btnApply.setTypeface(Typeface.DEFAULT_BOLD);
        btnApply.setMinimumHeight(dp(40));
        btnApply.setPadding(dp(4), dp(4), dp(4), dp(4));
        btnApply.setSingleLine(true);
        btnApply.setEllipsize(TextUtils.TruncateAt.END);
        btnApply.setBackground(roundRect(0xFF1477FF, dp(14), 1, 0xFF3367B8));

        actionsRow.addView(btnAdvanced, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.05f));
        actionsRow.addView(s1, new LinearLayout.LayoutParams(dp(6), 1));
        actionsRow.addView(btnCancel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.95f));
        actionsRow.addView(s2, new LinearLayout.LayoutParams(dp(6), 1));
        actionsRow.addView(btnApply, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f));
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
                    int fallbackOp = (mConfig.styleMode == ConfigManager.STYLE_BLUR)
                            ? OneUiBlurSpec.clampTintOpacity(mConfig.blurTintOpacity)
                            : mConfig.opacity;
                    showCustomColorPickerDialog(
                            I18n.get(MainActivity.this, "dialog_color_opacity_prefix") + meta.getTitle(MainActivity.this),
                            selectedColor[0],
                            hasCustomOpacity[0] ? selectedOpacity[0] : fallbackOp,
                            true,
                            new OnColorAndOpacityPickedListener() {
                                @Override
                                public void onColorAndOpacityPicked(int color, int opacityPercent) {
                                    isUsingGlobal[0] = false;
                                    selectedMode[0] = (currentTab[0] == 1) ? ConfigManager.MODE_LOCK_WALLPAPER : ConfigManager.MODE_FIXED_COLOR;
                                    selectedColor[0] = color;
                                    hasCustomOpacity[0] = true;
                                    selectedOpacity[0] = (mConfig.styleMode == ConfigManager.STYLE_BLUR)
                                            ? OneUiBlurSpec.clampTintOpacity(opacityPercent)
                                            : opacityPercent;
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
                    int finalMode = (currentTab[0] == 3) ? ConfigManager.MODE_IMAGE
                            : ((currentTab[0] == 2) ? ConfigManager.MODE_GRADIENT
                                : ((currentTab[0] == 1) ? ConfigManager.MODE_LOCK_WALLPAPER : ConfigManager.MODE_FIXED_COLOR));
                    cc.customColorMode = finalMode;
                    cc.customColor = selectedColor[0];
                    cc.customGradientStart = gradStart[0];
                    cc.customGradientEnd = gradEnd[0];
                    cc.customImageRev = catImgRev[0];
                    cc.customImageStyle = catImgStyle[0];
                    cc.customImageBlurPercent = catImgBlurPct[0];
                    cc.customImageScrimPercent = catImgScrimPct[0];
                    cc.customImageScrimMode = catImgScrimMode[0];
                    cc.customImageDuotonePreset = catImgDuotone[0];
                    cc.customImageDomColor = catImgDomColor[0];
                    cc.customImageLuma = catImgLuma[0];
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

    private String formatPrimarySliderValue(boolean isBlur) {
        if (isBlur) {
            return OneUiBlurSpec.clampBlurIntensity(mConfig.blurIntensity)
                    + "% (" + mConfig.resolveBlurRadiusPx() + "px)";
        }
        return mConfig.opacity + "%";
    }

    private void refreshPreviewOnly() {
        if (mLockscreenStage != null) mLockscreenStage.setBaseColor(resolveWallpaperBottomColor());
        Integer liveWall = WallpaperColorHelper.resolveForVariant(
                mWallpaperPalette, mConfig.wallpaperVariant);

        if (!mConfig.masterEnabled) {
            mPreviewModeTag.setText(I18n.get(this, "preview_original"));
            if (mPreviewMediaCapsule != null) {
                mPreviewMediaCapsule.setCapsuleColor(0x99222630, false, 0x33FFFFFF);
            }
            return;
        }

        if (mConfig.colorMode == ConfigManager.MODE_IMAGE) {
            String slot = CapsuleImageProcessor.slotForGlobal();
            Bitmap bmp = mConfig.imageRev > 0L
                    ? resolveBakedBitmapForUi(slot, mConfig.imageRev, new Runnable() {
                        @Override
                        public void run() {
                            refreshPreviewOnly();
                        }
                    })
                    : null;
            int dom = mConfig.imageDomColor | 0xFF000000;
            String scrimLabel = (mConfig.imageScrimMode == ConfigManager.SCRIM_GRADIENT)
                    ? I18n.get(this, "img_scrim_gradient")
                    : I18n.get(this, "img_scrim_uniform");
            mPreviewModeTag.setText(I18n.get(this, "preview_image") + " • " + scrimLabel + " " + mConfig.imageScrimPercent + "%");
            if (mPreviewMediaCapsule != null) {
                mPreviewMediaCapsule.setCapsuleImage(
                        bmp, dom, mConfig.imageLuma, mConfig.imageScrimPercent, mConfig.imageScrimMode);
            }
            if (mLockscreenStage != null) {
                mLockscreenStage.invalidate();
            }
            return;
        }

        boolean isBlur = (mConfig.styleMode == ConfigManager.STYLE_BLUR);
        int blurRadiusPx = isBlur ? mConfig.resolveBlurRadiusPx() : 0;
        int tintOp = OneUiBlurSpec.clampTintOpacity(mConfig.blurTintOpacity);
        int strokeColor = isBlur ? 0x26FFFFFF : 0x22FFFFFF;

        if (mConfig.colorMode == ConfigManager.MODE_GRADIENT) {
            int[] grad = isBlur
                    ? mConfig.resolveGlobalBlurGradientArgb()
                    : mConfig.resolveGlobalGradientArgb();
            String bTag = isBlur ? (I18n.get(this, "preview_blur") + " • ") : (I18n.get(this, "preview_solid") + " • ");
            String metrics = isBlur
                    ? (blurRadiusPx + "px • " + tintOp + "%")
                    : (mConfig.opacity + "%");
            mPreviewModeTag.setText(bTag + I18n.get(this, "preview_gradient") + " • " + metrics);
            if (mPreviewMediaCapsule != null && grad != null) {
                mPreviewMediaCapsule.setCapsuleGradient(grad[0], grad[1], isBlur, blurRadiusPx, strokeColor);
            }
        } else {
            int finalArgb = isBlur
                    ? mConfig.resolveGlobalBlurFinalArgb(liveWall)
                    : mConfig.resolveGlobalFinalArgb(liveWall);
            String bTag2 = isBlur ? (I18n.get(this, "preview_blur") + " • ") : (I18n.get(this, "preview_solid") + " • ");
            String metrics = isBlur
                    ? (blurRadiusPx + "px • " + tintOp + "%")
                    : (mConfig.opacity + "%");
            mPreviewModeTag.setText(bTag2 + metrics);
            if (mPreviewMediaCapsule != null) {
                mPreviewMediaCapsule.setCapsuleColor(finalArgb, isBlur, blurRadiusPx, strokeColor);
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
        boolean isImg = (mConfig.colorMode == ConfigManager.MODE_IMAGE);
        styleSelectableButton(mBtnFixedMode, isFixed);
        styleSelectableButton(mBtnWallpaperMode, isWall);
        styleSelectableButton(mBtnGradientMode, isGrad);
        styleSelectableButton(mBtnImageMode, isImg);

        mFixedPaletteContainer.setVisibility(isFixed ? View.VISIBLE : View.GONE);
        mWallpaperModeContainer.setVisibility(isWall ? View.VISIBLE : View.GONE);
        mGradientModeContainer.setVisibility(isGrad ? View.VISIBLE : View.GONE);
        if (mImageModeContainer != null) {
            mImageModeContainer.setVisibility(isImg ? View.VISIBLE : View.GONE);
        }

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

        // 4. Switch Card 2 between Solid/Blur controls vs Image Legibility/Glass Blur controls
        if (mFinishCardTitle != null) {
            mFinishCardTitle.setText(I18n.get(this, isImg ? "img_sec_legibility" : "sec_finish_opacity"));
        }
        if (mColorFinishControlsContainer != null) {
            mColorFinishControlsContainer.setVisibility(isImg ? View.GONE : View.VISIBLE);
        }
        if (mImageFinishControlsContainer != null) {
            mImageFinishControlsContainer.setVisibility(isImg ? View.VISIBLE : View.GONE);
        }
        if (mGlobalImagePanel != null) {
            mGlobalImagePanel.refresh();
        }

        boolean isBlur = (mConfig.styleMode == ConfigManager.STYLE_BLUR);
        styleSelectableButton(mBtnStyleSolid, !isBlur);
        styleSelectableButton(mBtnStyleBlur, isBlur);
        if (mOpacityTitle != null) mOpacityTitle.setText(I18n.get(this,
                isBlur ? "tint_strength_title" : "opacity_title"));
        if (mFinishDescription != null) mFinishDescription.setText(I18n.get(this,
                isBlur ? "finish_blur_desc" : "finish_solid_desc"));
        if (mBlurTintContainer != null) {
            mBlurTintContainer.setVisibility(isBlur ? View.VISIBLE : View.GONE);
        }
        mUpdatingSliders = true;
        try {
            if (mOpacitySeekBar != null) {
                int targetPrimary = isBlur ? OneUiBlurSpec.clampBlurIntensity(mConfig.blurIntensity) : mConfig.opacity;
                if (mOpacitySeekBar.getProgress() != targetPrimary) {
                    mOpacitySeekBar.setProgress(targetPrimary);
                }
            }
            if (mOpacityValueText != null) {
                mOpacityValueText.setText(formatPrimarySliderValue(isBlur));
            }
            int clampedTint = OneUiBlurSpec.clampTintOpacity(mConfig.blurTintOpacity);
            if (mBlurTintSeekBar != null) {
                int targetTintProgress = clampedTint - OneUiBlurSpec.MIN_TINT_OPACITY;
                if (mBlurTintSeekBar.getProgress() != targetTintProgress) {
                    mBlurTintSeekBar.setProgress(targetTintProgress);
                }
            }
            if (mBlurTintValueText != null) {
                mBlurTintValueText.setText(clampedTint + "%");
            }
        } finally {
            mUpdatingSliders = false;
        }
        if (mHideBgAodSwitch != null && mHideBgAodSwitch.isChecked() != mConfig.hideBgOnAod) {
            mHideBgAodSwitch.setChecked(mConfig.hideBgOnAod, false);
        }

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
        boolean isCompact = isCompactScreen();
        int pad = isCompact ? dp(14) : dp(18);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(roundRect(0xFF2B2B2D, dp(26), 1, 0xFF38383B));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = isCompact ? dp(10) : dp(14);
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

    private Drawable createCategoryIconBackground(String categoryId, ConfigManager.CategoryConfig cc, int resolvedRgb, int radiusPx) {
        if (mConfig.isImageActiveForCategory(categoryId)) {
            String slot = mConfig.resolveImageSlotForCategory(categoryId);
            long rev = mConfig.resolveImageRevForCategory(categoryId);
            int dom = mConfig.resolveImageDomColorForCategory(categoryId);
            Bitmap bmp = resolveBakedBitmapForUi(slot, rev, new Runnable() {
                @Override
                public void run() {
                    rebuildCategoriesList();
                }
            });
            CapsuleImageDrawable imgDrawable = new CapsuleImageDrawable();
            imgDrawable.configure(bmp, dom, radiusPx, 35, ConfigManager.SCRIM_UNIFORM, true, dp(1), 1.0f);
            return imgDrawable;
        }
        boolean isCatGrad = cc.useCustomColor && (cc.customColorMode == ConfigManager.MODE_GRADIENT);
        boolean isGlobGrad = !cc.useCustomColor && (mConfig.colorMode == ConfigManager.MODE_GRADIENT);
        if (isCatGrad) {
            GradientDrawable gd = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{cc.customGradientStart | 0xFF000000, cc.customGradientEnd | 0xFF000000});
            gd.setCornerRadius(radiusPx);
            gd.setStroke(dp(1), 0x33FFFFFF);
            return gd;
        } else if (isGlobGrad) {
            GradientDrawable gd = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{mConfig.gradientStartColor | 0xFF000000, mConfig.gradientEndColor | 0xFF000000});
            gd.setCornerRadius(radiusPx);
            gd.setStroke(dp(1), 0x33FFFFFF);
            return gd;
        } else {
            return roundRect(resolvedRgb | 0xFF000000, radiusPx, dp(1), 0x33FFFFFF);
        }
    }

    private FrameLayout createCategoryIconBadge(ConfigManager.CategoryMeta meta, ConfigManager.CategoryConfig cc, int resolvedRgb, int sizePx) {
        FrameLayout iconWrap = new FrameLayout(this);
        View iconBg = new View(this);
        int radius = Math.round(sizePx * (14f / 48f));
        iconBg.setBackground(createCategoryIconBackground(meta.id, cc, resolvedRgb, radius));
        iconWrap.addView(iconBg, new FrameLayout.LayoutParams(sizePx, sizePx));

        ImageView iconImg = new ImageView(this);
        int resId = meta.getIconResId(this);
        if (resId != 0) {
            iconImg.setImageResource(resId);
        }
        iconImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconImg.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        int innerIconSize = Math.round(sizePx * (28f / 48f));
        FrameLayout.LayoutParams imgLp = new FrameLayout.LayoutParams(innerIconSize, innerIconSize);
        imgLp.gravity = Gravity.CENTER;
        iconWrap.addView(iconImg, imgLp);

        if (!cc.enabled) {
            iconWrap.setAlpha(0.55f);
        }
        return iconWrap;
    }

    private FrameLayout createCategoryIconBadge(ConfigManager.CategoryMeta meta, ConfigManager.CategoryConfig cc, int resolvedRgb) {
        return createCategoryIconBadge(meta, cc, resolvedRgb, dp(48));
    }

    private int shiftAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private boolean isCompactScreen() {
        android.content.res.Configuration cfg = getResources().getConfiguration();
        return cfg.screenWidthDp < 360 || cfg.fontScale > 1.15f;
    }

    private int dp(float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }
}
