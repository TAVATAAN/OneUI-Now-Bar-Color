package io.github.tavataan.nowbarcolor;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Hardware-accelerated capsule image drawable using BitmapShader + drawRoundRect (never clipPath).
 * Recomputes CENTER_CROP Matrix dynamically on bounds changes so Samsung expand/width animations
 * never stretch or deform the image. Draws live legibility scrim and dominant-tinted 1dp rim.
 */
final class CapsuleImageDrawable extends Drawable {
    private boolean damaged = true;
    private boolean configuring;

    private Bitmap bitmap;
    private BitmapShader bitmapShader;
    private int bitmapW = -1;
    private int bitmapH = -1;
    private final Matrix shaderMatrix = new Matrix();

    private int fallbackDomColor = 0xFF1E88E5;
    private float corner = 100f;
    private int scrimPercent = ConfigManager.DEFAULT_IMAGE_SCRIM;
    private int scrimMode = ConfigManager.SCRIM_UNIFORM;
    private boolean drawBorder;
    private float strokeWidthPx = 3f;
    private float visibility = 1.0f;

    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();
    private final RectF strokeRectF = new RectF();

    private int cachedW = -1;
    private int cachedH = -1;
    private int cachedScrimPercent = -1;
    private int cachedScrimMode = -1;
    private float cachedVis = -1.0f;
    private int cachedDomColor = 0;
    private boolean cachedBorder = false;

    private int alpha = 255;
    private ColorStateList tint;
    private ColorFilter filter;

    CapsuleImageDrawable() {
        borderPaint.setStyle(Paint.Style.STROKE);
    }

    boolean needsRepair() {
        return damaged;
    }

    private void changed() {
        if (!configuring) damaged = true;
    }

