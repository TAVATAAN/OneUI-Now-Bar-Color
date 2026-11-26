package io.github.tavataan.nowbarcolor;

import android.view.View;

/**
 * Isolated adapter for One UI 8.0 (Android 16 / SDK 36, AOD_Service 8.9.15.x).
 *
 * <p>On 8.9.15.x, {@code AbsNowBarCardView} exposes {@code getCardGuidingEffectView()}
 * instead of {@code getCardOuterGlowView()}, {@code baseBlurMethod} takes
 * {@code androidx.cardview.widget.CardView} as parameter 2, {@code AI_BRIEF} renders via
 * {@code NowBarExternalViewCardView} + {@code a.F()} ({@code baseAodDrawableMethod}), and
 * One UI 8.5+ features ({@code NowBarBriefCardView}, {@code mediaForegroundMethod},
 * {@code baseProgressBgMethod}) are absent.</p>
 */
public final class OneUi80Adapter extends NowBarCardAdapter {
    public static final OneUi80Adapter INSTANCE = new OneUi80Adapter();

    private OneUi80Adapter() {
    }

    @Override
    public String getAdapterName() {
        return "OneUI 8.0";
    }

    @Override
    public boolean supportsBaseAodDrawable() {
        return true;
    }

    @Override
    public boolean supportsMediaForegroundLighting() {
        return false;
    }

    @Override
    public boolean supportsBriefCardView() {
        return false;
    }

    @Override
    public View resolveOuterGlowView(View card) {
        View v = callViewGetter(card, "getCardGuidingEffectView");
        return v != null ? v : super.resolveOuterGlowView(card);
    }

    @Override
    public View resolveAiPresenceContainer(View card) {
        return null;
    }

    @Override
    public View resolveAiPresenceView(View card) {
        return null;
    }

    @Override
    public void restoreBriefCardExtra(View card, HookMap map, float darkAmount) {
        if (card == null || map == null) return;
        if (map.baseProgressBgMethod != null) {
            super.restoreBriefCardExtra(card, map, darkAmount);
        } else {
            triggerShapeRefresh(card, map);
        }
    }

    @Override
    public Boolean readExpandedFallback(View card) {
        Object val = getFieldValue(card, "Q");
        return (val instanceof Boolean) ? (Boolean) val : null;
    }
}
