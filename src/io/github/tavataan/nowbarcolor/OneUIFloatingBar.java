package io.github.tavataan.nowbarcolor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * Compact, dark-only floating bottom navigation bar following One UI Floating Bar specs.
 * Houses two centered white icon tabs with a sliding translucent pill indicator.
 */
public class OneUIFloatingBar extends FrameLayout {

    public interface OnTabSelectedListener {
        void onTabSelected(int tabIndex);
    }

    private static final int COLOR_PILL_BG = 0xE63A3A3D;
    private static final int COLOR_PILL_BORDER = 0x24FFFFFF;
    private static final int COLOR_ACTIVE_PILL = 0x28FFFFFF;
    private static final int COLOR_ACTIVE_BORDER = 0x20FFFFFF;
    private static final int COLOR_RIPPLE = 0x26FFFFFF;

    private static final float ACTIVE_ALPHA = 1.0f;
    private static final float INACTIVE_ALPHA = 0.48f;
    private static final float ACTIVE_SCALE = 1.0f;
    private static final float INACTIVE_SCALE = 0.92f;
    private static final long ANIM_DURATION_MS = 240L;

    private static final PathInterpolator ONE_UI_EASING =
            new PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f);

    private final View mSelectionPill;
    private final FrameLayout[] mTabSlots = new FrameLayout[2];
    private final ImageView[] mTabIcons = new ImageView[2];
    private final int mTabWidthPx;
    private final int mTabGapPx;

    private int mSelectedTab = 0;
    private ValueAnimator mSlideAnimator;
    private OnTabSelectedListener mListener;

    public OneUIFloatingBar(Context context) {
        super(context);

        mTabWidthPx = dp(68);
        int tabHeightPx = dp(44);
        mTabGapPx = dp(4);
        int padHPx = dp(6);
        int padVPx = dp(5);

        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setShape(GradientDrawable.RECTANGLE);
        pillBg.setColor(COLOR_PILL_BG);
        pillBg.setCornerRadius(dp(999));
        pillBg.setStroke(dp(1), COLOR_PILL_BORDER);
        setBackground(pillBg);

        setElevation(dp(4));
        setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        setClipToOutline(true);
        setPadding(padHPx, padVPx, padHPx, padVPx);

        mSelectionPill = new View(context);
        GradientDrawable activeBg = new GradientDrawable();
        activeBg.setShape(GradientDrawable.RECTANGLE);
        activeBg.setColor(COLOR_ACTIVE_PILL);
        activeBg.setCornerRadius(dp(999));
        activeBg.setStroke(dp(1), COLOR_ACTIVE_BORDER);
        mSelectionPill.setBackground(activeBg);
        FrameLayout.LayoutParams indicatorLp =
                new FrameLayout.LayoutParams(mTabWidthPx, tabHeightPx);
        indicatorLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        addView(mSelectionPill, indicatorLp);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        addView(row, new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, tabHeightPx));

        String[] iconNames = {
                "ic_tab_global_sphere",
                "ic_tab_individual_brush"
        };
        String pkgName = context.getPackageName();

        for (int i = 0; i < 2; i++) {
            final int tabIndex = i;
            FrameLayout slot = new FrameLayout(context);
            slot.setClickable(true);
            slot.setFocusable(true);
            slot.setBackground(createSlotRipple());

            ImageView icon = new ImageView(context);
            int resId = context.getResources().getIdentifier(iconNames[i], "drawable", pkgName);
            if (resId == 0) {
                resId = context.getResources().getIdentifier(
                        iconNames[i], "drawable", "io.github.tavataan.nowbarcolor");
            }
            if (resId != 0) {
                icon.setImageResource(resId);
            }
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int iconSize = dp(24);
            FrameLayout.LayoutParams iconLp =
                    new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
            slot.addView(icon, iconLp);

            slot.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mSelectedTab != tabIndex) {
                        setSelectedTab(tabIndex, true);
                        if (mListener != null) {
                            mListener.onTabSelected(tabIndex);
                        }
                    }
                }
            });

            LinearLayout.LayoutParams slotLp =
                    new LinearLayout.LayoutParams(mTabWidthPx, tabHeightPx);
            if (i > 0) {
                slotLp.leftMargin = mTabGapPx;
            }
            row.addView(slot, slotLp);
            mTabSlots[i] = slot;
            mTabIcons[i] = icon;
        }

        applyImmediateState(0);
    }

    public void setOnTabSelectedListener(OnTabSelectedListener listener) {
        mListener = listener;
    }

    public void updateContentDescriptions(String tab0Desc, String tab1Desc) {
        mTabSlots[0].setContentDescription(tab0Desc);
        mTabSlots[1].setContentDescription(tab1Desc);
    }

    public void setSelectedTab(int tabIndex, boolean animate) {
        int clamped = (tabIndex == 1) ? 1 : 0;
        boolean changed = (clamped != mSelectedTab);
        mSelectedTab = clamped;

        if (!animate || !changed) {
            if (mSlideAnimator != null) {
                mSlideAnimator.cancel();
                mSlideAnimator = null;
            }
            applyImmediateState(clamped);
            return;
        }

        if (mSlideAnimator != null) {
            mSlideAnimator.cancel();
        }

        final float startX = mSelectionPill.getTranslationX();
        final float targetX = targetTranslationX(clamped);

        mSlideAnimator = ValueAnimator.ofFloat(0f, 1f);
        mSlideAnimator.setDuration(ANIM_DURATION_MS);
        mSlideAnimator.setInterpolator(ONE_UI_EASING);
        mSlideAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator anim) {
                float f = (float) anim.getAnimatedValue();
                mSelectionPill.setTranslationX(startX + (targetX - startX) * f);
            }
        });
        mSlideAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mSelectionPill.setTranslationX(targetX);
            }
        });
        mSlideAnimator.start();

        for (int i = 0; i < 2; i++) {
            boolean active = (i == clamped);
            mTabSlots[i].setSelected(active);
            mTabIcons[i].animate()
                    .alpha(active ? ACTIVE_ALPHA : INACTIVE_ALPHA)
                    .scaleX(active ? ACTIVE_SCALE : INACTIVE_SCALE)
                    .scaleY(active ? ACTIVE_SCALE : INACTIVE_SCALE)
                    .setDuration(ANIM_DURATION_MS)
                    .setInterpolator(ONE_UI_EASING)
                    .start();
        }
    }

    private void applyImmediateState(int activeIndex) {
        mSelectionPill.setTranslationX(targetTranslationX(activeIndex));
        for (int i = 0; i < 2; i++) {
            boolean active = (i == activeIndex);
            mTabSlots[i].setSelected(active);
            mTabIcons[i].animate().cancel();
            mTabIcons[i].setAlpha(active ? ACTIVE_ALPHA : INACTIVE_ALPHA);
            mTabIcons[i].setScaleX(active ? ACTIVE_SCALE : INACTIVE_SCALE);
            mTabIcons[i].setScaleY(active ? ACTIVE_SCALE : INACTIVE_SCALE);
        }
    }

    private float targetTranslationX(int index) {
        return index == 1 ? (mTabWidthPx + mTabGapPx) : 0f;
    }

    private RippleDrawable createSlotRipple() {
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.RECTANGLE);
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(999));
        return new RippleDrawable(ColorStateList.valueOf(COLOR_RIPPLE), null, mask);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
