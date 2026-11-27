package io.github.tavataan.nowbarcolor;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;

/**
 * Interactive One UI pan & pinch-zoom pill cropper modal with optional ghosted capsule guide
 * (showing album circle, title, and media controls layout) that bakes an 800x200 WebP safely off the UI thread.
 */
final class CapsuleCropDialog {

    interface Callback {
        void onBaked(CapsuleImageProcessor.BakeResult result);
    }

    private CapsuleCropDialog() {}

    static void showForUri(
            final Context ctx,
            final Uri uri,
            final String slot,
            final int style,
            final int blurPercent,
            final int duotonePreset,
            final int wallPrimary,
            final int wallAccent,
            final ExecutorService executor,
            final Callback callback) {
        final Handler main = new Handler(Looper.getMainLooper());
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap decoded = CapsuleImageProcessor.decodeSampledFromUri(ctx, uri);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (decoded == null) {
                            Toast.makeText(ctx, I18n.get(ctx, "crop_decoding_err"), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        showWithBitmap(ctx, decoded, slot, style, blurPercent, duotonePreset,
                                wallPrimary, wallAccent, executor, callback);
                    }
                });
            }
        });
    }

    static void showForExistingCrop(
            final Context ctx,
            final String slot,
            final int style,
            final int blurPercent,
            final int duotonePreset,
            final int wallPrimary,
            final int wallAccent,
            final ExecutorService executor,
            final Callback callback) {
        final Handler main = new Handler(Looper.getMainLooper());
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap existing = CapsuleImageProcessor.loadCropBitmap(ctx, slot);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (existing == null) {
                            Toast.makeText(ctx, I18n.get(ctx, "crop_decoding_err"), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        showWithBitmap(ctx, existing, slot, style, blurPercent, duotonePreset,
                                wallPrimary, wallAccent, executor, callback);
                    }
                });
            }
        });
    }

    private static void showWithBitmap(
            final Context ctx,
            final Bitmap sourceBitmap,
            final String slot,
            final int style,
            final int blurPercent,
            final int duotonePreset,
            final int wallPrimary,
            final int wallAccent,
            final ExecutorService executor,
            final Callback callback) {
        final float d = ctx.getResources().getDisplayMetrics().density;
        final Handler main = new Handler(Looper.getMainLooper());

        final AlertDialog dialog = new AlertDialog.Builder(ctx).create();
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF232325);
        cardBg.setCornerRadius(26f * d);
        cardBg.setStroke(Math.max(1, Math.round(d)), 0xFF38383B);
        card.setBackground(cardBg);
        int pad = Math.round(16f * d);
        card.setPadding(pad, pad, pad, pad);

        TextView titleTv = new TextView(ctx);
        titleTv.setText(I18n.get(ctx, "crop_dialog_title"));
        titleTv.setTextColor(0xFFFFFFFF);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        card.addView(titleTv);

        TextView hintTv = new TextView(ctx);
        hintTv.setText(I18n.get(ctx, "crop_hint"));
        hintTv.setTextColor(0xFF9AA0B4);
        hintTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        hintTv.setPadding(0, Math.round(4f * d), 0, Math.round(12f * d));
        card.addView(hintTv);

        final CropStageView stageView = new CropStageView(ctx, sourceBitmap);
        LinearLayout.LayoutParams stageLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(250f * d));
        card.addView(stageView, stageLp);

        LinearLayout toolsRow = new LinearLayout(ctx);
        toolsRow.setOrientation(LinearLayout.HORIZONTAL);
        toolsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trLp.topMargin = Math.round(12f * d);
        trLp.bottomMargin = Math.round(8f * d);

        final Button btnReset = makePillButton(ctx, I18n.get(ctx, "crop_reset"), false, d);
        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stageView.resetFit();
            }
        });

        final Button btnGuide = makePillButton(ctx, I18n.get(ctx, "crop_guide"), true, d);
        btnGuide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !stageView.isShowGuide();
                stageView.setShowGuide(next);
                styleButton(btnGuide, next, d);
            }
        });

        toolsRow.addView(btnReset, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        View tGap = new View(ctx);
        toolsRow.addView(tGap, new LinearLayout.LayoutParams(Math.round(8f * d), 1));
        toolsRow.addView(btnGuide, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(toolsRow, trLp);

        LinearLayout actionsRow = new LinearLayout(ctx);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);

        final Button btnCancel = makePillButton(ctx, I18n.get(ctx, "cancel"), false, d);
        btnCancel.setTextColor(0xFFB0B8C8);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        final Button btnSave = makePillButton(ctx, I18n.get(ctx, "crop_save"), true, d);
        btnSave.setTypeface(Typeface.DEFAULT_BOLD);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnSave.setEnabled(false);
                btnCancel.setEnabled(false);
                btnReset.setEnabled(false);
                btnSave.setText(I18n.get(ctx, "crop_baking"));
                final Bitmap cropped = stageView.renderFramedPillBitmap();
                if (cropped == null) {
                    Toast.makeText(ctx, I18n.get(ctx, "crop_decoding_err"), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    return;
                }
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        final CapsuleImageProcessor.BakeResult result;
                        try {
                            result = CapsuleImageProcessor.saveCropAndBake(
                                    ctx, slot, cropped, style, blurPercent, duotonePreset,
                                    wallPrimary, wallAccent);
                        } finally {
                            cropped.recycle();
                        }
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                dialog.dismiss();
                                if (result == null) {
                                    Toast.makeText(ctx, I18n.get(ctx, "crop_decoding_err"), Toast.LENGTH_SHORT).show();
                                } else if (callback != null) {
                                    callback.onBaked(result);
                                }
                            }
                        });
                    }
                });
            }
        });

        actionsRow.addView(btnCancel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        View aGap = new View(ctx);
        actionsRow.addView(aGap, new LinearLayout.LayoutParams(Math.round(8f * d), 1));
        actionsRow.addView(btnSave, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f));
        card.addView(actionsRow);

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dInt) {
                if (!sourceBitmap.isRecycled()) {
                    sourceBitmap.recycle();
                }
            }
        });
        dialog.setView(card);
        dialog.show();
    }

    private static Button makePillButton(Context ctx, String text, boolean accent, float d) {
        Button b = new Button(ctx);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        b.setMinimumHeight(Math.round(40f * d));
        b.setPadding(Math.round(8f * d), Math.round(6f * d), Math.round(8f * d), Math.round(6f * d));
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        styleButton(b, accent, d);
        return b;
    }

    private static void styleButton(Button b, boolean accent, float d) {
        b.setTextColor(accent ? 0xFFFFFFFF : 0xFFE0E0E5);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(14f * d);
        bg.setColor(accent ? 0xFF1477FF : 0xFF2E2E32);
        bg.setStroke(Math.max(1, Math.round(d)), accent ? 0xFF3367B8 : 0xFF45454A);
        b.setBackground(bg);
    }

    /** Interactive pan/pinch canvas enforcing pill boundary coverage with optional ghosted guide overlay. */
    private static final class CropStageView extends View {
        private final Bitmap source;
        private final float density;
        private final Matrix imageMatrix = new Matrix();
        private final float[] mValues = new float[9];

        private final RectF stageRect = new RectF();
        private final RectF pillRect = new RectF();
        private final Path maskPath = new Path();
        private final Path pillPath = new Path();

        private final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint guideFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float minScale = 1.0f;
        private float maxScale = 6.0f;
        private float lastX, lastY;
        private int activePointerId = MotionEvent.INVALID_POINTER_ID;
        private boolean showGuide = true;

        private final ScaleGestureDetector scaleDetector;
        private final GestureDetector tapDetector;

        CropStageView(Context ctx, Bitmap src) {
            super(ctx);
            this.source = src;
            this.density = ctx.getResources().getDisplayMetrics().density;

            maskPaint.setColor(0xC2000000);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(1.6f * density);
            borderPaint.setColor(0xE682B1FF);

            guidePaint.setStyle(Paint.Style.STROKE);
            guidePaint.setStrokeWidth(1.1f * density);
            guidePaint.setColor(0x99FFFFFF);
            guidePaint.setPathEffect(new DashPathEffect(new float[]{4f * density, 3f * density}, 0f));

            guideFillPaint.setStyle(Paint.Style.FILL);
            guideFillPaint.setColor(0x28FFFFFF);

            setLayerType(LAYER_TYPE_SOFTWARE, null); // ensures crisp DIFFERENCE/mask Path rendering across all GPUs

            scaleDetector = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    float factor = detector.getScaleFactor();
                    float cur = currentScale();
                    float next = Math.max(minScale, Math.min(maxScale, cur * factor));
                    float applied = cur > 0f ? next / cur : 1.0f;
                    imageMatrix.postScale(applied, applied, detector.getFocusX(), detector.getFocusY());
                    clampTranslationToPill();
                    invalidate();
                    return true;
                }
            });

            tapDetector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    resetFit();
                    return true;
                }
            });
        }

        boolean isShowGuide() {
            return showGuide;
        }

        void setShowGuide(boolean show) {
            this.showGuide = show;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            stageRect.set(0, 0, w, h);
            float pillW = Math.max(40f, w - 16f * density);
            float pillH = pillW / 4.0f; // matches 800x200 4:1 capsule aspect ratio
            if (pillH > h * 0.72f) {
                pillH = h * 0.72f;
                pillW = pillH * 4.0f;
            }
            float left = (w - pillW) * 0.5f;
            float top = (h - pillH) * 0.5f;
            pillRect.set(left, top, left + pillW, top + pillH);

            float pillR = pillH * 0.5f;
            pillPath.reset();
            pillPath.addRoundRect(pillRect, pillR, pillR, Path.Direction.CW);

            maskPath.reset();
            maskPath.setFillType(Path.FillType.EVEN_ODD);
            maskPath.addRect(stageRect, Path.Direction.CW);
            maskPath.addRoundRect(pillRect, pillR, pillR, Path.Direction.CCW);

            resetFit();
        }

        void resetFit() {
            if (pillRect.width() <= 0f || pillRect.height() <= 0f || source == null) return;
            float bw = source.getWidth();
            float bh = source.getHeight();
            minScale = Math.max(pillRect.width() / bw, pillRect.height() / bh);
            maxScale = minScale * 6.0f;
            imageMatrix.reset();
            imageMatrix.setScale(minScale, minScale);
            float dx = pillRect.centerX() - (bw * minScale) * 0.5f;
            float dy = pillRect.centerY() - (bh * minScale) * 0.5f;
            imageMatrix.postTranslate(dx, dy);
            invalidate();
        }

        private float currentScale() {
            imageMatrix.getValues(mValues);
            return mValues[Matrix.MSCALE_X];
        }

        private void clampTranslationToPill() {
            if (source == null || pillRect.width() <= 0f) return;
            imageMatrix.getValues(mValues);
            float sc = mValues[Matrix.MSCALE_X];
            if (sc < minScale) {
                float fix = minScale / Math.max(0.0001f, sc);
                imageMatrix.postScale(fix, fix, pillRect.centerX(), pillRect.centerY());
                imageMatrix.getValues(mValues);
                sc = mValues[Matrix.MSCALE_X];
            }
            float bw = source.getWidth() * sc;
            float bh = source.getHeight() * sc;
            float tx = mValues[Matrix.MTRANS_X];
            float ty = mValues[Matrix.MTRANS_Y];

            float minTx = pillRect.right - bw;
            float maxTx = pillRect.left;
            float minTy = pillRect.bottom - bh;
            float maxTy = pillRect.top;

            float clampedTx = Math.max(minTx, Math.min(maxTx, tx));
            float clampedTy = Math.max(minTy, Math.min(maxTy, ty));
            if (Math.abs(clampedTx - tx) > 0.01f || Math.abs(clampedTy - ty) > 0.01f) {
                imageMatrix.postTranslate(clampedTx - tx, clampedTy - ty);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            tapDetector.onTouchEvent(event);
            scaleDetector.onTouchEvent(event);

            int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    activePointerId = event.getPointerId(0);
                    lastX = event.getX();
                    lastY = event.getY();
                    return true;
                case MotionEvent.ACTION_POINTER_UP: {
                    int idx = event.getActionIndex();
                    if (event.getPointerId(idx) == activePointerId) {
                        int newIdx = idx == 0 ? 1 : 0;
                        if (newIdx < event.getPointerCount()) {
                            activePointerId = event.getPointerId(newIdx);
                            lastX = event.getX(newIdx);
                            lastY = event.getY(newIdx);
                        }
                    }
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                    if (!scaleDetector.isInProgress()) {
                        int idx = event.findPointerIndex(activePointerId);
                        if (idx >= 0) {
                            float x = event.getX(idx);
                            float y = event.getY(idx);
                            imageMatrix.postTranslate(x - lastX, y - lastY);
                            clampTranslationToPill();
                            lastX = x;
                            lastY = y;
                            invalidate();
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    activePointerId = MotionEvent.INVALID_POINTER_ID;
                    return true;
                default:
                    return true;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(0xFF141416);
            if (source != null && !source.isRecycled()) {
                canvas.drawBitmap(source, imageMatrix, bmpPaint);
            }
            canvas.drawPath(maskPath, maskPaint);
            float pillR = pillRect.height() * 0.5f;
            canvas.drawRoundRect(pillRect, pillR, pillR, borderPaint);

            if (showGuide && pillRect.height() > 0f) {
                float h = pillRect.height();
                float albumSize = h * (56f / 84f);
                float albumMargin = h * (14f / 84f);
                float albumCx = pillRect.left + albumMargin + albumSize * 0.5f;
                float albumCy = pillRect.centerY();
                canvas.drawCircle(albumCx, albumCy, albumSize * 0.5f, guideFillPaint);
                canvas.drawCircle(albumCx, albumCy, albumSize * 0.5f, guidePaint);

                float rightLeft = pillRect.left + albumMargin + albumSize + h * (14f / 84f);
                float rightRight = pillRect.right - h * (18f / 84f);
                if (rightRight > rightLeft + h * 0.4f) {
                    float titleH = h * 0.14f;
                    float titleTop = pillRect.top + h * 0.20f;
                    RectF titleBar = new RectF(rightLeft + h * 0.15f, titleTop, rightRight - h * 0.15f, titleTop + titleH);
                    canvas.drawRoundRect(titleBar, titleH * 0.5f, titleH * 0.5f, guideFillPaint);

                    float btnR = h * 0.11f;
                    float btnCy = pillRect.top + h * 0.60f;
                    float cx = (rightLeft + rightRight) * 0.5f;
                    float step = Math.min(h * 0.46f, (rightRight - rightLeft) * 0.30f);
                    canvas.drawCircle(cx - step, btnCy, btnR, guideFillPaint);
                    canvas.drawCircle(cx, btnCy, btnR * 1.15f, guideFillPaint);
                    canvas.drawCircle(cx + step, btnCy, btnR, guideFillPaint);
                }
            }
        }

        Bitmap renderFramedPillBitmap() {
            return CapsuleImageProcessor.renderCroppedBase(
                    source, imageMatrix,
                    pillRect.left, pillRect.top, pillRect.width(), pillRect.height());
        }
    }
}
