package io.github.tavataan.nowbarcolor;

import android.content.res.ColorStateList;
import android.graphics.BlendMode;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;

/** Detect mutations of the SAME drawable without allocating getColors() arrays per frame. */
final class CapsuleGradient extends GradientDrawable {
    private boolean damaged = true;
    private boolean configuring;
    private int startColor;
    private int endColor;
    private float corner;
    private ColorStateList tint;
    private ColorFilter filter;

    boolean needsRepair() { return damaged; }

    private void changed() { if (!configuring) damaged = true; }

    void configure(int start, int end, float radius) {
        if (!damaged && startColor == start && endColor == end && corner == radius) return;
        configuring = true;
        try {
            // Opacity belongs to the two ARGB stops, not to an inherited animation alpha.
            setAlpha(255);
            setVisible(true, false);
            setTintList(null);
            clearColorFilter();
            setShape(RECTANGLE);
            setGradientType(LINEAR_GRADIENT);
            setOrientation(Orientation.LEFT_RIGHT);
            setColors(new int[]{start, end});
            setCornerRadius(radius);
            startColor = start;
            endColor = end;
            corner = radius;
            damaged = false;
        } finally {
            configuring = false;
        }
    }

    @Override public void setAlpha(int alpha) {
        if (getAlpha() != alpha) changed();
        super.setAlpha(alpha);
    }
    @Override public void setColor(int color) { changed(); super.setColor(color); }
    @Override public void setColor(ColorStateList color) { changed(); super.setColor(color); }
    @Override public void setColors(int[] colors) { changed(); super.setColors(colors); }
    @Override public void setColors(int[] colors, float[] offsets) { changed(); super.setColors(colors, offsets); }
    @Override public void setTintList(ColorStateList value) {
        if (tint != value) changed();
        super.setTintList(value);
        tint = value;
    }
    @Override public void setTintMode(PorterDuff.Mode mode) { changed(); super.setTintMode(mode); }
    @Override public void setTintBlendMode(BlendMode mode) { changed(); super.setTintBlendMode(mode); }
    @Override public void setColorFilter(ColorFilter value) {
        if (filter != value) changed();
        super.setColorFilter(value);
        filter = value;
    }
    @Override public boolean setVisible(boolean visible, boolean restart) {
        if (isVisible() != visible) changed();
        return super.setVisible(visible, restart);
    }
    @Override public void setCornerRadius(float radius) {
        if (getCornerRadius() != radius) changed();
        super.setCornerRadius(radius);
    }
    @Override public void setOrientation(Orientation orientation) {
        if (getOrientation() != orientation) changed();
        super.setOrientation(orientation);
    }
    @Override public void setShape(int shape) { changed(); super.setShape(shape); }
    @Override public void setGradientType(int type) { changed(); super.setGradientType(type); }
}
