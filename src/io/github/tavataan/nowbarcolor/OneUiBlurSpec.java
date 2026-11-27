package io.github.tavataan.nowbarcolor;

import android.graphics.LinearGradient;
import android.graphics.Shader;

/**
 * One UI Live Blur (SemBlurInfo BLUR_MODE_WINDOW = 0) specification with vivid color curve,
 * separated blur radius (16px..100px, default 60% ~ 66px) and tint opacity (25%..85%, default 60%),
 * plus One UI 8.5 glass specular finish & contrast helpers.
 */
final class OneUiBlurSpec {
    static final int MIN_RADIUS_PX = 16;
    static final int MAX_RADIUS_PX = 100;
    static final int DEFAULT_BLUR_INTENSITY = 60;

    static final int MIN_TINT_OPACITY = 25;
    static final int MAX_TINT_OPACITY = 85;
    static final int DEFAULT_TINT_OPACITY = 60;

    // Clamped to SemBlurInfo [-1.0f, 1.0f]; preview maps to ColorMatrix saturation (1 + SATURATION = 1.38f).
    static final float SATURATION = 0.38f;
    static final float CURVE = 0.0f;
    static final float MIN_X = 0.0f;
    static final float MAX_X = 255.0f;

    // Legacy subtle tint bounds preserved only for raw-alpha fallback mapping.
    static final int MIN_TINT_ALPHA = 0x40; // ~25%
    static final int MAX_TINT_ALPHA = 0xD9; // ~85%

    // One UI 8.5 glass finish constants.
    static final int SHEEN_TOP_COLOR = 0x1AFFFFFF;    // ~10% white top highlight
    static final int SHEEN_BOTTOM_COLOR = 0x00FFFFFF; // fades cleanly toward center
    static final int BORDER_START_COLOR = 0x52FFFFFF; // ~32% white top-left specular edge
    static final int BORDER_MID_COLOR = 0x24FFFFFF;   // ~14% white mid edge
    static final int BORDER_END_COLOR = 0x1AFFFFFF;   // ~10% white bottom-right edge

    // Foreground readability adaptation.
    static final int DARK_CONTENT_COLOR = 0xEB111318;
    static final int CONTRAST_SHADOW_COLOR = 0x7A000000;

    private OneUiBlurSpec() {}

    static float minY(boolean dark) { return dark ? 12.0f : 16.0f; }
    static float maxY(boolean dark) { return dark ? 226.0f : 236.0f; }

    static int clampBlurIntensity(int intensityPercent) {
        return Math.max(0, Math.min(100, intensityPercent));
    }

    static int clampTintOpacity(int tintOpacityPercent) {
        return Math.max(MIN_TINT_OPACITY, Math.min(MAX_TINT_OPACITY, tintOpacityPercent));
    }

    static int radiusPxFromPercent(int intensityPercent) {
        float t = clampBlurIntensity(intensityPercent) / 100.0f;
        return Math.round(MIN_RADIUS_PX + t * (MAX_RADIUS_PX - MIN_RADIUS_PX));
    }

    static float previewBlurRadiusPx(int hookBlurRadiusPx) {
        return Math.max(2.0f, Math.min(48.0f, hookBlurRadiusPx * 0.42f));
    }

    static int tintAlphaFromPercent(int tintOpacityPercent) {
        return Math.round((clampTintOpacity(tintOpacityPercent) / 100.0f) * 255.0f);
    }

    static int applyTintOpacity(int rgbOrArgb, int tintOpacityPercent) {
        int alpha = tintAlphaFromPercent(tintOpacityPercent);
        return (alpha << 24) | (rgbOrArgb & 0x00ffffff);
    }

    static int radiusPx(int argb) {
        int alpha = argb >>> 24;
        if (alpha <= 0) return MIN_RADIUS_PX;
        float t = (alpha - 38f) / (255f - 38f);
        if (t < 0f) t = 0f;
        else if (t > 1f) t = 1f;
        return Math.round(MIN_RADIUS_PX + t * (MAX_RADIUS_PX - MIN_RADIUS_PX));
    }

    static int tint(int argb) {
        int alpha = argb >>> 24;
        if (alpha <= 0) return argb & 0x00ffffff;
        float t = (alpha - 38f) / (255f - 38f);
        if (t < 0f) t = 0f;
        else if (t > 1f) t = 1f;
        int tintAlpha = Math.round(MIN_TINT_ALPHA + t * (MAX_TINT_ALPHA - MIN_TINT_ALPHA));
        return (tintAlpha << 24) | (argb & 0x00ffffff);
    }

