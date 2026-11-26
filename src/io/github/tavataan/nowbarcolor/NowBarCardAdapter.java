package io.github.tavataan.nowbarcolor;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;

import de.robv.android.xposed.XposedHelpers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Isolated branch adapter contract decoupling NowBar view/field/method resolution
 * across One UI releases while keeping a single unified APK.
 *
 * <p>Base defaults resolve views strictly via Android resource entry IDs and official
 * non-obfuscated public getters, avoiding dangerous single-letter R8 field collisions.</p>
 */
public abstract class NowBarCardAdapter {
    private static final Object[] NO_ARGS = new Object[0];
    private static final ConcurrentHashMap<String, Integer> RES_ID_CACHE = new ConcurrentHashMap<>();

    public abstract String getAdapterName();

    public boolean supportsMpsmContainer() {
        return false;
    }

    public boolean supportsBaseClearContainerBg() {
        return false;
    }

    public boolean supportsBaseAodDrawable() {
        return false;
    }

    public boolean supportsMediaForegroundLighting() {
        return true;
    }

    public boolean supportsBriefCardView() {
        return true;
    }

    public View resolveCardContainer(View card) {
        return resolveByGetterOrEntry(card, "getCardViewContainer", "nowbar_cardview_background");
    }

    public View resolveMpsmContainer(View card) {
        return null;
    }

    public View resolveGuidingBgView(View card) {
        return resolveByGetterOrEntry(
                card, "getCardBGViewForGuidingEffect", "nowbar_cardview_bgview_for_guidingeffect");
    }

    public View resolveGradientBgView(View card) {
        return resolveByGetterOrEntry(card, "getGradientBgView", "gradient_view");
    }

    public View resolveOuterGlowView(View card) {
        View v = callViewGetter(card, "getCardOuterGlowView");
        if (v != null) return v;
        v = callViewGetter(card, "getCardGuidingEffectView");
        return v != null ? v : findViewByEntryName(card, "nowbar_cardview_guidingeffect_view");
    }

    public View resolveBorderStrokeView(View card) {
        return resolveByGetterOrEntry(card, "getBorderStrokeView", "nowbar_card_border_stroke_view");
    }

    public View resolveNudgeContainer(View card) {
        View v = callViewGetter(card, "getNudgeContianer");
        if (v != null) return v;
        v = callViewGetter(card, "getNudgeContainer");
        return v != null ? v : findViewByEntryName(card, "nudge_container");
    }

    public View resolveNormalTextContainer(View card) {
        return findViewByEntryName(card, "normal_text_container");
    }

    public View resolveChronometerView(View card) {
        View root = findViewByEntryName(card, "ongoing_card_chronometer_root_view");
        if (root != null) {
            Chronometer nested = findFirstChronometer(root, 0);
            return nested != null ? nested : root;
        }
        return findViewByEntryName(card, "chronometer");
    }

    public View resolveAiPresenceContainer(View card) {
        return findViewByEntryName(card, "ai_presence_container");
    }

    public View resolveAiPresenceView(View card) {
        return findViewByEntryName(card, "ai_presence_view");
    }

    public View resolveBatteryProgressBar(View card) {
        return findViewByEntryName(card, "battery_progressbar");
    }

    public View resolveBriefBackgroundView(View card) {
        return findViewByEntryName(card, "nowbar_cardview_background_for_now_brief");
    }

    public View resolveExpandRootView(View card) {
        return findViewByEntryName(card, "expand_content_rootview");
    }

    public View resolveMediaPillSurface(View card) {
        return findViewByEntryName(card, "stack_background_cardview");
    }

    public View resolveMediaBackdropSurface(View card) {
        return findViewByEntryName(card, "nowbar_cardview_background");
    }

    public View resolveMediaSettingBgSurface(View card) {
        return findViewByEntryName(card, "nowbar_cardview_bgview_for_media_setting");
    }

    public View resolveMediaShaderView(View card) {
        return findViewByEntryName(card, "background_shader_view");
    }

    public View resolveMediaShaderParent(View card) {
        return findViewByEntryName(card, "background_shader_parent");
    }

    public Object resolveCardItem(View card) {
        Object item = callMethodNoArgs(card, "getItem");
        if (item != null) return item;
        item = callMethodNoArgs(card, "getNowbarItem");
        if (item != null) return item;
        return card != null ? XposedHelpers.getAdditionalInstanceField(card, "nb_cached_item") : null;
    }

    public float resolveCardRadius(View card, float fallback) {
        float r = callFloatGetter(card, "getCardRadius", -1f);
        if (r > 0f) return r;
        r = callFloatGetter(card, "getNormalCardRadius", -1f);
        if (r > 0f) return r;
        r = callFloatGetter(card, "getCurrentRadius", -1f);
        return r > 0f ? r : fallback;
    }

