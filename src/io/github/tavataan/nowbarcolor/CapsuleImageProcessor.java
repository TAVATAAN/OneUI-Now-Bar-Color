package io.github.tavataan.nowbarcolor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

/**
 * Companion-side image cropping, artistic style baking, WebP persistence, and color analysis.
 * Keeps heavy decoding and filtering inside MainActivity so SystemUI/AOD only render pre-baked bitmaps.
 */
final class CapsuleImageProcessor {
    static final int BAKED_WIDTH = 800;
    static final int BAKED_HEIGHT = 200;
    private static final int MAX_SOURCE_SIDE = 1600;
    private static final String IMAGE_DIR = "capsule_images";

    static final class ImageStats {
        final int dominantColor;
        final int avgLuma;

        ImageStats(int dominantColor, int avgLuma) {
            this.dominantColor = dominantColor | 0xFF000000;
            this.avgLuma = Math.max(0, Math.min(255, avgLuma));
        }
    }

    static final class BakeResult {
        final Bitmap bitmap;
        final int dominantColor;
        final int avgLuma;
        final long revision;

        BakeResult(Bitmap bitmap, int dominantColor, int avgLuma, long revision) {
            this.bitmap = bitmap;
            this.dominantColor = dominantColor;
            this.avgLuma = avgLuma;
            this.revision = revision;
        }
    }

    private CapsuleImageProcessor() {}

    static String sanitizeSlot(String rawSlot) {
        if (rawSlot == null) return null;
        String clean = rawSlot.trim().toLowerCase(Locale.ROOT);
        if ("global".equals(clean)) return "global";
        if (clean.startsWith("cat_") && clean.length() > 4 && clean.length() <= 40) {
            for (int i = 4; i < clean.length(); i++) {
                char c = clean.charAt(i);
                boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
                if (!ok) return null;
            }
            return clean;
        }
        return null;
    }

    static String slotForGlobal() {
        return "global";
    }

    static String slotForCategory(String categoryId) {
        return sanitizeSlot("cat_" + categoryId);
    }

