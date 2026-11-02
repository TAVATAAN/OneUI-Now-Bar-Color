package com.tavataan.nowbarcolor;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.Checkable;

/**
 * Exact replica of Samsung One UI / SESL Dark Mode Switch (SeslSwitch).
 *
 * Geometry & Proportions:
 * - Track: 44dp width x 24dp height (CornerRadius = 12dp / pill shape)
 * - Thumb: 20dp x 20dp circle at rest, horizontally stretching to 23dp on press
 * - Padding: Exactly 2dp uniform margin between thumb and track inner edges
 * - Elevation: 0dp (no shadows, contained within bounds, zero halo bleed)
 *
 * One UI Dark Mode Palette:
 * - Active (ON):   Track fill #1477FF, Track stroke 1dp #3367B8, Thumb #FFFFFF
 * - Inactive (OFF): Track fill #4E4E53, Track stroke 1dp #636368, Thumb #E0E0E5
 *
 * Physics & Transitions:
 * - 200ms smooth transition with Samsung One UI Cubic Bezier PathInterpolator(0.22f, 1f, 0.36f, 1f)
 * - Dual support: Instant tap-to-toggle + continuous drag gesture
 * - Tactile stretch: Subtly expands thumb width (20dp -> 23dp) on touch down and relaxes on release
 */
