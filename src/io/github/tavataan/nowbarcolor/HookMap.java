package io.github.tavataan.nowbarcolor;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import de.robv.android.xposed.XSharedPreferences;
import org.json.JSONObject;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Versioned cache mapping obfuscated AOD_Service symbols resolved dynamically
 * by AodScanner, plus per-slot resolution diagnostics (OK / MISSING / AMBIGUOUS).
 * Pure Java with zero JNI/DexKit dependencies so it is safe to load inside
 * SystemUI, AODService, and host unit tests.
 */
public final class HookMap {
    public static final String PREF_NAME = "nowbar_hook_cache";
    public static final String KEY_JSON = "aod_hook_map_json";
    public static final int SCHEMA_VERSION = 8;
    public static final String STATUS_NA_PREFIX = "N/A";

    public final int schemaVersion;
    public final long aodVersionCode;
    public final String aodVersionName;
    public final long aodLastUpdateTime;

    // Core layer (required for obfuscated card hooks; Layer-1 fallback works without them)
    public final String baseCardClass;
    public final String baseBindMethod;
    public final String baseBlurMethod;
    public final String baseColorMethod;
    public final String stackViewClass;
    public final String stackRefreshMethod;

    // Wake / transition lifecycle symbols on baseCardClass
    public final String baseShapeMethod;
    public final String baseProgressBgMethod;
    public final String baseStateMethod;
    public final String baseExpandMethod;
    public final String baseSetExpandStateMethod;
    public final String baseDarkModeMethod;
    public final String baseClearContainerBgMethod;
    public final String baseAodDrawableMethod;
    public final String baseLockscreenModeField;
    public final String baseDozingField;
    public final String baseExpandField;

    // Enhancements layer (independent failure; Core survives if any of these is null)
    public final String mediaCardClass;
    public final String mediaForegroundMethod;
    public final String mediaBlackAodMethod;
    public final String briefCardClass;
    public final String briefBgColorMethod;
    public final String briefResetBgMethod;
    public final String gradViewClass;
    public final String gradHideMethod;
    public final String settingsHelperClass;
    public final String showNotiLockscreenMethod;

    // Per-slot resolution status: "OK", "MISSING(0)", "AMBIGUOUS(N: ...)", "SKIPPED(...)"
    public final Map<String, String> slotStatuses;

    public HookMap(
            int schemaVersion,
            long aodVersionCode,
            String aodVersionName,
            long aodLastUpdateTime,
            String baseCardClass,
            String baseBindMethod,
            String baseBlurMethod,
            String baseColorMethod,
            String stackViewClass,
            String stackRefreshMethod,
            String mediaCardClass,
            String mediaForegroundMethod,
            String briefCardClass,
            String briefBgColorMethod,
            String briefResetBgMethod,
            String gradViewClass,
            String gradHideMethod,
            String settingsHelperClass,
            String showNotiLockscreenMethod) {
        this(schemaVersion, aodVersionCode, aodVersionName, aodLastUpdateTime,
                baseCardClass, baseBindMethod, baseBlurMethod, baseColorMethod,
                stackViewClass, stackRefreshMethod,
                null, null, null, null, null, null, null, null, null, null,
                mediaCardClass, mediaForegroundMethod, null,
                briefCardClass, briefBgColorMethod, briefResetBgMethod,
                gradViewClass, gradHideMethod,
                settingsHelperClass, showNotiLockscreenMethod, null);
    }

    public HookMap(
            int schemaVersion,
            long aodVersionCode,
            String aodVersionName,
            long aodLastUpdateTime,
            String baseCardClass,
            String baseBindMethod,
            String baseBlurMethod,
            String baseColorMethod,
            String stackViewClass,
            String stackRefreshMethod,
            String mediaCardClass,
            String mediaForegroundMethod,
            String briefCardClass,
            String briefBgColorMethod,
            String briefResetBgMethod,
            String gradViewClass,
            String gradHideMethod,
            String settingsHelperClass,
            String showNotiLockscreenMethod,
            Map<String, String> slotStatuses) {
        this(schemaVersion, aodVersionCode, aodVersionName, aodLastUpdateTime,
                baseCardClass, baseBindMethod, baseBlurMethod, baseColorMethod,
                stackViewClass, stackRefreshMethod,
                null, null, null, null, null, null, null, null, null, null,
                mediaCardClass, mediaForegroundMethod, null,
                briefCardClass, briefBgColorMethod, briefResetBgMethod,
                gradViewClass, gradHideMethod,
                settingsHelperClass, showNotiLockscreenMethod, slotStatuses);
    }

