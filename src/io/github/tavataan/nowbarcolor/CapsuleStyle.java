package io.github.tavataan.nowbarcolor;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import de.robv.android.xposed.XposedHelpers;

/** Per-view drawables and blur objects; Samsung can still replace native state. */
final class CapsuleStyle {
    private static final class State {
        CapsuleGradient gradient;
        android.content.res.ColorStateList originalTint;
        GradientDrawable solid;
        Drawable original;
        boolean gradientApplied;
        boolean originalClip;
        Object blur;
        float blurRadius;
        int blurColor;
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

    static void gradient(View view, int start, int end, float corner) {
        if (view == null) return;
        State state = state(view);
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
        return state != null && state.gradientApplied && state.gradient != null
                && view.getBackground() == state.gradient
                && (state.gradient.needsRepair() || view.getBackgroundTintList() != null);
    }

    static void restoreGradient(View view) {
        if (view == null) return;
        State state = state(view);
        if (!state.gradientApplied) return;
        view.setBackground(state.original);
        view.setClipToOutline(state.originalClip);
        view.setBackgroundTintList(state.originalTint);
        state.gradientApplied = false;
        state.original = null;
        state.originalTint = null;
    }

    private static boolean setNativeColor(View view, int color) {
        Method setter = ReflectionCache.method(view.getClass(), "setCardBackgroundColor", int.class);
        if (setter == null) return false;
        try {
            // Inspect current state, since Samsung can overwrite the native drawable.
            Method getter = ReflectionCache.method(view.getClass(), "getCardBackgroundColor");
            Object current = null;
            if (getter != null) {
                try { current = getter.invoke(view); } catch (Throwable ignored) {}
            }
            if (current instanceof android.content.res.ColorStateList
                    && !((android.content.res.ColorStateList) current).isStateful()
                    && ((android.content.res.ColorStateList) current).getDefaultColor() == color) return true;
            setter.invoke(view, color);
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
        static final Method PRESET, CORNER, COLOR, BUILD, SET, GET;
        static {
            Constructor<?> constructor = null;
            Method preset = null, corner = null, color = null, build = null, set = null, get = null;
            try {
                Class<?> info = Class.forName("android.view.SemBlurInfo");
                Class<?> builder = Class.forName("android.view.SemBlurInfo$Builder");
                constructor = builder.getConstructor(int.class);
                preset = ReflectionCache.method(builder, "setColorCurvePreset", int.class);
                corner = ReflectionCache.method(builder, "setBackgroundCornerRadius", float.class);
                color = ReflectionCache.method(builder, "setBackgroundColor", int.class);
                build = ReflectionCache.method(builder, "build");
                set = ReflectionCache.method(View.class, "semSetBlurInfo", info);
                get = ReflectionCache.method(View.class, "semGetBlurInfo");
            } catch (Throwable ignored) {}
            CONSTRUCTOR = constructor; PRESET = preset; CORNER = corner; COLOR = color;
            BUILD = build; SET = set; GET = get;
        }
    }

    private static boolean blurGetterFailed;

    private static boolean hasCurrentBlur(View view, Object expected) {
        if (BlurApi.GET == null || blurGetterFailed) return false;
        try { return BlurApi.GET.invoke(view) == expected; }
        catch (Throwable ignored) {
            // Some ROMs expose a getter that throws; keep the safe setter path.
            blurGetterFailed = true;
            return false;
        }
    }

    static boolean blur(View view, float corner, int color) {
        if (view == null || BlurApi.CONSTRUCTOR == null || BlurApi.BUILD == null || BlurApi.SET == null) return false;
        try {
            State state = state(view);
            if (state.blur == null || state.blurRadius != corner || state.blurColor != color) {
                Object builder = BlurApi.CONSTRUCTOR.newInstance(0);
                if (BlurApi.PRESET != null) BlurApi.PRESET.invoke(builder, 54);
                if (BlurApi.CORNER != null) BlurApi.CORNER.invoke(builder, corner);
                if (BlurApi.COLOR != null) BlurApi.COLOR.invoke(builder, color);
                Object info = BlurApi.BUILD.invoke(builder);
                state.blur = info;
                state.blurRadius = corner;
                state.blurColor = color;
            }
            if (!hasCurrentBlur(view, state.blur)) BlurApi.SET.invoke(view, state.blur);
            return true;
        } catch (Throwable ignored) { return false; }
    }

    static void clearBlur(View view) {
        if (view == null || BlurApi.SET == null) return;
        try {
            // Without a getter, clearing is required: a native hook may have restored blur.
            if (!hasCurrentBlur(view, null)) BlurApi.SET.invoke(view, new Object[]{null});
        } catch (Throwable ignored) {}
    }
}
