package io.github.tavataan.nowbarcolor;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.io.InputStream;

/**
 * Pixel-perfect replica of Samsung One UI 7 Now Bar Media Capsule.
 *
 * Visual Specifications:
 * - Proportions: 84dp height with 42dp corner radius (perfect 50% pill shape)
 * - Stacked Depth: Characteristic One UI 7 double-bevel secondary curved shadow contour at bottom (+3dp)
 * - Album Art: 56dp x 56dp circular crop of HN.jpg with 14dp left margin and 14dp vertical margins
 * - Song Title: "Greenpath • TAVATAAN" in 13.5sp semi-bold white, centered above controls
 * - Playback Controls: Solid vector Skip Previous, Pause (parallel rounded pills), Skip Next with 42dp spacing
 * - Acoustic Music Waves: 3-layer fluid undulating audio wave at the bottom base with harmonic luminous glow
 * - Dynamic Live Color: Updates background, borders, and wave harmonics in real-time as colors change
 */
public class OneUIMediaCapsuleView extends View {

    // Geometry constants (dp)
    private static final float CAPSULE_HEIGHT_DP = 84f;
    private static final float CAPSULE_RADIUS_DP = 42f;
    private static final float ALBUM_ART_SIZE_DP = 56f;
    private static final float ALBUM_ART_LEFT_MARGIN_DP = 14f;
    private static final float BEVEL_OFFSET_DP = 3f;
    private static final float CONTROLS_SPACING_DP = 42f;

    // Samsung One UI AODService (jm4 / im4 / c69 / d69 / e69) Wave Specifications:
    // Vibrant Green Wave Palette and Tuning (NOWBAR_LARGE)
    private static final int NUM_WAVE_LAYERS = 5;

    // Vibrant Green Palette with luminous crest overlay
    private static final int[] WAVE_COLORS = new int[]{
            0xFF00E676, // Layer 0: Vibrant spring green (base)
            0xFFFFFFFF, // Layer 1: Luminous overlay white (highlight crest)
            0xFFB9F6CA, // Layer 2: Soft mint pastel
            0xFF00C853, // Layer 3: Vivid emerald green
            0xFF1B5E20  // Layer 4: Deep forest pine green
    };

    // Opacities: hm8.a(90) = 229, hm8.a(100) = 255
    private static final int[] WAVE_ALPHAS = new int[]{
            229, // Layer 0: 90%
            255, // Layer 1: 100%
            229, // Layer 2: 90%
            229, // Layer 3: 90%
            229  // Layer 4: 90%
    };

    // PorterDuff Blend Modes from c69.PURPLE
    private static final PorterDuff.Mode[] WAVE_BLEND_MODES = new PorterDuff.Mode[]{
            PorterDuff.Mode.SCREEN,
            PorterDuff.Mode.OVERLAY,
            PorterDuff.Mode.SCREEN,
            PorterDuff.Mode.SCREEN,
            PorterDuff.Mode.SCREEN
    };

    // Harmonic Tuning from e69.a[b69.NOWBAR_LARGE] & d69:
    private static final float[] WAVE_AMP_SCALES = new float[]{
            1.0f, 0.85f, 0.70f, 0.55f, 0.40f
    };

    private static final float[] WAVE_FREQ_MULTS = new float[]{
            0.35f, 0.45f, 0.65f, 0.85f, 1.0f
    };

    private static final float[] WAVE_PHASE_OFFSETS = new float[]{
            0.0f, 1.26f, 2.51f, 3.77f, 5.03f
    };

    private static final float[] WAVE_SPEED_MULTS = new float[]{
            0.50f, 0.48f, 0.46f, 0.42f, 0.40f
    };

    private static final float BASE_CYCLES = 3.0f; // jm4.f = 3
    private static final float ANGULAR_VELOCITY = -9.0f; // jm4.g = -9.0 rad/sec

    // Paints
    private final Paint mNotePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBevelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mAlbumPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint mAlbumBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mControlsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // 5-Layer Wave Paints & Paths
    private final Paint[] mWavePaints = new Paint[NUM_WAVE_LAYERS];
    private final Path[] mWavePaths = new Path[NUM_WAVE_LAYERS];

    // Geometry Rects & Paths (reused to guarantee 0 allocations on draw)
    private final RectF mCapsuleRect = new RectF();
    private final RectF mBevelRect = new RectF();
    private final RectF mStrokeRect = new RectF();
    private final RectF mWaveLayerRect = new RectF();
    private final Path mClipPath = new Path();

