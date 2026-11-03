package io.github.tavataan.nowbarcolor;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WallpaperColorHelper {
    private static final String TAG = "NowBarColorWall";
    public static final String SETTINGS_WALL_KEY = "nowbar_lock_wallpaper_palette";

    public static class PaletteResult {
        public int primaryColor = 0xFF1E88E5;
        public int secondaryColor = 0xFF3949AB;
        public int monetAccentColor = 0xFF00ACC1;
        public int bottomAreaColor = 0xFF1565C0;
    }

    public static PaletteResult extractLockWallpaperPalette(Context context, boolean isAppProcess) {
        PaletteResult result = new PaletteResult();
        if (context == null) return result;

        // 1. Monet Accent from system resources
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                int accent1 = context.getColor(android.R.color.system_accent1_500);
                if (accent1 != 0) {
                    result.monetAccentColor = accent1 | 0xFF000000;
                }
                int accent2 = context.getColor(android.R.color.system_accent2_600);
                if (accent2 != 0) {
                    result.secondaryColor = accent2 | 0xFF000000;
                }
            }
        } catch (Throwable ignored) {}

        boolean gotLockColors = false;

        // 2. Try standard WallpaperManager.getWallpaperColors(FLAG_LOCK)
        try {
            WallpaperManager wm = WallpaperManager.getInstance(context);
            if (wm != null) {
                WallpaperColors lockColors = wm.getWallpaperColors(WallpaperManager.FLAG_LOCK);
                if (lockColors == null) {
                    lockColors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                }
                if (lockColors != null) {
                    if (lockColors.getPrimaryColor() != null) {
                        result.primaryColor = lockColors.getPrimaryColor().toArgb() | 0xFF000000;
                        gotLockColors = true;
                    }
                    if (lockColors.getSecondaryColor() != null) {
                        result.secondaryColor = lockColors.getSecondaryColor().toArgb() | 0xFF000000;
                    } else if (lockColors.getTertiaryColor() != null) {
                        result.secondaryColor = lockColors.getTertiaryColor().toArgb() | 0xFF000000;
                    } else {
                        result.secondaryColor = shiftHueAndBrightness(result.primaryColor, 18f, 1.15f);
                    }
                    result.bottomAreaColor = shiftHueAndBrightness(result.primaryColor, 0f, 0.85f);
                }

                // 3. Try Samsung SemWallpaperColors via reflection (Which = 6 for Lock Phone, or 2 for Lock)
                try {
                    Method semGetMethod = wm.getClass().getMethod("semGetWallpaperColors", int.class);
                    Object semColors = semGetMethod.invoke(wm, 6);
                    if (semColors == null) {
                        semColors = semGetMethod.invoke(wm, WallpaperManager.FLAG_LOCK);
                    }
                    if (semColors != null) {
                        try {
                            Method getSeedColors = semColors.getClass().getMethod("getSeedColors");
                            int[] seeds = (int[]) getSeedColors.invoke(semColors);
                            if (seeds != null && seeds.length > 0) {
                                result.primaryColor = seeds[0] | 0xFF000000;
                                gotLockColors = true;
                                if (seeds.length > 1) {
                                    result.secondaryColor = seeds[1] | 0xFF000000;
                                }
                            }
                        } catch (Throwable ignored) {}

                        // Also check SemWallpaperColors.toXml() or get(long) if available
                        try {
                            Method getXml = semColors.getClass().getMethod("getXml");
                            Object xmlObj = getXml.invoke(semColors);
                            if (xmlObj instanceof String) {
                                parseSamsungLockPhoneXml((String) xmlObj, result);
                                gotLockColors = true;
                            }
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        // 4. Direct file read if permitted
        try {
            File f = new File("/data/system/users/0/wallpaper_colors_info/lock_phone.xml");
            if (f.exists() && f.canRead()) {
                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                br.close();
                parseSamsungLockPhoneXml(sb.toString(), result);
                gotLockColors = true;
            }
        } catch (Throwable ignored) {}

        // 5. In App process, read palette synced by SystemUI via Settings.System or SharedPreferences
        if (isAppProcess) {
            try {
                String wallJson = Settings.System.getString(context.getContentResolver(), SETTINGS_WALL_KEY);
                if (wallJson != null && !wallJson.isEmpty()) {
                    JSONObject obj = new JSONObject(wallJson);
                    result.primaryColor = obj.optInt("primary", result.primaryColor) | 0xFF000000;
                    result.secondaryColor = obj.optInt("secondary", result.secondaryColor) | 0xFF000000;
                    result.monetAccentColor = obj.optInt("accent", result.monetAccentColor) | 0xFF000000;
                    result.bottomAreaColor = obj.optInt("bottom", result.bottomAreaColor) | 0xFF000000;
                    gotLockColors = true;
                }
            } catch (Throwable ignored) {}

            try {
                SharedPreferences prefs = context.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE);
                if (prefs.contains("wall_primary")) {
                    result.primaryColor = prefs.getInt("wall_primary", result.primaryColor) | 0xFF000000;
                    result.secondaryColor = prefs.getInt("wall_secondary", result.secondaryColor) | 0xFF000000;
                    result.monetAccentColor = prefs.getInt("wall_accent", result.monetAccentColor) | 0xFF000000;
                    result.bottomAreaColor = prefs.getInt("wall_bottom", result.bottomAreaColor) | 0xFF000000;
                    gotLockColors = true;
                }
            } catch (Throwable ignored) {}
        } else {
            // In SystemUI process: publish extracted palette to Settings.System & NowBarConfigProvider
            publishPaletteFromSystemUi(context, result);
        }

        if (!gotLockColors) {
            result.primaryColor = result.monetAccentColor;
            result.bottomAreaColor = shiftHueAndBrightness(result.primaryColor, 0f, 0.80f);
        }

        return result;
    }

    public static void publishPaletteFromSystemUi(Context sysUiContext, PaletteResult pal) {
        if (sysUiContext == null || pal == null) return;
        try {
            JSONObject obj = new JSONObject();
            obj.put("primary", pal.primaryColor);
            obj.put("secondary", pal.secondaryColor);
            obj.put("accent", pal.monetAccentColor);
            obj.put("bottom", pal.bottomAreaColor);
            Settings.System.putString(sysUiContext.getContentResolver(), SETTINGS_WALL_KEY, obj.toString());
        } catch (Throwable ignored) {}

        try {
            Bundle b = new Bundle();
            b.putInt("primary", pal.primaryColor);
            b.putInt("secondary", pal.secondaryColor);
            b.putInt("accent", pal.monetAccentColor);
            b.putInt("bottom", pal.bottomAreaColor);
            sysUiContext.getContentResolver().call(
                    NowBarConfigProvider.CONTENT_URI, "update_wallpaper_palette", null, b);
        } catch (Throwable ignored) {}
    }

    public static int resolveForVariant(PaletteResult palette, int variant) {
        if (palette == null) return 0xFF1E88E5;
        switch (variant) {
            case ConfigManager.WALLPAPER_SECONDARY:
                return palette.secondaryColor | 0xFF000000;
            case ConfigManager.WALLPAPER_MONET_ACCENT:
                return palette.monetAccentColor | 0xFF000000;
            case ConfigManager.WALLPAPER_BOTTOM_AREA:
                return palette.bottomAreaColor | 0xFF000000;
            case ConfigManager.WALLPAPER_PRIMARY:
            default:
                return palette.primaryColor | 0xFF000000;
        }
    }

    public static void parseSamsungLockPhoneXml(String xml, PaletteResult out) {
        if (xml == null || xml.isEmpty()) return;
        Matcher seedMatcher = Pattern.compile("<SeedColors>\\[([^\\]]+)\\]</SeedColors>").matcher(xml);
        if (seedMatcher.find()) {
            String[] parts = seedMatcher.group(1).split(",");
            List<Integer> seeds = new ArrayList<>();
            for (String p : parts) {
                try {
                    seeds.add(Integer.parseInt(p.trim()) | 0xFF000000);
                } catch (NumberFormatException ignored) {}
            }
            if (!seeds.isEmpty()) {
                out.primaryColor = seeds.get(0);
                if (seeds.size() > 1) {
                    out.secondaryColor = seeds.get(1);
                }
            }
        }

        Matcher hsvMatcher = Pattern.compile("<avgHSV>(-?\\d+)</avgHSV>").matcher(xml);
        List<Integer> avgColors = new ArrayList<>();
        while (hsvMatcher.find()) {
            try {
                avgColors.add(Integer.parseInt(hsvMatcher.group(1).trim()) | 0xFF000000);
            } catch (NumberFormatException ignored) {}
        }

        if (avgColors.size() >= 5) {
            out.bottomAreaColor = avgColors.get(4);
            if (out.secondaryColor == out.primaryColor && avgColors.size() >= 2) {
                out.secondaryColor = avgColors.get(1);
            }
        } else if (!avgColors.isEmpty()) {
            out.bottomAreaColor = avgColors.get(avgColors.size() - 1);
        }
    }

    public static int shiftHueAndBrightness(int color, float hueDelta, float valueFactor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color | 0xFF000000, hsv);
        hsv[0] = (hsv[0] + hueDelta + 360f) % 360f;
        hsv[2] = Math.max(0.12f, Math.min(0.95f, hsv[2] * valueFactor));
        return Color.HSVToColor(hsv);
    }
}
