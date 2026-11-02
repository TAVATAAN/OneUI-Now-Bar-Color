package com.tavataan.nowbarcolor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Advanced HSV Color Picker Component in Dark Mode.
 *
 * Exact 3-section vertical structure:
 * 1. Header: Two pill-shaped boxes side-by-side. Both update their background in real time
 *    to the selected color. Left pill displays "Current color", right pill displays the 6-digit HEX code.
 * 2. Body (2D Color Canvas): Square area where X controls Hue (0°..360°) and Y controls
 *    Value/Brightness (black at top, full brightness at bottom). Includes a draggable small square cursor
 *    with a red border and dark outline.
 * 3. Footer (Saturation Slider): "Saturation" text label above a custom horizontal slider with
 *    a thick hot pink active track, a thin vertical line thumb, a separated dark gray inactive track,
 *    and a small pink dot at the far right end.
 */
public class HsvColorPickerView extends LinearLayout {

    public interface OnColorChangeListener {
        void onColorChanged(int color, float[] hsv, String hex6);
    }

    public interface OnColorAndOpacityChangeListener {
        void onColorAndOpacityChanged(int rgbColor, int opacityPercent, int finalArgb);
    }

    private final float[] mHsv = new float[]{0f, 1f, 1f}; // [0]=Hue(0..360), [1]=Sat(0..1), [2]=Val(0..1)
    private int mOpacity = 92; // 0..100
    private boolean mShowOpacitySlider = false;
    private OnColorChangeListener mListener;
    private OnColorAndOpacityChangeListener mColorAndOpacityListener;

    private TextView mPillCurrentColor;
    private TextView mPillHexCode;
    private Canvas2DView mCanvas2D;
    private SaturationSliderView mSatSlider;
    private LinearLayout mOpacitySectionContainer;
    private TextView mOpacityLabel;
    private OpacitySliderView mOpacitySlider;

    public HsvColorPickerView(Context context) {
        super(context);
        init(context);
    }

    public HsvColorPickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setPadding(dp(18), dp(18), dp(18), dp(18));