    public HookMap(
            int schemaVersion,
            long aodVersionCode,
            String aodVersionName,
            long aodLastUpdateTime,
            String baseCardClass,
            String baseBindMethod,
            String baseBlurMethod,
            String baseColorMethod,
            String stackViewClass,
            String stackRefreshMethod,
            String baseShapeMethod,
            String baseProgressBgMethod,
            String baseStateMethod,
            String baseExpandMethod,
            String baseDarkModeMethod,
            String baseClearContainerBgMethod,
            String mediaCardClass,
            String mediaForegroundMethod,
            String briefCardClass,
            String briefBgColorMethod,
            String briefResetBgMethod,
            String gradViewClass,
            String gradHideMethod,
            String settingsHelperClass,
            String showNotiLockscreenMethod,
            Map<String, String> slotStatuses) {
        this(schemaVersion, aodVersionCode, aodVersionName, aodLastUpdateTime,
                baseCardClass, baseBindMethod, baseBlurMethod, baseColorMethod,
                stackViewClass, stackRefreshMethod,
                baseShapeMethod, baseProgressBgMethod, baseStateMethod,
                baseExpandMethod, null, baseDarkModeMethod, baseClearContainerBgMethod, null, null, null,
                mediaCardClass, mediaForegroundMethod, null,
                briefCardClass, briefBgColorMethod, briefResetBgMethod,
                gradViewClass, gradHideMethod,
                settingsHelperClass, showNotiLockscreenMethod, slotStatuses);
    }

    public HookMap(
            int schemaVersion,
            long aodVersionCode,
            String aodVersionName,
            long aodLastUpdateTime,
            String baseCardClass,
            String baseBindMethod,
            String baseBlurMethod,
            String baseColorMethod,
            String stackViewClass,
            String stackRefreshMethod,
            String baseShapeMethod,
            String baseProgressBgMethod,
            String baseStateMethod,
            String baseExpandMethod,
            String baseDarkModeMethod,
            String baseClearContainerBgMethod,
            String baseAodDrawableMethod,
            String mediaCardClass,
            String mediaForegroundMethod,
            String mediaBlackAodMethod,
            String briefCardClass,
            String briefBgColorMethod,
            String briefResetBgMethod,
            String gradViewClass,
            String gradHideMethod,
            String settingsHelperClass,
            String showNotiLockscreenMethod,
            Map<String, String> slotStatuses) {
        this(schemaVersion, aodVersionCode, aodVersionName, aodLastUpdateTime,
                baseCardClass, baseBindMethod, baseBlurMethod, baseColorMethod,
                stackViewClass, stackRefreshMethod,
                baseShapeMethod, baseProgressBgMethod, baseStateMethod,
                baseExpandMethod, null, baseDarkModeMethod, baseClearContainerBgMethod,
                baseAodDrawableMethod, null, null,
                mediaCardClass, mediaForegroundMethod, mediaBlackAodMethod,
                briefCardClass, briefBgColorMethod, briefResetBgMethod,
                gradViewClass, gradHideMethod,
                settingsHelperClass, showNotiLockscreenMethod, slotStatuses);
    }

