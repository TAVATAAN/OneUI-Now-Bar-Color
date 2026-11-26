package io.github.tavataan.nowbarcolor;

import android.view.View;

/**
 * Isolated adapter for One UI 8.x (Android 16 / SDK 36, AOD_Service 8.9.32.x).
 *
 * <p>Avoids One UI 9 R8 field names where letters collide destructively with core
 * views on 8.9.32.x (e.g., field {@code G} is {@code normal_content_rootview},
 * {@code F} is {@code nowbar_card_border_stroke_view}, and {@code K} is
 * {@code NowbarCardSettingView}). Uses exclusively resource IDs, public getters,
 * and HookMap-resolved symbols.</p>
 */
public final class OneUi85Adapter extends NowBarCardAdapter {
    public static final OneUi85Adapter INSTANCE = new OneUi85Adapter();

    private OneUi85Adapter() {
    }

    @Override
    public String getAdapterName() {
        return "OneUI 8.5";
    }

    @Override
    public View resolveAiPresenceContainer(View card) {
        return null;
    }

    @Override
    public View resolveAiPresenceView(View card) {
        return null;
    }
}