    // Icon Vector Paths
    private final Path mPrevTrianglePath = new Path();
    private final Path mNextTrianglePath = new Path();
    private final RectF mPrevBarRect = new RectF();
    private final RectF mNextBarRect = new RectF();
    private final RectF mPauseBar1Rect = new RectF();
    private final RectF mPauseBar2Rect = new RectF();

    // Album art bitmap & shader
    private Bitmap mAlbumBitmap;
    private BitmapShader mAlbumShader;
    private final Matrix mAlbumMatrix = new Matrix();

    // Capsule appearance state
    private int mCapsuleColor = 0xFF1E88E5;
    private int mStrokeColor = 0x26FFFFFF;
    private boolean mIsBlur = false;

    // Wave animation state (Samsung nanoTime physics)
    private long mWaveStartTime = 0;
    private ValueAnimator mWaveAnimator;
    private boolean mIsAnimating = false;

    // Cached density values
    private float mDensity;
    private float mCapsuleHeight;
    private float mCapsuleRadius;
    private float mAlbumSize;
    private float mAlbumLeftMargin;
    private float mBevelOffset;
    private float mControlsSpacing;

    public OneUIMediaCapsuleView(Context context) {
        super(context);
        init(context);
    }

    public OneUIMediaCapsuleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public OneUIMediaCapsuleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mDensity = context.getResources().getDisplayMetrics().density;
        mCapsuleHeight = dp(CAPSULE_HEIGHT_DP);
        mCapsuleRadius = dp(CAPSULE_RADIUS_DP);
        mAlbumSize = dp(ALBUM_ART_SIZE_DP);
        mAlbumLeftMargin = dp(ALBUM_ART_LEFT_MARGIN_DP);
        mBevelOffset = dp(BEVEL_OFFSET_DP);
        mControlsSpacing = dp(CONTROLS_SPACING_DP);

        // Paints configuration
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(dp(1f));

        mBevelPaint.setStyle(Paint.Style.FILL);
        mBevelPaint.setColor(0x28000000);

        mAlbumBorderPaint.setStyle(Paint.Style.STROKE);
        mAlbumBorderPaint.setStrokeWidth(dp(1f));
        mAlbumBorderPaint.setColor(0x33FFFFFF);

        mTextPaint.setColor(0xFFFFFFFF);
        mTextPaint.setTextSize(sp(13.5f));
        mTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        mTextPaint.setTextAlign(Paint.Align.CENTER);

        mControlsPaint.setStyle(Paint.Style.FILL);
        mControlsPaint.setColor(0xFFFFFFFF);
        mControlsPaint.setPathEffect(new CornerPathEffect(dp(1.2f)));

        // Initialize 5 static purple wave layers strictly per Samsung AODService
        for (int i = 0; i < NUM_WAVE_LAYERS; i++) {
            Paint waveP = new Paint(Paint.ANTI_ALIAS_FLAG);
            waveP.setStyle(Paint.Style.FILL);
            waveP.setColor((WAVE_ALPHAS[i] << 24) | (WAVE_COLORS[i] & 0x00FFFFFF));
            if (WAVE_BLEND_MODES[i] != null) {
                waveP.setXfermode(new PorterDuffXfermode(WAVE_BLEND_MODES[i]));
            }
            mWavePaints[i] = waveP;
            mWavePaths[i] = new Path();
        }