    public HookMap(
            int schemaVersion,
            long aodVersionCode,
            String aodVersionName,
            long aodLastUpdateTime,
            String baseCardClass,
            String baseBindMethod,
            String baseBlurMethod,
            String baseColorMethod,
            String stackViewClass,
            String stackRefreshMethod,
            String baseShapeMethod,
            String baseProgressBgMethod,
            String baseStateMethod,
            String baseExpandMethod,
            String baseSetExpandStateMethod,
            String baseDarkModeMethod,
            String baseClearContainerBgMethod,
            String baseAodDrawableMethod,
            String mediaCardClass,
            String mediaForegroundMethod,
            String mediaBlackAodMethod,
            String briefCardClass,
            String briefBgColorMethod,
            String briefResetBgMethod,
            String gradViewClass,
            String gradHideMethod,
            String settingsHelperClass,
            String showNotiLockscreenMethod,
            Map<String, String> slotStatuses) {
        this(schemaVersion, aodVersionCode, aodVersionName, aodLastUpdateTime,
                baseCardClass, baseBindMethod, baseBlurMethod, baseColorMethod,
                stackViewClass, stackRefreshMethod,
                baseShapeMethod, baseProgressBgMethod, baseStateMethod,
                baseExpandMethod, baseSetExpandStateMethod, baseDarkModeMethod, baseClearContainerBgMethod,
                baseAodDrawableMethod, null, null, null,
                mediaCardClass, mediaForegroundMethod, mediaBlackAodMethod,
                briefCardClass, briefBgColorMethod, briefResetBgMethod,
                gradViewClass, gradHideMethod,
                settingsHelperClass, showNotiLockscreenMethod, slotStatuses);
    }

    public HookMap(
            int schemaVersion,
            long aodVersionCode,
            String aodVersionName,
            long aodLastUpdateTime,
            String baseCardClass,
            String baseBindMethod,
            String baseBlurMethod,
            String baseColorMethod,
            String stackViewClass,
            String stackRefreshMethod,
            String baseShapeMethod,
            String baseProgressBgMethod,
            String baseStateMethod,
            String baseExpandMethod,
            String baseSetExpandStateMethod,
            String baseDarkModeMethod,
            String baseClearContainerBgMethod,
            String baseAodDrawableMethod,
            String baseLockscreenModeField,
            String baseDozingField,
            String mediaCardClass,
            String mediaForegroundMethod,
            String mediaBlackAodMethod,
            String briefCardClass,
            String briefBgColorMethod,
            String briefResetBgMethod,
            String gradViewClass,
            String gradHideMethod,
            String settingsHelperClass,
            String showNotiLockscreenMethod,
            Map<String, String> slotStatuses) {
        this(schemaVersion, aodVersionCode, aodVersionName, aodLastUpdateTime,
                baseCardClass, baseBindMethod, baseBlurMethod, baseColorMethod,
                stackViewClass, stackRefreshMethod,
                baseShapeMethod, baseProgressBgMethod, baseStateMethod,
                baseExpandMethod, baseSetExpandStateMethod, baseDarkModeMethod, baseClearContainerBgMethod,
                baseAodDrawableMethod, baseLockscreenModeField, baseDozingField, null,
                mediaCardClass, mediaForegroundMethod, mediaBlackAodMethod,
                briefCardClass, briefBgColorMethod, briefResetBgMethod,
                gradViewClass, gradHideMethod,
                settingsHelperClass, showNotiLockscreenMethod, slotStatuses);
    }

