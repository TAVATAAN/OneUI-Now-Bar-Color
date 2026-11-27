package io.github.tavataan.nowbarcolor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Shared One UI builder for Image mode selector + artistic style carousel + Duotone palette +
 * live legibility Scrim & Glass blur controls, reused across Global and Category settings without duplication.
 */
final class CapsuleImagePanelHelper {

    interface HostCallbacks {
        int getStyle();
        void setStyle(int style);
        int getBlurPercent();
        void setBlurPercent(int blurPercent);
        int getScrimPercent();
        void setScrimPercent(int scrimPercent);
        int getScrimMode();
        void setScrimMode(int scrimMode);
        int getDuotonePreset();
        void setDuotonePreset(int preset);
        long getImageRev();
        int getWallPrimary();
        int getWallAccent();
        void onBaked(CapsuleImageProcessor.BakeResult result);
        void onScrimChanged(boolean liveOnly);
        void onRequestPickPhoto();
    }

    static final class PanelController {
        private final Context ctx;
        private final String slot;
        private final ExecutorService io;
        private final HostCallbacks cb;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final float d;

        Button btnPick;
        Button btnRecrop;
        TextView txtEmptyHint;
        LinearLayout duotoneRowWrap;
        final List<LinearLayout> styleCards = new ArrayList<>();
        final List<ImageView> styleThumbs = new ArrayList<>();
        final List<LinearLayout> duotoneButtons = new ArrayList<>();

        SeekBar scrimSeekBar;
        TextView scrimValueText;
        Button btnScrimUniform;
        Button btnScrimGradient;
        LinearLayout blurSection;
        SeekBar blurSeekBar;
        TextView blurValueText;

        private boolean updatingSliders = false;
        private long lastLoadedThumbRev = -1L;
        private int lastLoadedDuotone = -1;
        private boolean lastCropPresent = false;
        private boolean thumbsLoading = false;

        PanelController(Context ctx, String slot, ExecutorService io, HostCallbacks cb) {
            this.ctx = ctx;
            this.slot = slot;
            this.io = io;
            this.cb = cb;
            this.d = ctx.getResources().getDisplayMetrics().density;
        }

        void refresh() {
            boolean hasCrop = CapsuleImageProcessor.hasCroppedSource(ctx, slot);
            long rev = cb.getImageRev();
            int curStyle = ConfigManager.clampImageStyle(cb.getStyle());
            int curDuotone = ConfigManager.clampDuotonePreset(cb.getDuotonePreset());
            int curScrim = ConfigManager.clampImageScrimPercent(cb.getScrimPercent());
            int curScrimMode = ConfigManager.clampScrimMode(cb.getScrimMode());
            int curBlur = ConfigManager.clampImageBlurPercent(cb.getBlurPercent());

            if (btnRecrop != null) {
                btnRecrop.setVisibility(hasCrop ? View.VISIBLE : View.GONE);
            }
            if (txtEmptyHint != null) {
                txtEmptyHint.setVisibility((hasCrop && rev > 0L) ? View.GONE : View.VISIBLE);
            }
            for (int i = 0; i < styleCards.size(); i++) {
                boolean sel = (i == curStyle);
                styleCards.get(i).setBackground(pillBox(
                        sel ? 0xFF1E3A66 : 0xFF232325,
                        14f * d,
                        sel ? 2 : 1,
                        sel ? 0xFF1477FF : 0xFF38383B, d));
            }
            if (duotoneRowWrap != null) {
                duotoneRowWrap.setVisibility(curStyle == ConfigManager.IMG_STYLE_DUOTONE ? View.VISIBLE : View.GONE);
            }
            for (int i = 0; i < duotoneButtons.size(); i++) {
                boolean sel = (i == curDuotone);
                duotoneButtons.get(i).setBackground(pillBox(
                        sel ? 0xFF333338 : 0xFF232325,
                        12f * d,
                        sel ? 2 : 1,
                        sel ? 0xFF1477FF : 0xFF38383B, d));
            }

            updatingSliders = true;
            try {
                if (scrimSeekBar != null && scrimSeekBar.getProgress() != curScrim) {
                    scrimSeekBar.setProgress(curScrim);
                }
                if (scrimValueText != null) {
                    scrimValueText.setText(curScrim + "%");
                }
                if (btnScrimUniform != null) {
                    styleChoiceBtn(btnScrimUniform, curScrimMode == ConfigManager.SCRIM_UNIFORM, d);
                }
                if (btnScrimGradient != null) {
                    styleChoiceBtn(btnScrimGradient, curScrimMode == ConfigManager.SCRIM_GRADIENT, d);
                }
                if (blurSection != null) {
                    blurSection.setVisibility(curStyle == ConfigManager.IMG_STYLE_BLUR ? View.VISIBLE : View.GONE);
                }
                if (blurSeekBar != null && blurSeekBar.getProgress() != curBlur) {
                    blurSeekBar.setProgress(curBlur);
                }
                if (blurValueText != null) {
                    blurValueText.setText(curBlur + "%");
                }
            } finally {
                updatingSliders = false;
            }

            if (!thumbsLoading && (lastLoadedThumbRev != rev || lastLoadedDuotone != curDuotone || lastCropPresent != hasCrop)) {
                loadThumbnailsAsync(rev, curDuotone, hasCrop);
            }
        }