    static float relativeLuminance(int rgb) {
        float r = linearChannel(((rgb >> 16) & 0xFF) / 255.0f);
        float g = linearChannel(((rgb >> 8) & 0xFF) / 255.0f);
        float b = linearChannel((rgb & 0xFF) / 255.0f);
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    private static float linearChannel(float c) {
        return c <= 0.04045f ? (c / 12.92f) : (float) Math.pow((c + 0.055f) / 1.055f, 2.4);
    }

    static float effectiveLuminance(int argb, boolean isBlur) {
        float alpha = ((argb >>> 24) & 0xFF) / 255.0f;
        float bgLum = isBlur ? 0.34f : 0.14f;
        float fgLum = relativeLuminance(argb);
        return alpha * fgLum + (1.0f - alpha) * bgLum;
    }

    static boolean needsDarkForeground(int startArgb, int endArgb, boolean isBlur) {
        float lStart = effectiveLuminance(startArgb, isBlur);
        float lEnd = effectiveLuminance(endArgb, isBlur);
        return Math.min(lStart, lEnd) >= 0.68f;
    }

    static boolean needsContrastShadow(int startArgb, int endArgb, boolean isBlur) {
        if (needsDarkForeground(startArgb, endArgb, isBlur)) return false;
        int midRgb = ConfigManager.midpointRgb(startArgb, endArgb);
        int midAlpha = (((startArgb >>> 24) & 0xFF) + ((endArgb >>> 24) & 0xFF)) / 2;
        int midArgb = (midAlpha << 24) | (midRgb & 0x00FFFFFF);
        float maxLum = Math.max(
                effectiveLuminance(startArgb, isBlur),
                Math.max(effectiveLuminance(endArgb, isBlur), effectiveLuminance(midArgb, isBlur)));
        return maxLum >= 0.44f;
    }

    static int blendRgb(int a, int b, float ratioB) {
        float t = Math.max(0.0f, Math.min(1.0f, ratioB));
        float inv = 1.0f - t;
        int r = Math.round(((a >> 16) & 0xFF) * inv + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * inv + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * inv + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static int computeImageContrastArgb(int baseLuma0to255, int scrimPercent, int scrimMode) {
        int clampedBase = Math.max(0, Math.min(255, baseLuma0to255));
        int clampedScrim = ConfigManager.clampImageScrimPercent(scrimPercent);
        float scrimCover = (clampedScrim / 100.0f) * (scrimMode == ConfigManager.SCRIM_GRADIENT ? 0.88f : 1.0f);
        float baseChan = clampedBase / 255.0f;
        int effChan = Math.max(0, Math.min(255, Math.round(baseChan * (1.0f - scrimCover) * 255.0f)));
        return 0xFF000000 | (effChan << 16) | (effChan << 8) | effChan;
    }

    static LinearGradient createTopSheenShader(float top, float height, float visibility) {
        float clamped = Math.max(0.0f, Math.min(1.0f, visibility));
        float span = Math.max(1.0f, height * 0.55f);
        int topColor = ConfigManager.scaleAlpha(SHEEN_TOP_COLOR, clamped);
        return new LinearGradient(0.0f, top, 0.0f, top + span,
                topColor, SHEEN_BOTTOM_COLOR, Shader.TileMode.CLAMP);
    }

    static LinearGradient createSpecularBorderShader(
            float left, float top, float right, float bottom, float visibility) {
        float clamped = Math.max(0.0f, Math.min(1.0f, visibility));
        int[] colors = new int[]{
                ConfigManager.scaleAlpha(BORDER_START_COLOR, clamped),
                ConfigManager.scaleAlpha(BORDER_MID_COLOR, clamped),
                ConfigManager.scaleAlpha(BORDER_END_COLOR, clamped)
        };
        return new LinearGradient(left, top, right, bottom,
                colors, new float[]{0.0f, 0.45f, 1.0f}, Shader.TileMode.CLAMP);
    }

    static LinearGradient createTintedSpecularBorderShader(
            float left, float top, float right, float bottom, int dominantRgb, float visibility) {
        float clamped = Math.max(0.0f, Math.min(1.0f, visibility));
        int dom = dominantRgb | 0xFF000000;
        int startTint = (0x66 << 24) | (blendRgb(0xFFFFFFFF, dom, 0.32f) & 0x00FFFFFF);
        int midTint = (0x33 << 24) | (blendRgb(0xFFFFFFFF, dom, 0.52f) & 0x00FFFFFF);
        int endTint = (0x24 << 24) | (blendRgb(0xFFFFFFFF, dom, 0.40f) & 0x00FFFFFF);
        int[] colors = new int[]{
                ConfigManager.scaleAlpha(startTint, clamped),
                ConfigManager.scaleAlpha(midTint, clamped),
                ConfigManager.scaleAlpha(endTint, clamped)
        };
        return new LinearGradient(left, top, right, bottom,
                colors, new float[]{0.0f, 0.45f, 1.0f}, Shader.TileMode.CLAMP);
    }
}