    public float resolveMediaBlackAodRadius(View card, float fallback) {
        float r = callFloatGetter(card, "getNormalCardRadius", -1f);
        if (r > 0f) return r;
        r = callFloatGetter(card, "getCardRadius", -1f);
        if (r > 0f) return r;
        r = callFloatGetter(card, "getCurrentRadius", -1f);
        return r > 0f ? r : fallback;
    }

    public float resolveNormalCardWidth(View card) {
        return callFloatGetter(card, "getNormalCardWidth", 0f);
    }

    public float resolveNormalCardHeight(View card) {
        return callFloatGetter(card, "getNormalCardHeight", 0f);
    }

    public float readDarkAmountFallback(View card) {
        return 0.0f;
    }

    public boolean readInitialDozingFallback(View card) {
        return false;
    }

    public int readCardStateFallback(View card) {
        return 0;
    }

    public Boolean readExpandedFallback(View card) {
        return null;
    }

    public boolean resolveBriefScreenOn(View card) {
        return true;
    }

    public void restoreBriefCardExtra(View card, HookMap map, float darkAmount) {
        if (card == null || map == null || map.baseProgressBgMethod == null) return;
        callFloatMethod(card, map.baseProgressBgMethod, darkAmount);
    }

    public void triggerShapeRefresh(View card, HookMap map) {
        if (card == null || map == null || map.baseShapeMethod == null) return;
        callMethodNoArgs(card, map.baseShapeMethod);
    }

    // --- Shared Safe Lookup Utilities (Package-Private Reuse) ---

    static View resolveByGetterOrEntry(View card, String getterName, String entryName) {
        View v = callViewGetter(card, getterName);
        return v != null ? v : findViewByEntryName(card, entryName);
    }

    static View findViewByEntryName(View root, String entryName) {
        if (root == null || entryName == null) return null;
        try {
            Context ctx = root.getContext();
            Resources res = root.getResources();
            if (res == null) return null;
            String pkg = ctx != null ? ctx.getPackageName() : "com.samsung.android.app.aodservice";
            String cacheKey = pkg + ":" + entryName;
            Integer cached = RES_ID_CACHE.get(cacheKey);
            int id = cached != null ? cached : 0;
            if (id == 0) {
                id = res.getIdentifier(entryName, "id", pkg);
                if (id == 0 && !"com.samsung.android.app.aodservice".equals(pkg)) {
                    id = res.getIdentifier(entryName, "id", "com.samsung.android.app.aodservice");
                }
                if (id == 0 && !"com.android.systemui".equals(pkg)) {
                    id = res.getIdentifier(entryName, "id", "com.android.systemui");
                }
                if (id != 0) {
                    RES_ID_CACHE.putIfAbsent(cacheKey, id);
                }
            }
            return id != 0 ? root.findViewById(id) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static String getResourceEntryNameSafe(View v) {
        if (v == null || v.getId() == View.NO_ID) return "";
        try {
            Resources res = v.getResources();
            return res != null ? res.getResourceEntryName(v.getId()) : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    static Object getFieldValue(Object obj, String fieldName) {
        if (obj == null || fieldName == null) return null;
        try {
            Field f = ReflectionCache.field(obj.getClass(), fieldName);
            if (f != null) return f.get(obj);
        } catch (Throwable ignored) {}
        return null;
    }

    static View getViewField(Object obj, String fieldName) {
        Object val = getFieldValue(obj, fieldName);
        return (val instanceof View) ? (View) val : null;
    }

    static Object callMethodNoArgs(Object obj, String methodName) {
        if (obj == null || methodName == null) return null;
        try {
            Method m = ReflectionCache.method(obj.getClass(), methodName);
            return m == null ? null : m.invoke(obj, NO_ARGS);
        } catch (Throwable ignored) {}
        return null;
    }

    static void callFloatMethod(Object obj, String methodName, float arg) {
        if (obj == null || methodName == null) return;
        try {
            Method m = ReflectionCache.method(obj.getClass(), methodName, float.class);
            if (m != null) {
                m.invoke(obj, arg);
            }
        } catch (Throwable ignored) {}
    }

    static View callViewGetter(Object obj, String getterName) {
        Object val = callMethodNoArgs(obj, getterName);
        return (val instanceof View) ? (View) val : null;
    }

    static float callFloatGetter(Object obj, String getterName, float defVal) {
        Object val = callMethodNoArgs(obj, getterName);
        return (val instanceof Float) ? (Float) val : defVal;
    }

    private static Chronometer findFirstChronometer(View node, int depth) {
        if (node instanceof Chronometer) return (Chronometer) node;
        if (!(node instanceof ViewGroup) || depth >= 4) return null;
        ViewGroup vg = (ViewGroup) node;
        int count = Math.min(vg.getChildCount(), 8);
        for (int i = 0; i < count; i++) {
            Chronometer found = findFirstChronometer(vg.getChildAt(i), depth + 1);
            if (found != null) return found;
        }
        return null;
    }
}