        private void loadThumbnailsAsync(final long rev, final int duotone, final boolean hasCrop) {
            thumbsLoading = true;
            final int thumbW = Math.round(76f * d);
            final int thumbH = Math.round(28f * d);
            final int wallP = cb.getWallPrimary();
            final int wallA = cb.getWallAccent();
            final int blurPct = ConfigManager.clampImageBlurPercent(cb.getBlurPercent());
            io.execute(new Runnable() {
                @Override
                public void run() {
                    Bitmap crop = hasCrop ? CapsuleImageProcessor.loadCropBitmap(ctx, slot) : null;
                    final Bitmap[] generated = new Bitmap[ConfigManager.IMG_STYLE_COUNT];
                    try {
                        for (int s = 0; s < ConfigManager.IMG_STYLE_COUNT; s++) {
                            generated[s] = CapsuleImageProcessor.renderStyleThumbnail(
                                    crop, s, blurPct, duotone, wallP, wallA, thumbW, thumbH);
                        }
                    } finally {
                        if (crop != null && !crop.isRecycled()) {
                            crop.recycle();
                        }
                    }
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            thumbsLoading = false;
                            lastLoadedThumbRev = rev;
                            lastLoadedDuotone = duotone;
                            lastCropPresent = hasCrop;
                            for (int s = 0; s < styleThumbs.size() && s < generated.length; s++) {
                                if (generated[s] != null) {
                                    styleThumbs.get(s).setImageBitmap(generated[s]);
                                }
                            }
                        }
                    });
                }
            });
        }

        void rebakeCurrentAsync() {
            if (!CapsuleImageProcessor.hasCroppedSource(ctx, slot)) return;
            final int style = ConfigManager.clampImageStyle(cb.getStyle());
            final int blurPct = ConfigManager.clampImageBlurPercent(cb.getBlurPercent());
            final int duotone = ConfigManager.clampDuotonePreset(cb.getDuotonePreset());
            final int wallP = cb.getWallPrimary();
            final int wallA = cb.getWallAccent();
            io.execute(new Runnable() {
                @Override
                public void run() {
                    final CapsuleImageProcessor.BakeResult res = CapsuleImageProcessor.rebakeExistingCrop(
                            ctx, slot, style, blurPct, duotone, wallP, wallA);
                    if (res != null) {
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                cb.onBaked(res);
                                refresh();
                            }
                        });
                    }
                }
            });
        }
    }

    private CapsuleImagePanelHelper() {}

    static PanelController createController(Context ctx, String slot, ExecutorService io, HostCallbacks cb) {
        return new PanelController(ctx, slot, io, cb);
    }

    static LinearLayout buildSelectorSection(final Context ctx, final PanelController ctrl) {
        final float d = ctrl.d;
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);

        TextView info = new TextView(ctx);
        info.setText(I18n.get(ctx, "img_info"));
        info.setTextColor(0xFFB0B8C8);
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        info.setPadding(0, 0, 0, Math.round(10f * d));
        box.addView(info);

        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brLp.bottomMargin = Math.round(8f * d);

        ctrl.btnPick = new Button(ctx);
        ctrl.btnPick.setAllCaps(false);
        ctrl.btnPick.setText(I18n.get(ctx, "img_pick_crop_btn"));
        ctrl.btnPick.setTextColor(0xFFFFFFFF);
        ctrl.btnPick.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        ctrl.btnPick.setTypeface(Typeface.DEFAULT_BOLD);
        ctrl.btnPick.setMinimumHeight(Math.round(42f * d));
        ctrl.btnPick.setBackground(pillBox(0xFF1477FF, 14f * d, 1, 0xFF3367B8, d));
        ctrl.btnPick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ctrl.cb.onRequestPickPhoto();
            }
        });
        btnRow.addView(ctrl.btnPick, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.35f));

        ctrl.btnRecrop = new Button(ctx);
        ctrl.btnRecrop.setAllCaps(false);
        ctrl.btnRecrop.setText(I18n.get(ctx, "img_recrop_btn"));
        ctrl.btnRecrop.setTextColor(0xFFE0E0E5);
        ctrl.btnRecrop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        ctrl.btnRecrop.setMinimumHeight(Math.round(42f * d));
        ctrl.btnRecrop.setBackground(pillBox(0xFF2E2E32, 14f * d, 1, 0xFF45454A, d));
        LinearLayout.LayoutParams recropLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.85f);
        recropLp.leftMargin = Math.round(8f * d);
        ctrl.btnRecrop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CapsuleCropDialog.showForExistingCrop(
                        ctx, ctrl.slot,
                        ctrl.cb.getStyle(), ctrl.cb.getBlurPercent(), ctrl.cb.getDuotonePreset(),
                        ctrl.cb.getWallPrimary(), ctrl.cb.getWallAccent(),
                        ctrl.io,
                        new CapsuleCropDialog.Callback() {
                            @Override
                            public void onBaked(CapsuleImageProcessor.BakeResult result) {
                                ctrl.cb.onBaked(result);
                                ctrl.refresh();
                            }
                        });
            }
        });
        btnRow.addView(ctrl.btnRecrop, recropLp);
        box.addView(btnRow, brLp);

        ctrl.txtEmptyHint = new TextView(ctx);
        ctrl.txtEmptyHint.setText(I18n.get(ctx, "img_none_selected"));
        ctrl.txtEmptyHint.setTextColor(0xFF9AA0B4);
        ctrl.txtEmptyHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        ctrl.txtEmptyHint.setPadding(0, 0, 0, Math.round(8f * d));
        box.addView(ctrl.txtEmptyHint);

        TextView stylesTitle = new TextView(ctx);
        stylesTitle.setText(I18n.get(ctx, "img_styles_title"));
        stylesTitle.setTextColor(0xFFE2E6F0);
        stylesTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        stylesTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        stylesTitle.setPadding(0, Math.round(4f * d), 0, Math.round(8f * d));
        box.addView(stylesTitle);

        HorizontalScrollView scroll = new HorizontalScrollView(ctx);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout strip = new LinearLayout(ctx);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        String[] styleKeys = new String[]{
                "img_style_original", "img_style_blur", "img_style_duotone",
                "img_style_noir", "img_style_pixel", "img_style_vignette"
        };
        ctrl.styleCards.clear();
        ctrl.styleThumbs.clear();
        for (int i = 0; i < styleKeys.length; i++) {
            final int styleIdx = i;
            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            int cardPad = Math.round(6f * d);
            card.setPadding(cardPad, cardPad, cardPad, Math.round(7f * d));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.rightMargin = Math.round(8f * d);

            ImageView iv = new ImageView(ctx);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setClipToOutline(true);
            iv.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Math.round(14f * d));
                }
            });
            iv.setBackgroundColor(0xFF1A1B1E);
            card.addView(iv, new LinearLayout.LayoutParams(Math.round(76f * d), Math.round(28f * d)));

            TextView lbl = new TextView(ctx);
            lbl.setText(I18n.get(ctx, styleKeys[i]));
            lbl.setTextColor(0xFFFFFFFF);
            lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            lbl.setGravity(Gravity.CENTER);
            lbl.setSingleLine(true);
            lbl.setPadding(0, Math.round(5f * d), 0, 0);
            card.addView(lbl);

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ctrl.cb.setStyle(styleIdx);
                    ctrl.refresh();
                    ctrl.rebakeCurrentAsync();
                }
            });
            ctrl.styleCards.add(card);
            ctrl.styleThumbs.add(iv);
            strip.addView(card, clp);
        }
        scroll.addView(strip);
        box.addView(scroll);

        // Conditional Duotone Preset Strip
        ctrl.duotoneRowWrap = new LinearLayout(ctx);
        ctrl.duotoneRowWrap.setOrientation(LinearLayout.VERTICAL);
        ctrl.duotoneRowWrap.setVisibility(View.GONE);

        TextView duoTitle = new TextView(ctx);
        duoTitle.setText(I18n.get(ctx, "img_duotone_title"));
        duoTitle.setTextColor(0xFFB0B8C8);
        duoTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        duoTitle.setPadding(0, Math.round(10f * d), 0, Math.round(6f * d));
        ctrl.duotoneRowWrap.addView(duoTitle);

        HorizontalScrollView duoScroll = new HorizontalScrollView(ctx);
        duoScroll.setHorizontalScrollBarEnabled(false);
        duoScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout duoStrip = new LinearLayout(ctx);
        duoStrip.setOrientation(LinearLayout.HORIZONTAL);
        String[] duoKeys = new String[]{
                "duotone_monet", "duotone_cyber", "duotone_sunset", "duotone_ocean", "duotone_crimson"
        };
        ctrl.duotoneButtons.clear();
        for (int i = 0; i < duoKeys.length; i++) {
            final int presetIdx = i;
            int[] pair = CapsuleImageProcessor.resolveDuotoneColors(
                    presetIdx, ctrl.cb.getWallPrimary(), ctrl.cb.getWallAccent());
            LinearLayout chip = new LinearLayout(ctx);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(Math.round(10f * d), Math.round(6f * d), Math.round(10f * d), Math.round(6f * d));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.rightMargin = Math.round(6f * d);

            View dot = new View(ctx);
            GradientDrawable gd = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT, new int[]{pair[0], pair[1]});
            gd.setCornerRadius(7f * d);
            gd.setStroke(Math.max(1, Math.round(d * 0.8f)), 0x88FFFFFF);
            dot.setBackground(gd);
            chip.addView(dot, new LinearLayout.LayoutParams(Math.round(14f * d), Math.round(14f * d)));

            TextView txt = new TextView(ctx);
            txt.setText(" " + I18n.get(ctx, duoKeys[i]));
            txt.setTextColor(0xFFFFFFFF);
            txt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            txt.setSingleLine(true);
            chip.addView(txt);

            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ctrl.cb.setDuotonePreset(presetIdx);
                    ctrl.refresh();
                    ctrl.rebakeCurrentAsync();
                }
            });
            ctrl.duotoneButtons.add(chip);
            duoStrip.addView(chip, dlp);
        }
        duoScroll.addView(duoStrip);
        ctrl.duotoneRowWrap.addView(duoScroll);
        box.addView(ctrl.duotoneRowWrap);

        return box;
    }

    static LinearLayout buildLegibilitySection(final Context ctx, final PanelController ctrl, boolean includeDescription) {
        final float d = ctrl.d;
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);

        // 1. Scrim slider (0..80%)
        LinearLayout scrimHeader = new LinearLayout(ctx);
        scrimHeader.setOrientation(LinearLayout.HORIZONTAL);
        scrimHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams shLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shLp.topMargin = Math.round(10f * d);

        TextView scrimTitle = new TextView(ctx);
        scrimTitle.setText(I18n.get(ctx, "img_scrim_title"));
        scrimTitle.setTextColor(0xFFFFFFFF);
        scrimTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        scrimHeader.addView(scrimTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ctrl.scrimValueText = new TextView(ctx);
        ctrl.scrimValueText.setTextColor(0xFF82B1FF);
        ctrl.scrimValueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        ctrl.scrimValueText.setTypeface(Typeface.DEFAULT_BOLD);
        scrimHeader.addView(ctrl.scrimValueText);
        box.addView(scrimHeader, shLp);

        ctrl.scrimSeekBar = new SeekBar(ctx);
        ctrl.scrimSeekBar.setMax(ConfigManager.MAX_IMAGE_SCRIM);
        ctrl.scrimSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (ctrl.updatingSliders) return;
                int clamped = ConfigManager.clampImageScrimPercent(progress);
                ctrl.cb.setScrimPercent(clamped);
                if (ctrl.scrimValueText != null) ctrl.scrimValueText.setText(clamped + "%");
                if (fromUser) ctrl.cb.onScrimChanged(true);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                ctrl.cb.onScrimChanged(false);
            }
        });
        LinearLayout.LayoutParams ssbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ssbLp.topMargin = Math.round(4f * d);
        ssbLp.bottomMargin = Math.round(10f * d);
        box.addView(ctrl.scrimSeekBar, ssbLp);

        // 2. Scrim Style Row (Uniform vs Reading Gradient)
        LinearLayout modeRow = new LinearLayout(ctx);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams mrLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mrLp.bottomMargin = Math.round(10f * d);

        ctrl.btnScrimUniform = new Button(ctx);
        ctrl.btnScrimUniform.setAllCaps(false);
        ctrl.btnScrimUniform.setText(I18n.get(ctx, "img_scrim_uniform"));
        ctrl.btnScrimUniform.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        ctrl.btnScrimUniform.setMinimumHeight(Math.round(38f * d));
        ctrl.btnScrimUniform.setSingleLine(true);
        ctrl.btnScrimUniform.setEllipsize(TextUtils.TruncateAt.END);
        ctrl.btnScrimUniform.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ctrl.cb.setScrimMode(ConfigManager.SCRIM_UNIFORM);
                ctrl.refresh();
                ctrl.cb.onScrimChanged(false);
            }
        });

        ctrl.btnScrimGradient = new Button(ctx);
        ctrl.btnScrimGradient.setAllCaps(false);
        ctrl.btnScrimGradient.setText(I18n.get(ctx, "img_scrim_gradient"));
        ctrl.btnScrimGradient.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        ctrl.btnScrimGradient.setMinimumHeight(Math.round(38f * d));
        ctrl.btnScrimGradient.setSingleLine(true);
        ctrl.btnScrimGradient.setEllipsize(TextUtils.TruncateAt.END);
        ctrl.btnScrimGradient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ctrl.cb.setScrimMode(ConfigManager.SCRIM_GRADIENT);
                ctrl.refresh();
                ctrl.cb.onScrimChanged(false);
            }
        });

        modeRow.addView(ctrl.btnScrimUniform, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        View gap = new View(ctx);
        modeRow.addView(gap, new LinearLayout.LayoutParams(Math.round(8f * d), 1));
        modeRow.addView(ctrl.btnScrimGradient, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(modeRow, mrLp);

        // 3. Conditional Photo Blur slider (when Glass style is selected)
        ctrl.blurSection = new LinearLayout(ctx);
        ctrl.blurSection.setOrientation(LinearLayout.VERTICAL);
        ctrl.blurSection.setVisibility(View.GONE);

        LinearLayout blurHeader = new LinearLayout(ctx);
        blurHeader.setOrientation(LinearLayout.HORIZONTAL);
        blurHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView blurTitle = new TextView(ctx);
        blurTitle.setText(I18n.get(ctx, "img_blur_strength"));
        blurTitle.setTextColor(0xFFE2E6F0);
        blurTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        blurHeader.addView(blurTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ctrl.blurValueText = new TextView(ctx);
        ctrl.blurValueText.setTextColor(0xFF82B1FF);
        ctrl.blurValueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        ctrl.blurValueText.setTypeface(Typeface.DEFAULT_BOLD);
        blurHeader.addView(ctrl.blurValueText);
        ctrl.blurSection.addView(blurHeader);

        ctrl.blurSeekBar = new SeekBar(ctx);
        ctrl.blurSeekBar.setMax(100);
        ctrl.blurSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (ctrl.updatingSliders) return;
                int clamped = ConfigManager.clampImageBlurPercent(progress);
                ctrl.cb.setBlurPercent(clamped);
                if (ctrl.blurValueText != null) ctrl.blurValueText.setText(clamped + "%");
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                ctrl.rebakeCurrentAsync();
            }
        });
        LinearLayout.LayoutParams bsbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bsbLp.topMargin = Math.round(4f * d);
        bsbLp.bottomMargin = Math.round(8f * d);
        ctrl.blurSection.addView(ctrl.blurSeekBar, bsbLp);
        box.addView(ctrl.blurSection);

        if (includeDescription) {
            TextView desc = new TextView(ctx);
            desc.setText(I18n.get(ctx, "img_legibility_desc"));
            desc.setTextColor(0xFFB0B8C8);
            desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            box.addView(desc);
        }

        return box;
    }

    private static void styleChoiceBtn(Button btn, boolean selected, float d) {
        btn.setTextColor(selected ? 0xFFFFFFFF : 0xFF9AA0B4);
        btn.setTypeface(Typeface.create("sans-serif-medium", selected ? Typeface.BOLD : Typeface.NORMAL));
        btn.setBackground(pillBox(
                selected ? 0xFF1477FF : 0xFF232325,
                12f * d,
                1,
                selected ? 0xFF3367B8 : 0xFF38383B, d));
    }

    private static GradientDrawable pillBox(int fill, float radiusPx, int strokeDp, int strokeColor, float d) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(fill);
        gd.setCornerRadius(radiusPx);
        gd.setStroke(Math.max(1, Math.round(strokeDp * d)), strokeColor);
        return gd;
    }
}