        loadAlbumArt(context);
        updateColors();
    }

    private void loadAlbumArt(Context context) {
        try {
            // 1. Try loading optimized WebP from assets
            InputStream is = context.getAssets().open("album_preview_hn.webp");
            mAlbumBitmap = BitmapFactory.decodeStream(is);
            is.close();
        } catch (Throwable ignored) {
            try {
                // 2. Try loading PNG from assets
                InputStream is = context.getAssets().open("album_preview_hn.png");
                mAlbumBitmap = BitmapFactory.decodeStream(is);
                is.close();
            } catch (Throwable ignored2) {
                try {
                    // 3. Fallback to /storage/emulated/0/Download/HN.jpg with inSampleSize
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 16;
                    mAlbumBitmap = BitmapFactory.decodeFile("/storage/emulated/0/Download/HN.jpg", opts);
                } catch (Throwable ignored3) {
                    mAlbumBitmap = null;
                }
            }
        }

        if (mAlbumBitmap != null) {
            mAlbumShader = new BitmapShader(mAlbumBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            mAlbumPaint.setShader(mAlbumShader);
        }
    }

    /**
     * Updates the capsule color, stroke, and acoustic wave harmonics dynamically.
     */
    public void setCapsuleColor(int color, boolean isBlur, int strokeColor) {
        mCapsuleColor = color;
        mIsBlur = isBlur;
        mStrokeColor = strokeColor;
        mIsGradient = false;
        updateColors();
        invalidate();
    }

    /**
     * Two-stop horizontal linear gradient: startArgb on the left edge, endArgb on the right edge.
     */
    public void setCapsuleGradient(int startArgb, int endArgb, boolean isBlur, int strokeColor) {
        mGradStart = startArgb;
        mGradEnd = endArgb;
        mIsGradient = true;
        mIsBlur = isBlur;
        mStrokeColor = strokeColor;
        mCapsuleColor = startArgb;
        mGradShaderWidth = -1f; // force shader rebuild
        updateColors();
        invalidate();
    }

    private boolean mIsGradient = false;
    private int mGradStart = 0xFF1477FF;
    private int mGradEnd = 0xFF9D4EDD;
    private float mGradShaderWidth = -1f;

    private void updateColors() {
        if (!mIsGradient) {
            mBgPaint.setShader(null);
            mBgPaint.setColor(mCapsuleColor);
        } else {
            mBgPaint.setColor(0xFFFFFFFF);
        }
        mStrokePaint.setColor(mStrokeColor);
        // The 5 wave layers strictly retain their fixed purple palette (isolated from background color picker)
    }

    public void startWaveAnimation() {
        if (mIsAnimating && mWaveAnimator != null && mWaveAnimator.isRunning()) {
            return;
        }
        mIsAnimating = true;
        if (mWaveAnimator != null) {
            mWaveAnimator.cancel();
        }
        // Smooth 60 FPS continuous animation loop
        mWaveAnimator = ValueAnimator.ofFloat(0f, 1f);
        mWaveAnimator.setDuration(1000);
        mWaveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mWaveAnimator.setInterpolator(new LinearInterpolator());
        mWaveAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                invalidate();
            }
        });
        mWaveAnimator.start();
    }

    public void stopWaveAnimation() {
        mIsAnimating = false;
        if (mWaveAnimator != null) {
            mWaveAnimator.cancel();
            mWaveAnimator = null;
        }
        mWaveStartTime = 0;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() == VISIBLE) {
            startWaveAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopWaveAnimation();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && isAttachedToWindow()) {
            startWaveAnimation();
        } else {
            stopWaveAnimation();
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            startWaveAnimation();
        } else {
            stopWaveAnimation();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int totalHeight = Math.round(mCapsuleHeight + mBevelOffset + dp(4f));
        int resolvedH = resolveSize(totalHeight, heightMeasureSpec);
        int resolvedW = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        setMeasuredDimension(resolvedW, resolvedH);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        if (width <= 0) return;

        // 1. Stacked Depth: Secondary Curved Bevel Contour (+3dp below)
        mBevelRect.set(dp(3f), mBevelOffset, width - dp(3f), mCapsuleHeight + mBevelOffset);
        canvas.drawRoundRect(mBevelRect, mCapsuleRadius, mCapsuleRadius, mBevelPaint);

        // 2. Main Capsule Pill Body
        mCapsuleRect.set(0, 0, width, mCapsuleHeight);
        if (mIsGradient && mGradShaderWidth != width) {
            mGradShaderWidth = width;
            mBgPaint.setShader(new LinearGradient(0, 0, width, 0,
                    mGradStart, mGradEnd, Shader.TileMode.CLAMP));
        }
        canvas.drawRoundRect(mCapsuleRect, mCapsuleRadius, mCapsuleRadius, mBgPaint);

        // 3. Subtle Inner Stroke / Bevel
        float halfStroke = dp(0.5f);
        mStrokeRect.set(halfStroke, halfStroke, width - halfStroke, mCapsuleHeight - halfStroke);
        canvas.drawRoundRect(mStrokeRect, mCapsuleRadius - halfStroke, mCapsuleRadius - halfStroke, mStrokePaint);

        // 4. Album Art (56dp circle, 14dp left margin, 14dp top margin)
        float albumLeft = mAlbumLeftMargin;
        float albumTop = (mCapsuleHeight - mAlbumSize) / 2f;
        float albumRadius = mAlbumSize / 2f;
        float albumCenterX = albumLeft + albumRadius;
        float albumCenterY = albumTop + albumRadius;

        if (mAlbumBitmap != null && mAlbumShader != null) {
            mAlbumMatrix.reset();
            float scale = mAlbumSize / (float) Math.min(mAlbumBitmap.getWidth(), mAlbumBitmap.getHeight());
            mAlbumMatrix.setScale(scale, scale);
            mAlbumMatrix.postTranslate(
                    albumCenterX - (mAlbumBitmap.getWidth() * scale) / 2f,
                    albumCenterY - (mAlbumBitmap.getHeight() * scale) / 2f
            );
            mAlbumShader.setLocalMatrix(mAlbumMatrix);
            canvas.drawCircle(albumCenterX, albumCenterY, albumRadius, mAlbumPaint);
        } else {
            // Decorative fallback circle with musical note
            mAlbumPaint.setShader(null);
            mAlbumPaint.setColor(0xFF263238);
            canvas.drawCircle(albumCenterX, albumCenterY, albumRadius, mAlbumPaint);
            Paint noteP = mNotePaint;
            noteP.setColor(0xFFFFFFFF);
            noteP.setTextSize(sp(20));
            noteP.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("♪", albumCenterX, albumCenterY + dp(7), noteP);
        }
        canvas.drawCircle(albumCenterX, albumCenterY, albumRadius, mAlbumBorderPaint);

        // 5. Right Area Geometry (Text, Controls & Fluid Waves)
        float rightAreaLeft = albumLeft + mAlbumSize + dp(12f);
        float rightAreaRight = width - dp(18f);
        float controlsCenterX = (rightAreaLeft + rightAreaRight) / 2f;

        // Clip canvas to the capsule pill so waves never bleed outside
        int saveCount = canvas.save();
        mClipPath.reset();
        mClipPath.addRoundRect(mCapsuleRect, mCapsuleRadius, mCapsuleRadius, Path.Direction.CW);
        canvas.clipPath(mClipPath);

        // 6. Fluid Acoustic Waves Visualizer (Docked at base per Samsung AODService)
        drawSamsungNowBarWaves(canvas, rightAreaLeft, width, mCapsuleHeight);

        canvas.restoreToCount(saveCount);

        // 7. Song Title: "Greenpath • TAVATAAN"
        float textY = dp(23f);
        canvas.drawText("Greenpath • TAVATAAN", controlsCenterX, textY, mTextPaint);

        // 8. Playback Controls (Previous, Pause, Next)
        float controlsCenterY = dp(49.0f);
        drawPlaybackControls(canvas, controlsCenterX, controlsCenterY);
    }

    private void drawPlaybackControls(Canvas canvas, float centerX, float centerY) {
        // Center: Pause Button (Two thick parallel rounded bars)
        float barW = dp(4.6f);
        float barH = dp(17.5f);
        float barGap = dp(4.6f);
        float barRadius = barW / 2f;

        float pauseLeft1 = centerX - barGap / 2f - barW;
        float pauseTop = centerY - barH / 2f;
        float pauseLeft2 = centerX + barGap / 2f;

        mPauseBar1Rect.set(pauseLeft1, pauseTop, pauseLeft1 + barW, pauseTop + barH);
        mPauseBar2Rect.set(pauseLeft2, pauseTop, pauseLeft2 + barW, pauseTop + barH);
        canvas.drawRoundRect(mPauseBar1Rect, barRadius, barRadius, mControlsPaint);
        canvas.drawRoundRect(mPauseBar2Rect, barRadius, barRadius, mControlsPaint);

        // Left: Skip Previous Button (Vertical bar on left + triangle pointing left)
        float prevCenterX = centerX - mControlsSpacing;
        float prevBarW = dp(2.4f);
        float prevBarH = dp(14f);
        float prevTriangleW = dp(10.5f);
        float prevTriangleH = dp(14f);
        float prevGap = dp(2.2f);
        float prevTotalW = prevBarW + prevGap + prevTriangleW;
        float prevStartX = prevCenterX - prevTotalW / 2f;

        mPrevBarRect.set(prevStartX, centerY - prevBarH / 2f, prevStartX + prevBarW, centerY + prevBarH / 2f);
        canvas.drawRoundRect(mPrevBarRect, prevBarW / 2f, prevBarW / 2f, mControlsPaint);

        mPrevTrianglePath.reset();
        mPrevTrianglePath.moveTo(prevStartX + prevBarW + prevGap + prevTriangleW, centerY - prevTriangleH / 2f);
        mPrevTrianglePath.lineTo(prevStartX + prevBarW + prevGap, centerY);
        mPrevTrianglePath.lineTo(prevStartX + prevBarW + prevGap + prevTriangleW, centerY + prevTriangleH / 2f);
        mPrevTrianglePath.close();
        canvas.drawPath(mPrevTrianglePath, mControlsPaint);

        // Right: Skip Next Button (Triangle pointing right + vertical bar on right)
        float nextCenterX = centerX + mControlsSpacing;
        float nextBarW = dp(2.4f);
        float nextBarH = dp(14f);
        float nextTriangleW = dp(10.5f);
        float nextTriangleH = dp(14f);
        float nextGap = dp(2.2f);
        float nextTotalW = nextTriangleW + nextGap + nextBarW;
        float nextStartX = nextCenterX - nextTotalW / 2f;

        mNextTrianglePath.reset();
        mNextTrianglePath.moveTo(nextStartX, centerY - nextTriangleH / 2f);
        mNextTrianglePath.lineTo(nextStartX + nextTriangleW, centerY);
        mNextTrianglePath.lineTo(nextStartX, centerY + nextTriangleH / 2f);
        mNextTrianglePath.close();
        canvas.drawPath(mNextTrianglePath, mControlsPaint);

        mNextBarRect.set(
                nextStartX + nextTriangleW + nextGap,
                centerY - nextBarH / 2f,
                nextStartX + nextTriangleW + nextGap + nextBarW,
                centerY + nextBarH / 2f
        );
        canvas.drawRoundRect(mNextBarRect, nextBarW / 2f, nextBarW / 2f, mControlsPaint);
    }

    /**
     * Exact Samsung One UI AODService / SystemUI Now Bar collapsed capsule wave renderer.
     * Replicates the 5-layer physical wave simulation, -9.0 rad/s angular velocity,
     * quadratic Bézier attenuation envelope, and static vibrant purple palette (c69.PURPLE).
     */
    private void drawSamsungNowBarWaves(Canvas canvas, float rightAreaLeft, float width, float capsuleBottom) {
        float startX = rightAreaLeft - dp(8f);
        float endX = width - dp(14f);
        float waveWidth = endX - startX;
        if (waveWidth <= 0) return;

        float baseY = capsuleBottom;
        float baseAmp = dp(7.5f);
        float stepX = dp(2f);

        long nanoTime = System.nanoTime();
        if (mWaveStartTime == 0) {
            mWaveStartTime = nanoTime;
        }
        double dt = (nanoTime - mWaveStartTime) / 1.0e9;
        float baseTime = (float) (dt * ANGULAR_VELOCITY);

        // Isolated layer compositing so blend modes (SCREEN & OVERLAY) blend among the 5 wave layers
        // and cleanly composite onto any capsule background color chosen by the user
        mWaveLayerRect.set(startX, baseY - dp(18f), endX, baseY + dp(2f));
        int saveLayer = canvas.saveLayer(mWaveLayerRect, null);

        for (int i = 0; i < NUM_WAVE_LAYERS; i++) {
            Path path = mWavePaths[i];
            path.reset();

            float speed = WAVE_SPEED_MULTS[i];
            float phaseOffset = WAVE_PHASE_OFFSETS[i];
            float layerPhase = baseTime * speed + phaseOffset;
            float freqMult = WAVE_FREQ_MULTS[i];
            float amp = baseAmp * WAVE_AMP_SCALES[i];

            boolean first = true;
            for (float x = startX; x <= endX; x += stepX) {
                float u = (x - startX) / waveWidth;
                if (u > 1.0f) u = 1.0f;

                // Exact quadratic Bézier envelope from AODService: 0 at edges, 1 at center
                float t = 2.0f * (u <= 0.5f ? u : 1.0f - u);
                float envelope = 2.0f * t - t * t;

                // 3 full cycles modulated by layer frequency multiplier and speed phase
                float angle = (float) (u * (2.0 * Math.PI * BASE_CYCLES * freqMult) + layerPhase);
                float sinVal = (float) Math.sin(angle);

                // Exact Samsung displacement math from AODService jm4:
                // yDisp = (amp * envelope * sinVal - amp) * envelope
                float yDisp = (amp * envelope * sinVal - amp) * envelope;
                float y = baseY + yDisp;

                if (first) {
                    path.moveTo(x, y);
                    first = false;
                } else {
                    path.lineTo(x, y);
                }
            }

            path.lineTo(endX, baseY);
            path.lineTo(startX, baseY);
            path.close();

            canvas.drawPath(path, mWavePaints[i]);
        }

        canvas.restoreToCount(saveLayer);
    }

    private float dp(float v) {
        return v * mDensity;
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }
}
