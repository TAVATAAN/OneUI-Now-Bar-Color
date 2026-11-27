package io.github.tavataan.nowbarcolor;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

public class NowBarConfigProvider extends ContentProvider {
    public static final String AUTHORITY = "io.github.tavataan.nowbarcolor.provider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/config");

    public static Uri buildImageUri(String slot) {
        String clean = CapsuleImageProcessor.sanitizeSlot(slot);
        return Uri.parse("content://" + AUTHORITY + "/image/" + (clean != null ? clean : "invalid"));
    }

    static File resolveSafeImageFile(Context ctx, Uri uri) throws FileNotFoundException {
        if (ctx == null || uri == null) {
            throw new FileNotFoundException("Missing context or uri");
        }
        List<String> segments = uri.getPathSegments();
        if (segments == null || segments.size() != 2 || !"image".equals(segments.get(0))) {
            throw new FileNotFoundException("Unsupported image uri: " + uri);
        }
        String slot = CapsuleImageProcessor.sanitizeSlot(segments.get(1));
        if (slot == null) {
            throw new FileNotFoundException("Invalid image slot: " + segments.get(1));
        }
        File dir = CapsuleImageProcessor.getImageDir(ctx);
        File target = CapsuleImageProcessor.getBakedImageFile(ctx, slot);
        if (target == null) {
            throw new FileNotFoundException("Image file unavailable");
        }
        try {
            String dirCanon = dir.getCanonicalPath() + File.separator;
            String targetCanon = target.getCanonicalPath();
            if (!targetCanon.startsWith(dirCanon)) {
                throw new FileNotFoundException("Path escapes capsule_images directory");
            }
        } catch (FileNotFoundException fnfe) {
            throw fnfe;
        } catch (Throwable t) {
            throw new FileNotFoundException("Failed canonical path check");
        }
        if (!target.isFile()) {
            throw new FileNotFoundException("Image not baked yet for slot: " + slot);
        }
        return target;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context ctx = getContext();
        if (ctx == null) return null;
        SharedPreferences prefs = ctx.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE);

        if ("get_config".equals(method)) {
            Bundle out = new Bundle();
            out.putString("config_json", prefs.getString(ConfigManager.SETTINGS_KEY, ""));
            return out;
        } else if ("get_hook_map".equals(method)) {
            HookMap cached = HookMap.loadFromApp(ctx);
            boolean needsScan = (cached == null);
            try {
                PackageInfo pi = ctx.getPackageManager().getPackageInfo(AodScanner.AOD_PKG, 0);
                if (cached == null || !cached.matchesTarget(pi.getLongVersionCode(), pi.lastUpdateTime)) {
                    needsScan = true;
                }
            } catch (Throwable ignored) {}

            if (needsScan) {
                AodScanner.ScanResult res = AodScanner.scanInstalledAod(ctx);
                if (res.map != null) {
                    HookMap.saveFromApp(ctx, res.map);
                    cached = res.map;
                }
            }
            Bundle out = new Bundle();
            String json = cached != null ? cached.toJson() : "";
            out.putString("hook_map_json", json != null ? json : "");
            return out;
        } else if ("update_diagnostics".equals(method) && extras != null) {
            SharedPreferences diagPrefs = ctx.getSharedPreferences(HookMap.PREF_NAME, Context.MODE_PRIVATE);
            diagPrefs.edit().putString("sysui_diag_dump", extras.getString("dump", "")).apply();
            return new Bundle();
        } else if ("get_diagnostics".equals(method)) {
            SharedPreferences diagPrefs = ctx.getSharedPreferences(HookMap.PREF_NAME, Context.MODE_PRIVATE);
            Bundle out = new Bundle();
            out.putString("dump", diagPrefs.getString("sysui_diag_dump", ""));
            return out;
        } else if ("update_wallpaper_palette".equals(method) && extras != null) {
            SharedPreferences.Editor ed = prefs.edit();
            if (extras.containsKey("primary")) ed.putInt("wall_primary", extras.getInt("primary"));
            if (extras.containsKey("secondary")) ed.putInt("wall_secondary", extras.getInt("secondary"));
            if (extras.containsKey("accent")) ed.putInt("wall_accent", extras.getInt("accent"));
            if (extras.containsKey("bottom")) ed.putInt("wall_bottom", extras.getInt("bottom"));
            ed.apply();
            return new Bundle();
        }
        return null;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode == null || !mode.startsWith("r") || mode.contains("w") || mode.contains("t")) {
            throw new SecurityException("Read-only mode required");
        }
        File target = resolveSafeImageFile(getContext(), uri);
        return ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Context ctx = getContext();
        if (ctx == null) return null;
        SharedPreferences prefs = ctx.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(ConfigManager.SETTINGS_KEY, "");
        MatrixCursor cursor = new MatrixCursor(new String[]{"config_json"});
        cursor.addRow(new Object[]{json});
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        if (uri != null && uri.getPath() != null && uri.getPath().startsWith("/image/")) {
            return "image/webp";
        }
        return "vnd.android.cursor.item/vnd.io.github.tavataan.nowbarcolor.config";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
