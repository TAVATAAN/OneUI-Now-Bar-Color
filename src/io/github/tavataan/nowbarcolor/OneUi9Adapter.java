package io.github.tavataan.nowbarcolor;

import android.view.View;

/**
 * Isolated adapter for One UI 9+ (Android 17 / SDK 37+, AOD_Service 8.9.65+).
 *
 * <p>Supports MPSM backdrop containers, {@code baseClearContainerBgMethod},
 * {@code baseAodDrawableMethod}, and One UI 9 fallback fields when synthetic
 * or stripped test views lack resource IDs.</p>
 */
public final class OneUi9Adapter extends NowBarCardAdapter {
    public static final OneUi9Adapter INSTANCE = new OneUi9Adapter();

    private OneUi9Adapter() {
    }

    @Override
    public String getAdapterName() {
        return "OneUI 9+";
    }

    @Override
    public boolean supportsMpsmContainer() {
        return true;
    }

    @Override
    public boolean supportsBaseClearContainerBg() {
        return true;
    }

    @Override
    public boolean supportsBaseAodDrawable() {
        return true;
    }

    @Override
    public View resolveMpsmContainer(View card) {
        View v = resolveByGetterOrEntry(card, "getCardViewMpsmBackground", "nowbar_cardview_mpsm_background");
        if (v != null) return v;
        View legacy = getViewField(card, "K");
        if (legacy != null && legacy.getClass().getName().endsWith("NowbarCardSettingView")) {
            return null;
        }
        return legacy;
    }

    @Override
    public View resolveNormalTextContainer(View card) {
        View v = super.resolveNormalTextContainer(card);
        return v != null ? v : getViewField(card, "H1");
    }

    @Override
    public View resolveChronometerView(View card) {
        View v = super.resolveChronometerView(card);
        return v != null ? v : getViewField(card, "s1");
    }

    @Override
    public View resolveAiPresenceContainer(View card) {
        View v = super.resolveAiPresenceContainer(card);
        return v != null ? v : getViewField(card, "J1");
    }

    @Override
    public View resolveAiPresenceView(View card) {
        View v = super.resolveAiPresenceView(card);
        return v != null ? v : getViewField(card, "K1");
    }

    @Override
    public View resolveBatteryProgressBar(View card) {
        View v = super.resolveBatteryProgressBar(card);
        return v != null ? v : getViewField(card, "q1");
    }

    @Override
    public View resolveBriefBackgroundView(View card) {
        View v = super.resolveBriefBackgroundView(card);
        return v != null ? v : getViewField(card, "s1");
    }

    @Override
    public View resolveMediaPillSurface(View card) {
        View v = super.resolveMediaPillSurface(card);
        return v != null ? v : getViewField(card, "p1");
    }

    @Override
    public View resolveMediaBackdropSurface(View card) {
        View v = super.resolveMediaBackdropSurface(card);
        return v != null ? v : getViewField(card, "o1");
    }

    @Override
    public View resolveMediaSettingBgSurface(View card) {
        View v = super.resolveMediaSettingBgSurface(card);
        return v != null ? v : getViewField(card, "t1");
    }

    @Override
    public View resolveMediaShaderView(View card) {
        View v = super.resolveMediaShaderView(card);
        return v != null ? v : getViewField(card, "v1");
    }

    @Override
    public View resolveMediaShaderParent(View card) {
        View v = super.resolveMediaShaderParent(card);
        return v != null ? v : getViewField(card, "w1");
    }

    @Override
    public View resolveCardContainer(View card) {
        View v = super.resolveCardContainer(card);
        return v != null ? v : getViewField(card, "J");
    }

    @Override
    public View resolveGuidingBgView(View card) {
        View v = super.resolveGuidingBgView(card);
        return v != null ? v : getViewField(card, "F");
    }

    @Override
    public View resolveGradientBgView(View card) {
        View v = super.resolveGradientBgView(card);
        return v != null ? v : getViewField(card, "x0");
    }

    @Override
    public View resolveOuterGlowView(View card) {
        View v = super.resolveOuterGlowView(card);
        return v != null ? v : getViewField(card, "G");
    }

    @Override
    public View resolveBorderStrokeView(View card) {
        View v = super.resolveBorderStrokeView(card);
        return v != null ? v : getViewField(card, "P");
    }

    @Override
    public View resolveNudgeContainer(View card) {
        View v = super.resolveNudgeContainer(card);
        return v != null ? v : getViewField(card, "e0");
    }

    @Override
    public Object resolveCardItem(View card) {
        Object item = super.resolveCardItem(card);
        return item != null ? item : getFieldValue(card, "g0");
    }

    @Override
    public float resolveCardRadius(View card, float fallback) {
        float direct = callFloatGetter(card, "getCardRadius", -1f);
        if (direct > 0f) return direct;
        direct = callFloatGetter(card, "getNormalCardRadius", -1f);
        if (direct > 0f) return direct;
        Object raw = getFieldValue(card, "w0");
        return (raw instanceof Float && (Float) raw > 0f) ? (Float) raw : fallback;
    }

    @Override
    public float resolveMediaBlackAodRadius(View card, float fallback) {
        float direct = callFloatGetter(card, "getNormalCardRadius", -1f);
        if (direct > 0f) return direct;
        Object raw = getFieldValue(card, "w0");
        return (raw instanceof Float && (Float) raw > 0f) ? (Float) raw : fallback;
    }

    @Override
    public float resolveNormalCardWidth(View card) {
        float w = super.resolveNormalCardWidth(card);
        if (w > 0f) return w;
        Object raw = getFieldValue(card, "o0");
        return (raw instanceof Float) ? (Float) raw : 0f;
    }

    @Override
    public float resolveNormalCardHeight(View card) {
        float h = super.resolveNormalCardHeight(card);
        if (h > 0f) return h;
        Object raw = getFieldValue(card, "p0");
        return (raw instanceof Float) ? (Float) raw : 0f;
    }

    @Override
    public float readDarkAmountFallback(View card) {
        Object raw = getFieldValue(card, "E");
        return (raw instanceof Float) ? (Float) raw : 0.0f;
    }

    @Override
    public boolean readInitialDozingFallback(View card) {
        if (card == null || ReflectionCache.method(card.getClass(), "setInitialDozing", boolean.class) != null) {
            return false;
        }
        Object raw = getFieldValue(card, "D");
        return (raw instanceof Boolean) ? (Boolean) raw : false;
    }

    @Override
    public int readCardStateFallback(View card) {
        Object raw = getFieldValue(card, "j0");
        return (raw instanceof Integer) ? (Integer) raw : 0;
    }

    @Override
    public Boolean readExpandedFallback(View card) {
        Object val = getFieldValue(card, "l0");
        return (val instanceof Boolean) ? (Boolean) val : null;
    }

    @Override
    public boolean resolveBriefScreenOn(View card) {
        Object raw = getFieldValue(card, "u1");
        return (raw instanceof Boolean) ? (Boolean) raw : true;
    }

    @Override
    public void restoreBriefCardExtra(View card, HookMap map, float darkAmount) {
        super.restoreBriefCardExtra(card, map, darkAmount);
        if (card == null) return;
        callMethodNoArgs(card, "l0");
    }

    @Override
    public void triggerShapeRefresh(View card, HookMap map) {
        if (card == null) return;
        String method = (map != null && map.baseShapeMethod != null) ? map.baseShapeMethod : "E";
        callMethodNoArgs(card, method);
    }
}
