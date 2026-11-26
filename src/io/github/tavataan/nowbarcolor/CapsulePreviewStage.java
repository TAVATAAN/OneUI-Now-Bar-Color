package io.github.tavataan.nowbarcolor;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

/** Illustrative backdrop. The actual lockscreen material is rendered by Samsung.
 * Only this recorded background is blurred; text, art, controls and waves stay sharp.
 */
final class CapsulePreviewStage extends FrameLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Path clip = new Path();
    private int baseColor;
    private boolean dirty = true;
    private boolean lastDark;
    private int lastBlurRadiusPx = -1;
    private RenderNode backdrop;

    CapsulePreviewStage(Context context, int color) {
        super(context);
        baseColor = color | 0xff000000;
        setWillNotDraw(false);
    }

    void setBaseColor(int color) {
        color |= 0xff000000;
        if (baseColor == color) return;
        baseColor = color;
        dirty = true;
        invalidate();
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        dirty = true;
        bounds.set(0, 0, w, h);
        clip.reset();
        float r = 22f * getResources().getDisplayMetrics().density;
        clip.addRoundRect(bounds, r, r, Path.Direction.CW);
    }

    private void drawScene(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        paint.setColor(baseColor);
        canvas.drawRect(0, 0, w, h, paint);
        // Broad shapes make real background diffusion visible even on a plain wallpaper.
        paint.setColor(0xb3448aff);
        canvas.drawCircle(w * 0.17f, h * 0.35f, h * 0.8f, paint);
        paint.setColor(0x997c4dff);
        canvas.drawCircle(w * 0.69f, h * 0.95f, h * 0.9f, paint);
        paint.setColor(0x9970d7c7);
        canvas.drawCircle(w * 0.92f, h * 0.08f, h * 0.6f, paint);
        paint.setColor(0x66ffffff);
        canvas.drawRect(w * 0.38f, 0, w * 0.41f, h, paint);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int save = canvas.save();
        canvas.clipPath(clip);
        drawScene(canvas);
        canvas.restoreToCount(save);
    }

    void drawBlurredBackdrop(Canvas canvas, View capsule, int blurRadiusPx) {
        if (Build.VERSION.SDK_INT < 31 || !canvas.isHardwareAccelerated()
                || getWidth() <= 0 || getHeight() <= 0) return;
        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (backdrop == null) backdrop = new RenderNode("NowBar preview backdrop");
        if (dirty || lastDark != dark || lastBlurRadiusPx != blurRadiusPx || !backdrop.hasDisplayList()) {
            backdrop.setPosition(0, 0, getWidth(), getHeight());
            Canvas recording = backdrop.beginRecording(getWidth(), getHeight());
            try { drawScene(recording); } finally { backdrop.endRecording(); }
            float scale = (OneUiBlurSpec.maxY(dark) - OneUiBlurSpec.minY(dark)) / 255f;
            float offset = OneUiBlurSpec.minY(dark);
            ColorMatrix tone = new ColorMatrix(new float[]{
                    scale, 0, 0, 0, offset, 0, scale, 0, 0, offset,
                    0, 0, scale, 0, offset, 0, 0, 0, 1, 0});
            ColorMatrix saturation = new ColorMatrix();
            saturation.setSaturation(1f + OneUiBlurSpec.SATURATION);
            tone.postConcat(saturation);
            float radius = OneUiBlurSpec.previewBlurRadiusPx(blurRadiusPx);
            backdrop.setRenderEffect(RenderEffect.createColorFilterEffect(
                    new ColorMatrixColorFilter(tone),
                    RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)));
            dirty = false;
            lastDark = dark;
            lastBlurRadiusPx = blurRadiusPx;
        }
        int save = canvas.save();
        canvas.translate(-capsule.getLeft(), -capsule.getTop());
        canvas.drawRenderNode(backdrop);
        canvas.restoreToCount(save);
    }

    @Override protected void onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= 29 && backdrop != null) backdrop.discardDisplayList();
        super.onDetachedFromWindow();
    }
}
