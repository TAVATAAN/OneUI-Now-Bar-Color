package io.github.tavataan.nowbarcolor;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;

public class NowBarConfigProvider extends ContentProvider {
    public static final String AUTHORITY = "io.github.tavataan.nowbarcolor.provider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/config");

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