    public HookMap(
            int schemaVersion,
            long aodVersionCode,
            String aodVersionName,
            long aodLastUpdateTime,
            String baseCardClass,
            String baseBindMethod,
            String baseBlurMethod,
            String baseColorMethod,
            String stackViewClass,
            String stackRefreshMethod,
            String baseShapeMethod,
            String baseProgressBgMethod,
            String baseStateMethod,
            String baseExpandMethod,
            String baseSetExpandStateMethod,
            String baseDarkModeMethod,
            String baseClearContainerBgMethod,
            String baseAodDrawableMethod,
            String baseLockscreenModeField,
            String baseDozingField,
            String baseExpandField,
            String mediaCardClass,
            String mediaForegroundMethod,
            String mediaBlackAodMethod,
            String briefCardClass,
            String briefBgColorMethod,
            String briefResetBgMethod,
            String gradViewClass,
            String gradHideMethod,
            String settingsHelperClass,
            String showNotiLockscreenMethod,
            Map<String, String> slotStatuses) {
        this.schemaVersion = schemaVersion;
        this.aodVersionCode = aodVersionCode;
        this.aodVersionName = aodVersionName != null ? aodVersionName : "";
        this.aodLastUpdateTime = aodLastUpdateTime;
        this.baseCardClass = emptyToNull(baseCardClass);
        this.baseBindMethod = emptyToNull(baseBindMethod);
        this.baseBlurMethod = emptyToNull(baseBlurMethod);
        this.baseColorMethod = emptyToNull(baseColorMethod);
        this.stackViewClass = emptyToNull(stackViewClass);
        this.stackRefreshMethod = emptyToNull(stackRefreshMethod);
        this.baseShapeMethod = emptyToNull(baseShapeMethod);
        this.baseProgressBgMethod = emptyToNull(baseProgressBgMethod);
        this.baseStateMethod = emptyToNull(baseStateMethod);
        this.baseExpandMethod = emptyToNull(baseExpandMethod);
        this.baseSetExpandStateMethod = emptyToNull(baseSetExpandStateMethod);
        this.baseDarkModeMethod = emptyToNull(baseDarkModeMethod);
        this.baseClearContainerBgMethod = emptyToNull(baseClearContainerBgMethod);
        this.baseAodDrawableMethod = emptyToNull(baseAodDrawableMethod);
        this.baseLockscreenModeField = emptyToNull(baseLockscreenModeField);
        this.baseDozingField = emptyToNull(baseDozingField);
        this.baseExpandField = emptyToNull(baseExpandField);
        this.mediaCardClass = emptyToNull(mediaCardClass);
        this.mediaForegroundMethod = emptyToNull(mediaForegroundMethod);
        this.mediaBlackAodMethod = emptyToNull(mediaBlackAodMethod);
        this.briefCardClass = emptyToNull(briefCardClass);
        this.briefBgColorMethod = emptyToNull(briefBgColorMethod);
        this.briefResetBgMethod = emptyToNull(briefResetBgMethod);
        this.gradViewClass = emptyToNull(gradViewClass);
        this.gradHideMethod = emptyToNull(gradHideMethod);
        this.settingsHelperClass = emptyToNull(settingsHelperClass);
        this.showNotiLockscreenMethod = emptyToNull(showNotiLockscreenMethod);
        if (slotStatuses != null && !slotStatuses.isEmpty()) {
            this.slotStatuses = Collections.unmodifiableMap(new LinkedHashMap<>(slotStatuses));
        } else {
            this.slotStatuses = Collections.emptyMap();
        }
    }

    public boolean matchesTarget(long installedVersionCode, long installedLastUpdate) {
        return schemaVersion == SCHEMA_VERSION
                && aodVersionCode == installedVersionCode
                && aodLastUpdateTime == installedLastUpdate;
    }

    public boolean hasCoreHooks() {
        return baseCardClass != null
                && baseBindMethod != null
                && baseBlurMethod != null
                && baseColorMethod != null;
    }

    public boolean hasAnyCoreHooks() {
        return baseCardClass != null
                && (baseBindMethod != null || baseBlurMethod != null || baseColorMethod != null);
    }

    public boolean isSlotNotApplicable(String slotKey) {
        if (slotKey == null) return false;
        String st = slotStatuses.get(slotKey);
        return st != null && st.startsWith(STATUS_NA_PREFIX);
    }

    public int countResolvedSlots() {
        String[] slots = allSlotValues();
        int count = 0;
        for (String s : slots) {
            if (s != null) count++;
        }
        return count;
    }

    public int totalSlotCount() {
        String[] keys = ALL_SLOT_KEYS;
        String[] vals = allSlotValues();
        int total = 0;
        for (int i = 0; i < keys.length; i++) {
            if (vals[i] != null || !isSlotNotApplicable(keys[i])) {
                total++;
            }
        }
        return total;
    }

    public String getSlotStatus(String slotKey, String resolvedValue) {
        String st = slotStatuses.get(slotKey);
        if (st != null && !st.isEmpty()) return st;
        return resolvedValue != null ? "OK" : "MISSING(0)";
    }

