package io.github.tavataan.nowbarcolor;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConfigManager {
    public static final String TAG = "NowBarColor";
    public static final String PREFS_NAME = "nowbar_color_prefs";
    public static final String SETTINGS_KEY = "nowbar_color_config_json";
    public static final String ACTION_CONFIG_UPDATED = "io.github.tavataan.nowbarcolor.ACTION_CONFIG_UPDATED";

    public static final int MODE_FIXED_COLOR = 0;
    public static final int MODE_LOCK_WALLPAPER = 1;
    public static final int MODE_GRADIENT = 2;
    public static final int MODE_IMAGE = 3;

    public static final int IMG_STYLE_ORIGINAL = 0;
    public static final int IMG_STYLE_BLUR = 1;
    public static final int IMG_STYLE_DUOTONE = 2;
    public static final int IMG_STYLE_NOIR = 3;
    public static final int IMG_STYLE_PIXEL = 4;
    public static final int IMG_STYLE_VIGNETTE = 5;
    public static final int IMG_STYLE_COUNT = 6;

    public static final int SCRIM_UNIFORM = 0;
    public static final int SCRIM_GRADIENT = 1;

    public static final int DUOTONE_MONET = 0;
    public static final int DUOTONE_CYBER = 1;
    public static final int DUOTONE_SUNSET = 2;
    public static final int DUOTONE_OCEAN = 3;
    public static final int DUOTONE_CRIMSON = 4;
    public static final int DUOTONE_COUNT = 5;

    public static final int DEFAULT_IMAGE_SCRIM = 40;
    public static final int MAX_IMAGE_SCRIM = 80;
    public static final int DEFAULT_IMAGE_BLUR = 45;
    public static final float AOD_IMAGE_DIM_VISIBILITY = 0.45f;

    public static final int WALLPAPER_PRIMARY = 0;
    public static final int WALLPAPER_SECONDARY = 1;
    public static final int WALLPAPER_MONET_ACCENT = 2;
    public static final int WALLPAPER_BOTTOM_AREA = 3;

    public static final int STYLE_SOLID = 0;
    public static final int STYLE_BLUR = 1;

    public static int clampImageStyle(int v) {
        return Math.max(0, Math.min(IMG_STYLE_COUNT - 1, v));
    }

    public static int clampImageScrimPercent(int v) {
        return Math.max(0, Math.min(MAX_IMAGE_SCRIM, v));
    }

    public static int clampImageBlurPercent(int v) {
        return Math.max(0, Math.min(100, v));
    }

    public static int clampScrimMode(int v) {
        return v == SCRIM_GRADIENT ? SCRIM_GRADIENT : SCRIM_UNIFORM;
    }

    public static int clampDuotonePreset(int v) {
        return Math.max(0, Math.min(DUOTONE_COUNT - 1, v));
    }

    public static final String CAT_MEDIA = "media";
    public static final String CAT_TIMER_CLOCK = "timer_clock";
    public static final String CAT_RECORDER = "recorder";
    public static final String CAT_FLASHLIGHT = "flashlight";
    public static final String CAT_BATTERY = "battery";
    public static final String CAT_BRIEF = "brief";
    public static final String CAT_CALL = "call";
    public static final String CAT_MODES = "modes";
    public static final String CAT_SPORTS_MAPS_HEALTH = "sports_maps_health";
    public static final String CAT_OTHER_ONGOING = "other_ongoing";

    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();

    public static class CategoryMeta {
        public final String id;
        public final String title;
        public final String subtitle;
        public final String iconName;
        public final String badgeText;
        public final int accentColor;

        public CategoryMeta(String id, String title, String subtitle, String iconName, int accentColor) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.iconName = iconName;
            this.badgeText = "";
            this.accentColor = accentColor;
        }

        public int getIconResId(Context ctx) {
            if (ctx == null || iconName == null || iconName.isEmpty()) return 0;
            return ctx.getResources().getIdentifier(iconName, "drawable", ctx.getPackageName());
        }

        public String getTitle(Context ctx) {
            if (ctx == null) return this.title;
            String res = I18n.get(ctx, "cat_" + id + "_title");
            if (res == null || res.equals("cat_" + id + "_title")) {
                return this.title;
            }
            return res;
        }

        public String getSubtitle(Context ctx) {
            if (ctx == null) return this.subtitle;
            String res = I18n.get(ctx, "cat_" + id + "_desc");
            if (res == null || res.equals("cat_" + id + "_desc")) {
                return this.subtitle;
            }
            return res;
        }
    }

    public static final List<CategoryMeta> ALL_CATEGORIES = new ArrayList<>();

    static {
        ALL_CATEGORIES.add(new CategoryMeta(
                CAT_MEDIA,
                "Música y Multimedia",
                "Spotify, YouTube Music, Samsung Music y reproductores de audio/video",
                "ic_cat_media",
                0xFFEC407A
        ));
        ALL_CATEGORIES.add(new CategoryMeta(
                CAT_TIMER_CLOCK,
                "Reloj, Temporizador y Cronómetro",
                "Temporizadores en cuenta regresiva, cronómetro y alarmas de Reloj",
                "ic_cat_timer",
                0xFF42A5F5
        ));
        ALL_CATEGORIES.add(new CategoryMeta(
                CAT_RECORDER,
                "Grabadora de Voz",
                "Notas de voz y grabaciones de audio en curso en pantalla de bloqueo",
                "ic_cat_recorder",
                0xFFEF5350
        ));
        ALL_CATEGORIES.add(new CategoryMeta(
                CAT_FLASHLIGHT,
                "Linterna",
                "Cápsula de linterna activa y controles rápidos en pantalla de bloqueo",
                "ic_cat_flashlight",
                0xFFFFB300
        ));
        ALL_CATEGORIES.add(new CategoryMeta(
                CAT_BATTERY,
                "Batería y Carga",
                "Cápsula de estado de batería y animación de carga rápida",
                "ic_cat_battery",
                0xFF66BB6A
        ));
        ALL_CATEGORIES.add(new CategoryMeta(
                CAT_BRIEF,
                "Now Brief e Inteligencia Artificial",
                "Resumen diario inteligente de Samsung, clima y sugerencias Galaxy AI",
                "ic_cat_brief",
                0xFFAB47BC
        ));
        ALL_CATEGORIES.add(new CategoryMeta(
                CAT_CALL,
                "Llamadas en Curso",
                "Cápsula de llamada activa, teléfono y llamadas VoIP en segundo plano",
                "ic_cat_call",
                0xFF26A69A
        ));
        ALL_CATEGORIES.add(new CategoryMeta(
                CAT_MODES,
                "Modos y Rutinas",
                "Modo Sueño, Conducción, Ejercicio, Trabajo y Rutinas de Bixby",
                "ic_cat_modes",
                0xFF5C6BC0
        ));
        ALL_CATEGORIES.add(new CategoryMeta(
                CAT_SPORTS_MAPS_HEALTH,
                "Deportes, Mapas y Salud",
                "Marcadores deportivos de Google, navegación en vivo y Samsung Health",
                "ic_cat_sports",
                0xFFFFA726
        ));
        ALL_CATEGORIES.add(new CategoryMeta(
                CAT_OTHER_ONGOING,
                "Otras Actividades en Vivo",
                "Intérprete, Smart View, compartición en vivo, contactos y apps de terceros",
                "ic_cat_other",
                0xFF78909C
        ));
    }

    public static class CategoryConfig {
        public boolean enabled = true;
        public boolean useCustomColor = false;
        public int customColor = 0xFF1E88E5;
        public boolean useCustomOpacity = false;
        public int customOpacity = 92;
        public int customColorMode = MODE_FIXED_COLOR;
        public int customGradientStart = 0xFF1477FF;
        public int customGradientEnd = 0xFF9D4EDD;
        public long customImageRev = 0L;
        public int customImageStyle = IMG_STYLE_ORIGINAL;
        public int customImageBlurPercent = DEFAULT_IMAGE_BLUR;
        public int customImageScrimPercent = DEFAULT_IMAGE_SCRIM;
        public int customImageScrimMode = SCRIM_UNIFORM;
        public int customImageDuotonePreset = DUOTONE_MONET;
        public int customImageDomColor = 0xFF1E88E5;
        public int customImageLuma = 128;

        public CategoryConfig() {}

        public CategoryConfig(boolean enabled, boolean useCustomColor, int customColor) {
            this.enabled = enabled;
            this.useCustomColor = useCustomColor;
            this.customColor = customColor;
            this.useCustomOpacity = false;
            this.customOpacity = 92;
        }

        public CategoryConfig(boolean enabled, boolean useCustomColor, int customColor, boolean useCustomOpacity, int customOpacity) {
            this.enabled = enabled;
            this.useCustomColor = useCustomColor;
            this.customColor = customColor;
            this.useCustomOpacity = useCustomOpacity;
            this.customOpacity = customOpacity;
        }
    }

    /** Linear RGB interpolation at the middle of two colors (opaque). */
    public static int midpointRgb(int a, int b) {
        int r = (((a >> 16) & 0xFF) + ((b >> 16) & 0xFF)) / 2;
        int g = (((a >> 8) & 0xFF) + ((b >> 8) & 0xFF)) / 2;
        int bl = ((a & 0xFF) + (b & 0xFF)) / 2;
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    public static int applyOpacity(int rgb, int opacityPercent) {
        int clamped = Math.max(0, Math.min(100, opacityPercent));
        int alpha = Math.round((clamped / 100.0f) * 255.0f);
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    /** Scales an ARGB color's alpha channel by a normalized visibility factor (0.0..1.0). */
    public static int scaleAlpha(int argb, float factor) {
        if (factor >= 0.999f) return argb;
        if (factor <= 0.005f) return argb & 0x00FFFFFF;
        int alpha = Math.max(0, Math.min(255, Math.round(((argb >>> 24) & 0xFF) * factor)));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    public static class Config {
        public boolean masterEnabled = true;
        public int colorMode = MODE_FIXED_COLOR;
        public int wallpaperVariant = WALLPAPER_PRIMARY;
        public int styleMode = STYLE_SOLID;
        public boolean hideBgOnAod = false;
        public int opacity = 92; // Solid fill opacity 0..100
        public int blurIntensity = OneUiBlurSpec.DEFAULT_BLUR_INTENSITY; // 0..100 -> 16px..100px
        public int blurTintOpacity = OneUiBlurSpec.DEFAULT_TINT_OPACITY; // 25..85
        public int globalColor = 0xFF1E88E5;
        public int gradientStartColor = 0xFF1477FF;
        public int gradientEndColor = 0xFF9D4EDD;
        public long imageRev = 0L;
        public int imageStyle = IMG_STYLE_ORIGINAL;
        public int imageBlurPercent = DEFAULT_IMAGE_BLUR;
        public int imageScrimPercent = DEFAULT_IMAGE_SCRIM;
        public int imageScrimMode = SCRIM_UNIFORM;
        public int imageDuotonePreset = DUOTONE_MONET;
        public int imageDomColor = 0xFF1E88E5;
        public int imageLuma = 128;
        public int cachedWallpaperPrimary = 0xFF1E88E5;
        public int cachedWallpaperSecondary = 0xFF3949AB;
        public int cachedWallpaperAccent = 0xFF00ACC1;
        public int cachedWallpaperBottom = 0xFF1565C0;
        public final Map<String, CategoryConfig> categories = new LinkedHashMap<>();

        public Config() {
            for (CategoryMeta meta : ALL_CATEGORIES) {
                categories.put(meta.id, new CategoryConfig(true, false, globalColor));
            }
        }

        public CategoryConfig getCategory(String id) {
            CategoryConfig cc = categories.get(id);
            if (cc == null) {
                cc = new CategoryConfig(true, false, globalColor);
                categories.put(id, cc);
            }
            return cc;
        }

        public int resolveGlobalBaseRgb(Integer liveWallpaperColor) {
            if (colorMode == MODE_IMAGE) {
                return imageDomColor | 0xFF000000;
            }
            if (colorMode == MODE_GRADIENT) {
                // Solid fallback (e.g. blur tint / GenUI views): midpoint of the gradient
                return midpointRgb(gradientStartColor, gradientEndColor);
            }
            if (colorMode == MODE_LOCK_WALLPAPER) {
                if (liveWallpaperColor != null && liveWallpaperColor != 0) {
                    return liveWallpaperColor | 0xFF000000;
                }
                switch (wallpaperVariant) {
                    case WALLPAPER_SECONDARY:
                        return cachedWallpaperSecondary | 0xFF000000;
                    case WALLPAPER_MONET_ACCENT:
                        return cachedWallpaperAccent | 0xFF000000;
                    case WALLPAPER_BOTTOM_AREA:
                        return cachedWallpaperBottom | 0xFF000000;
                    case WALLPAPER_PRIMARY:
                    default:
                        return cachedWallpaperPrimary | 0xFF000000;
                }
            }
            return globalColor | 0xFF000000;
        }

        public int resolveBaseRgbForCategory(String categoryId, Integer liveWallpaperColor) {
            CategoryConfig cc = getCategory(categoryId);
            if (cc.useCustomColor) {
                if (cc.customColorMode == MODE_IMAGE) {
                    return cc.customImageDomColor | 0xFF000000;
                }
                if (cc.customColorMode == MODE_GRADIENT) {
                    return midpointRgb(cc.customGradientStart, cc.customGradientEnd);
                }
                return cc.customColor | 0xFF000000;
            }
            return resolveGlobalBaseRgb(liveWallpaperColor);
        }

        public boolean isImageActiveForCategory(String categoryId) {
            CategoryConfig cc = getCategory(categoryId);
            if (cc.useCustomColor) {
                return cc.customColorMode == MODE_IMAGE && cc.customImageRev > 0L;
            }
            return colorMode == MODE_IMAGE && imageRev > 0L;
        }

        public String resolveImageSlotForCategory(String categoryId) {
            CategoryConfig cc = getCategory(categoryId);
            if (cc.useCustomColor) {
                return (cc.customColorMode == MODE_IMAGE && cc.customImageRev > 0L)
                        ? ("cat_" + categoryId)
                        : null;
            }
            return (colorMode == MODE_IMAGE && imageRev > 0L) ? "global" : null;
        }

        public long resolveImageRevForCategory(String categoryId) {
            CategoryConfig cc = getCategory(categoryId);
            if (cc.useCustomColor && cc.customColorMode == MODE_IMAGE) {
                return cc.customImageRev;
            }
            return colorMode == MODE_IMAGE ? imageRev : 0L;
        }

        public int resolveImageScrimPercentForCategory(String categoryId) {
            CategoryConfig cc = getCategory(categoryId);
            int raw = (cc.useCustomColor && cc.customColorMode == MODE_IMAGE)
                    ? cc.customImageScrimPercent
                    : imageScrimPercent;
            return clampImageScrimPercent(raw);
        }

        public int resolveImageScrimModeForCategory(String categoryId) {
            CategoryConfig cc = getCategory(categoryId);
            int raw = (cc.useCustomColor && cc.customColorMode == MODE_IMAGE)
                    ? cc.customImageScrimMode
                    : imageScrimMode;
            return clampScrimMode(raw);
        }

        public int resolveImageDomColorForCategory(String categoryId) {
            CategoryConfig cc = getCategory(categoryId);
            int raw = (cc.useCustomColor && cc.customColorMode == MODE_IMAGE)
                    ? cc.customImageDomColor
                    : imageDomColor;
            return raw | 0xFF000000;
        }

        public int resolveImageLumaForCategory(String categoryId) {
            CategoryConfig cc = getCategory(categoryId);
            int raw = (cc.useCustomColor && cc.customColorMode == MODE_IMAGE)
                    ? cc.customImageLuma
                    : imageLuma;
            return Math.max(0, Math.min(255, raw));
        }

        public int resolveImageContrastArgbForCategory(String categoryId) {
            return OneUiBlurSpec.computeImageContrastArgb(
                    resolveImageLumaForCategory(categoryId),
                    resolveImageScrimPercentForCategory(categoryId),
                    resolveImageScrimModeForCategory(categoryId));
        }

        public int resolveGlobalFinalArgb(Integer liveWallpaperColor) {
            return applyOpacity(resolveGlobalBaseRgb(liveWallpaperColor), opacity);
        }

        public int resolveOpacityForCategory(String categoryId) {
            CategoryConfig cc = getCategory(categoryId);
            return (cc != null && cc.useCustomColor && cc.useCustomOpacity) ? cc.customOpacity : opacity;
        }

        public int resolveFinalArgbForCategory(String categoryId, Integer liveWallpaperColor) {
            return applyOpacity(resolveBaseRgbForCategory(categoryId, liveWallpaperColor),
                    resolveOpacityForCategory(categoryId));
        }

        /** Returns {startArgb, endArgb} with opacity applied, or null if the global mode is not a gradient. */
        public int[] resolveGlobalGradientArgb() {
            if (colorMode != MODE_GRADIENT) return null;
            return new int[]{
                    applyOpacity(gradientStartColor, opacity),
                    applyOpacity(gradientEndColor, opacity)
            };
        }

        /** Returns {startArgb, endArgb} with effective opacity applied, or null if this category is not a gradient. */
        public int[] resolveGradientArgbForCategory(String categoryId) {
            CategoryConfig cc = getCategory(categoryId);
            if (cc.useCustomColor) {
                if (cc.customColorMode != MODE_GRADIENT) return null;
                int op = resolveOpacityForCategory(categoryId);
                return new int[]{
                        applyOpacity(cc.customGradientStart, op),
                        applyOpacity(cc.customGradientEnd, op)
                };
            }
            return resolveGlobalGradientArgb();
        }

        public int resolveBlurRadiusPx() {
            return OneUiBlurSpec.radiusPxFromPercent(blurIntensity);
        }

        public int resolveBlurTintOpacityForCategory(String categoryId) {
            CategoryConfig cc = getCategory(categoryId);
            int raw = (cc != null && cc.useCustomColor && cc.useCustomOpacity) ? cc.customOpacity : blurTintOpacity;
            return OneUiBlurSpec.clampTintOpacity(raw);
        }

        public int resolveGlobalBlurFinalArgb(Integer liveWallpaperColor) {
            return OneUiBlurSpec.applyTintOpacity(resolveGlobalBaseRgb(liveWallpaperColor), blurTintOpacity);
        }

        public int resolveBlurFinalArgbForCategory(String categoryId, Integer liveWallpaperColor) {
            return OneUiBlurSpec.applyTintOpacity(
                    resolveBaseRgbForCategory(categoryId, liveWallpaperColor),
                    resolveBlurTintOpacityForCategory(categoryId));
        }

        public int[] resolveGlobalBlurGradientArgb() {
            if (colorMode != MODE_GRADIENT) return null;
            return new int[]{
                    OneUiBlurSpec.applyTintOpacity(gradientStartColor, blurTintOpacity),
                    OneUiBlurSpec.applyTintOpacity(gradientEndColor, blurTintOpacity)
            };
        }

        public int[] resolveBlurGradientArgbForCategory(String categoryId) {
            CategoryConfig cc = getCategory(categoryId);
            if (cc.useCustomColor) {
                if (cc.customColorMode != MODE_GRADIENT) return null;
                int tintOp = resolveBlurTintOpacityForCategory(categoryId);
                return new int[]{
                        OneUiBlurSpec.applyTintOpacity(cc.customGradientStart, tintOp),
                        OneUiBlurSpec.applyTintOpacity(cc.customGradientEnd, tintOp)
                };
            }
            return resolveGlobalBlurGradientArgb();
        }

        public String toJsonString() {
            try {
                JSONObject root = new JSONObject();
                root.put("masterEnabled", masterEnabled);
                root.put("colorMode", colorMode);
                root.put("wallpaperVariant", wallpaperVariant);
                root.put("styleMode", styleMode);
                root.put("hideBgOnAod", hideBgOnAod);
                root.put("opacity", opacity);
                root.put("blurIntensity", blurIntensity);
                root.put("blurTintOpacity", blurTintOpacity);
                root.put("globalColor", globalColor);
                root.put("gradientStartColor", gradientStartColor);
                root.put("gradientEndColor", gradientEndColor);
                root.put("imageRev", imageRev);
                root.put("imageStyle", imageStyle);
                root.put("imageBlurPercent", imageBlurPercent);
                root.put("imageScrimPercent", imageScrimPercent);
                root.put("imageScrimMode", imageScrimMode);
                root.put("imageDuotonePreset", imageDuotonePreset);
                root.put("imageDomColor", imageDomColor);
                root.put("imageLuma", imageLuma);
                root.put("cachedWallpaperPrimary", cachedWallpaperPrimary);
                root.put("cachedWallpaperSecondary", cachedWallpaperSecondary);
                root.put("cachedWallpaperAccent", cachedWallpaperAccent);
                root.put("cachedWallpaperBottom", cachedWallpaperBottom);

                JSONObject catsObj = new JSONObject();
                for (Map.Entry<String, CategoryConfig> entry : categories.entrySet()) {
                    JSONObject cObj = new JSONObject();
                    CategoryConfig cc = entry.getValue();
                    cObj.put("enabled", cc.enabled);
                    cObj.put("useCustomColor", cc.useCustomColor);
                    cObj.put("customColor", cc.customColor);
                    cObj.put("useCustomOpacity", cc.useCustomOpacity);
                    cObj.put("customOpacity", cc.customOpacity);
                    cObj.put("customColorMode", cc.customColorMode);
                    cObj.put("customGradientStart", cc.customGradientStart);
                    cObj.put("customGradientEnd", cc.customGradientEnd);
                    cObj.put("customImageRev", cc.customImageRev);
                    cObj.put("customImageStyle", cc.customImageStyle);
                    cObj.put("customImageBlurPercent", cc.customImageBlurPercent);
                    cObj.put("customImageScrimPercent", cc.customImageScrimPercent);
                    cObj.put("customImageScrimMode", cc.customImageScrimMode);
                    cObj.put("customImageDuotonePreset", cc.customImageDuotonePreset);
                    cObj.put("customImageDomColor", cc.customImageDomColor);
                    cObj.put("customImageLuma", cc.customImageLuma);
                    catsObj.put(entry.getKey(), cObj);
                }
                root.put("categories", catsObj);
                return root.toString();
            } catch (Exception e) {
                Log.e(TAG, "Error serializing config", e);
                return "{}";
            }
        }

        public static Config fromJsonString(String jsonStr) {
            Config cfg = new Config();
            if (jsonStr == null || jsonStr.trim().isEmpty()) {
                return cfg;
            }
            try {
                JSONObject root = new JSONObject(jsonStr);
                cfg.masterEnabled = root.optBoolean("masterEnabled", true);
                cfg.colorMode = root.optInt("colorMode", MODE_FIXED_COLOR);
                cfg.wallpaperVariant = root.optInt("wallpaperVariant", WALLPAPER_PRIMARY);
                cfg.styleMode = root.optInt("styleMode", STYLE_SOLID);
                cfg.hideBgOnAod = root.optBoolean("hideBgOnAod", false);
                cfg.opacity = root.optInt("opacity", 92);
                cfg.blurIntensity = OneUiBlurSpec.clampBlurIntensity(
                        root.optInt("blurIntensity", OneUiBlurSpec.DEFAULT_BLUR_INTENSITY));
                cfg.blurTintOpacity = OneUiBlurSpec.clampTintOpacity(
                        root.optInt("blurTintOpacity", OneUiBlurSpec.DEFAULT_TINT_OPACITY));
                cfg.globalColor = root.optInt("globalColor", 0xFF1E88E5);
                cfg.gradientStartColor = root.optInt("gradientStartColor", 0xFF1477FF);
                cfg.gradientEndColor = root.optInt("gradientEndColor", 0xFF9D4EDD);
                cfg.imageRev = Math.max(0L, root.optLong("imageRev", 0L));
                cfg.imageStyle = clampImageStyle(root.optInt("imageStyle", IMG_STYLE_ORIGINAL));
                cfg.imageBlurPercent = clampImageBlurPercent(root.optInt("imageBlurPercent", DEFAULT_IMAGE_BLUR));
                cfg.imageScrimPercent = clampImageScrimPercent(root.optInt("imageScrimPercent", DEFAULT_IMAGE_SCRIM));
                cfg.imageScrimMode = clampScrimMode(root.optInt("imageScrimMode", SCRIM_UNIFORM));
                cfg.imageDuotonePreset = clampDuotonePreset(root.optInt("imageDuotonePreset", DUOTONE_MONET));
                cfg.imageDomColor = root.optInt("imageDomColor", 0xFF1E88E5) | 0xFF000000;
                cfg.imageLuma = Math.max(0, Math.min(255, root.optInt("imageLuma", 128)));
                cfg.cachedWallpaperPrimary = root.optInt("cachedWallpaperPrimary", 0xFF1E88E5);
                cfg.cachedWallpaperSecondary = root.optInt("cachedWallpaperSecondary", 0xFF3949AB);
                cfg.cachedWallpaperAccent = root.optInt("cachedWallpaperAccent", 0xFF00ACC1);
                cfg.cachedWallpaperBottom = root.optInt("cachedWallpaperBottom", 0xFF1565C0);

                JSONObject catsObj = root.optJSONObject("categories");
                if (catsObj != null) {
                    for (CategoryMeta meta : ALL_CATEGORIES) {
                        JSONObject cObj = catsObj.optJSONObject(meta.id);
                        if (cObj != null) {
                            CategoryConfig cc = new CategoryConfig();
                            cc.enabled = cObj.optBoolean("enabled", true);
                            cc.useCustomColor = cObj.optBoolean("useCustomColor", false);
                            cc.customColor = cObj.optInt("customColor", cfg.globalColor);
                            cc.useCustomOpacity = cObj.optBoolean("useCustomOpacity", false);
                            cc.customOpacity = cObj.optInt("customOpacity", cfg.opacity);
                            cc.customColorMode = cObj.optInt("customColorMode", MODE_FIXED_COLOR);
                            cc.customGradientStart = cObj.optInt("customGradientStart", 0xFF1477FF);
                            cc.customGradientEnd = cObj.optInt("customGradientEnd", 0xFF9D4EDD);
                            cc.customImageRev = Math.max(0L, cObj.optLong("customImageRev", 0L));
                            cc.customImageStyle = clampImageStyle(cObj.optInt("customImageStyle", IMG_STYLE_ORIGINAL));
                            cc.customImageBlurPercent = clampImageBlurPercent(cObj.optInt("customImageBlurPercent", DEFAULT_IMAGE_BLUR));
                            cc.customImageScrimPercent = clampImageScrimPercent(cObj.optInt("customImageScrimPercent", DEFAULT_IMAGE_SCRIM));
                            cc.customImageScrimMode = clampScrimMode(cObj.optInt("customImageScrimMode", SCRIM_UNIFORM));
                            cc.customImageDuotonePreset = clampDuotonePreset(cObj.optInt("customImageDuotonePreset", DUOTONE_MONET));
                            cc.customImageDomColor = cObj.optInt("customImageDomColor", 0xFF1E88E5) | 0xFF000000;
                            cc.customImageLuma = Math.max(0, Math.min(255, cObj.optInt("customImageLuma", 128)));
                            cfg.categories.put(meta.id, cc);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing config JSON", e);
            }
            return cfg;
        }
    }

    public static Config loadFromAppPrefs(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(SETTINGS_KEY, null);
            if (json != null && !json.isEmpty()) {
                return Config.fromJsonString(json);
            }
        } catch (Throwable ignored) {}
        return loadFromSystemSettings(context);
    }

    public static Config loadFromSystemSettings(Context context) {
        if (context == null) return new Config();
        // 1. Query NowBarConfigProvider first
        try {
            android.os.Bundle b = context.getContentResolver().call(
                    NowBarConfigProvider.CONTENT_URI, "get_config", null, null);
            if (b != null) {
                String json = b.getString("config_json", null);
                if (json != null && !json.isEmpty()) {
                    return Config.fromJsonString(json);
                }
            }
        } catch (Throwable ignored) {}

        // 2. Fallback to Settings.System / Settings.Global
        try {
            String json = Settings.System.getString(context.getContentResolver(), SETTINGS_KEY);
            if (json == null || json.isEmpty()) {
                json = Settings.Global.getString(context.getContentResolver(), SETTINGS_KEY);
            }
            if (json != null && !json.isEmpty()) {
                return Config.fromJsonString(json);
            }
        } catch (Throwable t) {
            Log.e(TAG, "loadFromSystemSettings error", t);
        }
        return new Config();
    }

    public static void saveAndSync(final Context context, final Config config) {
        final String json = config.toJsonString();
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(SETTINGS_KEY, json).apply();
        } catch (Throwable ignored) {}

        // 1. Write to Settings.System (WRITE_SETTINGS is granted via appops)
        try {
            Settings.System.putString(context.getContentResolver(), SETTINGS_KEY, json);
        } catch (Throwable ignored) {}

        // 2. Notify ContentObservers on NowBarConfigProvider
        try {
            context.getContentResolver().notifyChange(NowBarConfigProvider.CONTENT_URI, null);
        } catch (Throwable ignored) {}

        // 3. Send direct broadcast to SystemUI
        try {
            Intent intent = new Intent(ACTION_CONFIG_UPDATED);
            intent.putExtra("config_json", json);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
        } catch (Throwable ignored) {}
    }
}