public class OneUISwitch extends View implements Checkable {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(OneUISwitch view, boolean isChecked);
    }

    // Geometry constants (in dp)
    private static final float TRACK_WIDTH_DP = 44f;
    private static final float TRACK_HEIGHT_DP = 24f;
    private static final float TRACK_RADIUS_DP = 12f;
    private static final float STROKE_WIDTH_DP = 1f;
    private static final float THUMB_REST_WIDTH_DP = 20f;
    private static final float THUMB_HEIGHT_DP = 20f;
    private static final float THUMB_MAX_STRETCH_DP = 3f; // 20dp -> 23dp
    private static final float THUMB_MARGIN_DP = 2f;

    // Samsung One UI Dark Mode color palette
    private static final int COLOR_TRACK_ON = 0xFF1477FF;
    private static final int COLOR_STROKE_ON = 0xFF3367B8;
    private static final int COLOR_THUMB_ON = 0xFFFFFFFF;

    private static final int COLOR_TRACK_OFF = 0xFF4E4E53;
    private static final int COLOR_STROKE_OFF = 0xFF636368;
    private static final int COLOR_THUMB_OFF = 0xFFE0E0E5;

    // Timing and Bezier curves
    private static final long DURATION_TOGGLE_MS = 200;
    private static final long DURATION_STRETCH_DOWN_MS = 100;
    private static final long DURATION_STRETCH_UP_MS = 150;
    private static final Interpolator ONE_UI_INTERPOLATOR = new PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f);

    // Converted pixel dimensions
    private float mTrackWidth;
    private float mTrackHeight;
    private float mTrackRadius;
    private float mStrokeWidth;
    private float mThumbRestWidth;
    private float mThumbHeight;
    private float mThumbMaxStretch;
    private float mThumbMargin;
    private int mTouchSlop;

    // Drawing objects (reused to avoid GC churn)
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mTrackRect = new RectF();
    private final RectF mStrokeRect = new RectF();
    private final RectF mThumbRect = new RectF();

    // State & Animators
    private boolean mChecked = false;
    private float mProgress = 0f;        // 0.0f = OFF, 1.0f = ON
    private float mStretchFactor = 0f;    // 0.0f = rest (20dp), 1.0f = stretched (23dp)
    private ValueAnimator mProgressAnimator;
    private ValueAnimator mStretchAnimator;
    private OnCheckedChangeListener mListener;

    // Gesture tracking
    private float mStartX;
    private float mStartY;
    private float mStartProgress;
    private boolean mIsDragging = false;

    public OneUISwitch(Context context) {
        super(context);
        init(context);
    }

    public OneUISwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public OneUISwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        mTrackWidth = TRACK_WIDTH_DP * density;
        mTrackHeight = TRACK_HEIGHT_DP * density;
        mTrackRadius = TRACK_RADIUS_DP * density;
        mStrokeWidth = STROKE_WIDTH_DP * density;
        mThumbRestWidth = THUMB_REST_WIDTH_DP * density;
        mThumbHeight = THUMB_HEIGHT_DP * density;
        mThumbMaxStretch = THUMB_MAX_STRETCH_DP * density;
        mThumbMargin = THUMB_MARGIN_DP * density;

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setFocusable(true);
        setClickable(true);
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        mListener = listener;
    }

    @Override
    public boolean isChecked() {
        return mChecked;
    }

    @Override
    public void toggle() {
        setChecked(!mChecked, true);
    }

    @Override
    public void setChecked(boolean checked) {
        setChecked(checked, isAttachedToWindow());
    }

    public void setChecked(boolean checked, boolean animate) {
        setCheckedInternal(checked, animate, false);
    }

    private void setCheckedInternal(boolean checked, boolean animate, boolean notifyListener) {
        boolean changed = (mChecked != checked);
        mChecked = checked;
        float targetProgress = checked ? 1.0f : 0.0f;

        if (animate && isAttachedToWindow()) {
            animateProgress(targetProgress);
        } else {
            if (mProgressAnimator != null && mProgressAnimator.isRunning()) {
                mProgressAnimator.cancel();
            }
            mProgress = targetProgress;
            invalidate();
        }

        if (changed && notifyListener && mListener != null) {
            mListener.onCheckedChanged(this, mChecked);
        }
    }

    private void animateProgress(float targetProgress) {
        if (mProgressAnimator != null && mProgressAnimator.isRunning()) {
            mProgressAnimator.cancel();
        }
        mProgressAnimator = ValueAnimator.ofFloat(mProgress, targetProgress);
        mProgressAnimator.setDuration(DURATION_TOGGLE_MS);
        mProgressAnimator.setInterpolator(ONE_UI_INTERPOLATOR);
        mProgressAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mProgress = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        mProgressAnimator.start();
    }

    private void animateStretch(float targetStretch) {
        if (mStretchAnimator != null && mStretchAnimator.isRunning()) {
            mStretchAnimator.cancel();
        }
        mStretchAnimator = ValueAnimator.ofFloat(mStretchFactor, targetStretch);
        mStretchAnimator.setDuration(targetStretch > 0f ? DURATION_STRETCH_DOWN_MS : DURATION_STRETCH_UP_MS);
        mStretchAnimator.setInterpolator(ONE_UI_INTERPOLATOR);
        mStretchAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mStretchFactor = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        mStretchAnimator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredW = Math.round(mTrackWidth) + getPaddingLeft() + getPaddingRight();
        int desiredH = Math.round(mTrackHeight) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(
                resolveSizeAndState(desiredW, widthMeasureSpec, 0),
                resolveSizeAndState(desiredH, heightMeasureSpec, 0)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int availW = getWidth() - getPaddingLeft() - getPaddingRight();
        int availH = getHeight() - getPaddingTop() - getPaddingBottom();

        float trackLeft = getPaddingLeft() + (availW - mTrackWidth) / 2f;
        float trackTop = getPaddingTop() + (availH - mTrackHeight) / 2f;
        float trackRight = trackLeft + mTrackWidth;
        float trackBottom = trackTop + mTrackHeight;

        mTrackRect.set(trackLeft, trackTop, trackRight, trackBottom);

        int trackFill = evaluateColor(mProgress, COLOR_TRACK_OFF, COLOR_TRACK_ON);
        int trackStroke = evaluateColor(mProgress, COLOR_STROKE_OFF, COLOR_STROKE_ON);
        int thumbColor = evaluateColor(mProgress, COLOR_THUMB_OFF, COLOR_THUMB_ON);

        int alpha = isEnabled() ? 255 : 128;

        // 1. Draw Track Fill (Pill Shape)
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(trackFill);
        mPaint.setAlpha(alpha);
        canvas.drawRoundRect(mTrackRect, mTrackRadius, mTrackRadius, mPaint);

        // 2. Draw Track Stroke (1dp subtle bevel)
        float halfStroke = mStrokeWidth / 2f;
        mStrokeRect.set(
                trackLeft + halfStroke,
                trackTop + halfStroke,
                trackRight - halfStroke,
                trackBottom - halfStroke
        );
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mStrokeWidth);
        mPaint.setColor(trackStroke);
        mPaint.setAlpha(alpha);
        canvas.drawRoundRect(mStrokeRect, mTrackRadius - halfStroke, mTrackRadius - halfStroke, mPaint);

        // 3. Draw Thumb (Circle resting at 20dp, stretching to 23dp on press)
        float currentThumbWidth = mThumbRestWidth + (mThumbMaxStretch * mStretchFactor);
        float maxTravel = mTrackWidth - (2f * mThumbMargin) - currentThumbWidth;
        if (maxTravel < 0f) maxTravel = 0f;

        float thumbLeft = trackLeft + mThumbMargin + (mProgress * maxTravel);
        float thumbTop = trackTop + mThumbMargin;
        float thumbRight = thumbLeft + currentThumbWidth;
        float thumbBottom = thumbTop + mThumbHeight;

        mThumbRect.set(thumbLeft, thumbTop, thumbRight, thumbBottom);
        float thumbRadius = mThumbHeight / 2f;

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(thumbColor);
        mPaint.setAlpha(alpha);
        canvas.drawRoundRect(mThumbRect, thumbRadius, thumbRadius, mPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mStartX = event.getX();
                mStartY = event.getY();
                mStartProgress = mProgress;
                mIsDragging = false;
                animateStretch(1.0f);
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - mStartX;
                float dy = event.getY() - mStartY;

                if (!mIsDragging) {
                    if (Math.abs(dx) > mTouchSlop || Math.abs(dy) > mTouchSlop) {
                        if (Math.abs(dx) > Math.abs(dy)) {
                            mIsDragging = true;
                            if (getParent() != null) {
                                getParent().requestDisallowInterceptTouchEvent(true);
                            }
                        } else {
                            // User is scrolling vertically, cancel switch interaction
                            animateStretch(0.0f);
                            if (getParent() != null) {
                                getParent().requestDisallowInterceptTouchEvent(false);
                            }
                            return false;
                        }
                    }
                }

                if (mIsDragging) {
                    float travel = mTrackWidth - (2f * mThumbMargin) - mThumbRestWidth;
                    if (travel > 0) {
                        float newProgress = mStartProgress + (dx / travel);
                        mProgress = Math.max(0f, Math.min(1f, newProgress));
                        invalidate();
                    }
                    return true;
                }
                return true;
            }

            case MotionEvent.ACTION_UP: {
                animateStretch(0.0f);
                if (mIsDragging) {
                    boolean targetState = mProgress >= 0.5f;
                    setCheckedInternal(targetState, true, true);
                } else {
                    // Instant Tap
                    setCheckedInternal(!mChecked, true, true);
                    playSoundEffect(SoundEffectConstants.CLICK);
                    performClick();
                }
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;
            }

            case MotionEvent.ACTION_CANCEL: {
                animateStretch(0.0f);
                animateProgress(mChecked ? 1.0f : 0.0f);
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;
            }
        }

        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(mChecked);
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setChecked(mChecked);
    }

    private static int evaluateColor(float fraction, int startValue, int endValue) {
        int startA = (startValue >> 24) & 0xff;
        int startR = (startValue >> 16) & 0xff;
        int startG = (startValue >> 8) & 0xff;
        int startB = startValue & 0xff;

        int endA = (endValue >> 24) & 0xff;
        int endR = (endValue >> 16) & 0xff;
        int endG = (endValue >> 8) & 0xff;
        int endB = endValue & 0xff;

        return ((startA + (int) (fraction * (endA - startA))) << 24) |
               ((startR + (int) (fraction * (endR - startR))) << 16) |
               ((startG + (int) (fraction * (endG - startG))) << 8) |
               ((startB + (int) (fraction * (endB - startB))));
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.checked = mChecked;
        return ss;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setChecked(ss.checked, false);
    }

    static class SavedState extends BaseSavedState {
        boolean checked;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            checked = in.readInt() == 1;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(checked ? 1 : 0);
        }

        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
