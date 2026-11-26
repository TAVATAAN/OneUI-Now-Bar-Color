package io.github.tavataan.nowbarcolor;

import android.content.res.ColorStateList;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;

/** Detect mutations of the SAME drawable without allocating getColors() arrays per frame. */
final class CapsuleGradient extends GradientDrawable {
    private boolean damaged = true;
    private boolean configuring;
    private int startColor;
    private int endColor;
    private float corner;
    private ColorStateList tint;
    private ColorFilter filter;

    private boolean glassHighlight;
    private boolean glassBorder;
    private float strokeWidthPx;
    private float glassVisibility = 1.0f;

    private Paint sheenPaint;
    private Paint borderPaint;
    private RectF rectF;
    private RectF strokeRectF;
    private int cachedW = -1;
    private int cachedH = -1;
    private float cachedVis = -1.0f;
    private boolean cachedBorder = false;

    boolean needsRepair() { return damaged; }

    private void changed() { if (!configuring) damaged = true; }

    void configure(int start, int end, float radius) {
        configure(start, end, radius, false, false, 0.0f, 1.0f);
    }

    void configure(int start, int end, float radius,
                   boolean withGlassHighlight, boolean withGlassBorder,
                   float borderStrokePx, float visibility) {
        float vis = Math.max(0.0f, Math.min(1.0f, visibility));
        if (!damaged && startColor == start && endColor == end && corner == radius
                && glassHighlight == withGlassHighlight
                && glassBorder == withGlassBorder
                && Math.abs(strokeWidthPx - borderStrokePx) < 0.05f
                && Math.abs(glassVisibility - vis) < 0.005f) {
            return;
        }
        configuring = true;
        try {
            // Opacity belongs to the two ARGB stops, not to an inherited animation alpha.
            setAlpha(255);
            setVisible(true, false);
            setTintList(null);
            clearColorFilter();
            setShape(RECTANGLE);
            setGradientType(LINEAR_GRADIENT);
            setOrientation(Orientation.LEFT_RIGHT);
            setColors(new int[]{start, end});
            setCornerRadius(radius);
            startColor = start;
            endColor = end;
            corner = radius;
            glassHighlight = withGlassHighlight;
            glassBorder = withGlassBorder;
            strokeWidthPx = borderStrokePx;
            glassVisibility = vis;
            if (Math.abs(cachedVis - vis) >= 0.005f || cachedBorder != withGlassBorder) {
                cachedW = -1;
            }
            damaged = false;
        } finally {
            configuring = false;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (startColor != 0 || endColor != 0) {
            super.draw(canvas);
        }
        if ((!glassHighlight && !glassBorder) || glassVisibility <= 0.005f) {
            return;
        }
        Rect bounds = getBounds();
        int w = bounds.width();
        int h = bounds.height();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (rectF == null) rectF = new RectF();
        rectF.set(bounds);
        float maxR = Math.min(w, h) * 0.5f;
        float r = corner > 0.0f ? Math.min(corner, maxR) : maxR;

        if (cachedW != w || cachedH != h || Math.abs(cachedVis - glassVisibility) >= 0.005f
                || cachedBorder != glassBorder) {
            cachedW = w;
            cachedH = h;
            cachedVis = glassVisibility;
            cachedBorder = glassBorder;
            if (glassHighlight) {
                if (sheenPaint == null) sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                sheenPaint.setShader(OneUiBlurSpec.createTopSheenShader(bounds.top, h, glassVisibility));
            }
            if (glassBorder && strokeWidthPx > 0.0f) {
                if (borderPaint == null) {
                    borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    borderPaint.setStyle(Paint.Style.STROKE);
                }
                borderPaint.setStrokeWidth(strokeWidthPx);
                borderPaint.setShader(OneUiBlurSpec.createSpecularBorderShader(
                        bounds.left, bounds.top, bounds.right, bounds.bottom, glassVisibility));
            }
        }

        if (glassHighlight && sheenPaint != null) {
            canvas.drawRoundRect(rectF, r, r, sheenPaint);
        }
        if (glassBorder && borderPaint != null && strokeWidthPx > 0.0f) {
            if (strokeRectF == null) strokeRectF = new RectF();
            float half = strokeWidthPx * 0.5f;
            strokeRectF.set(bounds.left + half, bounds.top + half, bounds.right - half, bounds.bottom - half);
            float sr = Math.max(0.0f, r - half);
            canvas.drawRoundRect(strokeRectF, sr, sr, borderPaint);
        }
    }

    @Override public void setAlpha(int alpha) {
        if (getAlpha() != alpha) changed();
        super.setAlpha(alpha);
    }
    @Override public void setColor(int color) { changed(); super.setColor(color); }
    @Override public void setColor(ColorStateList color) { changed(); super.setColor(color); }
    @Override public void setColors(int[] colors) { changed(); super.setColors(colors); }
    @Override public void setColors(int[] colors, float[] offsets) { changed(); super.setColors(colors, offsets); }
    @Override public void setTintList(ColorStateList value) {
        if (tint != value) changed();
        super.setTintList(value);
        tint = value;
    }
    @Override public void setTintMode(PorterDuff.Mode mode) { changed(); super.setTintMode(mode); }
    @Override public void setTintBlendMode(BlendMode mode) { changed(); super.setTintBlendMode(mode); }
    @Override public void setColorFilter(ColorFilter value) {
        if (filter != value) changed();
        super.setColorFilter(value);
        filter = value;
    }
    @Override public boolean setVisible(boolean visible, boolean restart) {
        if (isVisible() != visible) changed();
        return super.setVisible(visible, restart);
    }
    @Override public void setCornerRadius(float radius) {
        if (getCornerRadius() != radius) changed();
        super.setCornerRadius(radius);
    }
    @Override public void setOrientation(Orientation orientation) {
        if (getOrientation() != orientation) changed();
        super.setOrientation(orientation);
    }
    @Override public void setShape(int shape) { changed(); super.setShape(shape); }
    @Override public void setGradientType(int type) { changed(); super.setGradientType(type); }
}