    private static final String[] ALL_SLOT_KEYS = {
            "baseCardClass", "baseBindMethod", "baseBlurMethod", "baseColorMethod",
            "stackViewClass", "stackRefreshMethod",
            "baseShapeMethod", "baseProgressBgMethod", "baseStateMethod",
            "baseExpandMethod", "baseSetExpandStateMethod", "baseDarkModeMethod",
            "baseClearContainerBgMethod", "baseAodDrawableMethod",
            "baseLockscreenModeField", "baseDozingField", "baseExpandField",
            "mediaCardClass", "mediaForegroundMethod", "mediaBlackAodMethod",
            "briefCardClass", "briefBgColorMethod", "briefResetBgMethod",
            "gradViewClass", "gradHideMethod",
            "settingsHelperClass", "showNotiLockscreenMethod"
    };

    private String[] allSlotValues() {
        return new String[]{
                baseCardClass, baseBindMethod, baseBlurMethod, baseColorMethod,
                stackViewClass, stackRefreshMethod,
                baseShapeMethod, baseProgressBgMethod, baseStateMethod,
                baseExpandMethod, baseSetExpandStateMethod, baseDarkModeMethod, baseClearContainerBgMethod,
                baseAodDrawableMethod, baseLockscreenModeField, baseDozingField, baseExpandField,
                mediaCardClass, mediaForegroundMethod, mediaBlackAodMethod,
                briefCardClass, briefBgColorMethod, briefResetBgMethod,
                gradViewClass, gradHideMethod,
                settingsHelperClass, showNotiLockscreenMethod
        };
    }

    public String formatDiagnosticsLines() {
        StringBuilder sb = new StringBuilder();
        sb.append("Schema: v").append(schemaVersion)
          .append(" | Target AOD: ").append(aodVersionName).append(" (").append(aodVersionCode).append(")\n");
        appendSlot(sb, "Core.baseCardClass", "baseCardClass", baseCardClass);
        appendSlot(sb, "Core.baseBindMethod", "baseBindMethod", baseBindMethod);
        appendSlot(sb, "Core.baseBlurMethod", "baseBlurMethod", baseBlurMethod);
        appendSlot(sb, "Core.baseColorMethod", "baseColorMethod", baseColorMethod);
        appendSlot(sb, "Core.stackViewClass", "stackViewClass", stackViewClass);
        appendSlot(sb, "Core.stackRefreshMethod", "stackRefreshMethod", stackRefreshMethod);
        appendSlot(sb, "Wake.baseShapeMethod", "baseShapeMethod", baseShapeMethod);
        appendSlot(sb, "Wake.baseProgressBgMethod", "baseProgressBgMethod", baseProgressBgMethod);
        appendSlot(sb, "Wake.baseStateMethod", "baseStateMethod", baseStateMethod);
        appendSlot(sb, "Wake.baseExpandMethod", "baseExpandMethod", baseExpandMethod);
        appendSlot(sb, "Wake.baseSetExpandStateMethod", "baseSetExpandStateMethod", baseSetExpandStateMethod);
        appendSlot(sb, "Wake.baseDarkModeMethod", "baseDarkModeMethod", baseDarkModeMethod);
        appendSlot(sb, "Wake.baseClearContainerBgMethod", "baseClearContainerBgMethod", baseClearContainerBgMethod);
        appendSlot(sb, "Wake.baseAodDrawableMethod", "baseAodDrawableMethod", baseAodDrawableMethod);
        appendSlot(sb, "Wake.baseLockscreenModeField", "baseLockscreenModeField", baseLockscreenModeField);
        appendSlot(sb, "Wake.baseDozingField", "baseDozingField", baseDozingField);
        appendSlot(sb, "Wake.baseExpandField", "baseExpandField", baseExpandField);
        appendSlot(sb, "Enh.mediaCardClass", "mediaCardClass", mediaCardClass);
        appendSlot(sb, "Enh.mediaForegroundMethod", "mediaForegroundMethod", mediaForegroundMethod);
        appendSlot(sb, "Enh.mediaBlackAodMethod", "mediaBlackAodMethod", mediaBlackAodMethod);
        appendSlot(sb, "Enh.briefCardClass", "briefCardClass", briefCardClass);
        appendSlot(sb, "Enh.briefBgColorMethod", "briefBgColorMethod", briefBgColorMethod);
        appendSlot(sb, "Enh.briefResetBgMethod", "briefResetBgMethod", briefResetBgMethod);
        appendSlot(sb, "Enh.gradViewClass", "gradViewClass", gradViewClass);
        appendSlot(sb, "Enh.gradHideMethod", "gradHideMethod", gradHideMethod);
        appendSlot(sb, "Enh.settingsHelperClass", "settingsHelperClass", settingsHelperClass);
        appendSlot(sb, "Enh.showNotiLockscreenMethod", "showNotiLockscreenMethod", showNotiLockscreenMethod);
        return sb.toString();
    }

