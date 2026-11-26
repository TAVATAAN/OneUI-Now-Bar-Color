package io.github.tavataan.nowbarcolor;

import android.graphics.Canvas;
import android.graphics.Color;
import android.content.res.Configuration;
import android.content.Context;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.provider.Settings;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewParent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedBridge;

/** Per-view drawables and blur objects; Samsung can still replace native state. */
final class CapsuleStyle {
    private static final class SpecularBorderDrawable extends Drawable {
        private Paint paint;
        private RectF strokeRect;
        private ColorFilter colorFilter;
        private float cornerRadius;
        private float strokeWidthPx;
        private int alpha = 255;
        private int cachedW = -1;
        private int cachedH = -1;

        SpecularBorderDrawable() {}

        void configure(float corner, float strokePx) {
            float resolvedStroke = strokePx > 0.0f ? strokePx : 3.0f;
            if (Math.abs(cornerRadius - corner) < 0.05f && Math.abs(strokeWidthPx - resolvedStroke) < 0.05f) {
                return;
            }
            cornerRadius = corner;
            strokeWidthPx = resolvedStroke;
            cachedW = -1;
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            int w = b.width();
            int h = b.height();
            if (w <= 0 || h <= 0 || strokeWidthPx <= 0.0f) return;
            if (paint == null) {
                paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setStyle(Paint.Style.STROKE);
            }
            if (strokeRect == null) {
                strokeRect = new RectF();
            }
            if (cachedW != w || cachedH != h) {
                cachedW = w;
                cachedH = h;
                paint.setStrokeWidth(strokeWidthPx);
                paint.setShader(OneUiBlurSpec.createSpecularBorderShader(b.left, b.top, b.right, b.bottom, 1.0f));
            }
            paint.setColorFilter(colorFilter);
            paint.setAlpha(alpha);
            float half = strokeWidthPx * 0.5f;
            strokeRect.set(b.left + half, b.top + half, b.right - half, b.bottom - half);
            float maxR = Math.min(w, h) * 0.5f;
            float r = Math.max(0.0f, (cornerRadius > 0.0f ? Math.min(cornerRadius, maxR) : maxR) - half);
            canvas.drawRoundRect(strokeRect, r, r, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            int clamped = Math.max(0, Math.min(255, alpha));
            if (this.alpha != clamped) {
                this.alpha = clamped;
                invalidateSelf();
            }
        }

        @Override
        public int getAlpha() {
            return alpha;
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            this.colorFilter = colorFilter;
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static final class State {
        CapsuleGradient gradient;
        android.content.res.ColorStateList originalTint;
        GradientDrawable solid;
        Drawable original;
        Drawable originalForeground;
        Drawable alphaHiddenDrawable;
        boolean gradientApplied;
        boolean foregroundApplied;
        boolean originalClip;
        Object blur;
        int blurRadiusPx;
        float blurRadius;
        int blurColor;
        boolean blurDark;
        boolean materialApplied;
        Drawable blurDrawableRef;
        int blurDrawableAlpha = 255;
        boolean elevationManaged;
        float originalElevation;
        boolean hasCardElevation;
        float originalCardElevation;
    }

    private static State state(View view) {
        State state = (State) XposedHelpers.getAdditionalInstanceField(view, "nb_style");
        if (state == null) {
            state = new State();
            XposedHelpers.setAdditionalInstanceField(view, "nb_style", state);
        }
        return state;
    }

    private static float radius(float value) { return value > 0f ? value : 100f; }

    private static float strokeWidthPx(View view) {
        if (view == null || view.getResources() == null) return 3.0f;
        try {
            float d = view.getResources().getDisplayMetrics().density;
            return d > 0.0f ? d : 3.0f;
        } catch (Throwable ignored) {
            return 3.0f;
        }
    }

    static void applyGlassBorder(View borderView, boolean enableGlassBorder, float corner) {
        if (borderView == null) return;
        Object savedFlag = XposedHelpers.getAdditionalInstanceField(borderView, "nb_border_saved");
        if (enableGlassBorder) {
            if (!Boolean.TRUE.equals(savedFlag)) {
                XposedHelpers.setAdditionalInstanceField(borderView, "nb_orig_border_bg", borderView.getBackground());
                XposedHelpers.setAdditionalInstanceField(borderView, "nb_border_saved", Boolean.TRUE);
            }
            Object existing = XposedHelpers.getAdditionalInstanceField(borderView, "nb_glass_border");
            SpecularBorderDrawable drawable;
            if (existing instanceof SpecularBorderDrawable) {
                drawable = (SpecularBorderDrawable) existing;
            } else {
                drawable = new SpecularBorderDrawable();
                XposedHelpers.setAdditionalInstanceField(borderView, "nb_glass_border", drawable);
            }
            drawable.configure(radius(corner), strokeWidthPx(borderView));
            if (borderView.getBackground() != drawable) {
                borderView.setBackground(drawable);
            }
        } else if (Boolean.TRUE.equals(savedFlag)) {
            Drawable orig = (Drawable) XposedHelpers.getAdditionalInstanceField(borderView, "nb_orig_border_bg");
            if (borderView.getBackground() != orig) {
                borderView.setBackground(orig);
            }
            XposedHelpers.setAdditionalInstanceField(borderView, "nb_orig_border_bg", null);
            XposedHelpers.setAdditionalInstanceField(borderView, "nb_border_saved", Boolean.FALSE);
        }
    }

    static void setElevationScale(View view, float scale) {
        if (view == null) return;
        if (scale >= 0.999f) {
            restoreElevation(view);
            return;
        }
        float clamped = Math.max(0f, Math.min(1f, scale));
        State state = state(view);
        Method getElev = ReflectionCache.method(view.getClass(), "getElevation");
        Method setElev = ReflectionCache.method(view.getClass(), "setElevation", float.class);
        Method getCardElev = ReflectionCache.method(view.getClass(), "getCardElevation");
        Method setCardElev = ReflectionCache.method(view.getClass(), "setCardElevation", float.class);
        if (!state.elevationManaged) {
            if (getElev != null) {
                try {
                    Object res = getElev.invoke(view);
                    if (res instanceof Number) state.originalElevation = ((Number) res).floatValue();
                } catch (Throwable ignored) {}
            }
            if (getCardElev != null) {
                try {
                    Object res = getCardElev.invoke(view);
                    if (res instanceof Number) {
                        state.hasCardElevation = true;
                        state.originalCardElevation = ((Number) res).floatValue();
                    }
                } catch (Throwable ignored) {}
            }
            state.elevationManaged = true;
        }
        if (state.hasCardElevation && setCardElev != null) {
            try { setCardElev.invoke(view, state.originalCardElevation * clamped); } catch (Throwable ignored) {}
        }
        if (setElev != null) {
            try { setElev.invoke(view, state.originalElevation * clamped); } catch (Throwable ignored) {}
        }
    }

    static void restoreElevation(View view) {
        if (view == null) return;
        State state = (State) XposedHelpers.getAdditionalInstanceField(view, "nb_style");
        if (state == null || !state.elevationManaged) return;
        if (state.hasCardElevation) {
            Method setCardElev = ReflectionCache.method(view.getClass(), "setCardElevation", float.class);
            if (setCardElev != null) {
                try { setCardElev.invoke(view, state.originalCardElevation); } catch (Throwable ignored) {}
            }
        }
        Method setElev = ReflectionCache.method(view.getClass(), "setElevation", float.class);
        if (setElev != null) {
            try { setElev.invoke(view, state.originalElevation); } catch (Throwable ignored) {}
        }
        state.elevationManaged = false;
    }

    static void transparent(View view) {
        if (view == null) return;
        State state = state(view);
        clearBlur(view);
        restoreForeground(view);
        if (Build.VERSION.SDK_INT >= 23 && view.getForeground() != null) {
            view.setForeground(null);
        }
        releaseBackgroundGradient(view);
        state.materialApplied = false;
        setElevationScale(view, 0f);
        boolean nativeColor = setNativeColor(view, Color.TRANSPARENT);
        if (view.getBackgroundTintList() != null) {
            view.setBackgroundTintList(null);
        }
        Drawable bg = view.getBackground();
        if (bg != null && (!nativeColor || bg == state.solid || bg instanceof GradientDrawable || bg instanceof ColorDrawable)) {
            if (state.alphaHiddenDrawable == bg) {
                state.alphaHiddenDrawable = null;
            }
            view.setBackground(null);
        } else if (bg != null) {
            if (state.alphaHiddenDrawable != null && state.alphaHiddenDrawable != bg) {
                try {
                    state.alphaHiddenDrawable.setAlpha(255);
                } catch (Throwable ignored) {}
            }
            try {
                bg.setAlpha(0);
                state.alphaHiddenDrawable = bg;
            } catch (Throwable ignored) {}
        }
    }

    private static void restoreForeground(View view) {
        if (view == null) return;
        State state = (State) XposedHelpers.getAdditionalInstanceField(view, "nb_style");
        if (state != null && state.foregroundApplied) {
            view.setForeground(state.originalForeground);
            if (!state.gradientApplied) view.setClipToOutline(state.originalClip);
            state.foregroundApplied = false;
            state.originalForeground = null;
        }
    }

    private static void releaseBackgroundGradient(View view) {
        if (view == null) return;
        State state = (State) XposedHelpers.getAdditionalInstanceField(view, "nb_style");
        if (state == null || !state.gradientApplied) return;
        view.setBackground(state.original);
        if (!state.foregroundApplied) view.setClipToOutline(state.originalClip);
        if (view.getBackgroundTintList() != state.originalTint) {
            view.setBackgroundTintList(state.originalTint);
        }
        state.gradientApplied = false;
        state.original = null;
        state.originalTint = null;
    }

    private static void foregroundGradient(
            View view, int start, int end, float corner,
            boolean withGlassHighlight, boolean withGlassBorder, float visibility) {
        if (view == null) return;
        State state = state(view);
        if (!state.foregroundApplied) {
            state.originalForeground = view.getForeground();
            if (!state.gradientApplied) state.originalClip = view.getClipToOutline();
            state.foregroundApplied = true;
        }
        if (state.gradient == null) state.gradient = new CapsuleGradient();
        CapsuleGradient drawable = state.gradient;
        float strokePx = withGlassBorder ? strokeWidthPx(view) : 0.0f;
        drawable.configure(start, end, radius(corner), withGlassHighlight, withGlassBorder, strokePx, visibility);
        if (view.getForeground() != drawable) view.setForeground(drawable);
        drawable.configure(start, end, radius(corner), withGlassHighlight, withGlassBorder, strokePx, visibility);
        if (!view.getClipToOutline()) view.setClipToOutline(true);
    }

    private static void restoreHiddenAlpha(State state) {
        if (state == null || state.alphaHiddenDrawable == null) return;
        try {
            state.alphaHiddenDrawable.setAlpha(255);
        } catch (Throwable ignored) {}
        state.alphaHiddenDrawable = null;
    }

    static void gradient(View view, int start, int end, float corner) {
        if (view == null) return;
        restoreForeground(view);
        State state = state(view);
        restoreHiddenAlpha(state);
        if (!state.gradientApplied) {
            state.original = view.getBackground();
            state.originalClip = view.getClipToOutline();
            state.originalTint = view.getBackgroundTintList();
            // Make the native CardView backing drawable transparent once, not every frame.
            setNativeColor(view, Color.TRANSPARENT);
            state.gradientApplied = true;
        }
        if (state.gradient == null) state.gradient = new CapsuleGradient();
        CapsuleGradient drawable = state.gradient;
        // A native tint can otherwise make newly attached drawables transparent too.
        if (view.getBackgroundTintList() != null) view.setBackgroundTintList(null);
        drawable.configure(start, end, radius(corner));
        if (view.getBackground() != drawable) view.setBackground(drawable);
        // setBackground may apply the view's native drawable state; normalize afterwards.
        drawable.configure(start, end, radius(corner));
        if (!view.getClipToOutline()) view.setClipToOutline(true);
    }

    static boolean needsGradientRepair(View view) {
        if (view == null) return false;
        // Read only: don't allocate per-view state for native/non-gradient layers.
        State state = (State) XposedHelpers.getAdditionalInstanceField(view, "nb_style");
        if (state == null || state.gradient == null) return false;
        if (state.gradientApplied && view.getBackground() == state.gradient) {
            return state.gradient.needsRepair() || view.getBackgroundTintList() != null;
        }
        if (state.foregroundApplied) {
            return view.getForeground() != state.gradient
                    || state.gradient.needsRepair()
                    || view.getBackgroundTintList() != null;
        }
        return false;
    }

    static void restoreGradient(View view) {
        if (view == null) return;
        State state = state(view);
        restoreHiddenAlpha(state);
        state.materialApplied = false;
        restoreForeground(view);
        releaseBackgroundGradient(view);
    }

    private static boolean setNativeColor(View view, int color) {
        Method setter = ReflectionCache.method(view.getClass(), "setCardBackgroundColor", int.class);
        if (setter == null) return false;
        try {
            // Inspect current state, since Samsung can overwrite the native drawable.
            // Never short-circuit when clearing to TRANSPARENT: a stale ColorStateList must not skip clearing.
            if (color != Color.TRANSPARENT) {
                Method getter = ReflectionCache.method(view.getClass(), "getCardBackgroundColor");
                Object current = null;
                if (getter != null) {
                    try { current = getter.invoke(view); } catch (Throwable ignored) {}
                }
                if (current instanceof android.content.res.ColorStateList
                        && !((android.content.res.ColorStateList) current).isStateful()
                        && ((android.content.res.ColorStateList) current).getDefaultColor() == color) return true;
            }
            invokeInternal(setter, view, color);
            return true;
        } catch (Throwable ignored) { return false; }
    }

    static void solid(View view, int color, float corner) {
        if (view == null) return;
        restoreGradient(view);
        State state = state(view);
        boolean nativeColor = setNativeColor(view, color);
        // A CardView getter can retain its color after Samsung clears its drawable.
        // Keep a visible fallback in that case; update it on subsequent color changes.
        if (nativeColor && view.getBackground() != null && view.getBackground() != state.solid) return;
        if (color == Color.TRANSPARENT) {
            if (view.getBackground() != null) view.setBackground(null);
            return;
        }
        if (state.solid == null) state.solid = new GradientDrawable();
        GradientDrawable drawable = state.solid;
        if (drawable.getColor() == null || drawable.getColor().getDefaultColor() != color) drawable.setColor(color);
        float r = radius(corner);
        if (drawable.getCornerRadius() != r) drawable.setCornerRadius(r);
        if (view.getBackground() != drawable) view.setBackground(drawable);
        if (!view.getClipToOutline()) view.setClipToOutline(true);
    }

    /** Resolve optional Samsung API once; absence must not throw every frame. */
    private static final class BlurApi {
        static final Constructor<?> CONSTRUCTOR;
        static final Method CURVE, RADIUS, CORNER, COLOR, BUILD, SET, GET;
        private static Method find(Class<?> type, String name, Class<?>... parameters) {
            // SESL also uses these declared wrappers on Samsung builds with restricted APIs.
            try {
                Method method = type.getDeclaredMethod("hidden_" + name, parameters);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException | SecurityException ignored) {
                return ReflectionCache.method(type, name, parameters);
            }
        }
        static {
            Constructor<?> constructor = null;
            Method curve = null, radius = null, corner = null, color = null, build = null, set = null, get = null;
            try {
                Class<?> info = Class.forName("android.view.SemBlurInfo");
                Class<?> builder = Class.forName("android.view.SemBlurInfo$Builder");
                constructor = builder.getConstructor(int.class);
                curve = find(builder, "setColorCurve", float.class, float.class,
                        float.class, float.class, float.class, float.class);
                radius = find(builder, "setRadius", int.class);
                corner = find(builder, "setBackgroundCornerRadius", float.class);
                color = find(builder, "setBackgroundColor", int.class);
                build = find(builder, "build");
                set = find(View.class, "semSetBlurInfo", info);
                get = find(View.class, "semGetBlurInfo");
            } catch (Throwable ignored) {}
            CONSTRUCTOR = constructor; CURVE = curve; RADIUS = radius; CORNER = corner; COLOR = color;
            BUILD = build; SET = set; GET = get;
        }
    }

    private static boolean blurGetterFailed;
    private static boolean blurFailureLogged;
    private static boolean curveRejected;
    private static boolean reduceTransparency;
    private static final ThreadLocal<Integer> INTERNAL_STYLE_DEPTH = new ThreadLocal<>();

    static boolean isApplyingInternalStyle() {
        Integer depth = INTERNAL_STYLE_DEPTH.get();
        return depth != null && depth > 0;
    }

    private static Object invokeInternal(Method method, View view, Object arg) throws Exception {
        Integer prev = INTERNAL_STYLE_DEPTH.get();
        int depth = prev != null ? prev : 0;
        INTERNAL_STYLE_DEPTH.set(depth + 1);
        try {
            return method.invoke(view, new Object[]{arg});
        } finally {
            if (depth == 0) INTERNAL_STYLE_DEPTH.remove();
            else INTERNAL_STYLE_DEPTH.set(depth);
        }
    }

    static void refreshAccessibility(Context context) {
        try {
            reduceTransparency = Settings.System.getInt(context.getContentResolver(),
                    "accessibility_reduce_transparency", 0) != 0;
        } catch (Throwable ignored) { reduceTransparency = false; }
    }

    private static boolean hasMaterialAncestor(View view) {
        for (ViewParent parent = view.getParent(); parent instanceof View; parent = parent.getParent()) {
            State state = (State) XposedHelpers.getAdditionalInstanceField(parent, "nb_style");
            if (state != null && state.materialApplied) return true;
        }
        return false;
    }

    private static void logBlurFailure(Throwable error) {
        if (blurFailureLogged) return;
        blurFailureLogged = true;
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        XposedBridge.log("NowBarColor: native blur unavailable; using color fallback: " + cause);
    }

    private static Object buildBlur(int blurRadiusPx, float corner, int color, boolean dark, boolean withCurve) throws Exception {
        Object builder = BlurApi.CONSTRUCTOR.newInstance(0); // BLUR_MODE_WINDOW: live backdrop
        // Radius is in Samsung pixels, independently of the capsule's corner radius.
        BlurApi.RADIUS.invoke(builder, blurRadiusPx);
        BlurApi.CORNER.invoke(builder, corner);
        BlurApi.COLOR.invoke(builder, color);
        if (withCurve) {
            BlurApi.CURVE.invoke(builder, OneUiBlurSpec.SATURATION, OneUiBlurSpec.CURVE,
                    OneUiBlurSpec.MIN_X, OneUiBlurSpec.MAX_X,
                    OneUiBlurSpec.minY(dark), OneUiBlurSpec.maxY(dark));
        }
        return BlurApi.BUILD.invoke(builder);
    }

    private static boolean hasCurrentBlur(View view, Object expected, State state) {
        if (expected != null && state != null) {
            Drawable bg = view.getBackground();
            if (bg == null || bg == state.solid || bg == state.gradient) return false;
        }
        if (BlurApi.GET == null || blurGetterFailed) return false;
        try { return BlurApi.GET.invoke(view) == expected; }
        catch (Throwable ignored) {
            // Some ROMs expose a getter that throws; keep the safe setter path.
            blurGetterFailed = true;
            return false;
        }
    }

    private static void syncBlurDrawableAlpha(View view, State state, int blurAlpha) {
        Drawable bg = view.getBackground();
        if (bg != null && bg != state.solid && bg != state.gradient
                && (state.blurDrawableRef != bg || state.blurDrawableAlpha != blurAlpha)) {
            bg.setAlpha(blurAlpha);
            state.blurDrawableRef = bg;
            state.blurDrawableAlpha = blurAlpha;
        }
    }

    static boolean blur(View view, float corner, int color) {
        return blur(view, OneUiBlurSpec.MAX_RADIUS_PX, corner, color, 1.0f);
    }

    static boolean blur(View view, int blurRadiusPx, float corner, int color) {
        return blur(view, blurRadiusPx, corner, color, 1.0f);
    }

    static boolean blur(View view, int blurRadiusPx, float corner, int color, float visibility) {
        if (view == null) return false;
        if (BlurApi.CONSTRUCTOR == null || BlurApi.BUILD == null || BlurApi.SET == null
                || BlurApi.RADIUS == null || BlurApi.CORNER == null || BlurApi.COLOR == null) {
            logBlurFailure(new IllegalStateException("SemBlurInfo API missing"));
            return false;
        }
        try {
            releaseBackgroundGradient(view);
            State state = state(view);
            restoreHiddenAlpha(state);
            if (state.solid != null && view.getBackground() == state.solid) {
                view.setBackground(null);
            }
            setNativeColor(view, Color.TRANSPARENT);
            if (view.getBackgroundTintList() != null) view.setBackgroundTintList(null);
            boolean dark = (view.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            corner = radius(corner);
            if (state.blur == null || state.blurRadiusPx != blurRadiusPx || state.blurRadius != corner
                    || state.blurColor != color || state.blurDark != dark) {
                Object info;
                if (BlurApi.CURVE != null && !curveRejected) {
                    try {
                        info = buildBlur(blurRadiusPx, corner, color, dark, true);
                    } catch (Throwable rejected) {
                        // A failed builder must never be reused (Samsung tracks its fields).
                        info = buildBlur(blurRadiusPx, corner, color, dark, false);
                        curveRejected = true;
                        XposedBridge.log("NowBarColor: color curve rejected; using native radius-only blur");
                    }
                } else {
                    info = buildBlur(blurRadiusPx, corner, color, dark, false);
                }
                state.blur = info;
                state.blurRadiusPx = blurRadiusPx;
                state.blurRadius = corner;
                state.blurColor = color;
                state.blurDark = dark;
            }
            if (!hasCurrentBlur(view, state.blur, state)) {
                state.blurDrawableRef = null;
                state.blurDrawableAlpha = -1;
                invokeInternal(BlurApi.SET, view, state.blur);
            }
            int blurAlpha = Math.max(0, Math.min(255, Math.round(Math.max(0f, Math.min(1f, visibility)) * 255f)));
            syncBlurDrawableAlpha(view, state, blurAlpha);
            return true;
        } catch (Throwable error) { logBlurFailure(error); return false; }
    }

    /**
     * Keep Samsung's BackgroundBlurDrawable on View.mBackground so SurfaceFlinger receives
     * the live window blur region (16px..100px). Single-color mode tints SemBlurInfo directly
     * with a top glass sheen foreground; 2-stop gradient mode pairs a transparent SemBlurInfo
     * with a foreground CapsuleGradient + top glass sheen (and optional 1dp specular border).
     */
    static void glass(View view, int start, int end, float corner) {
        glass(view, start, end, corner, 1.0f);
    }

    static void glass(View view, int start, int end, float corner, float visibility) {
        int blurRadiusPx = OneUiBlurSpec.radiusPx(start);
        int tintStart = OneUiBlurSpec.tint(start);
        int tintEnd = OneUiBlurSpec.tint(end);
        glass(view, tintStart, tintEnd, blurRadiusPx, corner, visibility, false);
    }

    static void glass(View view, int tintStartArgb, int tintEndArgb, int blurRadiusPx,
                      float corner, float visibility, boolean drawBorderInForeground) {
        if (view == null) return;
        float vis = Math.max(0f, Math.min(1f, visibility));
        if (vis <= 0.005f) {
            transparent(view);
            return;
        }
        int resolvedRadiusPx = Math.max(OneUiBlurSpec.MIN_RADIUS_PX,
                Math.min(OneUiBlurSpec.MAX_RADIUS_PX, blurRadiusPx));
        // Now Brief can expose nested native blur hosts. Render the material once.
        if (hasMaterialAncestor(view)) {
            clearBlur(view);
            restoreForeground(view);
            gradient(view, Color.TRANSPARENT, Color.TRANSPARENT, corner);
        } else if (reduceTransparency) {
            clearBlur(view);
            restoreForeground(view);
            gradient(view,
                    ConfigManager.scaleAlpha(tintStartArgb | 0xff000000, vis),
                    ConfigManager.scaleAlpha(tintEndArgb | 0xff000000, vis),
                    corner);
        } else if (tintStartArgb == tintEndArgb) {
            int tint = ConfigManager.scaleAlpha(tintStartArgb, vis);
            if (blur(view, resolvedRadiusPx, corner, tint, vis)) {
                foregroundGradient(view, 0, 0, corner, true, drawBorderInForeground, vis);
            } else {
                clearBlur(view);
                restoreForeground(view);
                gradient(view, tint, tint, corner);
            }
        } else {
            int tintStart = ConfigManager.scaleAlpha(tintStartArgb, vis);
            int tintEnd = ConfigManager.scaleAlpha(tintEndArgb, vis);
            if (blur(view, resolvedRadiusPx, corner, Color.TRANSPARENT, vis)) {
                foregroundGradient(view, tintStart, tintEnd, corner, true, drawBorderInForeground, vis);
            } else {
                clearBlur(view);
                restoreForeground(view);
                gradient(view, tintStart, tintEnd, corner);
            }
        }
        state(view).materialApplied = true;
        setElevationScale(view, vis);
    }

    /** Don't clear blur Samsung has already replaced during an expansion transition. */
    static void releaseBlur(View view) {
        if (view == null) return;
        State state = (State) XposedHelpers.getAdditionalInstanceField(view, "nb_style");
        if (state != null) {
            restoreHiddenAlpha(state);
            restoreForeground(view);
            if (state.blur != null && hasCurrentBlur(view, state.blur, state)) clearBlur(view);
        }
    }

    static void clearBlur(View view) {
        if (view == null) return;
        State state = (State) XposedHelpers.getAdditionalInstanceField(view, "nb_style");
        if (state != null) {
            state.materialApplied = false;
            state.blurDrawableRef = null;
            state.blurDrawableAlpha = 255;
        }
        if (BlurApi.SET == null) return;
        try {
            // Without a getter, clearing is required: a native hook may have restored blur.
            if (!hasCurrentBlur(view, null, state)) invokeInternal(BlurApi.SET, view, null);
        } catch (Throwable ignored) {}
    }

    static Method getBlurSetterMethod() {
        return BlurApi.SET;
    }

    static Object getBlurInfo(View view) {
        if (view == null || BlurApi.GET == null || blurGetterFailed) return null;
        try {
            return BlurApi.GET.invoke(view);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static boolean hasBlurInfo(View view) {
        return getBlurInfo(view) != null;
    }
}
