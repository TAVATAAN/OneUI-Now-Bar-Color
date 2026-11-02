package com.tavataan.nowbarcolor;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class NowBarHook implements IXposedHookLoadPackage {
    private static final String TAG = "NowBarColorHook";
    private static final Set<ClassLoader> HOOKED_CLASSLOADERS = Collections.synchronizedSet(new HashSet<ClassLoader>());
    private static final Set<View> ACTIVE_CARDS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<View, Boolean>()));
    private static final Set<View> ACTIVE_GENUI_BLUR_VIEWS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<View, Boolean>()));
    private static final Set<View> ACTIVE_GENUI_GUIDING_VIEWS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<View, Boolean>()));

    private static volatile ConfigManager.Config sConfig = new ConfigManager.Config();
    private static volatile WallpaperColorHelper.PaletteResult sLivePalette = null;
    private static volatile boolean sReceiverRegistered = false;
    private static volatile Context sSysUiContext = null;
    private static volatile Handler sMainHandler = null;

    private static Handler getMainHandler() {
        if (sMainHandler == null) {
            Looper mainLooper = Looper.getMainLooper();
            if (mainLooper != null) {
                sMainHandler = new Handler(mainLooper);
            }
        }
        return sMainHandler;
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if ("com.tavataan.nowbarcolor".equals(lpparam.packageName)) {
            hookSelfModuleStatus(lpparam.classLoader);
            return;
        }

        if (!"com.android.systemui".equals(lpparam.packageName)
                && !"com.samsung.android.app.aodservice".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + ": Loaded into package " + lpparam.packageName);

        if ("com.samsung.android.app.aodservice".equals(lpparam.packageName)) {
            hookAodPluginClassLoader(lpparam.classLoader);
            return;
        }

        // Only hook main com.android.systemui process (skip :screenshot and :edgelighting subprocesses)
        if (lpparam.processName != null && lpparam.processName.contains(":")) {
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(
                    ClassLoader.class,
                    "loadClass",
                    String.class,
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String name = (String) param.args[0];
                            if ("com.samsung.android.app.facewidget.pages.music.FaceWidgetMusicViewModel".equals(name)
                                    || "com.samsung.android.app.facewidget.presentation.NowBarBriefCardView".equals(name)
                                    || "aod.g92".equals(name)) {
                                Class<?> cls = (Class<?>) param.getResult();
                                if (cls != null) {
                                    final ClassLoader cl = cls.getClassLoader();
                                    if (cl != null && !HOOKED_CLASSLOADERS.contains(cl)) {
                                        Handler h = getMainHandler();
                                        if (h != null) {
                                            h.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    hookAodPluginClassLoader(cl);
                                                }
                                            });
                                        } else {
                                            hookAodPluginClassLoader(cl);
                                        }
                                    }
                                }
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": ClassLoader.loadClass hook error: " + t.getMessage());
        }
    }

    private void hookSelfModuleStatus(ClassLoader cl) {
        try {
            Class<?> mainAct = XposedHelpers.findClassIfExists("com.tavataan.nowbarcolor.MainActivity", cl);
            if (mainAct != null) {
                XposedHelpers.findAndHookMethod(mainAct, "isModuleActiveInLsposed", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(true);
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    private synchronized void hookAodPluginClassLoader(final ClassLoader cl) {
        if (cl == null || !HOOKED_CLASSLOADERS.add(cl)) {
            return;
        }
        XposedBridge.log(TAG + ": Hooking AOD / FaceWidget NowBar classes in ClassLoader: " + cl);

        try {
            final Class<?> baseCardClass = XposedHelpers.findClassIfExists(
                    "com.samsung.android.app.facewidget.presentation.a", cl);
            final Class<?> mediaCardClass = XposedHelpers.findClassIfExists(
                    "com.samsung.android.app.facewidget.presentation.NowBarMediaCardView", cl);
            final Class<?> batteryCardClass = XposedHelpers.findClassIfExists(
                    "com.samsung.android.app.facewidget.presentation.NowBarBatteryCardView", cl);
            final Class<?> briefCardClass = XposedHelpers.findClassIfExists(
                    "com.samsung.android.app.facewidget.presentation.NowBarBriefCardView", cl);
            final Class<?> ongoingCardClass = XposedHelpers.findClassIfExists(
                    "com.samsung.android.app.facewidget.presentation.NowBarOngoingCardView", cl);
            final Class<?> externalCardClass = XposedHelpers.findClassIfExists(
                    "com.samsung.android.app.facewidget.presentation.NowBarExternalViewCardView", cl);
            final Class<?> appSearchCardClass = XposedHelpers.findClassIfExists(
                    "com.samsung.android.app.facewidget.presentation.NowBarAppSearchCardView", cl);
            final Class<?> callCardClass = XposedHelpers.findClassIfExists(
                    "com.samsung.android.app.facewidget.presentation.NowBarCallCardView", cl);
            final Class<?> modeCardClass = XposedHelpers.findClassIfExists(
                    "com.samsung.android.app.facewidget.presentation.NowBarModeCardView", cl);
            final Class<?> contactCardClass = XposedHelpers.findClassIfExists(
                    "com.samsung.android.app.facewidget.presentation.NowBarContactCardView", cl);

            if (baseCardClass == null) {
                XposedBridge.log(TAG + ": Base card class 'a' not found in ClassLoader");
                return;
            }

            XC_MethodHook cardUpdateHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    View card = null;
                    if (param.thisObject instanceof View) {
                        card = (View) param.thisObject;
                    } else if (param.args != null && param.args.length > 0 && param.args[0] instanceof View) {
                        card = (View) param.args[0];
                    }
                    if (card != null) {
                        final View fCard = card;
                        ACTIVE_CARDS.add(fCard);
                        ensureSystemUiListeners(fCard.getContext());
                        if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(fCard, "nb_restoring"))) {
                            return;
                        }
                        applyOrRestoreCardBackground(fCard);
                        if (!Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(fCard, "nb_post_pending"))) {
                            XposedHelpers.setAdditionalInstanceField(fCard, "nb_post_pending", Boolean.TRUE);
                            fCard.post(new Runnable() {
                                @Override
                                public void run() {
                                    XposedHelpers.setAdditionalInstanceField(fCard, "nb_post_pending", Boolean.FALSE);
                                    if (fCard.isAttachedToWindow()) {
                                        applyOrRestoreCardBackground(fCard);
                                    }
                                }
                            });
                        }
                    }
                }
            };

            // 1. Hook constructors, lifecycle & background methods across base class 'a' and all card subclasses
            // (subclasses override E, O, D, T, P, a, u, q, h, i, so hooking both base and subclasses is essential)
            Class<?>[] allCardClasses = new Class<?>[]{
                    baseCardClass, mediaCardClass, batteryCardClass, briefCardClass,
                    ongoingCardClass, externalCardClass, appSearchCardClass,
                    callCardClass, modeCardClass, contactCardClass
            };
            String[] commonMethods = new String[]{
                    "U", "M", "E", "O", "D", "T", "P", "a", "u",
                    "h", "i", "s", "S", "setViewMode", "onLayout", "V",
                    "setExpanded", "setDozing", "setDarkAmount",
                    "onAttachedToWindow", "onFinishInflate", "onVisibilityChanged"
            };
            for (Class<?> cardCls : allCardClasses) {
                if (cardCls == null) continue;
                try {
                    XposedBridge.hookAllConstructors(cardCls, cardUpdateHook);
                } catch (Throwable ignored) {}
                for (String mName : commonMethods) {
                    try {
                        XposedBridge.hookAllMethods(cardCls, mName, cardUpdateHook);
                    } catch (Throwable ignored) {}
                }
            }

            // 1a. Dedicated hook for U(NowBarItem, Integer) on baseCardClass:
            // Intercepts Samsung's master item-binding method where this.g0 = nowBarItem is set.
            try {
                XposedBridge.hookAllMethods(baseCardClass, "U", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof View) {
                            final View card = (View) param.thisObject;
                            if (param.args != null && param.args.length > 0 && param.args[0] != null) {
                                XposedHelpers.setAdditionalInstanceField(card, "nb_cached_item", param.args[0]);
                            }
                            ACTIVE_CARDS.add(card);
                            ensureSystemUiListeners(card.getContext());
                            if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(card, "nb_restoring"))) {
                                return;
                            }
                            applyOrRestoreCardBackground(card);
                            card.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (card.isAttachedToWindow()) {
                                        applyOrRestoreCardBackground(card);
                                    }
                                }
                            });
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Failed to hook U on baseCardClass: " + t);
            }

            // 1b. Dedicated hook for N(NowBarItem, View, float, int) on baseCardClass:
            // Intercepts Samsung's native blur application to prevent black tint or unwanted blur in solid mode.
            try {
                XposedBridge.hookAllMethods(baseCardClass, "N", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof View) {
                            View card = (View) param.thisObject;
                            if (param.args != null && param.args.length > 0 && param.args[0] != null) {
                                XposedHelpers.setAdditionalInstanceField(card, "nb_cached_item", param.args[0]);
                            }
                            if (isCollapsedLockscreenCapsule(card)) {
                                ConfigManager.Config cfg = sConfig;
                                String catId = resolveCategoryForCard(card);
                                ConfigManager.CategoryConfig catCfg = cfg != null ? cfg.getCategory(catId) : null;
                                if (cfg != null && cfg.masterEnabled && catCfg != null && catCfg.enabled) {
                                    boolean useBlur = (cfg.styleMode == ConfigManager.STYLE_BLUR);
                                    if (!useBlur) {
                                        if (param.args != null && param.args.length > 1 && param.args[1] instanceof View) {
                                            clearBlurInfo((View) param.args[1]);
                                        }
                                        param.setResult(null); // Skip Samsung's blur in solid mode
                                        return;
                                    }
                                    int[] grad = cfg.resolveGradientArgbForCategory(catId);
                                    if (grad != null && param.args != null && param.args.length > 3) {
                                        param.args[3] = Color.TRANSPARENT; // Transparent blur tint for gradient
                                    } else if (param.args != null && param.args.length > 3) {
                                        param.args[3] = resolveTargetArgbForCategory(catId);
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof View) {
                            final View card = (View) param.thisObject;
                            if (param.args != null && param.args.length > 0 && param.args[0] != null) {
                                XposedHelpers.setAdditionalInstanceField(card, "nb_cached_item", param.args[0]);
                            }
                            ACTIVE_CARDS.add(card);
                            ensureSystemUiListeners(card.getContext());
                            if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(card, "nb_restoring"))) {
                                return;
                            }
                            applyOrRestoreCardBackground(card);
                            card.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (card.isAttachedToWindow()) {
                                        applyOrRestoreCardBackground(card);
                                    }
                                }
                            });
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Failed to hook N on baseCardClass: " + t);
            }

            // 1c. Dedicated hook for q(float, float) -> int on baseCardClass:
            // Intercepts Samsung's lockscreen card color calculation (which defaults to black 0xCC000000 / 0xFF2B2B2B)
            try {
                XposedBridge.hookAllMethods(baseCardClass, "q", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof View) {
                            View card = (View) param.thisObject;
                            if (isCollapsedLockscreenCapsule(card)) {
                                ConfigManager.Config cfg = sConfig;
                                String catId = resolveCategoryForCard(card);
                                ConfigManager.CategoryConfig catCfg = cfg != null ? cfg.getCategory(catId) : null;
                                if (cfg != null && cfg.masterEnabled && catCfg != null && catCfg.enabled) {
                                    int[] grad = cfg.resolveGradientArgbForCategory(catId);
                                    if (grad != null) {
                                        param.setResult(Color.TRANSPARENT);
                                    } else {
                                        param.setResult(resolveTargetArgbForCategory(catId));
                                    }
                                }
                            }
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Failed to hook q on baseCardClass: " + t);
            }

            // 2. Hook NowBarMediaCardView specific methods
            if (mediaCardClass != null) {
                // Hook m0(int, float, int, int):
                // When media customization is enabled, pass Integer.MAX_VALUE as arg 3 so m0() sets cardView.setForeground(null),
                // completely preventing Samsung from drawing a light gradient foreground rectangle on p1!
                try {
                    XposedBridge.hookAllMethods(mediaCardClass, "m0", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (shouldCustomizeCatMedia() && param.args != null && param.args.length >= 4) {
                                param.args[3] = Integer.MAX_VALUE;
                            }
                        }
                    });
                } catch (Throwable ignored) {}

                String[] mediaMethods = new String[]{"m0", "R", "i0", "y0", "r0"};
                for (String mName : mediaMethods) {
                    try {
                        XposedBridge.hookAllMethods(mediaCardClass, mName, cardUpdateHook);
                    } catch (Throwable ignored) {}
                }
                try {
                    XposedBridge.hookAllMethods(mediaCardClass, "setBgColor", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.thisObject instanceof View) {
                                View card = (View) param.thisObject;
                                if (isCollapsedLockscreenCapsule(card)) {
                                    ConfigManager.Config cfg = sConfig;
                                    ConfigManager.CategoryConfig catCfg = cfg != null ? cfg.getCategory(ConfigManager.CAT_MEDIA) : null;
                                    if (cfg != null && cfg.masterEnabled && catCfg != null && catCfg.enabled) {
                                        boolean useBlur = (cfg.styleMode == ConfigManager.STYLE_BLUR);
                                        if (!useBlur) {
                                            View p1 = getViewField(card, "p1");
                                            clearBlurInfo(p1);
                                            View o1 = getViewField(card, "o1");
                                            clearBlurInfo(o1);
                                            param.setResult(null);
                                            return;
                                        }
                                        int[] grad = cfg.resolveGradientArgbForCategory(ConfigManager.CAT_MEDIA);
                                        if (grad != null && param.args != null && param.args.length > 0) {
                                            param.args[0] = Color.TRANSPARENT;
                                        } else if (param.args != null && param.args.length > 0) {
                                            param.args[0] = resolveTargetArgbForCategory(ConfigManager.CAT_MEDIA);
                                        }
                                    }
                                }
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.thisObject instanceof View) {
                                View card = (View) param.thisObject;
                                ACTIVE_CARDS.add(card);
                                ensureSystemUiListeners(card.getContext());
                                if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(card, "nb_restoring"))) {
                                    return;
                                }
                                applyOrRestoreCardBackground(card);
                            }
                        }
                    });
                } catch (Throwable ignored) {}
            }

            // 3. Hook NowBarBatteryCardView specific methods
            if (batteryCardClass != null) {
                try {
                    XposedBridge.hookAllMethods(batteryCardClass, "f0", cardUpdateHook);
                } catch (Throwable ignored) {}
            }

            // 4. Hook NowBarOngoingCardView specific methods (nudge, AI presence, remote background views)
            if (ongoingCardClass != null) {
                String[] ongoingMethods = new String[]{"x0", "y0", "A0", "E0"};
                for (String mName : ongoingMethods) {
                    try {
                        XposedBridge.hookAllMethods(ongoingCardClass, mName, cardUpdateHook);
                    } catch (Throwable ignored) {}
                }
            }

            // 5. Hook NowBarBriefCardView specific methods (including static d0 and e0)
            if (briefCardClass != null) {
                try {
                    XposedBridge.hookAllMethods(briefCardClass, "h0", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.thisObject instanceof View) {
                                View card = (View) param.thisObject;
                                ACTIVE_CARDS.add(card);
                                ensureSystemUiListeners(card.getContext());
                                if (isCollapsedLockscreenCapsule(card) && shouldCustomizeCatBrief()) {
                                    ConfigManager.Config cfg = sConfig;
                                    int[] grad = cfg != null ? cfg.resolveGradientArgbForCategory(ConfigManager.CAT_BRIEF) : null;
                                    if (grad != null) {
                                        param.setResult(Color.TRANSPARENT);
                                    } else {
                                        Integer liveWall = getLiveWallpaperColorForConfig(cfg);
                                        int customArgb = cfg.resolveFinalArgbForCategory(ConfigManager.CAT_BRIEF, liveWall);
                                        param.setResult(customArgb);
                                    }
                                }
                            }
                        }
                    });
                    String[] briefMethods = new String[]{"g0", "l0", "h"};
                    for (String mName : briefMethods) {
                        try {
                            XposedBridge.hookAllMethods(briefCardClass, mName, cardUpdateHook);
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }

            // 6. Hook Samsung GenUI (com.samsung.android.genui) used inside NowBarBriefCardView's ComposeView (o1)
            hookGenUiClasses(cl);

            // 7. Ensure SettingsHelper.A() forces notiShowStateOnLockscreen=true so Samsung native filter displays ongoing activities

            final Class<?> settingsHelperClass = XposedHelpers.findClassIfExists(
                    "com.samsung.android.app.aodservice.common.utils.settings.SettingsHelper", cl);
            if (settingsHelperClass != null) {
                try {
                    XposedBridge.hookAllMethods(settingsHelperClass, "A", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(true);
                        }
                    });
                    XposedBridge.log(TAG + ": Hooked SettingsHelper.A() to force notiShowStateOnLockscreen=true");
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": Failed to hook SettingsHelper.A: " + t);
                }
            }

            // 8. Hook NowBarCardStackView (master container for capsules on Lockscreen)
            final Class<?> stackViewClass = XposedHelpers.findClassIfExists(
                    "com.samsung.android.app.facewidget.utils.NowBarCardStackView", cl);
            if (stackViewClass != null) {
                // 8a. Hook q(CopyOnWriteArrayList): Samsung's master refreshAllViews method
                XposedBridge.hookAllMethods(stackViewClass, "q", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof ViewGroup) {
                            final ViewGroup stack = (ViewGroup) param.thisObject;
                            ensureSystemUiListeners(stack.getContext());
                            stack.post(new Runnable() {
                                @Override
                                public void run() {
                                    int count = stack.getChildCount();
                                    for (int i = 0; i < count; i++) {
                                        View child = stack.getChildAt(i);
                                        if (child != null) {
                                            ACTIVE_CARDS.add(child);
                                            applyOrRestoreCardBackground(child);
                                        }
                                    }
                                }
                            });
                        }
                    }
                });

                // 8b. Hook addView: Intercepts the exact instant any card view is added to the stack hierarchy
                XposedBridge.hookAllMethods(stackViewClass, "addView", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.args != null && param.args.length > 0 && param.args[0] instanceof View) {
                            final View card = (View) param.args[0];
                            ACTIVE_CARDS.add(card);
                            ensureSystemUiListeners(card.getContext());
                            applyOrRestoreCardBackground(card);
                            card.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (card.isAttachedToWindow()) {
                                        applyOrRestoreCardBackground(card);
                                    }
                                }
                            });
                        }
                    }
                });
                XposedBridge.log(TAG + ": Hooked NowBarCardStackView (refreshAllViews 'q' and 'addView')");
            }

            // 9. Hook NowBarCardGradientView (suppress native notification chip background overlay)
            final Class<?> gradViewClass = XposedHelpers.findClassIfExists(
                    "com.samsung.android.app.facewidget.presentation.NowBarCardGradientView", cl);
            if (gradViewClass != null) {
                XC_MethodHook blockGradHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof View) {
                            View gradView = (View) param.thisObject;
                            View card = findEnclosingNowBarCard(gradView);
                            if (card != null && isCollapsedLockscreenCapsule(card)) {
                                ConfigManager.Config cfg = sConfig;
                                String catId = resolveCategoryForCard(card);
                                ConfigManager.CategoryConfig catCfg = cfg != null ? cfg.getCategory(catId) : null;
                                if (cfg != null && cfg.masterEnabled && catCfg != null && catCfg.enabled) {
                                    gradView.setVisibility(View.GONE);
                                    gradView.setAlpha(0.0f);
                                    param.setResult(null);
                                }
                            }
                        }
                    }
                };
                for (String m : new String[]{"setGradientBackgroundVisible", "setGradientColor", "b"}) {
                    try {
                        XposedBridge.hookAllMethods(gradViewClass, m, blockGradHook);
                    } catch (Throwable ignored) {}
                }
                XposedBridge.log(TAG + ": Hooked NowBarCardGradientView to suppress native chip overlay");
            }

            XposedBridge.log(TAG + ": Successfully hooked all NowBar capsule & GenUI classes!");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error hooking AOD plugin classes: " + t);
        }
    }

    private void hookGenUiClasses(final ClassLoader cl) {
        try {
            // 6a. StyleBox.updateBlur(View, Context, BaseStyle) -> applies SemBlurInfo with preset 54/62 to the GenUI FrameLayout pill
            final Class<?> styleBoxClass = XposedHelpers.findClassIfExists(
                    "com.samsung.android.genui.kit.nodes.StyleBox", cl);
            if (styleBoxClass != null) {
                XposedBridge.hookAllMethods(styleBoxClass, "updateBlur", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.args != null && param.args.length >= 1 && param.args[0] instanceof View) {
                            View blurView = (View) param.args[0];
                            if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(blurView, "nb_restoring_blur"))) {
                                return;
                            }
                            ACTIVE_GENUI_BLUR_VIEWS.add(blurView);
                            ensureSystemUiListeners(blurView.getContext());
                            if (param.thisObject != null) {
                                XposedHelpers.setAdditionalInstanceField(blurView, "nb_genui_stylebox", param.thisObject);
                            }
                            if (param.args.length >= 3 && param.args[2] != null) {
                                XposedHelpers.setAdditionalInstanceField(blurView, "nb_genui_style", param.args[2]);
                            }
                            applyOrRestoreGenUiBlurView(blurView);
                        }
                    }
                });
            }

            // 6b. GuidingLightEffectLayout -> draws animated opaque AI shader on top of the GenUI pill
            final Class<?> guidingLightClass = XposedHelpers.findClassIfExists(
                    "com.samsung.android.genui.kit.nodes.GuidingLightEffectLayout", cl);
            if (guidingLightClass != null) {
                XC_MethodHook glHook = new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof View) {
                            View glView = (View) param.thisObject;
                            ACTIVE_GENUI_GUIDING_VIEWS.add(glView);
                            ensureSystemUiListeners(glView.getContext());
                            applyOrRestoreGenUiGuidingView(glView);
                        }
                    }
                };
                String[] glMethods = new String[]{"setConfig", "updateAnimationState", "onAttachedToWindow"};
                for (String mName : glMethods) {
                    try {
                        XposedBridge.hookAllMethods(guidingLightClass, mName, glHook);
                    } catch (Throwable ignored) {}
                }
            }

            // 6c. BaseStyle.getBackgroundColor(), getBackgroundDarkColor() & getBackgroundAlpha() ->
            // Supply user's custom capsule color (targetArgb) directly to Compose Modifier.background,
            // while preserving small inner icon/action backgrounds untouched!
            final Class<?> baseStyleClass = XposedHelpers.findClassIfExists(
                    "com.samsung.android.genui.model.style.BaseStyle", cl);
            if (baseStyleClass != null) {
                XC_MethodHook bgListHook = new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object orig = param.getResult();
                        if (!(orig instanceof List) || ((List<?>) orig).isEmpty()) {
                            return;
                        }
                        if (shouldCustomizeCatBrief() && isAnyBriefCardCollapsed() && isCapsuleRootBaseStyle(param.thisObject)) {
                            ConfigManager.Config gcfg = sConfig;
                            int[] grad = gcfg != null ? gcfg.resolveGradientArgbForCategory(ConfigManager.CAT_BRIEF) : null;
                            if (grad != null) {
                                param.setResult(Arrays.asList(grad[0], grad[1]));
                            } else {
                                int targetArgb = resolveTargetArgbForCategory(ConfigManager.CAT_BRIEF);
                                param.setResult(Arrays.asList(targetArgb, targetArgb));
                            }
                        }
                    }
                };
                try {
                    XposedBridge.hookAllMethods(baseStyleClass, "getBackgroundColor", bgListHook);
                } catch (Throwable ignored) {}
                try {
                    XposedBridge.hookAllMethods(baseStyleClass, "getBackgroundDarkColor", bgListHook);
                } catch (Throwable ignored) {}
                try {
                    XposedBridge.hookAllMethods(baseStyleClass, "getBackgroundStyle", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (shouldCustomizeCatBrief() && isAnyBriefCardCollapsed() && isCapsuleRootBaseStyle(param.thisObject)) {
                                ConfigManager.Config gcfg = sConfig;
                                int[] grad = gcfg != null ? gcfg.resolveGradientArgbForCategory(ConfigManager.CAT_BRIEF) : null;
                                if (grad != null) {
                                    param.setResult("background_gradient");
                                }
                            }
                        }
                    });
                } catch (Throwable ignored) {}
                try {
                    XposedBridge.hookAllMethods(baseStyleClass, "getBackgroundAlpha", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (shouldCustomizeCatBrief() && isAnyBriefCardCollapsed() && isCapsuleRootBaseStyle(param.thisObject)) {
                                param.setResult(null);
                            }
                        }
                    });
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error hooking GenUI classes: " + t.getMessage());
        }
    }

    private static void ensureSystemUiListeners(Context context) {
        if (context == null || sReceiverRegistered) return;
        synchronized (NowBarHook.class) {
            if (sReceiverRegistered) return;
            final Context appCtx = context.getApplicationContext() != null ? context.getApplicationContext() : context;
            sSysUiContext = appCtx;
            sReceiverRegistered = true;

            try {
                Settings.System.putInt(appCtx.getContentResolver(), "nowbar_color_hook_active", 1);
            } catch (Throwable ignored) {}

            // Load initial config and wallpaper colors
            reloadConfigAndRefreshAll(appCtx, true);

            final Handler h = getMainHandler();
            if (h == null) return;

            h.post(new Runnable() {
                @Override
                public void run() {
                    // 1. Register BroadcastReceiver for instant updates from app & wallpaper changes
                    try {
                        IntentFilter filter = new IntentFilter();
                        filter.addAction(ConfigManager.ACTION_CONFIG_UPDATED);
                        filter.addAction(Intent.ACTION_WALLPAPER_CHANGED);
                        BroadcastReceiver receiver = new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context ctx, Intent intent) {
                                if (intent == null) return;
                                String action = intent.getAction();
                                if (ConfigManager.ACTION_CONFIG_UPDATED.equals(action)) {
                                    String json = intent.getStringExtra("config_json");
                                    if (json != null && !json.isEmpty()) {
                                        sConfig = ConfigManager.Config.fromJsonString(json);
                                        refreshAllActiveCards();
                                        return;
                                    }
                                }
                                reloadConfigAndRefreshAll(appCtx, Intent.ACTION_WALLPAPER_CHANGED.equals(action));
                            }
                        };
                        if (Build.VERSION.SDK_INT >= 33) {
                            appCtx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
                        } else {
                            appCtx.registerReceiver(receiver, filter);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": BroadcastReceiver registration error: " + t.getMessage());
                    }

                    // 2. Register ContentObserver on NowBarConfigProvider, Settings.System & Settings.Global
                    try {
                        ContentObserver observer = new ContentObserver(h) {
                            @Override
                            public void onChange(boolean selfChange, Uri uri) {
                                reloadConfigAndRefreshAll(appCtx, false);
                            }
                        };
                        appCtx.getContentResolver().registerContentObserver(
                                NowBarConfigProvider.CONTENT_URI,
                                true,
                                observer
                        );
                        appCtx.getContentResolver().registerContentObserver(
                                Settings.System.getUriFor(ConfigManager.SETTINGS_KEY),
                                false,
                                observer
                        );
                        appCtx.getContentResolver().registerContentObserver(
                                Settings.Global.getUriFor(ConfigManager.SETTINGS_KEY),
                                false,
                                observer
                        );
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": ContentObserver registration error: " + t.getMessage());
                    }

                    // 3. Register WallpaperManager.OnColorsChangedListener for live Lockscreen Wallpaper updates
                    try {
                        WallpaperManager wm = WallpaperManager.getInstance(appCtx);
                        if (wm != null) {
                            wm.addOnColorsChangedListener(new WallpaperManager.OnColorsChangedListener() {
                                @Override
                                public void onColorsChanged(WallpaperColors colors, int which) {
                                    sLivePalette = WallpaperColorHelper.extractLockWallpaperPalette(appCtx, false);
                                    refreshAllActiveCards();
                                }
                            }, h);
                        }
                    } catch (Throwable ignored) {}
                }
            });
        }
    }

    private static void reloadConfigAndRefreshAll(Context context, boolean refreshWallpaper) {
        try {
            if (context != null) {
                sConfig = ConfigManager.loadFromSystemSettings(context);
                if (refreshWallpaper || sLivePalette == null) {
                    sLivePalette = WallpaperColorHelper.extractLockWallpaperPalette(context, false);
                }
            }
            refreshAllActiveCards();
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": reloadConfigAndRefreshAll error: " + t.getMessage());
        }
    }

    private static void refreshAllActiveCards() {
        Handler h = getMainHandler();
        if (h == null) return;
        h.post(new Runnable() {
            @Override
            public void run() {
                List<View> cards;
                synchronized (ACTIVE_CARDS) {
                    cards = new ArrayList<>(ACTIVE_CARDS);
                }
                for (View card : cards) {
                    if (card != null && card.isAttachedToWindow()) {
                        applyOrRestoreCardBackground(card);
                    }
                }

                List<View> blurViews;
                synchronized (ACTIVE_GENUI_BLUR_VIEWS) {
                    blurViews = new ArrayList<>(ACTIVE_GENUI_BLUR_VIEWS);
                }
                for (View bv : blurViews) {
                    if (bv != null && bv.isAttachedToWindow()) {
                        applyOrRestoreGenUiBlurView(bv);
                    }
                }

                List<View> guidingViews;
                synchronized (ACTIVE_GENUI_GUIDING_VIEWS) {
                    guidingViews = new ArrayList<>(ACTIVE_GENUI_GUIDING_VIEWS);
                }
                for (View gv : guidingViews) {
                    if (gv != null && gv.isAttachedToWindow()) {
                        applyOrRestoreGenUiGuidingView(gv);
                    }
                }
            }
        });
    }

    private static Integer getLiveWallpaperColorForConfig(ConfigManager.Config cfg) {
        WallpaperColorHelper.PaletteResult pal = sLivePalette;
        if (pal == null && sSysUiContext != null) {
            pal = WallpaperColorHelper.extractLockWallpaperPalette(sSysUiContext, false);
            sLivePalette = pal;
        }
        if (pal != null) {
            return WallpaperColorHelper.resolveForVariant(pal, cfg.wallpaperVariant);
        }
        return null;
    }

    private static boolean shouldCustomizeCatBrief() {
        ConfigManager.Config cfg = sConfig;
        if (cfg == null || !cfg.masterEnabled) return false;
        ConfigManager.CategoryConfig catCfg = cfg.getCategory(ConfigManager.CAT_BRIEF);
        return catCfg != null && catCfg.enabled;
    }

    private static boolean shouldCustomizeCatMedia() {
        ConfigManager.Config cfg = sConfig;
        if (cfg == null || !cfg.masterEnabled) return false;
        ConfigManager.CategoryConfig catCfg = cfg.getCategory(ConfigManager.CAT_MEDIA);
        return catCfg != null && catCfg.enabled;
    }

    private static int resolveTargetArgbForCategory(String categoryId) {
        ConfigManager.Config cfg = sConfig;
        if (cfg == null) return 0xE61E293B;
        ConfigManager.CategoryConfig catCfg = cfg.getCategory(categoryId);
        Integer liveWallColor = (cfg.colorMode == ConfigManager.MODE_LOCK_WALLPAPER
                && (catCfg == null || !catCfg.useCustomColor))
                ? getLiveWallpaperColorForConfig(cfg)
                : null;
        return cfg.resolveFinalArgbForCategory(categoryId, liveWallColor);
    }

    private static boolean isAnyBriefCardCollapsed() {
        List<View> cards;
        synchronized (ACTIVE_CARDS) {
            cards = new ArrayList<>(ACTIVE_CARDS);
        }
        boolean foundBriefCard = false;
        for (View card : cards) {
            if (card != null && card.getClass().getName().endsWith("NowBarBriefCardView")) {
                foundBriefCard = true;
                if (isCollapsedLockscreenCapsule(card)) {
                    return true;
                }
            }
        }
        // If GenUI composes before NowBarBriefCardView is registered in ACTIVE_CARDS, default to true
        return !foundBriefCard;
    }

    private static View findEnclosingNowBarCard(View view) {
        ViewParent curr = view != null ? view.getParent() : null;
        while (curr instanceof View) {
            View v = (View) curr;
            String name = v.getClass().getName();
            if (name.startsWith("com.samsung.android.app.facewidget.presentation.NowBar")
                    || "com.samsung.android.app.facewidget.presentation.a".equals(name)) {
                return v;
            }
            curr = v.getParent();
        }
        return null;
    }

    private static boolean isBriefViewCollapsed(View genUiChildView) {
        View card = findEnclosingNowBarCard(genUiChildView);
        if (card != null) {
            return isCollapsedLockscreenCapsule(card);
        }
        return isAnyBriefCardCollapsed();
    }

    private static boolean isCapsuleRootBaseStyle(Object baseStyle) {
        if (baseStyle == null) return false;
        try {
            Object gl = callMethodNoArgs(baseStyle, "getGuidingLightEffect");
            if (Boolean.TRUE.equals(gl)) return true;
            Object ng = callMethodNoArgs(baseStyle, "getNudgeGlowEffect");
            if (Boolean.TRUE.equals(ng)) return true;
            Object bs = callMethodNoArgs(baseStyle, "getBlurStyle");
            if (bs != null) return true;

            float radius = 0f;
            Object rObj = callMethodNoArgs(baseStyle, "getRadius");
            if (rObj instanceof Number) {
                radius = ((Number) rObj).floatValue();
            } else {
                rObj = callMethodNoArgs(baseStyle, "getCornerRadius");
                if (rObj instanceof Number) radius = ((Number) rObj).floatValue();
            }

            float w = 0f;
            Object wObj = callMethodNoArgs(baseStyle, "getWidth");
            if (wObj instanceof Number) w = ((Number) wObj).floatValue();

            float h = 0f;
            Object hObj = callMethodNoArgs(baseStyle, "getHeight");
            if (hObj instanceof Number) h = ((Number) hObj).floatValue();

            // Root capsule pills use rounded corners (>= 16dp) and are not small square icon circles (<= 46dp x 46dp)
            if (radius >= 16f && !(w > 0f && w <= 46f && h > 0f && h <= 46f)) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static void applyOrRestoreGenUiBlurView(View blurView) {
        if (blurView == null) return;
        try {
            boolean shouldCustom = shouldCustomizeCatBrief() && isBriefViewCollapsed(blurView);
            if (shouldCustom) {
                ConfigManager.Config cfg = sConfig;
                int targetArgb = resolveTargetArgbForCategory(ConfigManager.CAT_BRIEF);
                int[] grad = cfg != null ? cfg.resolveGradientArgbForCategory(ConfigManager.CAT_BRIEF) : null;
                boolean useBlur = (cfg != null && cfg.styleMode == ConfigManager.STYLE_BLUR);

                float cornerDp = 34.0f;
                Object baseStyle = XposedHelpers.getAdditionalInstanceField(blurView, "nb_genui_style");
                if (baseStyle != null) {
                    Object rObj = callMethodNoArgs(baseStyle, "getRadius");
                    if (rObj instanceof Number && ((Number) rObj).floatValue() > 0f) {
                        cornerDp = ((Number) rObj).floatValue();
                    } else {
                        rObj = callMethodNoArgs(baseStyle, "getCornerRadius");
                        if (rObj instanceof Number && ((Number) rObj).floatValue() > 0f) {
                            cornerDp = ((Number) rObj).floatValue();
                        }
                    }
                }
                float density = blurView.getContext().getResources().getDisplayMetrics().density;
                float cornerPx = cornerDp * (density > 0f ? density : 3.0f);
                if (cornerPx <= 0f) cornerPx = 100.0f;

                XposedHelpers.setAdditionalInstanceField(blurView, "nb_genui_blur_applied", Boolean.TRUE);
                setCardViewColorAndStyle(blurView, targetArgb, useBlur, cornerPx, grad);
            } else {
                Object applied = XposedHelpers.getAdditionalInstanceField(blurView, "nb_genui_blur_applied");
                if (Boolean.TRUE.equals(applied)) {
                    XposedHelpers.setAdditionalInstanceField(blurView, "nb_genui_blur_applied", Boolean.FALSE);
                    XposedHelpers.setAdditionalInstanceField(blurView, "nb_restoring_blur", Boolean.TRUE);
                    try {
                        blurView.setBackground(null);
                        Object styleBox = XposedHelpers.getAdditionalInstanceField(blurView, "nb_genui_stylebox");
                        Object baseStyle = XposedHelpers.getAdditionalInstanceField(blurView, "nb_genui_style");
                        if (styleBox != null && baseStyle != null) {
                            XposedHelpers.callMethod(styleBox, "updateBlur", blurView, blurView.getContext(), baseStyle);
                        } else {
                            clearBlurInfo(blurView);
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        XposedHelpers.setAdditionalInstanceField(blurView, "nb_restoring_blur", Boolean.FALSE);
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": applyOrRestoreGenUiBlurView error: " + t.getMessage());
        }
    }

    private static void applyOrRestoreGenUiGuidingView(View glView) {
        if (glView == null) return;
        try {
            boolean shouldCustom = shouldCustomizeCatBrief() && isBriefViewCollapsed(glView);
            if (shouldCustom) {
                ConfigManager.Config cfg = sConfig;
                int targetArgb = resolveTargetArgbForCategory(ConfigManager.CAT_BRIEF);
                int[] grad = cfg != null ? cfg.resolveGradientArgbForCategory(ConfigManager.CAT_BRIEF) : null;
                boolean useBlur = (cfg != null && cfg.styleMode == ConfigManager.STYLE_BLUR);
                float density = glView.getContext().getResources().getDisplayMetrics().density;
                float cornerPx = 34.0f * (density > 0f ? density : 3.0f);

                XposedHelpers.setAdditionalInstanceField(glView, "nb_genui_gl_applied", Boolean.TRUE);
                glView.setAlpha(1.0f);
                setCardViewColorAndStyle(glView, targetArgb, useBlur, cornerPx, grad);
            } else {
                Object applied = XposedHelpers.getAdditionalInstanceField(glView, "nb_genui_gl_applied");
                if (Boolean.TRUE.equals(applied)) {
                    XposedHelpers.setAdditionalInstanceField(glView, "nb_genui_gl_applied", Boolean.FALSE);
                    glView.setBackground(null);
                    clearBlurInfo(glView);
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Checks if the NowBar capsule is currently in its small/collapsed state on the Lockscreen
     * (!isExpanded, cardState == 0). Keeps color pre-applied at all times (including screen-off/AOD)
     * so that upon screen-on it appears instantly with 0 ms delay.
     */
    private static boolean isCollapsedLockscreenCapsule(View card) {
        boolean isExpanded = getBooleanFieldOrGetter(card, "l0", "getExpanded", false);
        int cardState = getIntFieldOrGetter(card, "j0", "getCardState", 0);
        return !isExpanded && cardState == 0;
    }

    private static String collectCardText(View view) {
        if (view == null) return "";
        StringBuilder sb = new StringBuilder();
        if (view instanceof TextView) {
            CharSequence txt = ((TextView) view).getText();
            if (txt != null && txt.length() > 0) {
                sb.append(txt).append(" ");
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            int count = Math.min(vg.getChildCount(), 15);
            for (int i = 0; i < count; i++) {
                sb.append(collectCardText(vg.getChildAt(i)));
            }
        }
        return sb.toString();
    }

    private static String extractPackageFromNotiId(String notiId) {
        if (notiId == null || notiId.isEmpty()) return "";
        String[] parts = notiId.split("\\|");
        if (parts.length > 1) return parts[1].toLowerCase().trim();
        return "";
    }

    private static String extractIdFromNotiId(String notiId) {
        if (notiId == null || notiId.isEmpty()) return "";
        String[] parts = notiId.split("\\|");
        if (parts.length > 2) return parts[2].toLowerCase().trim();
        return "";
    }

    private static String extractTagFromNotiId(String notiId) {
        if (notiId == null || notiId.isEmpty()) return "";
        String[] parts = notiId.split("\\|");
        if (parts.length > 3) return parts[3].toLowerCase().trim();
        return "";
    }

    private static String resolveCategoryForCard(View card) {
        if (card == null) return ConfigManager.CAT_OTHER_ONGOING;
        String clsName = card.getClass().getName();
        Object item = getObjectFieldOrGetter(card, "g0", "getItem");
        if (item == null) {
            item = XposedHelpers.getAdditionalInstanceField(card, "nb_cached_item");
        }

        String pkg = "";
        String notiId = "";
        String notiPkg = "";
        String notiNumericId = "";
        String notiTag = "";
        String actualPkg = "";
        String nowBarKey = "";
        String primaryInfo = "";
        String secondaryInfo = "";
        String nowbarPrimaryInfo = "";
        String nowbarSecondaryInfo = "";
        int viewStyle = -1;
        boolean isCall = false;

        if (item != null) {
            Object pkgObj = callMethodNoArgs(item, "getNowBarPackage");
            if (pkgObj instanceof String) pkg = ((String) pkgObj).toLowerCase().trim();

            Object notiIdObj = callMethodNoArgs(item, "getNotiID");
            if (notiIdObj instanceof String) {
                notiId = ((String) notiIdObj).trim();
                notiPkg = extractPackageFromNotiId(notiId);
                notiNumericId = extractIdFromNotiId(notiId);
                notiTag = extractTagFromNotiId(notiId);
            }

            Object keyObj = callMethodNoArgs(item, "getNowBarKey");
            if (keyObj instanceof String) nowBarKey = ((String) keyObj).toLowerCase().trim();

            Object pInfoObj = callMethodNoArgs(item, "getPrimaryInfo");
            if (pInfoObj instanceof String) primaryInfo = ((String) pInfoObj).toLowerCase().trim();

            Object sInfoObj = callMethodNoArgs(item, "getSecondaryInfo");
            if (sInfoObj instanceof String) secondaryInfo = ((String) sInfoObj).toLowerCase().trim();

            Object npInfoObj = callMethodNoArgs(item, "getNowbarPrimaryInfo");
            if (npInfoObj instanceof String) nowbarPrimaryInfo = ((String) npInfoObj).toLowerCase().trim();

            Object nsInfoObj = callMethodNoArgs(item, "getNowbarSecondaryInfo");
            if (nsInfoObj instanceof String) nowbarSecondaryInfo = ((String) nsInfoObj).toLowerCase().trim();

            Object extraDataObj = callMethodNoArgs(item, "getExtraData");
            if (extraDataObj instanceof android.os.Bundle) {
                android.os.Bundle b = (android.os.Bundle) extraDataObj;
                String ap = b.getString("nowbar_key_actual_package_name");
                if (ap != null) actualPkg = ap.toLowerCase().trim();
                int callType = b.getInt("nowbar_key_call_type", 0);
                if (callType == 1 || callType == 2) isCall = true;
            }

            Object styleObj = callMethodNoArgs(item, "getNowBarViewStyle");
            if (styleObj instanceof Integer) viewStyle = (Integer) styleObj;

            Object callObj = callMethodNoArgs(item, "isCall");
            if (callObj instanceof Boolean && (Boolean) callObj) isCall = true;
        }

        String cardText = collectCardText(card).toLowerCase();
        String allMeta = (pkg + " " + notiId + " " + notiPkg + " " + notiTag + " " + actualPkg + " "
                + nowBarKey + " " + primaryInfo + " " + secondaryInfo + " "
                + nowbarPrimaryInfo + " " + nowbarSecondaryInfo + " " + cardText).trim();

        String category;

        // 1. Media Capsule (Spotify, YouTube Music, Samsung Music, etc.)
        if (clsName.endsWith("NowBarMediaCardView") || viewStyle == 1 || (item != null && callMethodNoArgs(item, "getFaceWidgetMediaData") != null)) {
            category = ConfigManager.CAT_MEDIA;
        }
        // 2. Battery & Charging
        else if (clsName.endsWith("NowBarBatteryCardView") || viewStyle == 4 || viewStyle == 6) {
            category = ConfigManager.CAT_BATTERY;
        }
        // 3. Now Brief, Galaxy AI, Smart Suggestions, App Search
        else if (clsName.endsWith("NowBarBriefCardView")
                || clsName.endsWith("NowBarAppSearchCardView")
                || clsName.endsWith("NowBarExternalViewCardView")
                || viewStyle == 3 || viewStyle == 7 || viewStyle == 8
                || nowBarKey.contains("now_brief")
                || allMeta.contains("smartsuggestions")
                || allMeta.contains("aisuggestion")
                || allMeta.contains("brief")
                || allMeta.contains("intelligence")
                || allMeta.contains("rubin")
                || allMeta.contains("mcfds")
                || allMeta.contains("bixby")
                || allMeta.contains("gemini")
                || allMeta.contains("bard")) {
            category = ConfigManager.CAT_BRIEF;
        }
        // 4. Calls
        else if (clsName.endsWith("NowBarCallCardView") || isCall
                || notiPkg.contains("incallui") || notiPkg.contains("dialer") || notiPkg.contains("telecom")
                || actualPkg.contains("incallui") || actualPkg.contains("dialer")
                || allMeta.contains("incallui") || allMeta.contains("dialer") || allMeta.contains("llamada")) {
            category = ConfigManager.CAT_CALL;
        }
        // 5. Modes & Routines
        else if (clsName.endsWith("NowBarModeCardView") || viewStyle == 2
                || notiPkg.contains("routines") || actualPkg.contains("routines")
                || allMeta.contains("routin") || allMeta.contains("modes") || allMeta.contains("rutina")) {
            category = ConfigManager.CAT_MODES;
        }
        // 6. Flashlight (Linterna) - Matches Samsung xz4.e0 tag check or ID 4660 or Linterna label
        else if (notiTag.equals("flashlight")
                || notiNumericId.equals("4660")
                || allMeta.contains("flashlight")
                || allMeta.contains("linterna")
                || allMeta.contains("torch")) {
            category = ConfigManager.CAT_FLASHLIGHT;
        }
        // 7. Voice Recorder (Grabadora de Voz / Notas de audio)
        else if (notiPkg.contains("voicenote") || notiPkg.contains("voicerecorder") || notiPkg.contains("soundrecorder")
                || actualPkg.contains("voicenote") || actualPkg.contains("voicerecorder")
                || allMeta.contains("voicenote") || allMeta.contains("voicerecorder") || allMeta.contains("soundrecorder")
                || allMeta.contains("grabadora") || allMeta.contains("grabacion") || allMeta.contains("grabación")
                || allMeta.contains("nota de voz") || allMeta.contains("voice_note")) {
            category = ConfigManager.CAT_RECORDER;
        }
        // 8. Sports, Maps & Samsung Health
        else if (nowbarPrimaryInfo.contains("sports") || nowbarPrimaryInfo.contains("commute") || nowbarPrimaryInfo.contains("finance")
                || notiPkg.contains("shealth") || notiPkg.contains("health") || notiPkg.contains("fitness") || notiPkg.contains("maps")
                || actualPkg.contains("shealth") || actualPkg.contains("health") || actualPkg.contains("maps")
                || allMeta.contains("shealth") || allMeta.contains("samsung health") || allMeta.contains("google maps")
                || allMeta.contains("sports") || allMeta.contains("navegación") || allMeta.contains("navigation")) {
            category = ConfigManager.CAT_SPORTS_MAPS_HEALTH;
        }
        // 9. Clock / Timer / Stopwatch / Alarm
        else if (notiPkg.contains("clockpackage") || notiPkg.contains("deskclock")
                || actualPkg.contains("clockpackage") || actualPkg.contains("deskclock")
                || pkg.contains("clock") || pkg.contains("reloj") || pkg.contains("timer") || pkg.contains("temporizador") || pkg.contains("cronometro") || pkg.contains("stopwatch")
                || allMeta.contains("clockpackage") || allMeta.contains("deskclock")
                || allMeta.contains("temporizador") || allMeta.contains("timer")
                || allMeta.contains("cronometro") || allMeta.contains("cronómetro") || allMeta.contains("stopwatch")
                || allMeta.contains("reloj") || allMeta.contains("alarma") || allMeta.contains("alarm")) {
            category = ConfigManager.CAT_TIMER_CLOCK;
        }
        // 10. Other Ongoing Activities (Contact cards, Interpreter, Smart View, etc.)
        else {
            category = ConfigManager.CAT_OTHER_ONGOING;
        }

        XposedBridge.log(TAG + ": Card " + clsName.substring(clsName.lastIndexOf('.') + 1)
                + " -> [" + category + "] (pkg=" + notiPkg + ", tag=" + notiTag + ", app=" + pkg
                + ", actualPkg=" + actualPkg + ", style=" + viewStyle + ")");
        return category;
    }

    private static void applyOrRestoreCardBackground(View card) {
        if (card == null) return;
        try {
            ConfigManager.Config cfg = sConfig;
            String categoryId = resolveCategoryForCard(card);
            ConfigManager.CategoryConfig catCfg = cfg != null ? cfg.getCategory(categoryId) : null;

            boolean shouldCustomize = cfg != null
                    && cfg.masterEnabled
                    && catCfg != null
                    && catCfg.enabled
                    && isCollapsedLockscreenCapsule(card);

            if (!shouldCustomize) {
                restoreOriginalCardBackgroundIfNeeded(card);
                return;
            }

            int targetArgb = resolveTargetArgbForCategory(categoryId);
            int[] grad = cfg.resolveGradientArgbForCategory(categoryId);
            boolean useBlur = (cfg.styleMode == ConfigManager.STYLE_BLUR);
            float radius = getFloatFieldOrGetter(card, "w0", "getCardRadius", 100.0f);
            if (radius <= 0f) radius = 100.0f;

            String clsName = card.getClass().getName();
            XposedHelpers.setAdditionalInstanceField(card, "nb_custom_applied", Boolean.TRUE);

            if (clsName.endsWith("NowBarMediaCardView")) {
                applyToMediaCard(card, targetArgb, useBlur, radius, grad);
            } else {
                applyToStandardCard(card, clsName, targetArgb, useBlur, radius, grad);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": applyOrRestoreCardBackground error: " + t.getMessage());
        }
    }

    private static void applyToMediaCard(View card, int targetArgb, boolean useBlur, float radius, int[] grad) {
        if (Build.VERSION.SDK_INT >= 23) {
            card.setForeground(null);
        }
        View p1 = getViewField(card, "p1"); // stack_background_cardview
        View o1 = getViewField(card, "o1"); // nowbar_cardview_background
        View v1 = getViewField(card, "v1"); // background_shader_view (MediaLightingView)
        View w1 = getViewField(card, "w1"); // background_shader_parent

        // Hide the animated shader background while collapsed so custom color is clean,
        // without touching album art (l1/m1), title text (n1), wave (a2), or action buttons (L1/M1).
        if (v1 != null) {
            v1.setAlpha(0.0f);
        }
        if (w1 != null) {
            w1.setAlpha(0.0f);
        }

        if (p1 != null && p1.getVisibility() == View.VISIBLE) {
            if (Build.VERSION.SDK_INT >= 23) {
                p1.setForeground(null);
            }
            setCardViewColorAndStyle(p1, targetArgb, useBlur, radius, grad);
            if (o1 != null) {
                if (Build.VERSION.SDK_INT >= 23) {
                    o1.setForeground(null);
                }
                clearBlurInfo(o1);
                setCardBackgroundColor(o1, Color.TRANSPARENT);
            }
        } else {
            if (p1 != null) {
                if (Build.VERSION.SDK_INT >= 23) {
                    p1.setForeground(null);
                }
                clearBlurInfo(p1);
                setCardBackgroundColor(p1, Color.TRANSPARENT);
            }
            if (o1 != null) {
                if (Build.VERSION.SDK_INT >= 23) {
                    o1.setForeground(null);
                }
                setCardViewColorAndStyle(o1, targetArgb, useBlur, radius, grad);
            }
        }
    }

    private static void applyToStandardCard(View card, String clsName, int targetArgb, boolean useBlur, float radius, int[] grad) {
        View cardContainer = getViewFieldOrGetter(card, "J", "getCardViewContainer");
        View mpsmContainer = getViewFieldOrGetter(card, "K", "getCardViewMpsmBackground");
        View guidingBg = getViewFieldOrGetter(card, "F", "getCardBGViewForGuidingEffect");
        View gradientBg = getViewFieldOrGetter(card, "x0", "getGradientBgView");
        View outerGlow = getViewFieldOrGetter(card, "G", "getCardOuterGlowView");

        if (guidingBg != null) {
            guidingBg.setAlpha(0.0f);
        }
        if (gradientBg != null) {
            gradientBg.setAlpha(0.0f);
            gradientBg.setVisibility(View.GONE);
        }
        if (outerGlow != null) {
            outerGlow.setAlpha(0.0f);
        }
        if (mpsmContainer != null && mpsmContainer.getVisibility() == View.VISIBLE) {
            setCardViewColorAndStyle(mpsmContainer, targetArgb, useBlur, radius, grad);
        }
        if (cardContainer != null) {
            setCardViewColorAndStyle(cardContainer, targetArgb, useBlur, radius, grad);
        }

        if (clsName.endsWith("NowBarBatteryCardView")) {
            // Subtle progress fill alpha so the user's chosen capsule background color dominates cleanly
            View progressBar = getViewField(card, "q1"); // battery_progressbar
            if (progressBar != null) {
                progressBar.setAlpha(0.28f);
            }
        } else if (clsName.endsWith("NowBarOngoingCardView")) {
            // Ensure normal_text_container (H1) and chronometer view (s1) are always visible (alpha 1.0)
            View normalTextContainer = getViewField(card, "H1");
            if (normalTextContainer != null && normalTextContainer.getAlpha() < 1.0f) {
                normalTextContainer.setAlpha(1.0f);
            }
            View chronometerView = getViewField(card, "s1");
            if (chronometerView != null && chronometerView.getAlpha() < 1.0f) {
                chronometerView.setAlpha(1.0f);
            }

            // Hide AI Nudge (e0) & AI Presence (J1/K1) shader overlays while collapsed so custom color is clean
            View nudgeContainer = getViewFieldOrGetter(card, "e0", "getNudgeContianer");
            View aiPresenceContainer = getViewField(card, "J1");
            View aiPresenceView = getViewField(card, "K1");
            if (nudgeContainer != null) nudgeContainer.setAlpha(0.0f);
            if (aiPresenceContainer != null) aiPresenceContainer.setAlpha(0.0f);
            if (aiPresenceView != null) {
                try {
                    aiPresenceView.animate().cancel();
                } catch (Throwable ignored) {}
                aiPresenceView.setAlpha(0.0f);
            }
        } else if (clsName.endsWith("NowBarBriefCardView")) {
            View briefBg = getViewField(card, "s1"); // nowbar_cardview_background_for_now_brief
            if (briefBg != null) {
                setCardViewColorAndStyle(briefBg, targetArgb, useBlur, radius, grad);
            }
            updateChildGenUiViews(card);
        }
    }

    private static boolean updateChildGenUiViews(View rootCard) {
        boolean foundBlurView = false;
        List<View> blurViews;
        synchronized (ACTIVE_GENUI_BLUR_VIEWS) {
            blurViews = new ArrayList<>(ACTIVE_GENUI_BLUR_VIEWS);
        }
        for (View bv : blurViews) {
            if (bv != null && bv.isAttachedToWindow() && findEnclosingNowBarCard(bv) == rootCard) {
                foundBlurView = true;
                applyOrRestoreGenUiBlurView(bv);
            }
        }
        List<View> glViews;
        synchronized (ACTIVE_GENUI_GUIDING_VIEWS) {
            glViews = new ArrayList<>(ACTIVE_GENUI_GUIDING_VIEWS);
        }
        for (View gv : glViews) {
            if (gv != null && gv.isAttachedToWindow() && findEnclosingNowBarCard(gv) == rootCard) {
                applyOrRestoreGenUiGuidingView(gv);
            }
        }
        return foundBlurView;
    }

    private static void restoreOriginalCardBackgroundIfNeeded(View card) {
        Object applied = XposedHelpers.getAdditionalInstanceField(card, "nb_custom_applied");
        if (!Boolean.TRUE.equals(applied)) {
            return;
        }
        XposedHelpers.setAdditionalInstanceField(card, "nb_custom_applied", Boolean.FALSE);
        XposedHelpers.setAdditionalInstanceField(card, "nb_restoring", Boolean.TRUE);
        try {
            String clsName = card.getClass().getName();
            if (clsName.endsWith("NowBarMediaCardView")) {
                View v1 = getViewField(card, "v1");
                View w1 = getViewField(card, "w1");
                View p1 = getViewField(card, "p1");
                View o1 = getViewField(card, "o1");

                boolean isExpanded = Boolean.TRUE.equals(callMethodNoArgs(card, "isExpanded"));
                Object stateObj = callMethodNoArgs(card, "getCardState");
                boolean isNotNormal = (stateObj instanceof Integer && (Integer) stateObj != 0);

                if (isExpanded || isNotNormal) {
                    // The card is expanding into full widget view or opening settings.
                    // Keep the animated lighting shader hidden and foreground null so NO light rectangle flashes!
                    if (v1 != null) v1.setAlpha(0.0f);
                    if (w1 != null) w1.setAlpha(0.0f);
                    if (p1 != null && Build.VERSION.SDK_INT >= 23) {
                        p1.setForeground(null);
                    }
                    if (Build.VERSION.SDK_INT >= 23) {
                        card.setForeground(null);
                    }
                    if (o1 != null) {
                        if (Build.VERSION.SDK_INT >= 23) {
                            o1.setForeground(null);
                        }
                        restoreGradientIfNeeded(o1);
                    }
                    try {
                        XposedHelpers.callMethod(card, "h", 0.0f);
                    } catch (Throwable ignored) {}
                    return;
                }

                if (v1 != null) v1.setAlpha(1.0f);
                if (w1 != null) w1.setAlpha(1.0f);
                if (p1 != null) {
                    restoreGradientIfNeeded(p1);
                    clearBlurInfo(p1);
                    setCardBackgroundColor(p1, Color.TRANSPARENT);
                }
                if (o1 != null) {
                    restoreGradientIfNeeded(o1);
                }
                Object item = getObjectFieldOrGetter(card, "g0", "getItem");
                float curRadius = getFloatFieldOrGetter(card, "w0", "getNormalCardRadius", 100.0f);
                if (item != null) {
                    try {
                        XposedHelpers.callMethod(card, "i0", item, curRadius);
                    } catch (Throwable ignored) {}
                }
                try {
                    XposedHelpers.callMethod(card, "h", 0.0f);
                } catch (Throwable ignored) {}
                try {
                    XposedHelpers.callMethod(card, "E");
                } catch (Throwable ignored) {}
            } else {
                View cardContainer = getViewFieldOrGetter(card, "J", "getCardViewContainer");
                View mpsmContainer = getViewFieldOrGetter(card, "K", "getCardViewMpsmBackground");
                restoreGradientIfNeeded(cardContainer);
                restoreGradientIfNeeded(mpsmContainer);

                View guidingBg = getViewFieldOrGetter(card, "F", "getCardBGViewForGuidingEffect");
                View gradientBg = getViewFieldOrGetter(card, "x0", "getGradientBgView");
                View outerGlow = getViewFieldOrGetter(card, "G", "getCardOuterGlowView");
                if (guidingBg != null) guidingBg.setAlpha(1.0f);
                if (gradientBg != null) {
                    gradientBg.setVisibility(View.VISIBLE);
                    gradientBg.setAlpha(1.0f);
                }
                if (outerGlow != null) outerGlow.setAlpha(1.0f);

                if (clsName.endsWith("NowBarBatteryCardView")) {
                    View progressBar = getViewField(card, "q1");
                    if (progressBar != null) progressBar.setAlpha(1.0f);
                } else if (clsName.endsWith("NowBarOngoingCardView")) {
                    View normalTextContainer = getViewField(card, "H1");
                    if (normalTextContainer != null) normalTextContainer.setAlpha(1.0f);
                    View chronometerView = getViewField(card, "s1");
                    if (chronometerView != null) chronometerView.setAlpha(1.0f);

                    View nudgeContainer = getViewFieldOrGetter(card, "e0", "getNudgeContianer");
                    View aiPresenceContainer = getViewField(card, "J1");
                    View aiPresenceView = getViewField(card, "K1");
                    if (nudgeContainer != null) nudgeContainer.setAlpha(1.0f);
                    if (aiPresenceContainer != null) aiPresenceContainer.setAlpha(1.0f);
                    if (aiPresenceView != null) aiPresenceView.setAlpha(1.0f);
                } else if (clsName.endsWith("NowBarBriefCardView")) {
                    View briefBg = getViewField(card, "s1");
                    restoreGradientIfNeeded(briefBg);
                    updateChildGenUiViews(card);
                    try {
                        XposedHelpers.callMethod(card, "l0");
                    } catch (Throwable ignored) {}
                }

                try {
                    XposedHelpers.callMethod(card, "E");
                } catch (Throwable ignored) {}
            }
        } finally {
            XposedHelpers.setAdditionalInstanceField(card, "nb_restoring", Boolean.FALSE);
        }
    }

    private static void setCardViewColorAndStyle(View cardView, int argbColor, boolean useBlur, float radius) {
        setCardViewColorAndStyle(cardView, argbColor, useBlur, radius, null);
    }

    private static void setCardViewColorAndStyle(View cardView, int argbColor, boolean useBlur, float radius, int[] grad) {
        if (cardView == null) return;
        if (grad != null && grad.length >= 2) {
            setCardViewGradientAndStyle(cardView, grad[0], grad[1], useBlur, radius);
            return;
        }
        if (useBlur) {
            setCardBackgroundColor(cardView, argbColor, radius);
            applyBlurInfo(cardView, radius, argbColor);
        } else {
            clearBlurInfo(cardView);
            setCardBackgroundColor(cardView, argbColor, radius);
        }
    }

    /** Paints a two-stop LEFT→RIGHT gradient as the view background, saving the original drawable. */
    private static void setCardViewGradientAndStyle(View view, int startArgb, int endArgb, boolean useBlur, float radius) {
        if (view == null) return;
        try {
            Object applied = XposedHelpers.getAdditionalInstanceField(view, "nb_grad_applied");
            if (!Boolean.TRUE.equals(applied)) {
                XposedHelpers.setAdditionalInstanceField(view, "nb_orig_bg", view.getBackground());
            }
            try {
                Method m = view.getClass().getMethod("setCardBackgroundColor", int.class);
                m.invoke(view, Color.TRANSPARENT);
            } catch (Throwable ignored) {}

            GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{startArgb, endArgb});
            gd.setCornerRadius(radius > 0f ? radius : 100f);
            view.setBackground(gd);
            view.setClipToOutline(true);
            XposedHelpers.setAdditionalInstanceField(view, "nb_grad_applied", Boolean.TRUE);

            if (useBlur) {
                applyBlurInfoNoFallback(view, radius, Color.TRANSPARENT);
            } else {
                clearBlurInfo(view);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": setCardViewGradientAndStyle error: " + t.getMessage());
        }
    }

    /** Restores the original (CardView) background drawable if a gradient was installed. */
    private static void restoreGradientIfNeeded(View view) {
        if (view == null) return;
        try {
            Object applied = XposedHelpers.getAdditionalInstanceField(view, "nb_grad_applied");
            if (Boolean.TRUE.equals(applied)) {
                Object orig = XposedHelpers.getAdditionalInstanceField(view, "nb_orig_bg");
                view.setBackground(orig instanceof android.graphics.drawable.Drawable
                        ? (android.graphics.drawable.Drawable) orig : null);
                XposedHelpers.setAdditionalInstanceField(view, "nb_grad_applied", Boolean.FALSE);
                XposedHelpers.setAdditionalInstanceField(view, "nb_orig_bg", null);
            }
        } catch (Throwable ignored) {}
    }

    private static void applyBlurInfoNoFallback(View view, float cornerRadius, int argbColor) {
        if (view == null) return;
        try {
            Class<?> builderCls = Class.forName("android.view.SemBlurInfo$Builder");
            Constructor<?> ctor = builderCls.getConstructor(int.class);
            Object builder = ctor.newInstance(0); // BLUR_MODE_WINDOW = 0
            try {
                Method setPreset = builderCls.getMethod("setColorCurvePreset", int.class);
                setPreset.invoke(builder, 54);
            } catch (Throwable ignored) {}
            try {
                Method setCorner = builderCls.getMethod("setBackgroundCornerRadius", float.class);
                setCorner.invoke(builder, cornerRadius);
            } catch (Throwable ignored) {}
            try {
                Method setBgColor = builderCls.getMethod("setBackgroundColor", int.class);
                setBgColor.invoke(builder, argbColor);
            } catch (Throwable ignored) {}
            Object blurInfo = builderCls.getMethod("build").invoke(builder);
            Class<?> blurInfoCls = Class.forName("android.view.SemBlurInfo");
            View.class.getMethod("semSetBlurInfo", blurInfoCls).invoke(view, blurInfo);
        } catch (Throwable ignored) {}
    }

    private static void setCardBackgroundColor(View view, int color) {
        setCardBackgroundColor(view, color, 100f);
    }

    private static void setCardBackgroundColor(View view, int color, float radiusPx) {
        if (view == null) return;
        restoreGradientIfNeeded(view);
        try {
            Method m = view.getClass().getMethod("setCardBackgroundColor", int.class);
            m.invoke(view, color);
            return;
        } catch (Throwable ignored) {}
        try {
            if (color == Color.TRANSPARENT) {
                view.setBackground(null);
                return;
            }
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(color);
            gd.setCornerRadius(radiusPx > 0f ? radiusPx : 100f);
            view.setBackground(gd);
            view.setClipToOutline(true);
        } catch (Throwable ignored) {}
    }

    private static void clearBlurInfo(View view) {
        if (view == null) return;
        try {
            Class<?> blurInfoCls = Class.forName("android.view.SemBlurInfo");
            Method semSetBlur = View.class.getMethod("semSetBlurInfo", blurInfoCls);
            semSetBlur.invoke(view, new Object[]{null});
        } catch (Throwable ignored) {}
    }

    private static void applyBlurInfo(View view, float cornerRadius, int argbColor) {
        if (view == null) return;
        try {
            Class<?> builderCls = Class.forName("android.view.SemBlurInfo$Builder");
            Constructor<?> ctor = builderCls.getConstructor(int.class);
            Object builder = ctor.newInstance(0); // BLUR_MODE_WINDOW = 0

            try {
                Method setPreset = builderCls.getMethod("setColorCurvePreset", int.class);
                setPreset.invoke(builder, 54);
            } catch (Throwable ignored) {}

            try {
                Method setCorner = builderCls.getMethod("setBackgroundCornerRadius", float.class);
                setCorner.invoke(builder, cornerRadius);
            } catch (Throwable ignored) {}

            try {
                Method setBgColor = builderCls.getMethod("setBackgroundColor", int.class);
                setBgColor.invoke(builder, argbColor);
            } catch (Throwable ignored) {}

            Method buildMethod = builderCls.getMethod("build");
            Object blurInfo = buildMethod.invoke(builder);

            Class<?> blurInfoCls = Class.forName("android.view.SemBlurInfo");
            Method semSetBlur = View.class.getMethod("semSetBlurInfo", blurInfoCls);
            semSetBlur.invoke(view, blurInfo);
        } catch (Throwable t) {
            // Fallback if SemBlurInfo reflection fails
            setCardBackgroundColor(view, argbColor, cornerRadius);
        }
    }

    // --- Reflection Helpers searching class hierarchy ---

    private static Field findFieldHierarchy(Class<?> cls, String name) {
        Class<?> curr = cls;
        while (curr != null && curr != Object.class) {
            try {
                Field f = curr.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                curr = curr.getSuperclass();
            }
        }
        return null;
    }

    private static Object getFieldValue(Object obj, String fieldName) {
        if (obj == null) return null;
        try {
            Field f = findFieldHierarchy(obj.getClass(), fieldName);
            if (f != null) return f.get(obj);
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object callMethodNoArgs(Object obj, String methodName) {
        if (obj == null) return null;
        try {
            Method m = obj.getClass().getMethod(methodName);
            m.setAccessible(true);
            return m.invoke(obj);
        } catch (Throwable ignored) {}
        return null;
    }

    private static View getViewField(Object obj, String fieldName) {
        Object val = getFieldValue(obj, fieldName);
        return (val instanceof View) ? (View) val : null;
    }

    private static View getViewFieldOrGetter(Object obj, String fieldName, String getterName) {
        Object val = getFieldValue(obj, fieldName);
        if (val instanceof View) return (View) val;
        val = callMethodNoArgs(obj, getterName);
        if (val instanceof View) return (View) val;
        return null;
    }

    private static Object getObjectFieldOrGetter(Object obj, String fieldName, String getterName) {
        Object val = getFieldValue(obj, fieldName);
        if (val != null) return val;
        return callMethodNoArgs(obj, getterName);
    }

    private static boolean getBooleanFieldOrGetter(Object obj, String fieldName, String getterName, boolean defVal) {
        Object val = getFieldValue(obj, fieldName);
        if (val instanceof Boolean) return (Boolean) val;
        val = callMethodNoArgs(obj, getterName);
        if (val instanceof Boolean) return (Boolean) val;
        return defVal;
    }

    private static int getIntFieldOrGetter(Object obj, String fieldName, String getterName, int defVal) {
        Object val = getFieldValue(obj, fieldName);
        if (val instanceof Integer) return (Integer) val;
        val = callMethodNoArgs(obj, getterName);
        if (val instanceof Integer) return (Integer) val;
        return defVal;
    }

    private static float getFloatFieldOrGetter(Object obj, String fieldName, String getterName, float defVal) {
        Object val = getFieldValue(obj, fieldName);
        if (val instanceof Float) return (Float) val;
        val = callMethodNoArgs(obj, getterName);
        if (val instanceof Float) return (Float) val;
        return defVal;
    }
}