    private void appendSlot(StringBuilder sb, String label, String slotKey, String value) {
        String status = getSlotStatus(slotKey, value);
        sb.append("  - ").append(label).append(": ").append(status);
        if (value != null) {
            sb.append(" -> ").append(value);
        }
        sb.append('\n');
    }

    private static String encodeStatuses(Map<String, String> statuses) {
        if (statuses == null || statuses.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : statuses.entrySet()) {
            if (sb.length() > 0) sb.append('|');
            String k = e.getKey().replace("|", "_").replace("=", "_");
            String v = (e.getValue() != null ? e.getValue() : "").replace("|", ";").replace("=", ":");
            sb.append(k).append('=').append(v);
        }
        return sb.toString();
    }

    private static Map<String, String> decodeStatuses(String raw) {
        if (raw == null || raw.isEmpty()) return Collections.emptyMap();
        Map<String, String> out = new LinkedHashMap<>();
        String[] parts = raw.split("\\|");
        for (String p : parts) {
            int eq = p.indexOf('=');
            if (eq > 0) {
                out.put(p.substring(0, eq), p.substring(eq + 1));
            }
        }
        return out;
    }

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("schema", schemaVersion);
            o.put("ver", aodVersionCode);
            o.put("verName", aodVersionName);
            o.put("ts", aodLastUpdateTime);
            o.put("baseCardClass", nullToEmpty(baseCardClass));
            o.put("baseBindMethod", nullToEmpty(baseBindMethod));
            o.put("baseBlurMethod", nullToEmpty(baseBlurMethod));
            o.put("baseColorMethod", nullToEmpty(baseColorMethod));
            o.put("stackViewClass", nullToEmpty(stackViewClass));
            o.put("stackRefreshMethod", nullToEmpty(stackRefreshMethod));
            o.put("baseShapeMethod", nullToEmpty(baseShapeMethod));
            o.put("baseProgressBgMethod", nullToEmpty(baseProgressBgMethod));
            o.put("baseStateMethod", nullToEmpty(baseStateMethod));
            o.put("baseExpandMethod", nullToEmpty(baseExpandMethod));
            o.put("baseSetExpandStateMethod", nullToEmpty(baseSetExpandStateMethod));
            o.put("baseDarkModeMethod", nullToEmpty(baseDarkModeMethod));
            o.put("baseClearContainerBgMethod", nullToEmpty(baseClearContainerBgMethod));
            o.put("baseAodDrawableMethod", nullToEmpty(baseAodDrawableMethod));
            o.put("baseLockscreenModeField", nullToEmpty(baseLockscreenModeField));
            o.put("baseDozingField", nullToEmpty(baseDozingField));
            o.put("baseExpandField", nullToEmpty(baseExpandField));
            o.put("mediaCardClass", nullToEmpty(mediaCardClass));
            o.put("mediaForegroundMethod", nullToEmpty(mediaForegroundMethod));
            o.put("mediaBlackAodMethod", nullToEmpty(mediaBlackAodMethod));
            o.put("briefCardClass", nullToEmpty(briefCardClass));
            o.put("briefBgColorMethod", nullToEmpty(briefBgColorMethod));
            o.put("briefResetBgMethod", nullToEmpty(briefResetBgMethod));
            o.put("gradViewClass", nullToEmpty(gradViewClass));
            o.put("gradHideMethod", nullToEmpty(gradHideMethod));
            o.put("settingsHelperClass", nullToEmpty(settingsHelperClass));
            o.put("showNotiLockscreenMethod", nullToEmpty(showNotiLockscreenMethod));
            o.put("statuses", encodeStatuses(slotStatuses));
            return o.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static HookMap fromJson(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(raw);
            return new HookMap(
                    o.optInt("schema", 0),
                    o.optLong("ver", -1L),
                    o.optString("verName", ""),
                    o.optLong("ts", -1L),
                    o.optString("baseCardClass", ""),
                    o.optString("baseBindMethod", ""),
                    o.optString("baseBlurMethod", ""),
                    o.optString("baseColorMethod", ""),
                    o.optString("stackViewClass", ""),
                    o.optString("stackRefreshMethod", ""),
                    o.optString("baseShapeMethod", ""),
                    o.optString("baseProgressBgMethod", ""),
                    o.optString("baseStateMethod", ""),
                    o.optString("baseExpandMethod", ""),
                    o.optString("baseSetExpandStateMethod", ""),
                    o.optString("baseDarkModeMethod", ""),
                    o.optString("baseClearContainerBgMethod", ""),
                    o.optString("baseAodDrawableMethod", ""),
                    o.optString("baseLockscreenModeField", ""),
                    o.optString("baseDozingField", ""),
                    o.optString("baseExpandField", ""),
                    o.optString("mediaCardClass", ""),
                    o.optString("mediaForegroundMethod", ""),
                    o.optString("mediaBlackAodMethod", ""),
                    o.optString("briefCardClass", ""),
                    o.optString("briefBgColorMethod", ""),
                    o.optString("briefResetBgMethod", ""),
                    o.optString("gradViewClass", ""),
                    o.optString("gradHideMethod", ""),
                    o.optString("settingsHelperClass", ""),
                    o.optString("showNotiLockscreenMethod", ""),
                    decodeStatuses(o.optString("statuses", ""))
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    @SuppressWarnings("deprecation")
    public static boolean saveFromApp(Context ctx, HookMap map) {
        if (ctx == null || map == null) return false;
        String json = map.toJson();
        if (json == null) return false;

        boolean privateSaved = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_JSON, json).commit();
        boolean worldSaved = false;
        try {
            SharedPreferences world = ctx.getSharedPreferences(PREF_NAME, Context.MODE_WORLD_READABLE);
            worldSaved = world.edit().putString(KEY_JSON, json).commit();
        } catch (SecurityException ignored) {}
        return worldSaved || privateSaved;
    }

    @SuppressWarnings("deprecation")
    public static HookMap loadFromApp(Context ctx) {
        if (ctx == null) return null;
        try {
            SharedPreferences world = ctx.getSharedPreferences(PREF_NAME, Context.MODE_WORLD_READABLE);
            HookMap fromWorld = fromJson(world.getString(KEY_JSON, null));
            if (fromWorld != null) return fromWorld;
        } catch (SecurityException ignored) {}
        SharedPreferences priv = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return fromJson(priv.getString(KEY_JSON, null));
    }

    public static HookMap loadFromXSharedPrefs(String modulePackage) {
        try {
            XSharedPreferences xprefs = new XSharedPreferences(modulePackage, PREF_NAME);
            File f = xprefs.getFile();
            if (f != null && f.canRead()) {
                xprefs.reload();
                return fromJson(xprefs.getString(KEY_JSON, null));
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static HookMap loadFromProvider(Context sysUiCtx) {
        if (sysUiCtx == null) return null;
        try {
            Bundle res = sysUiCtx.getContentResolver().call(
                    NowBarConfigProvider.CONTENT_URI, "get_hook_map", null, null);
            if (res != null) {
                return fromJson(res.getString("hook_map_json", null));
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