    void configure(
            Bitmap bmp, int domColor, float radius,
            int scrimPct, int scrimModeVal,
            boolean withBorder, float borderStrokePx, float vis) {
        int clampedScrim = ConfigManager.clampImageScrimPercent(scrimPct);
        int clampedMode = ConfigManager.clampScrimMode(scrimModeVal);
        float clampedVis = Math.max(0.0f, Math.min(1.0f, vis));
        float resolvedStroke = borderStrokePx > 0f ? borderStrokePx : 3.0f;
        int opaqueDom = domColor | 0xFF000000;

        if (!damaged && bitmap == bmp && fallbackDomColor == opaqueDom
                && Math.abs(corner - radius) < 0.05f
                && scrimPercent == clampedScrim
                && scrimMode == clampedMode
                && drawBorder == withBorder
                && Math.abs(strokeWidthPx - resolvedStroke) < 0.05f
                && Math.abs(visibility - clampedVis) < 0.005f) {
            return;
        }

        configuring = true;
        try {
            setAlpha(255);
            setVisible(true, false);
            setTintList(null);
            clearColorFilter();

            boolean bmpChanged = (bitmap != bmp);
            bitmap = bmp;
            if (bmpChanged) {
                if (bmp != null && bmp.getWidth() > 0 && bmp.getHeight() > 0) {
                    bitmapShader = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    imagePaint.setShader(bitmapShader);
                    bitmapW = bmp.getWidth();
                    bitmapH = bmp.getHeight();
                } else {
                    bitmapShader = null;
                    imagePaint.setShader(null);
                    bitmapW = -1;
                    bitmapH = -1;
                }
                cachedW = -1;
            }
            fallbackDomColor = opaqueDom;
            corner = radius;
            scrimPercent = clampedScrim;
            scrimMode = clampedMode;
            drawBorder = withBorder;
            strokeWidthPx = resolvedStroke;
            visibility = clampedVis;
            cachedW = -1;
            damaged = false;
            updateBoundsGeometry(getBounds());
            invalidateSelf();
        } finally {
            configuring = false;
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        cachedW = -1;
        updateBoundsGeometry(bounds);
    }

    private void updateBoundsGeometry(Rect bounds) {
        if (bounds == null) return;
        int w = bounds.width();
        int h = bounds.height();
        if (w <= 0 || h <= 0) return;

        if (cachedW == w && cachedH == h
                && cachedScrimPercent == scrimPercent
                && cachedScrimMode == scrimMode
                && Math.abs(cachedVis - visibility) < 0.005f
                && cachedDomColor == fallbackDomColor
                && cachedBorder == drawBorder) {
            return;
        }
        cachedW = w;
        cachedH = h;
        cachedScrimPercent = scrimPercent;
        cachedScrimMode = scrimMode;
        cachedVis = visibility;
        cachedDomColor = fallbackDomColor;
        cachedBorder = drawBorder;

        if (bitmapShader != null && bitmapW > 0 && bitmapH > 0) {
            float scale = Math.max(w / (float) bitmapW, h / (float) bitmapH);
            float dx = bounds.left + (w - bitmapW * scale) * 0.5f;
            float dy = bounds.top + (h - bitmapH * scale) * 0.5f;
            shaderMatrix.setScale(scale, scale);
            shaderMatrix.postTranslate(dx, dy);
            bitmapShader.setLocalMatrix(shaderMatrix);
        } else {
            imagePaint.setShader(null);
        }

        if (scrimPercent > 0 && visibility > 0.005f) {
            if (scrimMode == ConfigManager.SCRIM_GRADIENT) {
                float baseA = (scrimPercent / 100.0f) * visibility;
                int aLeft = clampByte(Math.round(Math.min(0.85f, baseA * 0.50f) * 255.0f));
                int aMid = clampByte(Math.round(Math.min(0.85f, baseA * 1.06f) * 255.0f));
                int aRight = clampByte(Math.round(Math.min(0.85f, baseA * 0.90f) * 255.0f));
                scrimPaint.setShader(new LinearGradient(
                        bounds.left, 0f, bounds.right, 0f,
                        new int[]{aLeft << 24, aMid << 24, aRight << 24},
                        new float[]{0.0f, 0.52f, 1.0f},
                        Shader.TileMode.CLAMP));
            } else {
                scrimPaint.setShader(null);
                int a = clampByte(Math.round((scrimPercent / 100.0f) * visibility * 255.0f));
                scrimPaint.setColor(a << 24);
            }
        } else {
            scrimPaint.setShader(null);
            scrimPaint.setColor(0);
        }

        if (drawBorder && strokeWidthPx > 0.0f && visibility > 0.005f) {
            borderPaint.setStrokeWidth(strokeWidthPx);
            borderPaint.setShader(OneUiBlurSpec.createTintedSpecularBorderShader(
                    bounds.left, bounds.top, bounds.right, bounds.bottom,
                    fallbackDomColor, visibility));
        }
    }

    private static int clampByte(int v) {
        return Math.max(0, Math.min(255, v));
    }

    @Override
    public void draw(Canvas canvas) {
        if (visibility <= 0.005f) return;
        Rect b = getBounds();
        int w = b.width();
        int h = b.height();
        if (w <= 0 || h <= 0) return;

        updateBoundsGeometry(b);
        rectF.set(b);
        float maxR = Math.min(w, h) * 0.5f;
        float r = corner > 0.0f ? Math.min(corner, maxR) : maxR;

        if (bitmapShader != null) {
            imagePaint.setShader(bitmapShader);
            imagePaint.setAlpha(clampByte(Math.round(visibility * 255.0f)));
        } else {
            imagePaint.setShader(null);
            imagePaint.setColor(ConfigManager.scaleAlpha(fallbackDomColor, visibility));
        }
        canvas.drawRoundRect(rectF, r, r, imagePaint);

        if (scrimPercent > 0) {
            canvas.drawRoundRect(rectF, r, r, scrimPaint);
        }

        if (drawBorder && strokeWidthPx > 0.0f) {
            float half = strokeWidthPx * 0.5f;
            strokeRectF.set(b.left + half, b.top + half, b.right - half, b.bottom - half);
            float sr = Math.max(0.0f, r - half);
            canvas.drawRoundRect(strokeRectF, sr, sr, borderPaint);
        }
    }

    @Override public int getAlpha() {
        return alpha;
    }

    @Override public void setAlpha(int value) {
        int clamped = clampByte(value);
        if (alpha != clamped) changed();
        alpha = clamped;
        invalidateSelf();
    }

    @Override public void setTintList(ColorStateList value) {
        if (tint != value) changed();
        super.setTintList(value);
        tint = value;
    }

    @Override public void setTintMode(PorterDuff.Mode mode) { changed(); super.setTintMode(mode); }
    @Override public void setTintBlendMode(BlendMode mode) { changed(); super.setTintBlendMode(mode); }

    @Override public ColorFilter getColorFilter() {
        return filter;
    }

    @Override public void setColorFilter(ColorFilter value) {
        if (filter != value) changed();
        filter = value;
        imagePaint.setColorFilter(value);
        invalidateSelf();
    }

    @Override public boolean setVisible(boolean visible, boolean restart) {
        if (isVisible() != visible) changed();
        return super.setVisible(visible, restart);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