        // Dark container styling: very dark gray background with smooth rounded corners
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF161822);
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), 0xFF282E3E);
        setBackground(bg);

        // ==========================================
        // 1. CABECERA (Header): Two pill boxes
        // ==========================================
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        headerLp.bottomMargin = dp(16);
        addView(headerRow, headerLp);

        // Left Pill: "Current color"
        mPillCurrentColor = new TextView(context);
        mPillCurrentColor.setText("Current color");
        mPillCurrentColor.setTextColor(Color.WHITE);
        mPillCurrentColor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mPillCurrentColor.setTypeface(Typeface.DEFAULT_BOLD);
        mPillCurrentColor.setGravity(Gravity.CENTER);
        mPillCurrentColor.setShadowLayer(dp(2), 0, dp(1), 0x99000000);
        LinearLayout.LayoutParams leftPillLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        leftPillLp.rightMargin = dp(6);
        headerRow.addView(mPillCurrentColor, leftPillLp);

        // Right Pill: 6-digit HEX (e.g. "4337B2")
        mPillHexCode = new TextView(context);
        mPillHexCode.setText("FFFFFF");
        mPillHexCode.setTextColor(Color.WHITE);
        mPillHexCode.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mPillHexCode.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        mPillHexCode.setGravity(Gravity.CENTER);
        mPillHexCode.setShadowLayer(dp(2), 0, dp(1), 0x99000000);
        LinearLayout.LayoutParams rightPillLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        rightPillLp.leftMargin = dp(6);
        headerRow.addView(mPillHexCode, rightPillLp);

        // ==========================================
        // 2. CUERPO (2D Color Canvas)
        // ==========================================
        mCanvas2D = new Canvas2DView(context);
        LinearLayout.LayoutParams canvasLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        canvasLp.bottomMargin = dp(16);
        addView(mCanvas2D, canvasLp);

        // ==========================================
        // 3. PIE (Saturation Slider)
        // ==========================================
        TextView satLabel = new TextView(context);
        satLabel.setText("Saturation");
        satLabel.setTextColor(0xFFD6DCE8);
        satLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        satLabel.setTypeface(Typeface.DEFAULT_BOLD);
        satLabel.setGravity(Gravity.START);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.bottomMargin = dp(8);
        addView(satLabel, labelLp);

        mSatSlider = new SaturationSliderView(context);
        LinearLayout.LayoutParams sliderLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        addView(mSatSlider, sliderLp);

        // Synchronize listeners between Canvas and Slider
        mCanvas2D.setOnHueValChangeListener(new Canvas2DView.OnHueValChangeListener() {
            @Override
            public void onHueValChanged(float hue, float value) {
                mHsv[0] = hue;
                mHsv[2] = value;
                updateHeaderAndNotify();
            }
        });

        mSatSlider.setOnSaturationChangeListener(new SaturationSliderView.OnSaturationChangeListener() {
            @Override
            public void onSaturationChanged(float saturation) {
                mHsv[1] = saturation;
                mCanvas2D.setSaturation(saturation);
                updateHeaderAndNotify();
            }
        });

        // ==========================================
        // 4. OPACIDAD (Opacity Slider)
        // ==========================================
        mOpacitySectionContainer = new LinearLayout(context);
        mOpacitySectionContainer.setOrientation(VERTICAL);
        mOpacitySectionContainer.setVisibility(mShowOpacitySlider ? VISIBLE : GONE);
        LinearLayout.LayoutParams opSecLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        opSecLp.topMargin = dp(14);

        mOpacityLabel = new TextView(context);
        mOpacityLabel.setText("Opacidad: " + mOpacity + "%");
        mOpacityLabel.setTextColor(0xFFD6DCE8);
        mOpacityLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mOpacityLabel.setTypeface(Typeface.DEFAULT_BOLD);
        mOpacityLabel.setGravity(Gravity.START);
        LinearLayout.LayoutParams opLabelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        opLabelLp.bottomMargin = dp(8);
        mOpacitySectionContainer.addView(mOpacityLabel, opLabelLp);

        mOpacitySlider = new OpacitySliderView(context);
        mOpacitySlider.setOpacity(mOpacity / 100f);
        LinearLayout.LayoutParams opSliderLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        mOpacitySectionContainer.addView(mOpacitySlider, opSliderLp);

        mOpacitySlider.setOnOpacityChangeListener(new OpacitySliderView.OnOpacityChangeListener() {
            @Override
            public void onOpacityChanged(float opacity) {
                mOpacity = Math.round(opacity * 100f);
                if (mOpacityLabel != null) {
                    mOpacityLabel.setText("Opacidad: " + mOpacity + "%");
                }
                updateHeaderAndNotify();
            }
        });

        addView(mOpacitySectionContainer, opSecLp);

        updateHeaderAndNotify();
    }

    public void setOpacitySliderVisible(boolean visible) {
        this.mShowOpacitySlider = visible;
        if (mOpacitySectionContainer != null) {
            mOpacitySectionContainer.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    public boolean isOpacitySliderVisible() {
        return mShowOpacitySlider;
    }

    public void setOpacity(int opacityPercent) {
        this.mOpacity = Math.max(0, Math.min(100, opacityPercent));
        if (mOpacitySlider != null) {
            mOpacitySlider.setOpacity(this.mOpacity / 100f);
        }
        if (mOpacityLabel != null) {
            mOpacityLabel.setText("Opacidad: " + this.mOpacity + "%");
        }
    }

    public int getOpacity() {
        return mOpacity;
    }

    public int getFinalArgb() {
        int rgb = Color.HSVToColor(mHsv) & 0x00FFFFFF;
        int alpha = Math.round((mOpacity / 100.0f) * 255.0f);
        return (alpha << 24) | rgb;
    }

    public void setOnColorAndOpacityChangeListener(OnColorAndOpacityChangeListener listener) {
        this.mColorAndOpacityListener = listener;
    }

    public void setColor(int color) {
        Color.colorToHSV(color | 0xFF000000, mHsv);
        if (mCanvas2D != null) {
            mCanvas2D.setHueAndVal(mHsv[0], mHsv[2], mHsv[1]);
        }
        if (mSatSlider != null) {
            mSatSlider.setSaturation(mHsv[1]);
        }
        updateHeaderAndNotify();
    }

    public int getColor() {
        return Color.HSVToColor(mHsv);
    }

    public void setOnColorChangeListener(OnColorChangeListener listener) {
        this.mListener = listener;
    }

    private void updateHeaderAndNotify() {
        int currentColor = Color.HSVToColor(mHsv);
        String hex6 = String.format("%06X", (currentColor & 0x00FFFFFF));

        // Update Pill background with rounded corners
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(currentColor | 0xFF000000);
        pillBg.setCornerRadius(dp(23));
        pillBg.setStroke(dp(1), 0x33FFFFFF);

        GradientDrawable pillBg2 = new GradientDrawable();
        pillBg2.setColor(currentColor | 0xFF000000);
        pillBg2.setCornerRadius(dp(23));
        pillBg2.setStroke(dp(1), 0x33FFFFFF);

        mPillCurrentColor.setBackground(pillBg);
        mPillHexCode.setBackground(pillBg2);
        mPillHexCode.setText(hex6);

        if (mListener != null) {
            mListener.onColorChanged(currentColor, mHsv, hex6);
        }
        if (mColorAndOpacityListener != null) {
            int alpha = Math.round((mOpacity / 100.0f) * 255.0f);
            int finalArgb = (alpha << 24) | (currentColor & 0x00FFFFFF);
            mColorAndOpacityListener.onColorAndOpacityChanged(currentColor, mOpacity, finalArgb);
        }
    }

    // =========================================================================
    // 2D Color Canvas (X: Hue, Y: Value/Brightness)
    // =========================================================================
    public static class Canvas2DView extends View {

        interface OnHueValChangeListener {
            void onHueValChanged(float hue, float value);
        }

        private float mHue = 0f;       // 0..360
        private float mValue = 1f;     // 0..1 (top: 0=black, bottom: 1=max brightness)
        private float mSaturation = 1f;// 0..1

        private OnHueValChangeListener mListener;
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mCursorStrokeDark = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mCursorStrokeRed = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Path mClipPath = new Path();
        private final RectF mBounds = new RectF();
        private Shader mShader;
        private int mLastWidth = 0;
        private int mLastHeight = 0;

        public Canvas2DView(Context context) {
            super(context);
            init();
        }

        public Canvas2DView(Context context, AttributeSet attrs) {
            super(context, attrs);
            init();
        }

        private int dp(float value) {
            return Math.round(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
        }

        private void init() {
            // Dark outline for cursor
            mCursorStrokeDark.setStyle(Paint.Style.STROKE);
            mCursorStrokeDark.setColor(0xFF0F1118);
            mCursorStrokeDark.setStrokeWidth(dp(4.0f));

            // Vibrant red border for cursor
            mCursorStrokeRed.setStyle(Paint.Style.STROKE);
            mCursorStrokeRed.setColor(0xFFFF1744);
            mCursorStrokeRed.setStrokeWidth(dp(2.0f));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            // Maintain square aspect ratio
            setMeasuredDimension(width, width);
        }

        public void setHueAndVal(float hue, float val, float sat) {
            this.mHue = Math.max(0f, Math.min(360f, hue));
            this.mValue = Math.max(0f, Math.min(1f, val));
            this.mSaturation = Math.max(0f, Math.min(1f, sat));
            mShader = null;
            invalidate();
        }

        public void setSaturation(float sat) {
            this.mSaturation = Math.max(0f, Math.min(1f, sat));
            mShader = null;
            invalidate();
        }

        public void setOnHueValChangeListener(OnHueValChangeListener listener) {
            this.mListener = listener;
        }

        private void rebuildShader(int w, int h) {
            if (w <= 0 || h <= 0) return;

            // X axis: Hue (0..360) at current saturation and value=1
            int[] hueColors = new int[]{
                    Color.HSVToColor(new float[]{0f, mSaturation, 1f}),
                    Color.HSVToColor(new float[]{60f, mSaturation, 1f}),
                    Color.HSVToColor(new float[]{120f, mSaturation, 1f}),
                    Color.HSVToColor(new float[]{180f, mSaturation, 1f}),
                    Color.HSVToColor(new float[]{240f, mSaturation, 1f}),
                    Color.HSVToColor(new float[]{300f, mSaturation, 1f}),
                    Color.HSVToColor(new float[]{360f, mSaturation, 1f})
            };
            float[] huePositions = new float[]{0f, 1f/6f, 2f/6f, 3f/6f, 4f/6f, 5f/6f, 1f};
            Shader hueShader = new LinearGradient(0, 0, w, 0, hueColors, huePositions, Shader.TileMode.CLAMP);

            // Y axis: Value / Brightness (top: black, bottom: maximum brightness)
            Shader valShader = new LinearGradient(0, 0, 0, h, 0xFF000000, 0xFFFFFFFF, Shader.TileMode.CLAMP);

            // Combine shaders with MULTIPLY to scale brightness smoothly
            mShader = new ComposeShader(hueShader, valShader, PorterDuff.Mode.MULTIPLY);
            mPaint.setShader(mShader);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            if (mShader == null || w != mLastWidth || h != mLastHeight) {
                mLastWidth = w;
                mLastHeight = h;
                rebuildShader(w, h);
                mBounds.set(0, 0, w, h);
                mClipPath.reset();
                mClipPath.addRoundRect(mBounds, dp(16), dp(16), Path.Direction.CW);
            }

            canvas.save();
            canvas.clipPath(mClipPath);
            canvas.drawRect(mBounds, mPaint);
            canvas.restore();

            // Draw cursor: small square with red border and dark outline
            float cursorX = (mHue / 360f) * w;
            float cursorY = mValue * h;

            float cursorSize = dp(18);
            float half = cursorSize / 2f;
            float left = Math.max(half, Math.min(w - half, cursorX)) - half;
            float top = Math.max(half, Math.min(h - half, cursorY)) - half;
            RectF cursorRect = new RectF(left, top, left + cursorSize, top + cursorSize);

            // Dark outline first, then red border
            canvas.drawRoundRect(cursorRect, dp(3), dp(3), mCursorStrokeDark);
            canvas.drawRoundRect(cursorRect, dp(3), dp(3), mCursorStrokeRed);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                getParent().requestDisallowInterceptTouchEvent(true);
                float x = Math.max(0f, Math.min(getWidth(), event.getX()));
                float y = Math.max(0f, Math.min(getHeight(), event.getY()));

                mHue = (x / getWidth()) * 360f;
                if (mHue >= 360f) mHue = 359.99f;
                mValue = y / getHeight();

                invalidate();
                if (mListener != null) {
                    mListener.onHueValChanged(mHue, mValue);
                }
                return true;
            }
            return super.onTouchEvent(event);
        }
    }

    // =========================================================================
    // Custom Saturation Slider View
    // =========================================================================
    public static class SaturationSliderView extends View {

        interface OnSaturationChangeListener {
            void onSaturationChanged(float saturation);
        }

        private float mSaturation = 1f; // 0..1
        private OnSaturationChangeListener mListener;

        private final Paint mActiveTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mInactiveTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private static final int COLOR_HOT_PINK = 0xFFFF2D75;
        private static final int COLOR_INACTIVE_GRAY = 0xFF2A2E3D;

        public SaturationSliderView(Context context) {
            super(context);
            init();
        }

        public SaturationSliderView(Context context, AttributeSet attrs) {
            super(context, attrs);
            init();
        }

        private int dp(float value) {
            return Math.round(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
        }

        private void init() {
            // Thick hot pink active track with rounded caps
            mActiveTrackPaint.setColor(COLOR_HOT_PINK);
            mActiveTrackPaint.setStyle(Paint.Style.STROKE);
            mActiveTrackPaint.setStrokeCap(Paint.Cap.ROUND);

            // Inactive separated dark gray track
            mInactiveTrackPaint.setColor(COLOR_INACTIVE_GRAY);
            mInactiveTrackPaint.setStyle(Paint.Style.STROKE);
            mInactiveTrackPaint.setStrokeCap(Paint.Cap.ROUND);

            // Thin vertical line thumb in hot pink
            mThumbPaint.setColor(COLOR_HOT_PINK);
            mThumbPaint.setStyle(Paint.Style.STROKE);
            mThumbPaint.setStrokeCap(Paint.Cap.ROUND);

            // Small pink dot on the far right end
            mDotPaint.setColor(COLOR_HOT_PINK);
            mDotPaint.setStyle(Paint.Style.FILL);
        }

        public void setSaturation(float sat) {
            this.mSaturation = Math.max(0f, Math.min(1f, sat));
            invalidate();
        }

        public void setOnSaturationChangeListener(OnSaturationChangeListener listener) {
            this.mListener = listener;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float padStart = dp(14);
            float padEnd = dp(14);
            float trackY = h / 2f;
            float dotX = w - padEnd;
            float trackStart = padStart;
            float trackEnd = dotX - dp(12);
            float availableW = trackEnd - trackStart;

            float thumbX = trackStart + (mSaturation * availableW);

            float trackThick = dp(8);
            float inactiveThick = dp(6);
            float thumbWidth = dp(2.5f);
            float thumbHeight = dp(24);
            float dotRadius = dp(3.5f);
            float gap = dp(7);

            mActiveTrackPaint.setStrokeWidth(trackThick);
            mInactiveTrackPaint.setStrokeWidth(inactiveThick);
            mThumbPaint.setStrokeWidth(thumbWidth);

            // 1. Active Track (Hot pink, rounded ends)
            if (thumbX > trackStart) {
                canvas.drawLine(trackStart, trackY, thumbX, trackY, mActiveTrackPaint);
            }

            // 2. Inactive Track (Dark gray, separated from thumb)
            float inactiveStart = thumbX + gap;
            if (trackEnd > inactiveStart) {
                canvas.drawLine(inactiveStart, trackY, trackEnd, trackY, mInactiveTrackPaint);
            }

            // 3. Small pink dot at the far right end
            canvas.drawCircle(dotX, trackY, dotRadius, mDotPaint);

            // 4. Thumb: thin vertical line in hot pink
            float thumbTop = trackY - (thumbHeight / 2f);
            float thumbBottom = trackY + (thumbHeight / 2f);
            canvas.drawLine(thumbX, thumbTop, thumbX, thumbBottom, mThumbPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                getParent().requestDisallowInterceptTouchEvent(true);
                float padStart = dp(14);
                float padEnd = dp(14);
                float dotX = getWidth() - padEnd;
                float trackStart = padStart;
                float trackEnd = dotX - dp(12);
                float availableW = trackEnd - trackStart;
                if (availableW > 0) {
                    float x = Math.max(trackStart, Math.min(trackEnd, event.getX()));
                    mSaturation = (x - trackStart) / availableW;
                    invalidate();
                    if (mListener != null) {
                        mListener.onSaturationChanged(mSaturation);
                    }
                }
                return true;
            }
            return super.onTouchEvent(event);
        }
    }

    // =========================================================================
    // Custom Opacity Slider View (0% .. 100%)
    // =========================================================================
    public static class OpacitySliderView extends View {

        interface OnOpacityChangeListener {
            void onOpacityChanged(float opacity);
        }

        private float mOpacity = 0.92f; // 0..1
        private OnOpacityChangeListener mListener;

        private final Paint mActiveTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mInactiveTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private static final int COLOR_HOT_PINK = 0xFFFF2D75;
        private static final int COLOR_INACTIVE_GRAY = 0xFF2A2E3D;

        public OpacitySliderView(Context context) {
            super(context);
            init();
        }

        public OpacitySliderView(Context context, AttributeSet attrs) {
            super(context, attrs);
            init();
        }

        private int dp(float value) {
            return Math.round(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
        }

        private void init() {
            // Thick active track with rounded caps
            mActiveTrackPaint.setColor(COLOR_HOT_PINK);
            mActiveTrackPaint.setStyle(Paint.Style.STROKE);
            mActiveTrackPaint.setStrokeCap(Paint.Cap.ROUND);

            // Inactive separated dark gray track
            mInactiveTrackPaint.setColor(COLOR_INACTIVE_GRAY);
            mInactiveTrackPaint.setStyle(Paint.Style.STROKE);
            mInactiveTrackPaint.setStrokeCap(Paint.Cap.ROUND);

            // Thin vertical line thumb
            mThumbPaint.setColor(COLOR_HOT_PINK);
            mThumbPaint.setStyle(Paint.Style.STROKE);
            mThumbPaint.setStrokeCap(Paint.Cap.ROUND);

            // Small pink dot on the far right end
            mDotPaint.setColor(COLOR_HOT_PINK);
            mDotPaint.setStyle(Paint.Style.FILL);
        }

        public void setOpacity(float op) {
            this.mOpacity = Math.max(0f, Math.min(1f, op));
            invalidate();
        }

        public void setOnOpacityChangeListener(OnOpacityChangeListener listener) {
            this.mListener = listener;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float padStart = dp(14);
            float padEnd = dp(14);
            float trackY = h / 2f;
            float dotX = w - padEnd;
            float trackStart = padStart;
            float trackEnd = dotX - dp(12);
            float availableW = trackEnd - trackStart;

            float thumbX = trackStart + (mOpacity * availableW);

            float trackThick = dp(8);
            float inactiveThick = dp(6);
            float thumbWidth = dp(2.5f);
            float thumbHeight = dp(24);
            float dotRadius = dp(3.5f);
            float gap = dp(7);

            mActiveTrackPaint.setStrokeWidth(trackThick);
            mInactiveTrackPaint.setStrokeWidth(inactiveThick);
            mThumbPaint.setStrokeWidth(thumbWidth);

            // 1. Active Track
            if (thumbX > trackStart) {
                canvas.drawLine(trackStart, trackY, thumbX, trackY, mActiveTrackPaint);
            }

            // 2. Inactive Track
            float inactiveStart = thumbX + gap;
            if (trackEnd > inactiveStart) {
                canvas.drawLine(inactiveStart, trackY, trackEnd, trackY, mInactiveTrackPaint);
            }

            // 3. Small pink dot at the far right end
            canvas.drawCircle(dotX, trackY, dotRadius, mDotPaint);

            // 4. Thumb: thin vertical line
            float thumbTop = trackY - (thumbHeight / 2f);
            float thumbBottom = trackY + (thumbHeight / 2f);
            canvas.drawLine(thumbX, thumbTop, thumbX, thumbBottom, mThumbPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                getParent().requestDisallowInterceptTouchEvent(true);
                float padStart = dp(14);
                float padEnd = dp(14);
                float dotX = getWidth() - padEnd;
                float trackStart = padStart;
                float trackEnd = dotX - dp(12);
                float availableW = trackEnd - trackStart;
                if (availableW > 0) {
                    float x = Math.max(trackStart, Math.min(trackEnd, event.getX()));
                    mOpacity = (x - trackStart) / availableW;
                    invalidate();
                    if (mListener != null) {
                        mListener.onOpacityChanged(mOpacity);
                    }
                }
                return true;
            }
            return super.onTouchEvent(event);
        }
    }
}