    static File getImageDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), IMAGE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    static File getBaseCropFile(Context ctx, String rawSlot) {
        String slot = sanitizeSlot(rawSlot);
        if (ctx == null || slot == null) return null;
        return new File(getImageDir(ctx), "crop_" + slot + ".webp");
    }

    static File getBakedImageFile(Context ctx, String rawSlot) {
        String slot = sanitizeSlot(rawSlot);
        if (ctx == null || slot == null) return null;
        return new File(getImageDir(ctx), "capsule_img_" + slot + ".webp");
    }

    static boolean hasCroppedSource(Context ctx, String slot) {
        File f = getBaseCropFile(ctx, slot);
        return f != null && f.isFile() && f.length() > 0;
    }

    static Bitmap loadCropBitmap(Context ctx, String slot) {
        File f = getBaseCropFile(ctx, slot);
        if (f == null || !f.isFile()) return null;
        return BitmapFactory.decodeFile(f.getAbsolutePath());
    }

    static Bitmap loadBakedBitmap(Context ctx, String slot) {
        File f = getBakedImageFile(ctx, slot);
        if (f == null || !f.isFile()) return null;
        return BitmapFactory.decodeFile(f.getAbsolutePath());
    }

    static Bitmap loadScaledThumbnail(Context ctx, String slot, int targetW, int targetH) {
        File f = getBakedImageFile(ctx, slot);
        if (f == null || !f.isFile()) return null;
        Bitmap raw = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (raw == null) return null;
        if (raw.getWidth() == targetW && raw.getHeight() == targetH) return raw;
        Bitmap scaled = Bitmap.createScaledBitmap(raw, Math.max(1, targetW), Math.max(1, targetH), true);
        if (scaled != raw) raw.recycle();
        return scaled;
    }

    static Bitmap decodeSampledFromUri(Context ctx, Uri uri) {
        if (ctx == null || uri == null) return null;
        int orientation = readExifOrientation(ctx, uri);

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, bounds);
        } catch (Throwable ignored) {
            return null;
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        int maxSide = Math.max(bounds.outWidth, bounds.outHeight);
        while ((maxSide / sample) > MAX_SOURCE_SIDE) {
            sample *= 2;
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decoded;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            decoded = BitmapFactory.decodeStream(in, null, opts);
        } catch (Throwable ignored) {
            return null;
        }
        if (decoded == null) return null;
        return applyExifOrientation(decoded, orientation);
    }

    private static int readExifOrientation(Context ctx, Uri uri) {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return ExifInterface.ORIENTATION_NORMAL;
            ExifInterface exif = new ExifInterface(in);
            return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Throwable ignored) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Bitmap applyExifOrientation(Bitmap src, int orientation) {
        Matrix m = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                m.setScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                m.setRotate(180f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                m.setScale(1f, -1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                m.setRotate(90f);
                m.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                m.setRotate(90f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                m.setRotate(-90f);
                m.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                m.setRotate(-90f);
                break;
            default:
                return src;
        }
        try {
            Bitmap rotated = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
            if (rotated != src) src.recycle();
            return rotated;
        } catch (Throwable ignored) {
            return src;
        }
    }

    /**
     * Renders the framed pill region (pillRect in crop-view coordinates, mapped through viewImageMatrix)
     * into an exact 800x200 ARGB_8888 cropped base bitmap.
     */
    static Bitmap renderCroppedBase(Bitmap source, Matrix displayMatrix, float pillLeft, float pillTop, float pillWidth, float pillHeight) {
        if (source == null || pillWidth <= 0f || pillHeight <= 0f) return null;
        Bitmap out = Bitmap.createBitmap(BAKED_WIDTH, BAKED_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.BLACK);
        Matrix m = new Matrix(displayMatrix);
        m.postTranslate(-pillLeft, -pillTop);
        m.postScale(BAKED_WIDTH / pillWidth, BAKED_HEIGHT / pillHeight);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source, m, paint);
        return out;
    }

    static BakeResult saveCropAndBake(
            Context ctx, String slot, Bitmap baseCrop,
            int style, int blurPercent, int duotonePreset,
            int wallPrimary, int wallAccent) {
        if (ctx == null || baseCrop == null) return null;
        File cropFile = getBaseCropFile(ctx, slot);
        File bakedFile = getBakedImageFile(ctx, slot);
        if (cropFile == null || bakedFile == null) return null;

        if (!writeWebp(baseCrop, cropFile, 92)) {
            return null;
        }
        return bakeAndWrite(bakedFile, baseCrop, style, blurPercent, duotonePreset, wallPrimary, wallAccent);
    }

    static BakeResult rebakeExistingCrop(
            Context ctx, String slot,
            int style, int blurPercent, int duotonePreset,
            int wallPrimary, int wallAccent) {
        if (ctx == null) return null;
        Bitmap baseCrop = loadCropBitmap(ctx, slot);
        if (baseCrop == null) return null;
        File bakedFile = getBakedImageFile(ctx, slot);
        if (bakedFile == null) {
            baseCrop.recycle();
            return null;
        }
        try {
            return bakeAndWrite(bakedFile, baseCrop, style, blurPercent, duotonePreset, wallPrimary, wallAccent);
        } finally {
            baseCrop.recycle();
        }
    }

    private static BakeResult bakeAndWrite(
            File bakedFile, Bitmap baseCrop,
            int style, int blurPercent, int duotonePreset,
            int wallPrimary, int wallAccent) {
        Bitmap styled = applyArtisticStyle(baseCrop, style, blurPercent, duotonePreset, wallPrimary, wallAccent);
        if (styled == null) return null;
        if (!writeWebp(styled, bakedFile, 88)) {
            styled.recycle();
            return null;
        }
        ImageStats stats = analyzeBitmap(styled);
        long rev = System.currentTimeMillis();
        return new BakeResult(styled, stats.dominantColor, stats.avgLuma, rev);
    }

    private static boolean writeWebp(Bitmap bmp, File outFile, int quality) {
        Bitmap.CompressFormat fmt = Build.VERSION.SDK_INT >= 30
                ? Bitmap.CompressFormat.WEBP_LOSSY
                : Bitmap.CompressFormat.WEBP;
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        File tmp = new File(outFile.getAbsolutePath() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            if (!bmp.compress(fmt, quality, fos)) return false;
            fos.flush();
        } catch (Throwable ignored) {
            tmp.delete();
            return false;
        }
        if (outFile.exists()) outFile.delete();
        return tmp.renameTo(outFile);
    }

    static int[] resolveDuotoneColors(int preset, int wallPrimary, int wallAccent) {
        switch (ConfigManager.clampDuotonePreset(preset)) {
            case ConfigManager.DUOTONE_CYBER:
                return new int[]{0xFF160B33, 0xFF00E5FF};
            case ConfigManager.DUOTONE_SUNSET:
                return new int[]{0xFF2C0A24, 0xFFFFA726};
            case ConfigManager.DUOTONE_OCEAN:
                return new int[]{0xFF061E34, 0xFF45F0DF};
            case ConfigManager.DUOTONE_CRIMSON:
                return new int[]{0xFF1F070E, 0xFFFF3864};
            case ConfigManager.DUOTONE_MONET:
            default: {
                int p = (wallPrimary != 0 ? wallPrimary : 0xFF1E88E5) | 0xFF000000;
                int a = (wallAccent != 0 ? wallAccent : p) | 0xFF000000;
                int shadow = darkenOrLighten(p, 0.28f);
                int highlight = darkenOrLighten(a, 1.35f);
                return new int[]{shadow, highlight};
            }
        }
    }

    private static int darkenOrLighten(int rgb, float mult) {
        int r = Math.max(0, Math.min(255, Math.round(((rgb >> 16) & 0xFF) * mult)));
        int g = Math.max(0, Math.min(255, Math.round(((rgb >> 8) & 0xFF) * mult)));
        int b = Math.max(0, Math.min(255, Math.round((rgb & 0xFF) * mult)));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static Bitmap applyArtisticStyle(
            Bitmap source, int style, int blurPercent, int duotonePreset,
            int wallPrimary, int wallAccent) {
        if (source == null) return null;
        int resolvedStyle = ConfigManager.clampImageStyle(style);
        switch (resolvedStyle) {
            case ConfigManager.IMG_STYLE_BLUR: {
                int clamped = ConfigManager.clampImageBlurPercent(blurPercent);
                int radius = Math.max(2, Math.round(4 + (clamped / 100.0f) * 20));
                return stackBlur(source, radius);
            }
            case ConfigManager.IMG_STYLE_DUOTONE: {
                int[] pair = resolveDuotoneColors(duotonePreset, wallPrimary, wallAccent);
                return applyDuotoneLut(source, pair[0], pair[1]);
            }
            case ConfigManager.IMG_STYLE_NOIR:
                return applyColorMatrixStyle(source, 0.0f, 1.34f, -22f);
            case ConfigManager.IMG_STYLE_PIXEL:
                return applyPixelArt(source);
            case ConfigManager.IMG_STYLE_VIGNETTE:
                return applyCinematicVignette(source);
            case ConfigManager.IMG_STYLE_ORIGINAL:
            default:
                return applyColorMatrixStyle(source, 1.06f, 1.06f, -4f);
        }
    }

    private static Bitmap applyColorMatrixStyle(Bitmap src, float saturation, float contrast, float translate) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        ColorMatrix sat = new ColorMatrix();
        sat.setSaturation(saturation);
        ColorMatrix con = new ColorMatrix(new float[]{
                contrast, 0, 0, 0, translate,
                0, contrast, 0, 0, translate,
                0, 0, contrast, 0, translate,
                0, 0, 0, 1, 0
        });
        sat.postConcat(con);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(sat));
        c.drawBitmap(src, 0, 0, p);
        return out;
    }

    private static Bitmap applyDuotoneLut(Bitmap src, int shadowRgb, int highlightRgb) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] pixels = new int[w * h];
        src.getPixels(pixels, 0, w, 0, 0, w, h);

        int[] lut = new int[256];
        for (int i = 0; i < 256; i++) {
            float t = i / 255.0f;
            // Mild S-curve for rich capsule separation
            float s = t * t * (3.0f - 2.0f * t);
            lut[i] = OneUiBlurSpec.blendRgb(shadowRgb, highlightRgb, s);
        }
        for (int idx = 0; idx < pixels.length; idx++) {
            int px = pixels[idx];
            int luma = (((px >> 16) & 0xFF) * 77 + ((px >> 8) & 0xFF) * 150 + (px & 0xFF) * 29) >> 8;
            if (luma < 0) luma = 0;
            else if (luma > 255) luma = 255;
            pixels[idx] = (px & 0xFF000000) | (lut[luma] & 0x00FFFFFF);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(pixels, 0, w, 0, 0, w, h);
        return out;
    }

    private static Bitmap applyPixelArt(Bitmap src) {
        int outW = src.getWidth();
        int outH = src.getHeight();
        int gridW = Math.max(16, Math.min(48, Math.round(outW * (48.0f / BAKED_WIDTH))));
        int gridH = Math.max(4, Math.round(gridW * (outH / (float) Math.max(1, outW))));
        Bitmap tiny = Bitmap.createScaledBitmap(src, gridW, gridH, true);
        Bitmap vividTiny = applyColorMatrixStyle(tiny, 1.15f, 1.10f, -6f);
        if (vividTiny != tiny) tiny.recycle();
        Bitmap crisp = Bitmap.createScaledBitmap(vividTiny, outW, outH, false);
        vividTiny.recycle();
        return crisp;
    }

    private static Bitmap applyCinematicVignette(Bitmap src) {
        Bitmap out = applyColorMatrixStyle(src, 1.08f, 1.12f, -8f);
        int w = out.getWidth();
        int h = out.getHeight();
        Canvas canvas = new Canvas(out);
        Paint vigPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float r = w * 0.58f;
        RadialGradient grad = new RadialGradient(
                cx, cy, Math.max(1f, r),
                new int[]{0x00000000, 0x26000000, 0xB8000000},
                new float[]{0.35f, 0.72f, 1.0f},
                Shader.TileMode.CLAMP);
        Matrix scaleY = new Matrix();
        scaleY.setScale(1.0f, (h * 1.15f) / Math.max(1.0f, w), cx, cy);
        grad.setLocalMatrix(scaleY);
        vigPaint.setShader(grad);
        canvas.drawRect(0, 0, w, h, vigPaint);
        return out;
    }

    /** Fast pure-Java StackBlur (O(w*h), radius-independent performance). */
    static Bitmap stackBlur(Bitmap src, int radius) {
        if (src == null) return null;
        int r = Math.max(1, Math.min(40, radius));
        Bitmap bitmap = src.copy(Bitmap.Config.ARGB_8888, true);
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pix = new int[w * h];
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);

        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = r + r + 1;

        int[] rArr = new int[wh];
        int[] gArr = new int[wh];
        int[] bArr = new int[wh];
        int[] vmin = new int[Math.max(w, h)];

        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int[] dv = new int[256 * divsum];
        for (int i = 0; i < 256 * divsum; i++) dv[i] = i / divsum;

        int[][] stack = new int[div][3];
        int r1 = r + 1;
        int yw = 0, yi = 0;

        for (int y = 0; y < h; y++) {
            int rsum = 0, gsum = 0, bsum = 0;
            int routsum = 0, goutsum = 0, boutsum = 0;
            int rinsum = 0, ginsum = 0, binsum = 0;
            for (int i = -r; i <= r; i++) {
                int p = pix[yi + Math.min(wm, Math.max(i, 0))];
                int[] sir = stack[i + r];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = p & 0x0000ff;
                int rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; }
                else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; }
            }
            int stackpointer = r;
            for (int x = 0; x < w; x++) {
                rArr[yi] = dv[rsum];
                gArr[yi] = dv[gsum];
                bArr[yi] = dv[bsum];
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum;
                int stackstart = stackpointer - r + div;
                int[] sir = stack[stackstart % div];
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2];
                if (y == 0) vmin[x] = Math.min(x + r + 1, wm);
                int p = pix[yw + vmin[x]];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = p & 0x0000ff;
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2];
                rsum += rinsum; gsum += ginsum; bsum += binsum;
                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer % div];
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2];
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2];
                yi++;
            }
            yw += w;
        }

        for (int x = 0; x < w; x++) {
            int rsum = 0, gsum = 0, bsum = 0;
            int routsum = 0, goutsum = 0, boutsum = 0;
            int rinsum = 0, ginsum = 0, binsum = 0;
            int yp = -r * w;
            for (int i = -r; i <= r; i++) {
                yi = Math.max(0, yp) + x;
                int[] sir = stack[i + r];
                sir[0] = rArr[yi]; sir[1] = gArr[yi]; sir[2] = bArr[yi];
                int rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs; gsum += sir[1] * rbs; bsum += sir[2] * rbs;
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; }
                else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; }
                if (i < hm) yp += w;
            }
            yi = x;
            int stackpointer = r;
            for (int y = 0; y < h; y++) {
                pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum;
                int stackstart = stackpointer - r + div;
                int[] sir = stack[stackstart % div];
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2];
                if (x == 0) vmin[y] = Math.min(y + r1, hm) * w;
                int p = x + vmin[y];
                sir[0] = rArr[p]; sir[1] = gArr[p]; sir[2] = bArr[p];
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2];
                rsum += rinsum; gsum += ginsum; bsum += binsum;
                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2];
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2];
                yi += w;
            }
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h);
        return bitmap;
    }

    static ImageStats analyzeBitmap(Bitmap bmp) {
        if (bmp == null || bmp.getWidth() <= 0 || bmp.getHeight() <= 0) {
            return new ImageStats(0xFF1E88E5, 128);
        }
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int stepX = Math.max(1, w / 48);
        int stepY = Math.max(1, h / 16);

        double sumR = 0, sumG = 0, sumB = 0, weightSum = 0;
        double meanR = 0, meanG = 0, meanB = 0, lumaSum = 0;
        int count = 0;
        float[] hsv = new float[3];

        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int c = bmp.getPixel(x, y);
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                meanR += r;
                meanG += g;
                meanB += b;
                lumaSum += 0.2126 * r + 0.7152 * g + 0.0722 * b;
                count++;

                Color.RGBToHSV(r, g, b, hsv);
                float weight = (0.12f + hsv[1] * hsv[1] * 2.4f) * (hsv[2] < 0.08f ? 0.25f : 1.0f);
                sumR += r * weight;
                sumG += g * weight;
                sumB += b * weight;
                weightSum += weight;
            }
        }
        if (count == 0) return new ImageStats(0xFF1E88E5, 128);
        int domR = Math.round((float) (weightSum > 0.01 ? sumR / weightSum : meanR / count));
        int domG = Math.round((float) (weightSum > 0.01 ? sumG / weightSum : meanG / count));
        int domB = Math.round((float) (weightSum > 0.01 ? sumB / weightSum : meanB / count));
        int domColor = 0xFF000000 | (Math.max(0, Math.min(255, domR)) << 16)
                | (Math.max(0, Math.min(255, domG)) << 8)
                | Math.max(0, Math.min(255, domB));
        int avgLuma = Math.round((float) (lumaSum / count));
        return new ImageStats(domColor, avgLuma);
    }

    static Bitmap renderStyleThumbnail(
            Bitmap cropOrSample, int style, int blurPct, int duotonePreset,
            int wallPrimary, int wallAccent, int w, int h) {
        Bitmap src = cropOrSample;
        boolean ownsSrc = false;
        if (src == null) {
            src = createSamplePlaceholder(w, h, wallPrimary, wallAccent);
            ownsSrc = true;
        }
        try {
            Bitmap mini = (src.getWidth() == w && src.getHeight() == h)
                    ? src
                    : Bitmap.createScaledBitmap(src, Math.max(1, w), Math.max(1, h), true);
            try {
                int thumbBlur = Math.max(1, Math.round(blurPct * 0.25f));
                return applyArtisticStyle(mini, style, thumbBlur, duotonePreset, wallPrimary, wallAccent);
            } finally {
                if (mini != src) mini.recycle();
            }
        } finally {
            if (ownsSrc) src.recycle();
        }
    }

    private static Bitmap createSamplePlaceholder(int w, int h, int wallPrimary, int wallAccent) {
        Bitmap bmp = Bitmap.createBitmap(Math.max(2, w), Math.max(2, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        int c1 = (wallPrimary != 0 ? wallPrimary : 0xFF1E88E5) | 0xFF000000;
        int c2 = (wallAccent != 0 ? wallAccent : 0xFF9D4EDD) | 0xFF000000;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.LinearGradient(
                0, 0, w, h, c1, c2, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setColor(0x55FFFFFF);
        c.drawCircle(w * 0.28f, h * 0.42f, h * 0.48f, p);
        p.setColor(0x44000000);
        c.drawCircle(w * 0.72f, h * 0.72f, h * 0.56f, p);
        return bmp;
    }
}
