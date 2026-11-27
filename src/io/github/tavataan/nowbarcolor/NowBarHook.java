package io.github.tavataan.nowbarcolor;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import java.lang.ref.WeakReference;
import java.util.Locale;
import android.widget.ImageView;
import android.widget.TextView;

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
    private static final Set<ClassLoader> LAYER1_CLASSLOADERS = Collections.synchronizedSet(new HashSet<ClassLoader>());
    private static final Set<ClassLoader> LAYER2_CLASSLOADERS = Collections.synchronizedSet(new HashSet<ClassLoader>());
    private static final Set<ClassLoader> PENDING_LAYER2_CLASSLOADERS = Collections.synchronizedSet(new HashSet<ClassLoader>());
    private static final Set<View> ACTIVE_CARDS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<View, Boolean>()));
    private static final Set<View> ACTIVE_GENUI_BLUR_VIEWS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<View, Boolean>()));
    private static final Set<View> ACTIVE_GENUI_GUIDING_VIEWS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<View, Boolean>()));
    private static final Set<View> MEDIA_BACKDROP_SURFACES = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<View, Boolean>()));
    private static final Set<View> MEDIA_PILL_SURFACES = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<View, Boolean>()));

    private static volatile ConfigManager.Config sConfig = null;
    private static volatile WallpaperColorHelper.PaletteResult sLivePalette = null;
    private static volatile boolean sReceiverRegistered = false;
    private static volatile Context sSysUiContext = null;
    private static volatile Handler sMainHandler = null;
    private static volatile long sWakeUntilNs = 0L;
    private static volatile boolean sWakeWatchArmed = false;
    private static volatile long sLastLockDiagPushNs = 0L;
    private static volatile long sLastAodDiagPushNs = 0L;
    private static volatile long sLastExpandedDiagPushNs = 0L;
    private static volatile String sHookTimeContextSource = "NONE";
    private static volatile String sHookMapSource = "NONE";
    private static volatile String sHookMapTarget = "none";
    private static volatile boolean sLayer1Hooked = false;
    private static volatile boolean sSemBlurHookInstalled = false;
    private static volatile boolean sLayer2CoreHooked = false;
    private static volatile int sLayer2WakeCount = 0;
    private static volatile int sLayer3Count = 0;
    private static volatile String sLastCardSummary = "";
    private static volatile String sLastLockSummary = "";
    private static volatile String sLastAodSummary = "";
    private static volatile String sLastAodLeak = "none";
    private static volatile String sLastExpandedLeak = "none";
    private static volatile String sLastLockTreeDump = "";
    private static volatile String sLastExpandedTreeDump = "";
    private static volatile String sLastAodTreeDump = "";
    private static volatile HookMap sHookMap = null;
    private static volatile NowBarCardAdapter sAdapter = NowBarAdapterFactory.fallbackForSdk();

    private static NowBarCardAdapter activeAdapter() {
        NowBarCardAdapter adapter = sAdapter;
        return adapter != null ? adapter : NowBarAdapterFactory.fallbackForSdk();
    }

    // Main-thread request state; slow provider/wallpaper work runs on one worker.
    private static Handler sIoHandler;
    private static int sConfigVersion;
    private static boolean sWallpaperRequested;
    private static boolean sRefreshPosted;
    private static boolean sLoadRunning;
    private static boolean sReloadAfterFlight;
    private static final Runnable RELOAD = new Runnable() {
        @Override public void run() {
            final Context context = sSysUiContext;
            if (context == null) return;
            if (sLoadRunning) {
                sReloadAfterFlight = true;
                return;
            }
            sLoadRunning = true;
            final int version = sConfigVersion;
            final boolean wallpaper = sWallpaperRequested || sLivePalette == null;
            sWallpaperRequested = false;
            if (sIoHandler == null) {
                HandlerThread worker = new HandlerThread("NowBarColor-IO", android.os.Process.THREAD_PRIORITY_BACKGROUND);
                worker.start();
                sIoHandler = new Handler(worker.getLooper());
            }
            sIoHandler.post(new Runnable() {
                @Override public void run() {
                    ConfigManager.Config loadedConfig = null;
                    WallpaperColorHelper.PaletteResult loadedPalette = null;
                    try {
                        loadedConfig = ConfigManager.loadFromSystemSettings(context);
                        if (wallpaper) loadedPalette = WallpaperColorHelper.extractLockWallpaperPalette(context, false);
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": background config load failed: " + t.getMessage());
                    }
                    final ConfigManager.Config config = loadedConfig;
                    final WallpaperColorHelper.PaletteResult palette = loadedPalette;
                    Handler main = getMainHandler();
                    if (main != null) main.post(new Runnable() {
                        @Override public void run() {
                            // A direct broadcast or newer request must win over an older read.
                            if (config != null && version == sConfigVersion) sConfig = config;
                            if (palette != null) sLivePalette = palette;
                            sLoadRunning = false;
                            refreshAllActiveCards();
                            if (sReloadAfterFlight) {
                                sReloadAfterFlight = false;
                                Handler handler = getMainHandler();
                                handler.removeCallbacks(RELOAD);
                                handler.post(RELOAD);
                            }
                        }
                    });
                }
            });
        }
    };

    private static Handler getMainHandler() {
        if (sMainHandler == null) {
            Looper mainLooper = Looper.getMainLooper();
            if (mainLooper != null) {
                sMainHandler = new Handler(mainLooper);
            }
        }
        return sMainHandler;
    }

    /** Track only presentation changes, not animation progress or notification text. */
    private static final class MediaPresentation {
        final MediaLayer stack = new MediaLayer();
        final MediaLayer normal = new MediaLayer();
        boolean initialized;
        boolean collapsed;
        float aodProgress;
        boolean hideBgOnAod;

        boolean capture(View card) {
            boolean nowCollapsed = isCollapsedLockscreenCapsule(card);
            float nowAodProgress = getCardAodProgress(card);
            ConfigManager.Config cfg = sConfig;
            boolean nowHideBgOnAod = cfg != null && cfg.hideBgOnAod;
            boolean changed = !initialized || collapsed != nowCollapsed
                    || hideBgOnAod != nowHideBgOnAod
                    || Math.abs(aodProgress - nowAodProgress) > 0.001f;
            initialized = true;
            collapsed = nowCollapsed;
            aodProgress = nowAodProgress;
            hideBgOnAod = nowHideBgOnAod;
            boolean inspectStackGradient = nowCollapsed && (!nowHideBgOnAod || nowAodProgress < 0.995f);
            boolean inspectBackdrop = shouldCustomizeCatMedia();
            // Evaluate both even when another part has changed.
            NowBarCardAdapter adapter = activeAdapter();
            changed |= stack.capture(adapter.resolveMediaPillSurface(card), inspectStackGradient);
            changed |= normal.capture(adapter.resolveMediaBackdropSurface(card), inspectBackdrop);
            return changed;
        }
    }

    private static final class MediaLayer {
        WeakReference<View> viewRef;
        WeakReference<android.graphics.drawable.Drawable> backgroundRef;
        WeakReference<Object> blurRef;
        int visibility = -1;
        boolean hasAlpha;
        int cardBgColor;

        boolean capture(View view, boolean inspectGradient) {
            View previous = viewRef == null ? null : viewRef.get();
            android.graphics.drawable.Drawable background = view == null ? null : view.getBackground();
            android.graphics.drawable.Drawable oldBackground = backgroundRef == null ? null : backgroundRef.get();
            Object blurInfo = CapsuleStyle.getBlurInfo(view);
            Object oldBlurInfo = blurRef == null ? null : blurRef.get();
            int nextVisibility = view == null ? -1 : view.getVisibility();
            boolean nextAlpha = view != null && view.getAlpha() > 0f;
            int nextCardBg = inspectGradient ? readCardBackgroundColor(view) : 0;
            boolean changed = previous != view || oldBackground != background
                    || oldBlurInfo != blurInfo
                    || visibility != nextVisibility || hasAlpha != nextAlpha
                    || (inspectGradient && cardBgColor != nextCardBg);
            if (previous != view) viewRef = view == null ? null : new WeakReference<>(view);
            if (oldBackground != background) backgroundRef = background == null ? null : new WeakReference<>(background);
            if (oldBlurInfo != blurInfo) blurRef = blurInfo == null ? null : new WeakReference<>(blurInfo);
            visibility = nextVisibility;
            hasAlpha = nextAlpha;
            cardBgColor = nextCardBg;
            return changed || (inspectGradient && CapsuleStyle.needsGradientRepair(view));
        }
    }

    private static int readCardBackgroundColor(View view) {
        if (view == null) return 0;
        Method getter = ReflectionCache.method(view.getClass(), "getCardBackgroundColor");
        if (getter == null) return 0;
        try {
            Object res = getter.invoke(view);
            if (res instanceof android.content.res.ColorStateList) {
                return ((android.content.res.ColorStateList) res).getDefaultColor();
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    /** Coalesce requests. Observes pre-draw state continuously on media or briefly during screen-wake transitions. */
    private static final class CardUpdate implements ViewTreeObserver.OnPreDrawListener,
            View.OnAttachStateChangeListener {
        final WeakReference<View> cardRef;
        ViewTreeObserver observer;
        boolean applying;
        boolean dirty;
        final MediaPresentation media;
        final MediaLayer wakeSurface;

        CardUpdate(View card) {
            cardRef = new WeakReference<>(card);
            boolean isMedia = card.getClass().getName().endsWith("NowBarMediaCardView");
            media = isMedia ? new MediaPresentation() : null;
            wakeSurface = isMedia ? null : new MediaLayer();
            card.addOnAttachStateChangeListener(this);
        }

        void request() {
            View card = cardRef.get();
            if (card == null || applying || !card.isAttachedToWindow()) return;
            boolean wasDirty = dirty;
            dirty = true;
            if (observer != null) {
                if (!wasDirty) card.invalidate();
                return;
            }
            ViewTreeObserver tree = card.getViewTreeObserver();
            if (!tree.isAlive()) return;
            observer = tree;
            tree.addOnPreDrawListener(this);
            card.invalidate();
        }

        void remove() {
            if (observer == null) return;
            ViewTreeObserver tree = observer;
            observer = null;
            if (!tree.isAlive()) {
                View card = cardRef.get();
                tree = card == null ? null : card.getViewTreeObserver();
            }
            if (tree != null && tree.isAlive()) tree.removeOnPreDrawListener(this);
        }

        @Override public boolean onPreDraw() {
            View card = cardRef.get();
            if (card == null || !card.isAttachedToWindow()) {
                remove();
                return true;
            }
            boolean inWakeWatch = isWakeTransitionWatchActive();
            if (media == null && !inWakeWatch) remove();

            boolean changed = false;
            if (media != null) {
                changed = media.capture(card);
            } else if (inWakeWatch && wakeSurface != null) {
                View surface = resolveStandardWakeSurface(card);
                if (surface != null) {
                    changed = wakeSurface.capture(surface, true);
                }
            }
            if (!dirty && !changed) return true;
            dirty = false;
            applying = true;
            try { applyOrRestoreCardBackground(card); }
            finally {
                applying = false;
                // Ignore our own drawable changes on the next frame.
                if (media != null) media.capture(card);
                else if (inWakeWatch && wakeSurface != null) {
                    View surface = resolveStandardWakeSurface(card);
                    if (surface != null) wakeSurface.capture(surface, true);
                }
            }
            return true;
        }

        @Override public void onViewAttachedToWindow(View view) { request(); }
        @Override public void onViewDetachedFromWindow(View view) { remove(); }
    }

    private static View resolveStandardWakeSurface(View card) {
        if (card == null) return null;
        NowBarCardAdapter adapter = activeAdapter();
        if (card.getClass().getName().endsWith("NowBarBriefCardView")) {
            View briefBg = adapter.resolveBriefBackgroundView(card);
            if (briefBg != null) return briefBg;
        }
        return adapter.resolveCardContainer(card);
    }

    private static boolean isWakeTransitionWatchActive() {
        if (!sWakeWatchArmed) return false;
        if ((sWakeUntilNs - System.nanoTime()) > 0L) {
            return true;
        }
        sWakeWatchArmed = false;
        return false;
    }

    private static void armWakeTransitionWatch(long durationMs) {
        sWakeUntilNs = System.nanoTime() + durationMs * 1_000_000L;
        sWakeWatchArmed = true;
    }

    private static void requestCardUpdate(final View card) {
        if (card == null) return;
        Handler handler = getMainHandler();
        if (handler == null) return;
        if (Looper.myLooper() != handler.getLooper()) {
            handler.post(new Runnable() {
                @Override public void run() { requestCardUpdate(card); }
            });
            return;
        }
        if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(card, "nb_restoring"))) return;
        CardUpdate update = (CardUpdate) XposedHelpers.getAdditionalInstanceField(card, "nb_update");
        if (update == null) {
            update = new CardUpdate(card);
            XposedHelpers.setAdditionalInstanceField(card, "nb_update", update);
            ACTIVE_CARDS.add(card);
        }
        ensureSystemUiListeners(card.getContext());
        update.request();
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if ("io.github.tavataan.nowbarcolor".equals(lpparam.packageName)) {
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
                                    || "com.samsung.android.app.facewidget.presentation.NowBarMediaCardView".equals(name)
                                    || "com.samsung.android.app.facewidget.presentation.NowBarOngoingCardView".equals(name)
                                    || "com.samsung.android.app.facewidget.presentation.NowBarExternalViewCardView".equals(name)
                                    || "com.samsung.android.app.facewidget.utils.NowBarCardStackView".equals(name)) {
                                Class<?> cls = (Class<?>) param.getResult();
                                if (cls != null) {
                                    final ClassLoader cl = cls.getClassLoader();
                                    if (cl != null && (!LAYER1_CLASSLOADERS.contains(cl) || !LAYER2_CLASSLOADERS.contains(cl))) {
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
            Class<?> mainAct = XposedHelpers.findClassIfExists("io.github.tavataan.nowbarcolor.MainActivity", cl);
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

    private static Context resolveEarlyContext() {
        if (sSysUiContext != null) {
            if ("NONE".equals(sHookTimeContextSource)) sHookTimeContextSource = "sSysUiContext";
            return sSysUiContext;
        }
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Method curApp = at.getMethod("currentApplication");
            Object app = curApp.invoke(null);
            if (app instanceof Context) {
                Context ctx = ((Context) app).getApplicationContext() != null
                        ? ((Context) app).getApplicationContext() : (Context) app;
                sSysUiContext = ctx;
                sHookTimeContextSource = "ActivityThread";
                return ctx;
            }
        } catch (Throwable ignored) {}
        sHookTimeContextSource = "NONE";
        return null;
    }

    private static HookMap resolveVerifiedHookMap(Context ctx) {
        PackageInfo aodPi = null;
        if (ctx != null) {
            try {
                aodPi = ctx.getPackageManager().getPackageInfo(AodScanner.AOD_PKG, 0);
            } catch (Throwable ignored) {}
        }

        HookMap xmap = HookMap.loadFromXSharedPrefs("io.github.tavataan.nowbarcolor");
        if (xmap != null) {
            if (aodPi == null || xmap.matchesTarget(aodPi.getLongVersionCode(), aodPi.lastUpdateTime)) {
                sHookMapSource = "XSharedPrefs";
                sHookMapTarget = xmap.aodVersionName + " (" + xmap.aodVersionCode + ") ["
                        + xmap.countResolvedSlots() + "/" + xmap.totalSlotCount() + "]";
                return xmap;
            }
        }

        if (ctx != null) {
            HookMap pmap = HookMap.loadFromProvider(ctx);
            if (pmap != null) {
                if (aodPi == null || pmap.matchesTarget(aodPi.getLongVersionCode(), aodPi.lastUpdateTime)) {
                    sHookMapSource = "Provider";
                    sHookMapTarget = pmap.aodVersionName + " (" + pmap.aodVersionCode + ") ["
                            + pmap.countResolvedSlots() + "/" + pmap.totalSlotCount() + "]";
                    return pmap;
                }
            }
        }

        sHookMapSource = (xmap != null) ? "STALE_XSP" : "NULL";
        return null;
    }

    private static void schedulePendingLayer2Install(final Context ctx) {
        if (ctx == null || PENDING_LAYER2_CLASSLOADERS.isEmpty()) {
            return;
        }
        if (sIoHandler == null) {
            HandlerThread worker = new HandlerThread("NowBarColor-IO", android.os.Process.THREAD_PRIORITY_BACKGROUND);
            worker.start();
            sIoHandler = new Handler(worker.getLooper());
        }
        sIoHandler.post(new Runnable() {
            @Override
            public void run() {
                final HookMap map = resolveVerifiedHookMap(ctx);
                if (map == null) return;
                Handler main = getMainHandler();
                Runnable apply = new Runnable() {
                    @Override
                    public void run() {
                        List<ClassLoader> pending;
                        synchronized (PENDING_LAYER2_CLASSLOADERS) {
                            pending = new ArrayList<>(PENDING_LAYER2_CLASSLOADERS);
                            PENDING_LAYER2_CLASSLOADERS.clear();
                        }
                        for (ClassLoader pcl : pending) {
                            if (pcl != null) {
                                installLayer2And3Hooks(pcl, map);
                            }
                        }
                        armWakeTransitionWatch(900L);
                        refreshAllActiveCards();
                        pushSystemUiTelemetryOnly(ctx);
                    }
                };
                if (main != null) {
                    main.post(apply);
                } else {
                    apply.run();
                }
            }
        });
    }

    private static synchronized void hookAodPluginClassLoader(final ClassLoader cl) {
        if (cl == null) return;

        // Layer 1 + GenUI hooks (idempotent per ClassLoader, never depends on HookMap)
        if (LAYER1_CLASSLOADERS.add(cl)) {
            XposedBridge.log(TAG + ": Installing Layer-1 NowBar hooks in ClassLoader: " + cl);
            installLayer1Hooks(cl);
            hookGenUiClasses(cl);
        }

        if (LAYER2_CLASSLOADERS.contains(cl)) {
            return;
        }

        Context ctx = resolveEarlyContext();
        HookMap map = resolveVerifiedHookMap(ctx);
        if (map != null) {
            PENDING_LAYER2_CLASSLOADERS.remove(cl);
            installLayer2And3Hooks(cl, map);
            if (ctx != null) {
                pushSystemUiTelemetryOnly(ctx);
            }
        } else {
            PENDING_LAYER2_CLASSLOADERS.add(cl);
            XposedBridge.log(TAG + ": HookMap deferred (" + sHookMapSource + "); queued ClassLoader for retry.");
            if (ctx != null) {
                schedulePendingLayer2Install(ctx);
            }
        }
    }

    private static Class<?>[] resolveKnownCardClasses(ClassLoader cl, HookMap map) {
        Class<?> mediaCardClass = XposedHelpers.findClassIfExists(
                map != null && map.mediaCardClass != null
                        ? map.mediaCardClass
                        : "com.samsung.android.app.facewidget.presentation.NowBarMediaCardView", cl);
        Class<?> batteryCardClass = XposedHelpers.findClassIfExists(
                "com.samsung.android.app.facewidget.presentation.NowBarBatteryCardView", cl);
        Class<?> briefCardClass = XposedHelpers.findClassIfExists(
                map != null && map.briefCardClass != null
                        ? map.briefCardClass
                        : "com.samsung.android.app.facewidget.presentation.NowBarBriefCardView", cl);
        Class<?> ongoingCardClass = XposedHelpers.findClassIfExists(
                "com.samsung.android.app.facewidget.presentation.NowBarOngoingCardView", cl);
        Class<?> externalCardClass = XposedHelpers.findClassIfExists(
                "com.samsung.android.app.facewidget.presentation.NowBarExternalViewCardView", cl);
        Class<?> appSearchCardClass = XposedHelpers.findClassIfExists(
                "com.samsung.android.app.facewidget.presentation.NowBarAppSearchCardView", cl);
        Class<?> callCardClass = XposedHelpers.findClassIfExists(
                "com.samsung.android.app.facewidget.presentation.NowBarCallCardView", cl);
        Class<?> modeCardClass = XposedHelpers.findClassIfExists(
                "com.samsung.android.app.facewidget.presentation.NowBarModeCardView", cl);
        Class<?> contactCardClass = XposedHelpers.findClassIfExists(
                "com.samsung.android.app.facewidget.presentation.NowBarContactCardView", cl);

        Class<?> baseCardClass = (map != null && map.baseCardClass != null)
                ? XposedHelpers.findClassIfExists(map.baseCardClass, cl)
                : null;
        if (baseCardClass == null && mediaCardClass != null) {
            for (Class<?> sup = mediaCardClass.getSuperclass();
                 sup != null && !sup.getName().startsWith("android.") && !sup.getName().startsWith("androidx.");
                 sup = sup.getSuperclass()) {
                baseCardClass = sup;
                break;
            }
        }
        return new Class<?>[]{
                baseCardClass, mediaCardClass, batteryCardClass, briefCardClass,
                ongoingCardClass, externalCardClass, appSearchCardClass,
                callCardClass, modeCardClass, contactCardClass
        };
    }

    private static void installLayer1Hooks(final ClassLoader cl) {
        try {
            Class<?>[] allCardClasses = resolveKnownCardClasses(cl, null);
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
                        requestCardUpdate(card);
                    }
                }
            };
            XC_MethodHook wakeUpdateHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof View) {
                        armWakeTransitionWatch(700L);
                        requestCardUpdate((View) param.thisObject);
                    }
                }
            };

            XC_MethodHook expandHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof View) {
                        Boolean next = (param.args != null && param.args.length >= 1 && param.args[0] instanceof Boolean)
                                ? (Boolean) param.args[0] : null;
                        handleExpandTransition((View) param.thisObject, next, true);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof View) {
                        Boolean next = (param.args != null && param.args.length >= 1 && param.args[0] instanceof Boolean)
                                ? (Boolean) param.args[0] : null;
                        handleExpandTransition((View) param.thisObject, next, false);
                    }
                }
            };

            String[] androidBoundaryMethods = new String[]{
                    "onLayout", "onAttachedToWindow", "onFinishInflate", "onVisibilityChanged",
                    "setViewMode"
            };
            for (Class<?> cardCls : allCardClasses) {
                if (cardCls == null) continue;
                try {
                    XposedBridge.hookAllConstructors(cardCls, cardUpdateHook);
                } catch (Throwable ignored) {}
                try {
                    XposedBridge.hookAllMethods(cardCls, "setExpanded", expandHook);
                } catch (Throwable ignored) {}
                for (String mName : androidBoundaryMethods) {
                    try {
                        XposedBridge.hookAllMethods(cardCls, mName, cardUpdateHook);
                    } catch (Throwable ignored) {}
                }
            }
            hookAodStateSetters(allCardClasses);
            installMediaSurfaceGuardHooks(cl);

            Class<?> stackViewClass = XposedHelpers.findClassIfExists(
                    "com.samsung.android.app.facewidget.utils.NowBarCardStackView", cl);
            if (stackViewClass != null) {
                try {
                    XposedBridge.hookAllMethods(stackViewClass, "addView", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.args != null && param.args.length > 0 && param.args[0] instanceof View) {
                                View card = (View) param.args[0];
                                if (looksLikeNowBarCardTree(card)) {
                                    requestCardUpdate(card);
                                }
                            }
                        }
                    });
                } catch (Throwable ignored) {}
            }
            sLayer1Hooked = true;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error installing Layer-1 hooks: " + t);
        }
    }

    private static XC_MethodHook createAodSetterHook(
            final String fieldKey, final Class<?> expectedType, final boolean wakeTransition) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof View && param.args != null
                        && param.args.length >= 1 && expectedType.isInstance(param.args[0])) {
                    XposedHelpers.setAdditionalInstanceField(param.thisObject, fieldKey, param.args[0]);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof View) {
                    View card = (View) param.thisObject;
                    if (param.args != null && param.args.length >= 1 && expectedType.isInstance(param.args[0])) {
                        XposedHelpers.setAdditionalInstanceField(card, fieldKey, param.args[0]);
                    }
                    if (wakeTransition) {
                        armWakeTransitionWatch(700L);
                    }
                    syncOrKeepMediaOnTransition(card);
                    requestCardUpdate(card);
                }
            }
        };
    }

    private static void hookAodStateSetters(Class<?>[] cardClasses) {
        hookAcrossCardHierarchy(cardClasses, "setLockscreenViewMode",
                createAodSetterHook("nb_lockscreen_mode", Boolean.class, true));
        hookAcrossCardHierarchy(cardClasses, "setInitialDozing",
                createAodSetterHook("nb_initial_dozing", Boolean.class, false));
        hookAcrossCardHierarchy(cardClasses, "setDozing",
                createAodSetterHook("nb_dozing", Boolean.class, true));
        hookAcrossCardHierarchy(cardClasses, "setDarkAmount",
                createAodSetterHook("nb_dark_amount", Float.class, true));
    }

    private static void installMediaSurfaceGuardHooks(ClassLoader cl) {
        Class<?> cardViewClass = XposedHelpers.findClassIfExists("androidx.cardview.widget.CardView", cl);
        if (cardViewClass != null) {
            XC_MethodHook cardBgGuard = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (CapsuleStyle.isApplyingInternalStyle()
                            || !(param.thisObject instanceof View)) {
                        return;
                    }
                    View v = (View) param.thisObject;
                    boolean isBackdrop = MEDIA_BACKDROP_SURFACES.contains(v);
                    boolean isPill = !isBackdrop && MEDIA_PILL_SURFACES.contains(v);
                    boolean isCustomSurface = !isBackdrop && !isPill && CapsuleStyle.isCustomBackgroundActive(v);
                    if (!isBackdrop && !isPill && !isCustomSurface) return;
                    View card = findEnclosingNowBarCard(v);
                    if (isBackdrop || isPill) {
                        if (card == null || !shouldCustomizeCatMedia() || isCardInSettingMode(card)) {
                            MEDIA_BACKDROP_SURFACES.remove(v);
                            MEDIA_PILL_SURFACES.remove(v);
                            return;
                        }
                    } else {
                        ConfigManager.Config cfg = sConfig;
                        ConfigManager.CategoryConfig catCfg = (cfg != null && card != null)
                                ? cfg.getCategory(resolveCategoryForCard(card)) : null;
                        if (card == null || cfg == null || !cfg.masterEnabled
                                || catCfg == null || !catCfg.enabled
                                || !isCollapsedLockscreenCapsule(card)) {
                            return;
                        }
                    }
                    if (param.args != null && param.args.length >= 1) {
                        boolean isCsl = param.args[0] instanceof android.content.res.ColorStateList;
                        if (isBackdrop) {
                            param.args[0] = isCsl
                                    ? android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
                                    : Color.TRANSPARENT;
                        } else if (isCustomSurface || isCollapsedLockscreenCapsule(card)) {
                            param.setResult(null);
                        } else {
                            param.args[0] = isCsl
                                    ? android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
                                    : Color.TRANSPARENT;
                        }
                    }
                }
            };
            try {
                XposedBridge.hookAllMethods(cardViewClass, "setCardBackgroundColor", cardBgGuard);
            } catch (Throwable ignored) {}
        }

        if (!sSemBlurHookInstalled) {
            Method setBlur = CapsuleStyle.getBlurSetterMethod();
            if (setBlur != null) {
                sSemBlurHookInstalled = true;
                try {
                    XposedBridge.hookMethod(setBlur, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (CapsuleStyle.isApplyingInternalStyle()
                                    || (MEDIA_BACKDROP_SURFACES.isEmpty() && MEDIA_PILL_SURFACES.isEmpty())
                                    || !(param.thisObject instanceof View)) {
                                return;
                            }
                            View v = (View) param.thisObject;
                            boolean isBackdrop = MEDIA_BACKDROP_SURFACES.contains(v);
                            boolean isPill = !isBackdrop && MEDIA_PILL_SURFACES.contains(v);
                            if (!isBackdrop && !isPill) return;
                            View card = findEnclosingNowBarCard(v);
                            if (card == null || !shouldCustomizeCatMedia() || isCardInSettingMode(card)) {
                                MEDIA_BACKDROP_SURFACES.remove(v);
                                MEDIA_PILL_SURFACES.remove(v);
                                return;
                            }
                            if (param.args != null && param.args.length >= 1) {
                                if (isBackdrop) {
                                    param.args[0] = null;
                                } else {
                                    ConfigManager.Config cfg = sConfig;
                                    boolean useBlur = (cfg != null && cfg.styleMode == ConfigManager.STYLE_BLUR);
                                    if (isCollapsedLockscreenCapsule(card)) {
                                        if (useBlur && getCardAodVisibility(card) > 0.005f) {
                                            // Prevent cz4.onAnimationEnd or native resets from clearing Blur mid-collapse.
                                            param.setResult(null);
                                        } else {
                                            param.args[0] = null;
                                        }
                                    } else {
                                        param.args[0] = null;
                                    }
                                }
                            }
                        }
                    });
                } catch (Throwable ignored) {}
            }
        }
    }

    private static void hookAcrossCardHierarchy(Class<?>[] cardClasses, String methodName, XC_MethodHook callback) {
        if (cardClasses == null || methodName == null || callback == null) return;
        for (Class<?> cls : cardClasses) {
            if (cls == null) continue;
            try {
                XposedBridge.hookAllMethods(cls, methodName, callback);
            } catch (Throwable ignored) {}
        }
    }

    private static synchronized void installLayer2And3Hooks(final ClassLoader cl, final HookMap map) {
        if (cl == null || map == null || !LAYER2_CLASSLOADERS.add(cl)) {
            return;
        }
        sHookMap = map;
        try {
            Class<?>[] cardClasses = resolveKnownCardClasses(cl, map);
            final Class<?> baseCardClass = cardClasses[0];
            final Class<?> mediaCardClass = cardClasses[1];
            final Class<?> briefCardClass = cardClasses[3];
            final NowBarCardAdapter adapter = NowBarAdapterFactory.resolve(baseCardClass, map);
            sAdapter = adapter;
            hookAodStateSetters(cardClasses);

            final XC_MethodHook wakeCardUpdateHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof View) {
                        View card = (View) param.thisObject;
                        armWakeTransitionWatch(1200L);
                        syncOrKeepMediaOnTransition(card);
                        requestCardUpdate(card);
                    }
                }
            };

            // =========================================================================
            // LAYER 2 — CORE + WAKE/TRANSITION (Decoupled per-slot dynamic HookMap)
            // =========================================================================
            boolean coreOk = false;
            if (baseCardClass != null) {
                int coreHookCount = 0;
                if (map.baseBindMethod != null) {
                    try {
                        XposedBridge.hookAllMethods(baseCardClass, map.baseBindMethod, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                if (param.thisObject instanceof View) {
                                    View card = (View) param.thisObject;
                                    if (param.args != null && param.args.length > 0 && param.args[0] != null) {
                                        XposedHelpers.setAdditionalInstanceField(card, "nb_cached_item", param.args[0]);
                                    }
                                    syncOrKeepMediaOnTransition(card);
                                    requestCardUpdate(card);
                                }
                            }
                        });
                        coreHookCount++;
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": Failed to hook baseBindMethod: " + t);
                    }
                }

                if (map.baseBlurMethod != null) {
                    try {
                        XposedBridge.hookAllMethods(baseCardClass, map.baseBlurMethod, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (param.thisObject instanceof View) {
                                    View card = (View) param.thisObject;
                                    if (param.args != null && param.args.length > 0 && param.args[0] != null) {
                                        XposedHelpers.setAdditionalInstanceField(card, "nb_cached_item", param.args[0]);
                                    }
                                    ConfigManager.Config cfg = sConfig;
                                    String catId = resolveCategoryForCard(card);
                                    ConfigManager.CategoryConfig catCfg = cfg != null ? cfg.getCategory(catId) : null;
                                    boolean enabledForCard = (cfg != null && cfg.masterEnabled && catCfg != null && catCfg.enabled);
                                    View targetView = (param.args != null && param.args.length > 1 && param.args[1] instanceof View)
                                            ? (View) param.args[1] : null;
                                    if (enabledForCard && isCollapsedLockscreenCapsule(card)) {
                                        boolean useBlur = (cfg.styleMode == ConfigManager.STYLE_BLUR);
                                        if (!useBlur && targetView != null) {
                                            clearBlurInfo(targetView);
                                        }
                                        View container = adapter.resolveCardContainer(card);
                                        if (targetView != null && targetView == container
                                                && adapter.supportsBaseClearContainerBg()
                                                && map.baseClearContainerBgMethod != null) {
                                            callMethodNoArgs(card, map.baseClearContainerBgMethod);
                                        }
                                        // CapsuleStyle owns the blur curve, radius, and tint.
                                        param.setResult(null);
                                        return;
                                    }
                                    if (enabledForCard && card.getClass().getName().endsWith("NowBarMediaCardView")
                                            && !isCardInSettingMode(card) && targetView != null
                                            && (targetView == adapter.resolveMediaBackdropSurface(card)
                                                    || targetView == adapter.resolveMediaPillSurface(card)
                                                    || targetView == adapter.resolveMediaSettingBgSurface(card))) {
                                        clearBlurInfo(targetView);
                                        param.setResult(null);
                                    }
                                }
                            }

                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                if (param.thisObject instanceof View) {
                                    View card = (View) param.thisObject;
                                    if (param.args != null && param.args.length > 0 && param.args[0] != null) {
                                        XposedHelpers.setAdditionalInstanceField(card, "nb_cached_item", param.args[0]);
                                    }
                                    armWakeTransitionWatch(900L);
                                    syncOrKeepMediaOnTransition(card);
                                    requestCardUpdate(card);
                                }
                            }
                        });
                        coreHookCount++;
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": Failed to hook baseBlurMethod: " + t);
                    }
                }

                if (map.baseColorMethod != null) {
                    try {
                        XposedBridge.hookAllMethods(baseCardClass, map.baseColorMethod, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (param.thisObject instanceof View) {
                                    View card = (View) param.thisObject;
                                    ConfigManager.Config cfg = sConfig;
                                    String catId = resolveCategoryForCard(card);
                                    ConfigManager.CategoryConfig catCfg = cfg != null ? cfg.getCategory(catId) : null;
                                    if (cfg != null && cfg.masterEnabled && catCfg != null && catCfg.enabled) {
                                        if (isCollapsedLockscreenCapsule(card)) {
                                            param.setResult(Color.TRANSPARENT);
                                        } else if (card.getClass().getName().endsWith("NowBarMediaCardView")
                                                && !isCardInSettingMode(card)) {
                                            param.setResult(Color.TRANSPARENT);
                                        }
                                    }
                                }
                            }
                        });
                        coreHookCount++;
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": Failed to hook baseColorMethod: " + t);
                    }
                }
                coreOk = (coreHookCount > 0);

                // Hook wake / transition methods across baseCardClass & subclasses so AOD -> Screen-ON
                // background/blur repaints are always re-intercepted cleanly.
                String[] wakeMethods = new String[]{
                        map.baseShapeMethod, map.baseProgressBgMethod, map.baseDarkModeMethod
                };
                int wakeCount = 0;
                for (String wMethod : wakeMethods) {
                    if (wMethod == null) continue;
                    hookAcrossCardHierarchy(cardClasses, wMethod, wakeCardUpdateHook);
                    wakeCount++;
                }
                if (map.baseExpandMethod != null) {
                    XC_MethodHook baseExpandHook = new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.thisObject instanceof View) {
                                View card = (View) param.thisObject;
                                Boolean nativeExp = readNativeExpandedState(card);
                                Boolean next = nativeExp != null ? nativeExp
                                        : ((param.args != null && param.args.length >= 1 && param.args[0] instanceof Boolean)
                                                ? (Boolean) param.args[0] : null);
                                handleExpandTransition(card, next, true);
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.thisObject instanceof View) {
                                View card = (View) param.thisObject;
                                Boolean nativeExp = readNativeExpandedState(card);
                                Boolean next = nativeExp != null ? nativeExp
                                        : ((param.args != null && param.args.length >= 1 && param.args[0] instanceof Boolean)
                                                ? (Boolean) param.args[0] : null);
                                handleExpandTransition(card, next, false);
                            }
                        }
                    };
                    hookAcrossCardHierarchy(cardClasses, map.baseExpandMethod, baseExpandHook);
                    wakeCount++;
                }
                if (map.baseSetExpandStateMethod != null) {
                    XC_MethodHook setExpandStateHook = new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.thisObject instanceof View) {
                                View card = (View) param.thisObject;
                                Boolean nativeExp = readNativeExpandedState(card);
                                handleExpandTransition(card, nativeExp, false);
                            }
                        }
                    };
                    hookAcrossCardHierarchy(cardClasses, map.baseSetExpandStateMethod, setExpandStateHook);
                    wakeCount++;
                }
                if (map.baseStateMethod != null) {
                    XC_MethodHook baseStateHook = new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.thisObject instanceof View && param.args != null
                                    && param.args.length >= 1 && param.args[0] instanceof Integer) {
                                XposedHelpers.setAdditionalInstanceField(param.thisObject, "nb_card_state", (Integer) param.args[0]);
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.thisObject instanceof View) {
                                View card = (View) param.thisObject;
                                if (param.args != null && param.args.length >= 1 && param.args[0] instanceof Integer) {
                                    XposedHelpers.setAdditionalInstanceField(card, "nb_card_state", (Integer) param.args[0]);
                                }
                                armWakeTransitionWatch(1200L);
                                syncOrKeepMediaOnTransition(card);
                                requestCardUpdate(card);
                            }
                        }
                    };
                    hookAcrossCardHierarchy(cardClasses, map.baseStateMethod, baseStateHook);
                    wakeCount++;
                }
                if (adapter.supportsBaseAodDrawable() && map.baseAodDrawableMethod != null) {
                    XC_MethodHook aodDrawableHook = new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.thisObject instanceof View) {
                                View card = (View) param.thisObject;
                                if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(card, "nb_restoring"))) {
                                    return;
                                }
                                armWakeTransitionWatch(1200L);
                                syncOrKeepMediaOnTransition(card);
                                requestCardUpdate(card);
                            }
                        }
                    };
                    hookAcrossCardHierarchy(cardClasses, map.baseAodDrawableMethod, aodDrawableHook);
                    wakeCount++;
                }
                sLayer2WakeCount = wakeCount;
            }
            sLayer2CoreHooked = coreOk;

            if (map.stackViewClass != null && map.stackRefreshMethod != null) {
                try {
                    Class<?> stackCls = XposedHelpers.findClassIfExists(map.stackViewClass, cl);
                    if (stackCls != null) {
                        XposedBridge.hookAllMethods(stackCls, map.stackRefreshMethod, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                if (param.thisObject instanceof ViewGroup) {
                                    ViewGroup stack = (ViewGroup) param.thisObject;
                                    ensureSystemUiListeners(stack.getContext());
                                    armWakeTransitionWatch(1200L);
                                    for (int i = 0; i < stack.getChildCount(); i++) {
                                        View child = stack.getChildAt(i);
                                        syncOrKeepMediaOnTransition(child);
                                        requestCardUpdate(child);
                                    }
                                }
                            }
                        });
                    }
                } catch (Throwable ignored) {}
            }

            // =========================================================================
            // LAYER 3 — ENHANCEMENTS (Independent failure; Core keeps running)
            // =========================================================================
            int enhCount = 0;
            if (mediaCardClass != null) {
                if (adapter.supportsMediaForegroundLighting() && map.mediaForegroundMethod != null) {
                    try {
                        XposedBridge.hookAllMethods(mediaCardClass, map.mediaForegroundMethod, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (shouldCustomizeCatMedia()) {
                                    if (param.thisObject instanceof View && isCollapsedLockscreenCapsule((View) param.thisObject)) {
                                        param.setResult(null);
                                        return;
                                    }
                                    if (param.args != null && param.args.length >= 4) {
                                        param.args[3] = Integer.MAX_VALUE;
                                    }
                                }
                            }
                        });
                        enhCount++;
                    } catch (Throwable ignored) {}
                }
                try {
                    XposedBridge.hookAllMethods(mediaCardClass, "getBgColorForBlackAod", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.thisObject instanceof View) {
                                View card = (View) param.thisObject;
                                ConfigManager.Config cfg = sConfig;
                                if (cfg != null && cfg.masterEnabled && cfg.hideBgOnAod
                                        && shouldCustomizeCatMedia()
                                        && isCollapsedLockscreenCapsule(card)
                                        && getCardAodVisibility(card) <= 0.005f) {
                                    param.setResult(Color.TRANSPARENT);
                                }
                            }
                        }
                    });
                } catch (Throwable ignored) {}
                if (map.mediaBlackAodMethod != null) {
                    try {
                        XposedBridge.hookAllMethods(mediaCardClass, map.mediaBlackAodMethod, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (param.thisObject instanceof View) {
                                    keepExpandedMediaBackdropIfNeeded((View) param.thisObject);
                                }
                            }

                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                if (param.thisObject instanceof View) {
                                    View card = (View) param.thisObject;
                                    if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(card, "nb_restoring"))) {
                                        return;
                                    }
                                    armWakeTransitionWatch(800L);
                                    syncOrKeepMediaOnTransition(card);
                                    requestCardUpdate(card);
                                }
                            }
                        });
                        enhCount++;
                    } catch (Throwable ignored) {}
                }
                try {
                    XposedBridge.hookAllMethods(mediaCardClass, "setBgColor", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.thisObject instanceof View) {
                                View card = (View) param.thisObject;
                                if (shouldCustomizeCatMedia() && !isCardInSettingMode(card)) {
                                    syncMediaSurfaceProtection(card);
                                    ConfigManager.Config cfg = sConfig;
                                    boolean useBlur = (cfg != null && cfg.styleMode == ConfigManager.STYLE_BLUR);
                                    boolean collapsed = isCollapsedLockscreenCapsule(card);
                                    boolean aodHidden = collapsed && getCardAodVisibility(card) <= 0.005f;
                                    NowBarCardAdapter adapter = activeAdapter();
                                    View o1 = adapter.resolveMediaBackdropSurface(card);
                                    View t1 = adapter.resolveMediaSettingBgSurface(card);
                                    View p1 = adapter.resolveMediaPillSurface(card);
                                    clearBackdropSurface(o1, aodHidden);
                                    clearBackdropSurface(t1, false);
                                    if (p1 != null) {
                                        if (!collapsed || !useBlur) {
                                            clearBlurInfo(p1);
                                        }
                                        if (!collapsed) {
                                            setCardBackgroundColor(p1, Color.TRANSPARENT);
                                        }
                                    }
                                    param.setResult(null);
                                }
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.thisObject instanceof View) {
                                View card = (View) param.thisObject;
                                ACTIVE_CARDS.add(card);
                                ensureSystemUiListeners(card.getContext());
                                syncMediaSurfaceProtection(card);
                                if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(card, "nb_restoring"))) {
                                    return;
                                }
                                syncOrKeepMediaOnTransition(card);
                                requestCardUpdate(card);
                            }
                        }
                    });
                    enhCount++;
                } catch (Throwable ignored) {}
                try {
                    XposedBridge.hookAllMethods(mediaCardClass, "setSettingBgColor", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.thisObject instanceof View) {
                                View card = (View) param.thisObject;
                                if (shouldCustomizeCatMedia() && !isCardInSettingMode(card)) {
                                    syncMediaSurfaceProtection(card);
                                    View t1 = activeAdapter().resolveMediaSettingBgSurface(card);
                                    clearBackdropSurface(t1, false);
                                    param.setResult(null);
                                }
                            }
                        }
                    });
                } catch (Throwable ignored) {}
            }

            if (adapter.supportsBriefCardView() && briefCardClass != null) {
                if (map.briefBgColorMethod != null) {
                    try {
                        XposedBridge.hookAllMethods(briefCardClass, map.briefBgColorMethod, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                if (param.thisObject instanceof View) {
                                    View card = (View) param.thisObject;
                                    ACTIVE_CARDS.add(card);
                                    ensureSystemUiListeners(card.getContext());
                                    if (isCollapsedLockscreenCapsule(card) && shouldCustomizeCatBrief()) {
                                        ConfigManager.Config cfg = sConfig;
                                        float vis = getCardAodVisibility(card);
                                        int[] grad = cfg != null ? cfg.resolveGradientArgbForCategory(ConfigManager.CAT_BRIEF) : null;
                                        if (vis <= 0.005f || grad != null || (cfg != null && cfg.styleMode == ConfigManager.STYLE_BLUR)) {
                                            param.setResult(Color.TRANSPARENT);
                                        } else {
                                            Integer liveWall = getLiveWallpaperColorForConfig(cfg);
                                            int customArgb = cfg.resolveFinalArgbForCategory(ConfigManager.CAT_BRIEF, liveWall);
                                            param.setResult(ConfigManager.scaleAlpha(customArgb, vis));
                                        }
                                    }
                                }
                            }
                        });
                        enhCount++;
                    } catch (Throwable ignored) {}
                }
                if (map.briefResetBgMethod != null) {
                    try {
                        XposedBridge.hookAllMethods(briefCardClass, map.briefResetBgMethod, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (param.args != null && param.args.length >= 1 && param.args[0] instanceof View) {
                                    View card = (View) param.args[0];
                                    if (!Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(card, "nb_restoring"))
                                            && isCollapsedLockscreenCapsule(card)
                                            && shouldCustomizeCatBrief()) {
                                        param.setResult(null);
                                    }
                                }
                            }
                        });
                        enhCount++;
                    } catch (Throwable ignored) {}
                }
            }

            if (map.settingsHelperClass != null && map.showNotiLockscreenMethod != null) {
                try {
                    Class<?> settingsHelperClass = XposedHelpers.findClassIfExists(map.settingsHelperClass, cl);
                    if (settingsHelperClass != null) {
                        XposedBridge.hookAllMethods(settingsHelperClass, map.showNotiLockscreenMethod, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                param.setResult(true);
                            }
                        });
                        enhCount++;
                    }
                } catch (Throwable ignored) {}
            }

            Class<?> gradViewClass = XposedHelpers.findClassIfExists(
                    map.gradViewClass != null
                            ? map.gradViewClass
                            : "com.samsung.android.app.facewidget.presentation.NowBarCardGradientView", cl);
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
                for (String m : new String[]{"setGradientBackgroundVisible", "setGradientColor"}) {
                    try {
                        XposedBridge.hookAllMethods(gradViewClass, m, blockGradHook);
                    } catch (Throwable ignored) {}
                }
                if (map.gradHideMethod != null) {
                    try {
                        XposedBridge.hookAllMethods(gradViewClass, map.gradHideMethod, blockGradHook);
                        enhCount++;
                    } catch (Throwable ignored) {}
                }
            }
            sLayer3Count = enhCount;

            XposedBridge.log(TAG + ": Installed Layer-2/3 NowBar hooks (Core=" + sLayer2CoreHooked
                    + ", Wake=" + sLayer2WakeCount + ", Enh=" + sLayer3Count + ")");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error installing Layer-2/3 hooks: " + t);
        }
    }

    private static void pushSystemUiTelemetryOnly(Context ctx) {
        if (ctx == null) return;
        StringBuilder sb = new StringBuilder();
        appendSystemUiTelemetry(sb);
        sb.append("\n=== Live NowBar View Tree ===\n<Waiting for first card render>");
        pushDiagnosticsDumpAsync(ctx, sb.toString());
    }

    private static void hookGenUiClasses(final ClassLoader cl) {
        try {
            // 6a. StyleBox.updateBlur(View, Context, BaseStyle) -> applies SemBlurInfo with preset 54/62 to the GenUI FrameLayout pill
            final Class<?> styleBoxClass = XposedHelpers.findClassIfExists(
                    "com.samsung.android.genui.kit.nodes.StyleBox", cl);
            if (styleBoxClass != null) {
                XposedBridge.hookAllMethods(styleBoxClass, "updateBlur", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.getThrowable() != null) {
                            param.setThrowable(null);
                        }
                        if (param.args != null && param.args.length >= 1 && param.args[0] instanceof View) {
                            View blurView = (View) param.args[0];
                            if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(blurView, "nb_restoring_blur"))) {
                                return;
                            }
                            ACTIVE_GENUI_BLUR_VIEWS.add(blurView);
                            ensureSystemUiListeners(blurView.getContext());
                            ensureGenUiAttachListener(blurView, true);
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
                            ensureGenUiAttachListener(glView, false);
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
                        if (shouldCustomizeCatBrief() && isCapsuleRootBaseStyle(param.thisObject) && isAnyBriefCardCollapsed()) {
                            ConfigManager.Config gcfg = sConfig;
                            float vis = resolveBriefVisibility(null);
                            if ((gcfg != null && gcfg.styleMode == ConfigManager.STYLE_BLUR)
                                    || (gcfg != null && gcfg.hideBgOnAod && vis < 0.995f)
                                    || hasAnyAttachedGenUiView()) {
                                param.setResult(Arrays.asList(Color.TRANSPARENT, Color.TRANSPARENT));
                                return;
                            }
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
                            if (shouldCustomizeCatBrief() && isCapsuleRootBaseStyle(param.thisObject) && isAnyBriefCardCollapsed()) {
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
                            if (shouldCustomizeCatBrief() && isCapsuleRootBaseStyle(param.thisObject) && isAnyBriefCardCollapsed()) {
                                param.setResult(null);
                            }
                        }
                    });
                } catch (Throwable ignored) {}
                try {
                    XposedBridge.hookAllMethods(baseStyleClass, "getStrokeStyle", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            ConfigManager.Config gcfg = sConfig;
                            if (gcfg != null && gcfg.hideBgOnAod
                                    && shouldCustomizeCatBrief()
                                    && isCapsuleRootBaseStyle(param.thisObject)
                                    && isAnyBriefCardCollapsed()
                                    && resolveBriefVisibility(null) < 0.995f) {
                                param.setResult("without_stroke");
                            }
                        }
                    });
                } catch (Throwable ignored) {}
                XC_MethodHook strokeColorHook = new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object orig = param.getResult();
                        if (!(orig instanceof List) || ((List<?>) orig).isEmpty()) {
                            return;
                        }
                        ConfigManager.Config gcfg = sConfig;
                        if (gcfg != null && gcfg.hideBgOnAod
                                && shouldCustomizeCatBrief()
                                && isCapsuleRootBaseStyle(param.thisObject)
                                && isAnyBriefCardCollapsed()
                                && resolveBriefVisibility(null) < 0.995f) {
                            param.setResult(Arrays.asList(Color.TRANSPARENT, Color.TRANSPARENT));
                        }
                    }
                };
                try {
                    XposedBridge.hookAllMethods(baseStyleClass, "getStrokeColor", strokeColorHook);
                } catch (Throwable ignored) {}
                try {
                    XposedBridge.hookAllMethods(baseStyleClass, "getStrokeDarkColor", strokeColorHook);
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
            CapsuleStyle.refreshAccessibility(appCtx);

            try {
                Settings.System.putInt(appCtx.getContentResolver(), "nowbar_color_hook_active", 1);
            } catch (Throwable ignored) {}

            // Load initial config and wallpaper colors
            reloadConfigAndRefreshAll(appCtx, true);

            // Install any deferred Layer-2/3 hooks if hookAodPluginClassLoader ran before Context/HookMap was ready
            schedulePendingLayer2Install(appCtx);

            final Handler h = getMainHandler();
            if (h == null) return;

            h.post(new Runnable() {
                @Override
                public void run() {
                    // 1. Register BroadcastReceiver for instant updates from app, wallpaper, & screen wake/sleep
                    try {
                        IntentFilter filter = new IntentFilter();
                        filter.addAction(ConfigManager.ACTION_CONFIG_UPDATED);
                        filter.addAction(Intent.ACTION_WALLPAPER_CHANGED);
                        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
                        filter.addAction(Intent.ACTION_SCREEN_ON);
                        filter.addAction(Intent.ACTION_SCREEN_OFF);
                        filter.addAction(Intent.ACTION_USER_PRESENT);
                        BroadcastReceiver receiver = new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context ctx, Intent intent) {
                                if (intent == null) return;
                                String action = intent.getAction();
                                if (Intent.ACTION_SCREEN_ON.equals(action)
                                        || Intent.ACTION_SCREEN_OFF.equals(action)
                                        || Intent.ACTION_USER_PRESENT.equals(action)) {
                                    armWakeTransitionWatch(2000L);
                                    refreshAllActiveCards();
                                    return;
                                }
                                if (Intent.ACTION_CONFIGURATION_CHANGED.equals(action)) {
                                    refreshAllActiveCards();
                                    return;
                                }
                                if (ConfigManager.ACTION_CONFIG_UPDATED.equals(action)) {
                                    String json = intent.getStringExtra("config_json");
                                    if (json != null && !json.isEmpty()) {
                                        sConfigVersion++;
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

                    try {
                        appCtx.getContentResolver().registerContentObserver(
                                Settings.System.getUriFor("accessibility_reduce_transparency"), false,
                                new ContentObserver(h) {
                                    @Override public void onChange(boolean selfChange) {
                                        CapsuleStyle.refreshAccessibility(appCtx);
                                        refreshAllActiveCards();
                                    }
                                });
                    } catch (Throwable ignored) {}

                    // 3. Register WallpaperManager.OnColorsChangedListener for live Lockscreen Wallpaper updates
                    try {
                        WallpaperManager wm = WallpaperManager.getInstance(appCtx);
                        if (wm != null) {
                            wm.addOnColorsChangedListener(new WallpaperManager.OnColorsChangedListener() {
                                @Override
                                public void onColorsChanged(WallpaperColors colors, int which) {
                                    reloadConfigAndRefreshAll(appCtx, true);
                                }
                            }, h);
                        }
                    } catch (Throwable ignored) {}
                }
            });
        }
    }

    private static void reloadConfigAndRefreshAll(Context context, boolean refreshWallpaper) {
        Handler main = getMainHandler();
        if (main == null || context == null) return;
        sConfigVersion++;
        sWallpaperRequested |= refreshWallpaper;
        main.removeCallbacks(RELOAD);
        main.postDelayed(RELOAD, 32);
    }

    private static void refreshAllActiveCards() {
        Handler h = getMainHandler();
        if (h == null) return;
        if (sRefreshPosted) return;
        sRefreshPosted = true;
        h.post(new Runnable() {
            @Override
            public void run() {
                sRefreshPosted = false;
                List<View> cards;
                synchronized (ACTIVE_CARDS) {
                    cards = new ArrayList<>(ACTIVE_CARDS);
                }
                for (View card : cards) {
                    if (card != null && card.isAttachedToWindow()) {
                        requestCardUpdate(card);
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

    private static Integer resolveLiveWallpaperColorForCategory(ConfigManager.Config cfg, String categoryId) {
        if (cfg == null) return null;
        ConfigManager.CategoryConfig catCfg = cfg.getCategory(categoryId);
        return (cfg.colorMode == ConfigManager.MODE_LOCK_WALLPAPER
                && (catCfg == null || !catCfg.useCustomColor))
                ? getLiveWallpaperColorForConfig(cfg)
                : null;
    }

    private static int resolveTargetArgbForCategory(String categoryId) {
        ConfigManager.Config cfg = sConfig;
        if (cfg == null) return 0xE61E293B;
        return cfg.resolveFinalArgbForCategory(categoryId, resolveLiveWallpaperColorForCategory(cfg, categoryId));
    }

    private static int resolveBlurTargetArgbForCategory(String categoryId) {
        ConfigManager.Config cfg = sConfig;
        if (cfg == null) return OneUiBlurSpec.applyTintOpacity(0x1E293B, OneUiBlurSpec.DEFAULT_TINT_OPACITY);
        return cfg.resolveBlurFinalArgbForCategory(categoryId, resolveLiveWallpaperColorForCategory(cfg, categoryId));
    }

    private static int resolveConfiguredBlurRadiusPx(ConfigManager.Config cfg) {
        return cfg != null
                ? cfg.resolveBlurRadiusPx()
                : OneUiBlurSpec.radiusPxFromPercent(OneUiBlurSpec.DEFAULT_BLUR_INTENSITY);
    }

    private static View findCollapsedBriefCard() {
        synchronized (ACTIVE_CARDS) {
            for (View card : ACTIVE_CARDS) {
                if (card != null && card.getClass().getName().endsWith("NowBarBriefCardView")) {
                    if (isCollapsedLockscreenCapsule(card)) return card;
                }
            }
        }
        return null;
    }

    private static boolean isAnyBriefCardCollapsed() {
        boolean foundBriefCard = false;
        synchronized (ACTIVE_CARDS) {
            for (View card : ACTIVE_CARDS) {
                if (card != null && card.getClass().getName().endsWith("NowBarBriefCardView")) {
                    foundBriefCard = true;
                    if (isCollapsedLockscreenCapsule(card)) return true;
                }
            }
        }
        return !foundBriefCard;
    }

    private static void scheduleAodZeroFallbackCheck(final View card) {
        if (card == null || Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(card, "nb_doze_zero_scheduled"))) {
            return;
        }
        Handler h = getMainHandler();
        if (h == null) return;
        XposedHelpers.setAdditionalInstanceField(card, "nb_doze_zero_scheduled", Boolean.TRUE);
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                XposedHelpers.setAdditionalInstanceField(card, "nb_doze_zero_scheduled", Boolean.FALSE);
                if (card.isAttachedToWindow()) {
                    requestCardUpdate(card);
                }
            }
        }, 190L);
    }

    /**
     * Returns 0.0f when fully awake on the Lockscreen, 1.0f when fully in AOD, or an
     * intermediate fraction synchronized frame-by-frame to Samsung's darkAmount (0.0 <-> 1.0).
     * Always returns 0.0f when hideBgOnAod is disabled.
     */
    private static boolean readAodBooleanState(
            View card, String trackedKey, String getterName, String setterName,
            String resolvedField, String legacyField, boolean defVal) {
        if (card == null) return defVal;
        if (getterName != null) {
            Object direct = callMethodNoArgs(card, getterName);
            if (direct instanceof Boolean) {
                return (Boolean) direct;
            }
        }
        if (resolvedField != null) {
            Object resolved = getFieldValue(card, resolvedField);
            if (resolved instanceof Boolean) {
                return (Boolean) resolved;
            }
        }
        Object tracked = XposedHelpers.getAdditionalInstanceField(card, trackedKey);
        if (tracked instanceof Boolean) {
            return (Boolean) tracked;
        }
        if (legacyField != null && (setterName == null
                || ReflectionCache.method(card.getClass(), setterName, boolean.class) == null)) {
            Object legacy = getFieldValue(card, legacyField);
            if (legacy instanceof Boolean) {
                return (Boolean) legacy;
            }
        }
        return defVal;
    }

    private static boolean readCardLockscreenMode(View card) {
        HookMap map = sHookMap;
        return readAodBooleanState(card, "nb_lockscreen_mode", "getLockscreenViewMode",
                "setLockscreenViewMode", map != null ? map.baseLockscreenModeField : null, "M", true);
    }

    private static boolean readCardDozing(View card) {
        HookMap map = sHookMap;
        return readAodBooleanState(card, "nb_dozing", "getDozing",
                "setDozing", map != null ? map.baseDozingField : null, "C", false);
    }

    private static boolean readCardInitialDozing(View card) {
        if (readAodBooleanState(card, "nb_initial_dozing", "getInitialDozing",
                "setInitialDozing", null, null, false)) {
            return true;
        }
        return activeAdapter().readInitialDozingFallback(card);
    }

    private static float readCardDarkAmount(View card) {
        if (card == null) return 0.0f;
        Object direct = callMethodNoArgs(card, "getDarkAmount");
        if (direct instanceof Float) return (Float) direct;
        Object tracked = XposedHelpers.getAdditionalInstanceField(card, "nb_dark_amount");
        if (tracked instanceof Float) return (Float) tracked;
        return activeAdapter().readDarkAmountFallback(card);
    }

    private static float computeRawCardAodProgress(View card) {
        if (card == null) return 0.0f;
        float darkAmount = readCardDarkAmount(card);
        if (darkAmount < 0.0f) darkAmount = 0.0f;
        if (darkAmount > 1.0f) darkAmount = 1.0f;
        if (darkAmount > 0.0f) {
            XposedHelpers.setAdditionalInstanceField(card, "nb_doze_zero_since", null);
            return darkAmount;
        }
        boolean dozing = readCardDozing(card);
        boolean initialDozing = readCardInitialDozing(card);
        boolean lockscreenMode = readCardLockscreenMode(card);
        if (!lockscreenMode && (isCollapsedLockscreenCapsule(card) || dozing || initialDozing)) {
            return 1.0f;
        }
        if (!card.isAttachedToWindow()) {
            return (dozing || initialDozing) ? 1.0f : 0.0f;
        }
        boolean briefScreenOn = true;
        if (card.getClass().getName().endsWith("NowBarBriefCardView")) {
            briefScreenOn = activeAdapter().resolveBriefScreenOn(card);
        }
        if (!dozing && briefScreenOn) {
            XposedHelpers.setAdditionalInstanceField(card, "nb_doze_zero_since", null);
            return 0.0f;
        }
        Long sinceNs = (Long) XposedHelpers.getAdditionalInstanceField(card, "nb_doze_zero_since");
        long nowNs = System.nanoTime();
        if (sinceNs == null) {
            XposedHelpers.setAdditionalInstanceField(card, "nb_doze_zero_since", nowNs);
            scheduleAodZeroFallbackCheck(card);
            return 0.0f;
        }
        long elapsedMs = (nowNs - sinceNs) / 1_000_000L;
        return elapsedMs >= 180L ? 1.0f : 0.0f;
    }

    private static float getCardAodProgress(View card) {
        ConfigManager.Config cfg = sConfig;
        if (card == null || cfg == null || !cfg.masterEnabled || !cfg.hideBgOnAod) {
            return 0.0f;
        }
        return computeRawCardAodProgress(card);
    }

    private static float getCardAodVisibility(View card) {
        return Math.max(0.0f, Math.min(1.0f, 1.0f - getCardAodProgress(card)));
    }

    private static float getCardEffectiveVisibility(View card, boolean isImageMode) {
        ConfigManager.Config cfg = sConfig;
        if (card == null || cfg == null || !cfg.masterEnabled) return 1.0f;
        if (cfg.hideBgOnAod) {
            return getCardAodVisibility(card);
        }
        if (!isImageMode) {
            return 1.0f;
        }
        float raw = computeRawCardAodProgress(card);
        return Math.max(ConfigManager.AOD_IMAGE_DIM_VISIBILITY,
                Math.min(1.0f, 1.0f - raw * (1.0f - ConfigManager.AOD_IMAGE_DIM_VISIBILITY)));
    }

    private static final class ImageStyleSpec {
        final Bitmap bitmap;
        final int domColor;
        final int scrimPercent;
        final int scrimMode;
        final int contrastArgb;

        ImageStyleSpec(Bitmap bitmap, int domColor, int scrimPercent, int scrimMode, int contrastArgb) {
            this.bitmap = bitmap;
            this.domColor = domColor;
            this.scrimPercent = scrimPercent;
            this.scrimMode = scrimMode;
            this.contrastArgb = contrastArgb;
        }
    }

    private static ImageStyleSpec resolveImageStyleForCategory(
            ConfigManager.Config cfg, String categoryId, Context ctx,
            final View refreshTarget, final boolean isGenUiTarget) {
        if (cfg == null || !cfg.isImageActiveForCategory(categoryId)) return null;
        String slot = cfg.resolveImageSlotForCategory(categoryId);
        long rev = cfg.resolveImageRevForCategory(categoryId);
        if (slot == null || rev <= 0L) return null;

        Context loadCtx = ctx != null ? ctx : sSysUiContext;
        if (loadCtx == null && refreshTarget != null) {
            loadCtx = refreshTarget.getContext();
        }
        Bitmap bmp = CapsuleImageCache.getOrLoadAsync(loadCtx, slot, rev, new Runnable() {
            @Override
            public void run() {
                if (refreshTarget == null) return;
                if (isGenUiTarget) {
                    applyOrRestoreGenUiBlurView(refreshTarget);
                } else {
                    requestCardUpdate(refreshTarget);
                }
            }
        });
        return new ImageStyleSpec(
                bmp,
                cfg.resolveImageDomColorForCategory(categoryId),
                cfg.resolveImageScrimPercentForCategory(categoryId),
                cfg.resolveImageScrimModeForCategory(categoryId),
                cfg.resolveImageContrastArgbForCategory(categoryId));
    }

    private static float resolveBriefVisibility(View genUiChildView) {
        return resolveBriefVisibility(genUiChildView, false);
    }

    private static float resolveBriefVisibility(View genUiChildView, boolean isImageMode) {
        View card = genUiChildView != null ? findEnclosingNowBarCard(genUiChildView) : null;
        if (card == null) {
            card = findCollapsedBriefCard();
        }
        return card != null ? getCardEffectiveVisibility(card, isImageMode) : 1.0f;
    }

    private static void applyBorderStroke(View card, boolean useBlur, float corner, float alpha) {
        applyBorderStroke(card, useBlur, corner, alpha, 0);
    }

    private static void applyBorderStroke(View card, boolean enableGlassBorder, float corner, float alpha, int dominantAccentRgb) {
        if (card == null) return;
        View border = activeAdapter().resolveBorderStrokeView(card);
        if (border == null) return;
        CapsuleStyle.applyGlassBorder(border, enableGlassBorder, corner, dominantAccentRgb);
        float clamped = alpha <= 0.005f ? 0.0f : (alpha >= 0.995f ? 1.0f : alpha);
        if (clamped >= 0.995f) {
            if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(card, "nb_border_faded"))) {
                border.setAlpha(1.0f);
                XposedHelpers.setAdditionalInstanceField(card, "nb_border_faded", Boolean.FALSE);
            }
        } else {
            border.setAlpha(clamped);
            XposedHelpers.setAdditionalInstanceField(card, "nb_border_faded", Boolean.TRUE);
        }
    }

    private static void applyAdaptiveContentContrast(View card, int startArgb, int endArgb, boolean useBlur, float vis) {
        if (card == null) return;
        if (vis <= 0.005f) {
            restoreAdaptiveContentContrast(card);
            return;
        }
        boolean applyDark = OneUiBlurSpec.needsDarkForeground(startArgb, endArgb, useBlur);
        boolean applyShadow = !applyDark && OneUiBlurSpec.needsContrastShadow(startArgb, endArgb, useBlur);
        if (!applyDark && !applyShadow) {
            restoreAdaptiveContentContrast(card);
            return;
        }
        XposedHelpers.setAdditionalInstanceField(card, "nb_contrast_applied", Boolean.TRUE);
        View normalRoot = findViewByEntryName(card, "normal_content_rootview");
        View targetRoot = normalRoot != null ? normalRoot : card;
        float density = 3.0f;
        try {
            if (card.getResources() != null) {
                float d = card.getResources().getDisplayMetrics().density;
                if (d > 0f) density = d;
            }
        } catch (Throwable ignored) {}
        ColorStateList darkTint = applyDark ? ColorStateList.valueOf(OneUiBlurSpec.DARK_CONTENT_COLOR) : null;
        applyContrastRecursive(targetRoot, 0, applyDark, applyShadow, density, darkTint);
    }

    private static void restoreAdaptiveContentContrast(View card) {
        if (card == null) return;
        if (!Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(card, "nb_contrast_applied"))) {
            return;
        }
        XposedHelpers.setAdditionalInstanceField(card, "nb_contrast_applied", Boolean.FALSE);
        View normalRoot = findViewByEntryName(card, "normal_content_rootview");
        View targetRoot = normalRoot != null ? normalRoot : card;
        applyContrastRecursive(targetRoot, 0, false, false, 3.0f, null);
    }

    private static boolean shouldSkipContrastSubtree(String entryName) {
        return "expand_content_rootview".equals(entryName)
                || "setting_content_rootview".equals(entryName)
                || "album_art_container".equals(entryName)
                || "stack_pip_app_icon_parent_layout".equals(entryName)
                || "background_shader_parent".equals(entryName);
    }

    private static boolean isMonochromeControlImage(String entryName) {
        if (entryName == null || entryName.isEmpty()) return false;
        if (entryName.contains("album") || entryName.contains("app_icon") || entryName.contains("battery_icon")) {
            return false;
        }
        return entryName.contains("button")
                || entryName.contains("play")
                || entryName.contains("pause")
                || entryName.contains("prev")
                || entryName.contains("next")
                || entryName.contains("skip")
                || entryName.equals("ongoing_action")
                || entryName.equals("ongoing_sec_action");
    }

    private static void applyContrastRecursive(
            View node, int depth, boolean applyDark, boolean applyShadow,
            float density, ColorStateList darkTint) {
        if (node == null || depth > 5) return;
        String entry = getResourceEntryNameSafe(node);
        if (shouldSkipContrastSubtree(entry)) return;

        if (node instanceof TextView) {
            TextView tv = (TextView) node;
            Object savedFlag = XposedHelpers.getAdditionalInstanceField(tv, "nb_contrast_saved");
            if ((applyDark || applyShadow) && !Boolean.TRUE.equals(savedFlag)) {
                XposedHelpers.setAdditionalInstanceField(tv, "nb_orig_text_colors", tv.getTextColors());
                XposedHelpers.setAdditionalInstanceField(tv, "nb_contrast_saved", Boolean.TRUE);
            }
            if (applyDark) {
                tv.setTextColor(OneUiBlurSpec.DARK_CONTENT_COLOR);
                tv.setShadowLayer(0f, 0f, 0f, 0);
            } else if (applyShadow) {
                Object orig = XposedHelpers.getAdditionalInstanceField(tv, "nb_orig_text_colors");
                if (orig instanceof ColorStateList) {
                    tv.setTextColor((ColorStateList) orig);
                }
                tv.setShadowLayer(2.2f * density, 0f, 0.7f * density, OneUiBlurSpec.CONTRAST_SHADOW_COLOR);
            } else if (Boolean.TRUE.equals(savedFlag)) {
                Object orig = XposedHelpers.getAdditionalInstanceField(tv, "nb_orig_text_colors");
                if (orig instanceof ColorStateList) {
                    tv.setTextColor((ColorStateList) orig);
                }
                tv.setShadowLayer(0f, 0f, 0f, 0);
                XposedHelpers.setAdditionalInstanceField(tv, "nb_orig_text_colors", null);
                XposedHelpers.setAdditionalInstanceField(tv, "nb_contrast_saved", Boolean.FALSE);
            }
        } else if (node instanceof ImageView && isMonochromeControlImage(entry)) {
            ImageView iv = (ImageView) node;
            Object savedFlag = XposedHelpers.getAdditionalInstanceField(iv, "nb_img_contrast_saved");
            if (applyDark) {
                if (!Boolean.TRUE.equals(savedFlag)) {
                    XposedHelpers.setAdditionalInstanceField(iv, "nb_orig_img_tint", iv.getImageTintList());
                    XposedHelpers.setAdditionalInstanceField(iv, "nb_img_contrast_saved", Boolean.TRUE);
                }
                iv.setImageTintList(darkTint);
            } else if (Boolean.TRUE.equals(savedFlag)) {
                ColorStateList origTint = (ColorStateList) XposedHelpers.getAdditionalInstanceField(iv, "nb_orig_img_tint");
                iv.setImageTintList(origTint);
                XposedHelpers.setAdditionalInstanceField(iv, "nb_orig_img_tint", null);
                XposedHelpers.setAdditionalInstanceField(iv, "nb_img_contrast_saved", Boolean.FALSE);
            }
        }

        if (node instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) node;
            int count = vg.getChildCount();
            for (int i = 0; i < count; i++) {
                applyContrastRecursive(vg.getChildAt(i), depth + 1, applyDark, applyShadow, density, darkTint);
            }
        }
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
                ImageStyleSpec imgSpec = resolveImageStyleForCategory(
                        cfg, ConfigManager.CAT_BRIEF, blurView.getContext(), blurView, true);
                boolean isImageMode = (imgSpec != null);
                boolean useBlur = !isImageMode && (cfg != null && cfg.styleMode == ConfigManager.STYLE_BLUR);
                int targetArgb = useBlur
                        ? resolveBlurTargetArgbForCategory(ConfigManager.CAT_BRIEF)
                        : resolveTargetArgbForCategory(ConfigManager.CAT_BRIEF);
                int[] grad = (!isImageMode && cfg != null)
                        ? (useBlur
                            ? cfg.resolveBlurGradientArgbForCategory(ConfigManager.CAT_BRIEF)
                            : cfg.resolveGradientArgbForCategory(ConfigManager.CAT_BRIEF))
                        : null;
                int blurRadiusPx = useBlur ? resolveConfiguredBlurRadiusPx(cfg) : 0;
                float vis = resolveBriefVisibility(blurView, isImageMode);

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

                View card = findEnclosingNowBarCard(blurView);
                boolean drawBorderFg = (activeAdapter().resolveBorderStrokeView(card) == null);
                XposedHelpers.setAdditionalInstanceField(blurView, "nb_genui_blur_applied", Boolean.TRUE);
                if (isImageMode) {
                    setCardViewImageAndStyle(blurView, imgSpec, cornerPx, vis, drawBorderFg);
                } else {
                    setCardViewColorAndStyle(blurView, targetArgb, useBlur, blurRadiusPx, cornerPx, grad, vis, drawBorderFg);
                }
            } else {
                Object applied = XposedHelpers.getAdditionalInstanceField(blurView, "nb_genui_blur_applied");
                if (Boolean.TRUE.equals(applied)) {
                    XposedHelpers.setAdditionalInstanceField(blurView, "nb_genui_blur_applied", Boolean.FALSE);
                    XposedHelpers.setAdditionalInstanceField(blurView, "nb_restoring_blur", Boolean.TRUE);
                    try {
                        CapsuleStyle.restoreElevation(blurView);
                        restoreGradientIfNeeded(blurView);
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

    private static void ensureGenUiAttachListener(View view, final boolean isBlurNode) {
        if (view == null) return;
        if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(view, "nb_genui_attach_listener"))) {
            return;
        }
        XposedHelpers.setAdditionalInstanceField(view, "nb_genui_attach_listener", Boolean.TRUE);
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                if (isBlurNode) {
                    applyOrRestoreGenUiBlurView(v);
                } else {
                    applyOrRestoreGenUiGuidingView(v);
                }
                View card = findEnclosingNowBarCard(v);
                if (card != null) {
                    requestCardUpdate(card);
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
            }
        });
    }

    private static boolean hasActiveAttachedView(Set<View> trackedViews, View rootCard) {
        List<View> snapshot;
        synchronized (trackedViews) {
            snapshot = new ArrayList<>(trackedViews);
        }
        for (View v : snapshot) {
            if (v == null || !v.isAttachedToWindow()) continue;
            if (rootCard == null || findEnclosingNowBarCard(v) == rootCard) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasActiveGenUiBlurViewForCard(View rootCard) {
        return hasActiveAttachedView(ACTIVE_GENUI_BLUR_VIEWS, rootCard);
    }

    private static boolean hasAnyAttachedGenUiView() {
        return hasActiveAttachedView(ACTIVE_GENUI_BLUR_VIEWS, null)
                || hasActiveAttachedView(ACTIVE_GENUI_GUIDING_VIEWS, null);
    }

    private static void applyOrRestoreGenUiGuidingView(View glView) {
        if (glView == null) return;
        try {
            boolean shouldCustom = shouldCustomizeCatBrief() && isBriefViewCollapsed(glView);
            if (shouldCustom) {
                ConfigManager.Config cfg = sConfig;
                View card = findEnclosingNowBarCard(glView);
                ImageStyleSpec imgSpec = resolveImageStyleForCategory(
                        cfg, ConfigManager.CAT_BRIEF, glView.getContext(), card != null ? card : glView, card == null);
                boolean isImageMode = (imgSpec != null);
                boolean useBlur = !isImageMode && (cfg != null && cfg.styleMode == ConfigManager.STYLE_BLUR);
                int targetArgb = useBlur
                        ? resolveBlurTargetArgbForCategory(ConfigManager.CAT_BRIEF)
                        : resolveTargetArgbForCategory(ConfigManager.CAT_BRIEF);
                int[] grad = (!isImageMode && cfg != null)
                        ? (useBlur
                            ? cfg.resolveBlurGradientArgbForCategory(ConfigManager.CAT_BRIEF)
                            : cfg.resolveGradientArgbForCategory(ConfigManager.CAT_BRIEF))
                        : null;
                int blurRadiusPx = useBlur ? resolveConfiguredBlurRadiusPx(cfg) : 0;
                float vis = resolveBriefVisibility(glView, isImageMode);
                float density = glView.getContext().getResources().getDisplayMetrics().density;
                float cornerPx = 34.0f * (density > 0f ? density : 3.0f);

                XposedHelpers.setAdditionalInstanceField(glView, "nb_genui_gl_applied", Boolean.TRUE);

                // Stop Samsung's opaque RuntimeShader on GuidingLightEffectLayout so our solid or blur pill shows cleanly.
                if (Build.VERSION.SDK_INT >= 31) {
                    try {
                        glView.setRenderEffect(null);
                    } catch (Throwable ignored) {}
                }
                try {
                    Object gle = XposedHelpers.getObjectField(glView, "guidingLightEffect");
                    if (gle != null) {
                        XposedHelpers.callMethod(gle, "j");
                    }
                } catch (Throwable ignored) {}

                // GenUIRenderer skips StyleBox.Blur when strokeStyle is "without_stroke" on GuidingLight pills,
                // leaving GuidingLightEffectLayout (glView) as the ONLY Android View for the Now Brief capsule.
                // When a separate StyleBox.Blur sibling view is attached, blurView handles all modes cleanly.
                boolean hasBlurSibling = hasActiveGenUiBlurViewForCard(card);
                if (hasBlurSibling) {
                    glView.setAlpha(0.0f);
                    CapsuleStyle.transparent(glView);
                } else {
                    glView.setAlpha(1.0f);
                    boolean drawBorderFg = (activeAdapter().resolveBorderStrokeView(card) == null);
                    if (isImageMode) {
                        setCardViewImageAndStyle(glView, imgSpec, cornerPx, vis, drawBorderFg);
                    } else {
                        setCardViewColorAndStyle(glView, targetArgb, useBlur, blurRadiusPx, cornerPx, grad, vis, drawBorderFg);
                    }
                }
            } else {
                Object applied = XposedHelpers.getAdditionalInstanceField(glView, "nb_genui_gl_applied");
                if (Boolean.TRUE.equals(applied)) {
                    XposedHelpers.setAdditionalInstanceField(glView, "nb_genui_gl_applied", Boolean.FALSE);
                    CapsuleStyle.restoreElevation(glView);
                    glView.setAlpha(1.0f);
                    restoreGradientIfNeeded(glView);
                    glView.setBackground(null);
                    clearBlurInfo(glView);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static Boolean readNativeExpandedState(View card) {
        if (card == null) return null;
        Object direct = callMethodNoArgs(card, "isExpanded");
        if (direct instanceof Boolean) return (Boolean) direct;
        Object getter = callMethodNoArgs(card, "getExpanded");
        if (getter instanceof Boolean) return (Boolean) getter;
        HookMap map = sHookMap;
        if (map != null && map.baseExpandField != null) {
            Object resolved = getFieldValue(card, map.baseExpandField);
            if (resolved instanceof Boolean) return (Boolean) resolved;
        }
        return activeAdapter().readExpandedFallback(card);
    }

    private static boolean isCardExpanded(View card) {
        if (card == null) return false;
        Boolean nativeExp = readNativeExpandedState(card);
        if (nativeExp != null) {
            return nativeExp;
        }
        Object tracked = XposedHelpers.getAdditionalInstanceField(card, "nb_expanded");
        if (tracked instanceof Boolean) {
            return (Boolean) tracked;
        }
        return false;
    }

    private static void handleExpandTransition(View card, Boolean nextExpanded, boolean beforeCall) {
        if (card == null) return;
        if (nextExpanded != null) {
            XposedHelpers.setAdditionalInstanceField(card, "nb_expanded", nextExpanded);
        }
        if (beforeCall) {
            if (isCollapsedLockscreenCapsule(card)) {
                syncOrKeepMediaOnTransition(card);
            } else {
                keepExpandedMediaBackdropIfNeeded(card);
            }
            return;
        }
        armWakeTransitionWatch(1200L);
        syncOrKeepMediaOnTransition(card);
        requestCardUpdate(card);
        if (card.getClass().getName().endsWith("NowBarMediaCardView") && !isCollapsedLockscreenCapsule(card)) {
            captureExpandedTreeDiagnosticsDelayed(card);
        }
    }

    private static int getCardStateValue(View card) {
        if (card == null) return 0;
        Object stateObj = callMethodNoArgs(card, "getCardState");
        if (stateObj instanceof Integer) {
            return (Integer) stateObj;
        }
        Object tracked = XposedHelpers.getAdditionalInstanceField(card, "nb_card_state");
        if (tracked instanceof Integer) {
            return (Integer) tracked;
        }
        return activeAdapter().readCardStateFallback(card);
    }

    private static boolean isCardInSettingMode(View card) {
        return getCardStateValue(card) == 2;
    }

    /**
     * Checks if the NowBar capsule is currently in its small/collapsed state on the Lockscreen
     * (!isExpanded, cardState == 0). Keeps style pre-applied at all times (including screen-off/AOD)
     * so that upon screen-on it appears instantly with 0 ms delay.
     */
    private static boolean isCollapsedLockscreenCapsule(View card) {
        return card != null && !isCardExpanded(card) && getCardStateValue(card) == 0;
    }

    private static boolean shouldKeepExpandedMediaBackdropTransparent(View card) {
        return card != null
                && card.getClass().getName().endsWith("NowBarMediaCardView")
                && shouldCustomizeCatMedia()
                && !isCardInSettingMode(card);
    }

    private static void syncMediaSurfaceProtection(View card) {
        if (card == null || !card.getClass().getName().endsWith("NowBarMediaCardView")) return;
        NowBarCardAdapter adapter = activeAdapter();
        View p1 = adapter.resolveMediaPillSurface(card);
        View o1 = adapter.resolveMediaBackdropSurface(card);
        View t1 = adapter.resolveMediaSettingBgSurface(card);
        boolean active = shouldCustomizeCatMedia() && !isCardInSettingMode(card);
        if (active) {
            if (o1 != null) MEDIA_BACKDROP_SURFACES.add(o1);
            if (t1 != null) MEDIA_BACKDROP_SURFACES.add(t1);
            if (p1 != null) MEDIA_PILL_SURFACES.add(p1);
        } else {
            if (o1 != null) MEDIA_BACKDROP_SURFACES.remove(o1);
            if (t1 != null) MEDIA_BACKDROP_SURFACES.remove(t1);
            if (p1 != null) MEDIA_PILL_SURFACES.remove(p1);
        }
    }

    private static void clearBackdropSurface(View surface, boolean aodHidden) {
        if (surface == null) return;
        if (Build.VERSION.SDK_INT >= 23 && surface.getForeground() != null) {
            surface.setForeground(null);
        }
        clearBlurInfo(surface);
        if (aodHidden) {
            CapsuleStyle.transparent(surface);
        } else {
            CapsuleStyle.restoreGradient(surface);
            if (surface.getBackgroundTintList() != null) {
                surface.setBackgroundTintList(null);
            }
            android.graphics.drawable.Drawable bg = surface.getBackground();
            if (bg instanceof android.graphics.drawable.GradientDrawable
                    || bg instanceof android.graphics.drawable.ColorDrawable) {
                surface.setBackground(null);
            }
            CapsuleStyle.setElevationScale(surface, 0f);
            setCardBackgroundColor(surface, Color.TRANSPARENT);
        }
    }

    private static void keepExpandedMediaBackdropIfNeeded(View card) {
        if (!shouldKeepExpandedMediaBackdropTransparent(card)) return;
        if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(card, "nb_applying"))
                || Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(card, "nb_restoring"))) {
            return;
        }
        XposedHelpers.setAdditionalInstanceField(card, "nb_applying", Boolean.TRUE);
        try {
            applyExpandedMediaBackdropClear(card);
        } finally {
            XposedHelpers.setAdditionalInstanceField(card, "nb_applying", Boolean.FALSE);
        }
    }

    private static void applyExpandedMediaBackdropClear(View card) {
        syncMediaSurfaceProtection(card);
        if (!shouldKeepExpandedMediaBackdropTransparent(card)) return;
        NowBarCardAdapter adapter = activeAdapter();
        View v1 = adapter.resolveMediaShaderView(card);
        View w1 = adapter.resolveMediaShaderParent(card);
        View p1 = adapter.resolveMediaPillSurface(card);
        View o1 = adapter.resolveMediaBackdropSurface(card);
        View t1 = adapter.resolveMediaSettingBgSurface(card);
        if (v1 != null) v1.setAlpha(0.0f);
        if (w1 != null) w1.setAlpha(0.0f);
        if (o1 != null && p1 != null) {
            clearBackdropSurface(o1, false);
        }
        if (t1 != null) {
            clearBackdropSurface(t1, false);
        }
        if (p1 != null && !isCollapsedLockscreenCapsule(card)) {
            if (Build.VERSION.SDK_INT >= 23 && p1.getForeground() != null) {
                p1.setForeground(null);
            }
            clearBlurInfo(p1);
            setCardBackgroundColor(p1, Color.TRANSPARENT);
        }
    }

    private static void syncOrKeepMediaOnTransition(View card) {
        if (card == null
                || Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(card, "nb_applying"))
                || Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(card, "nb_restoring"))) {
            return;
        }
        ConfigManager.Config cfg = sConfig;
        if (cfg == null) return;
        if (isCollapsedLockscreenCapsule(card)) {
            if (cfg.masterEnabled) {
                String catId = resolveCategoryForCard(card);
                ConfigManager.CategoryConfig catCfg = cfg.getCategory(catId);
                if (catCfg != null && catCfg.enabled) {
                    applyOrRestoreCardBackground(card);
                    return;
                }
            }
            if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(card, "nb_custom_applied"))) {
                applyOrRestoreCardBackground(card);
            }
            return;
        }
        if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(card, "nb_custom_applied"))) {
            applyOrRestoreCardBackground(card);
        } else {
            keepExpandedMediaBackdropIfNeeded(card);
            if (card.getClass().getName().endsWith("NowBarMediaCardView")) {
                captureExpandedTreeDiagnosticsIfNeeded(card);
            }
        }
    }

    private static String collectCardText(View view) {
        StringBuilder out = new StringBuilder();
        appendCardText(view, out);
        return out.toString();
    }

    private static void appendCardText(View view, StringBuilder out) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && text.length() > 0) out.append(text).append(' ');
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int count = Math.min(group.getChildCount(), 15);
            for (int i = 0; i < count; i++) appendCardText(group.getChildAt(i), out);
        }
    }

    private static String notificationPart(String id, int index) {
        if (id == null) return "";
        int start = 0;
        for (int i = 0; i < index; i++) {
            int separator = id.indexOf('|', start);
            if (separator < 0) return "";
            start = separator + 1;
        }
        int end = id.indexOf('|', start);
        return id.substring(start, end < 0 ? id.length() : end).toLowerCase(Locale.ROOT).trim();
    }

    private static String extractPackageFromNotiId(String id) { return notificationPart(id, 1); }
    private static String extractIdFromNotiId(String id) { return notificationPart(id, 2); }
    private static String extractTagFromNotiId(String id) { return notificationPart(id, 3); }

    private static String resolveCategoryForCard(View card) {
        if (card == null) return ConfigManager.CAT_OTHER_ONGOING;
        String clsName = card.getClass().getName();
        if (clsName.endsWith("NowBarMediaCardView")) return ConfigManager.CAT_MEDIA;
        Object item = activeAdapter().resolveCardItem(card);

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
            if (pkgObj instanceof String) pkg = ((String) pkgObj).toLowerCase(Locale.ROOT).trim();

            Object notiIdObj = callMethodNoArgs(item, "getNotiID");
            if (notiIdObj instanceof String) {
                notiId = ((String) notiIdObj).trim();
                notiPkg = extractPackageFromNotiId(notiId);
                notiNumericId = extractIdFromNotiId(notiId);
                notiTag = extractTagFromNotiId(notiId);
            }

            Object keyObj = callMethodNoArgs(item, "getNowBarKey");
            if (keyObj instanceof String) nowBarKey = ((String) keyObj).toLowerCase(Locale.ROOT).trim();

            Object pInfoObj = callMethodNoArgs(item, "getPrimaryInfo");
            if (pInfoObj instanceof String) primaryInfo = ((String) pInfoObj).toLowerCase(Locale.ROOT).trim();

            Object sInfoObj = callMethodNoArgs(item, "getSecondaryInfo");
            if (sInfoObj instanceof String) secondaryInfo = ((String) sInfoObj).toLowerCase(Locale.ROOT).trim();

            Object npInfoObj = callMethodNoArgs(item, "getNowbarPrimaryInfo");
            if (npInfoObj instanceof String) nowbarPrimaryInfo = ((String) npInfoObj).toLowerCase(Locale.ROOT).trim();

            Object nsInfoObj = callMethodNoArgs(item, "getNowbarSecondaryInfo");
            if (nsInfoObj instanceof String) nowbarSecondaryInfo = ((String) nsInfoObj).toLowerCase(Locale.ROOT).trim();

            Object extraDataObj = callMethodNoArgs(item, "getExtraData");
            if (extraDataObj instanceof android.os.Bundle) {
                android.os.Bundle b = (android.os.Bundle) extraDataObj;
                String ap = b.getString("nowbar_key_actual_package_name");
                if (ap != null) actualPkg = ap.toLowerCase(Locale.ROOT).trim();
                int callType = b.getInt("nowbar_key_call_type", 0);
                if (callType == 1 || callType == 2) isCall = true;
            }

            Object styleObj = callMethodNoArgs(item, "getNowBarViewStyle");
            if (styleObj instanceof Integer) viewStyle = (Integer) styleObj;

            Object callObj = callMethodNoArgs(item, "isCall");
            if (callObj instanceof Boolean && (Boolean) callObj) isCall = true;
        }

        String cardText = collectCardText(card).toLowerCase(Locale.ROOT);
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

        return category;
    }

    private static void applyOrRestoreCardBackground(View card) {
        if (card == null) return;
        if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(card, "nb_applying"))) {
            return;
        }
        XposedHelpers.setAdditionalInstanceField(card, "nb_applying", Boolean.TRUE);
        try {
            ConfigManager.Config cfg = sConfig;
            if (cfg == null || !cfg.masterEnabled || !isCollapsedLockscreenCapsule(card)) {
                restoreOriginalCardBackgroundIfNeeded(card);
                applyExpandedMediaBackdropClear(card);
                if (card.getClass().getName().endsWith("NowBarMediaCardView")
                        && (isCardExpanded(card) || isCardInSettingMode(card))) {
                    captureExpandedTreeDiagnosticsIfNeeded(card);
                }
                return;
            }
            String categoryId = resolveCategoryForCard(card);
            ConfigManager.CategoryConfig catCfg = cfg.getCategory(categoryId);

            if (catCfg == null || !catCfg.enabled) {
                restoreOriginalCardBackgroundIfNeeded(card);
                return;
            }

            ImageStyleSpec imgSpec = resolveImageStyleForCategory(
                    cfg, categoryId, card.getContext(), card, false);
            boolean isImageMode = (imgSpec != null);
            boolean useBlur = !isImageMode && (cfg.styleMode == ConfigManager.STYLE_BLUR);
            int targetArgb = useBlur
                    ? resolveBlurTargetArgbForCategory(categoryId)
                    : resolveTargetArgbForCategory(categoryId);
            int[] grad = !isImageMode
                    ? (useBlur
                        ? cfg.resolveBlurGradientArgbForCategory(categoryId)
                        : cfg.resolveGradientArgbForCategory(categoryId))
                    : null;
            int blurRadiusPx = useBlur ? resolveConfiguredBlurRadiusPx(cfg) : 0;
            float vis = getCardEffectiveVisibility(card, isImageMode);
            float radius = activeAdapter().resolveCardRadius(card, 100.0f);
            if (radius <= 0f) radius = 100.0f;

            String clsName = card.getClass().getName();
            XposedHelpers.setAdditionalInstanceField(card, "nb_custom_applied", Boolean.TRUE);
            int borderTint = isImageMode ? imgSpec.domColor : 0;
            applyBorderStroke(card, (useBlur || isImageMode) && vis > 0.005f, radius, vis, borderTint);
            int contrastStart = isImageMode ? imgSpec.contrastArgb : ((grad != null && grad.length >= 2) ? grad[0] : targetArgb);
            int contrastEnd = isImageMode ? imgSpec.contrastArgb : ((grad != null && grad.length >= 2) ? grad[1] : targetArgb);
            applyAdaptiveContentContrast(card, contrastStart, contrastEnd, useBlur, vis);

            boolean aodHiddenMode = cfg.hideBgOnAod && vis <= 0.005f;
            sLastCardSummary = "cat=" + categoryId
                    + " vis=" + String.format(Locale.US, "%.2f", vis)
                    + (isImageMode ? (" img=true scrim=" + imgSpec.scrimPercent + "%") : (" blur=" + useBlur))
                    + (useBlur ? (" blurPx=" + blurRadiusPx) : "")
                    + " hideAod=" + cfg.hideBgOnAod
                    + " cls=" + card.getClass().getSimpleName();
            if (aodHiddenMode) {
                sLastAodSummary = sLastCardSummary;
            } else if (vis >= 0.995f) {
                sLastLockSummary = sLastCardSummary;
            }

            boolean drawBorderInFg = (activeAdapter().resolveBorderStrokeView(card) == null);
            if (clsName.endsWith("NowBarMediaCardView")) {
                applyToMediaCard(card, imgSpec, targetArgb, useBlur, blurRadiusPx, radius, grad, vis, drawBorderInFg);
            } else {
                applyToStandardCard(card, imgSpec, clsName, targetArgb, useBlur, blurRadiusPx, radius, grad, vis, drawBorderInFg);
            }
            captureTreeDiagnosticsIfNeeded(card, aodHiddenMode, vis);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": applyOrRestoreCardBackground error: " + t.getMessage());
        } finally {
            XposedHelpers.setAdditionalInstanceField(card, "nb_applying", Boolean.FALSE);
        }
    }

    private static void applyToMediaCard(
            View card, ImageStyleSpec imgSpec, int targetArgb, boolean useBlur, int blurRadiusPx,
            float radius, int[] grad, float vis, boolean drawBorderInFg) {
        if (Build.VERSION.SDK_INT >= 23) {
            card.setForeground(null);
        }
        syncMediaSurfaceProtection(card);
        NowBarCardAdapter adapter = activeAdapter();
        View p1 = adapter.resolveMediaPillSurface(card);
        View o1 = adapter.resolveMediaBackdropSurface(card);
        View t1 = adapter.resolveMediaSettingBgSurface(card);
        View v1 = adapter.resolveMediaShaderView(card);
        View w1 = adapter.resolveMediaShaderParent(card);

        // Hide the animated shader background while collapsed so custom color is clean,
        // without touching album art, title text, wave, or action buttons.
        if (v1 != null) {
            v1.setAlpha(0.0f);
        }
        if (w1 != null) {
            w1.setAlpha(0.0f);
        }

        // o1 (nowbar_cardview_background) holds expanded card dimensions in NowBarMediaCardView.
        // Always keep it and t1 transparent & elevation-free when p1 exists; otherwise o1 is fallback pill surface.
        if (o1 != null && p1 != null) {
            clearBackdropSurface(o1, vis <= 0.005f);
        }
        if (t1 != null) {
            clearBackdropSurface(t1, vis <= 0.005f);
        }

        View mediaTarget = p1 != null ? p1 : o1;
        if (mediaTarget != null) {
            if (Build.VERSION.SDK_INT >= 23 && (vis <= 0.005f || !useBlur)) {
                mediaTarget.setForeground(null);
            }
            if (imgSpec != null) {
                setCardViewImageAndStyle(mediaTarget, imgSpec, radius, vis, drawBorderInFg);
            } else {
                setCardViewColorAndStyle(mediaTarget, targetArgb, useBlur, blurRadiusPx, radius, grad, vis, drawBorderInFg);
            }
        }
        syncResidualAodContainers(card, vis <= 0.005f, p1, o1);
    }

    private static void applyToStandardCard(
            View card, ImageStyleSpec imgSpec, String clsName, int targetArgb, boolean useBlur, int blurRadiusPx,
            float radius, int[] grad, float vis, boolean drawBorderInFg) {
        NowBarCardAdapter adapter = activeAdapter();
        View cardContainer = adapter.resolveCardContainer(card);
        View mpsmContainer = adapter.resolveMpsmContainer(card);
        View guidingBg = adapter.resolveGuidingBgView(card);
        View gradientBg = adapter.resolveGradientBgView(card);
        View outerGlow = adapter.resolveOuterGlowView(card);

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
        if (mpsmContainer != null && (mpsmContainer.getVisibility() == View.VISIBLE || vis <= 0.005f)) {
            if (imgSpec != null) {
                setCardViewImageAndStyle(mpsmContainer, imgSpec, radius, vis, drawBorderInFg);
            } else {
                setCardViewColorAndStyle(mpsmContainer, targetArgb, useBlur, blurRadiusPx, radius, grad, vis, drawBorderInFg);
            }
        }
        if (cardContainer != null) {
            if (imgSpec != null) {
                setCardViewImageAndStyle(cardContainer, imgSpec, radius, vis, drawBorderInFg);
            } else {
                setCardViewColorAndStyle(cardContainer, targetArgb, useBlur, blurRadiusPx, radius, grad, vis, drawBorderInFg);
            }
        }
        syncResidualAodContainers(card, vis <= 0.005f, cardContainer, mpsmContainer);

        if (clsName.endsWith("NowBarBatteryCardView")) {
            // Subtle progress fill alpha so the user's chosen capsule background color dominates cleanly
            // and fades to 0 in transparent AOD mode.
            View progressBar = adapter.resolveBatteryProgressBar(card);
            if (progressBar != null) {
                progressBar.setAlpha(vis <= 0.005f ? 0.0f : (0.28f * vis));
            }
        } else if (clsName.endsWith("NowBarOngoingCardView")) {
            // Ensure normal_text_container and chronometer view remain visible (alpha 1.0)
            View normalTextContainer = adapter.resolveNormalTextContainer(card);
            if (normalTextContainer != null && normalTextContainer.getAlpha() < 1.0f) {
                normalTextContainer.setAlpha(1.0f);
            }
            View chronometerView = adapter.resolveChronometerView(card);
            if (chronometerView != null && chronometerView.getAlpha() < 1.0f) {
                chronometerView.setAlpha(1.0f);
            }

            // Hide AI Nudge & AI Presence shader overlays while collapsed so custom color is clean
            View nudgeContainer = adapter.resolveNudgeContainer(card);
            View aiPresenceContainer = adapter.resolveAiPresenceContainer(card);
            View aiPresenceView = adapter.resolveAiPresenceView(card);
            if (nudgeContainer != null) nudgeContainer.setAlpha(0.0f);
            if (aiPresenceContainer != null) aiPresenceContainer.setAlpha(0.0f);
            if (aiPresenceView != null) {
                try {
                    aiPresenceView.animate().cancel();
                } catch (Throwable ignored) {}
                aiPresenceView.setAlpha(0.0f);
            }
        } else if (clsName.endsWith("NowBarBriefCardView")) {
            boolean foundGenUiSurface = updateChildGenUiViews(card);
            View briefBg = adapter.resolveBriefBackgroundView(card);
            if (briefBg != null) {
                try {
                    XposedHelpers.callMethod(briefBg, "setRadius", radius);
                } catch (Throwable ignored) {}
                if (vis <= 0.005f) {
                    restoreGradientIfNeeded(briefBg);
                    CapsuleStyle.transparent(briefBg);
                } else if (foundGenUiSurface) {
                    CapsuleStyle.restoreElevation(briefBg);
                    restoreGradientIfNeeded(briefBg);
                    clearBlurInfo(briefBg);
                    setCardBackgroundColor(briefBg, Color.TRANSPARENT);
                } else {
                    try {
                        ViewGroup.LayoutParams lp = briefBg.getLayoutParams();
                        int normalW = (int) adapter.resolveNormalCardWidth(card);
                        int normalH = (int) adapter.resolveNormalCardHeight(card);
                        if (lp != null && normalW > 0 && normalH > 0 && (lp.width <= 0 || lp.height <= 0)) {
                            lp.width = normalW;
                            lp.height = normalH;
                            if (lp instanceof android.widget.FrameLayout.LayoutParams) {
                                ((android.widget.FrameLayout.LayoutParams) lp).gravity = 81;
                            }
                            briefBg.setLayoutParams(lp);
                        }
                    } catch (Throwable ignored) {}
                    if (imgSpec != null) {
                        setCardViewImageAndStyle(briefBg, imgSpec, radius, vis, drawBorderInFg);
                    } else {
                        setCardViewColorAndStyle(briefBg, targetArgb, useBlur, blurRadiusPx, radius, grad, vis, drawBorderInFg);
                    }
                }
            }
        }
    }

    private static boolean updateChildGenUiViews(View rootCard) {
        boolean foundGenUiSurface = false;
        List<View> blurViews;
        synchronized (ACTIVE_GENUI_BLUR_VIEWS) {
            blurViews = new ArrayList<>(ACTIVE_GENUI_BLUR_VIEWS);
        }
        for (View bv : blurViews) {
            if (bv != null && bv.isAttachedToWindow() && findEnclosingNowBarCard(bv) == rootCard) {
                foundGenUiSurface = true;
                applyOrRestoreGenUiBlurView(bv);
            }
        }
        List<View> glViews;
        synchronized (ACTIVE_GENUI_GUIDING_VIEWS) {
            glViews = new ArrayList<>(ACTIVE_GENUI_GUIDING_VIEWS);
        }
        for (View gv : glViews) {
            if (gv != null && gv.isAttachedToWindow() && findEnclosingNowBarCard(gv) == rootCard) {
                foundGenUiSurface = true;
                applyOrRestoreGenUiGuidingView(gv);
            }
        }
        return foundGenUiSurface;
    }

    private static void restoreOriginalCardBackgroundIfNeeded(View card) {
        Object applied = XposedHelpers.getAdditionalInstanceField(card, "nb_custom_applied");
        if (!Boolean.TRUE.equals(applied)) {
            return;
        }
        XposedHelpers.setAdditionalInstanceField(card, "nb_custom_applied", Boolean.FALSE);
        XposedHelpers.setAdditionalInstanceField(card, "nb_restoring", Boolean.TRUE);
        NowBarCardAdapter adapter = activeAdapter();
        try {
            applyBorderStroke(card, false, 100.0f, 1.0f);
            restoreAdaptiveContentContrast(card);
            syncResidualAodContainers(card, false, null, null);
            syncMediaSurfaceProtection(card);
            String clsName = card.getClass().getName();
            if (clsName.endsWith("NowBarMediaCardView")) {
                View v1 = adapter.resolveMediaShaderView(card);
                View w1 = adapter.resolveMediaShaderParent(card);
                View p1 = adapter.resolveMediaPillSurface(card);
                View o1 = adapter.resolveMediaBackdropSurface(card);
                View t1 = adapter.resolveMediaSettingBgSurface(card);

                boolean isExpanded = isCardExpanded(card);
                boolean isNotNormal = isCardInSettingMode(card);

                if (isExpanded || isNotNormal) {
                    // The card is expanding into full widget view or opening settings.
                    // Keep the animated lighting shader hidden and foreground null so NO light rectangle flashes!
                    if (v1 != null) v1.setAlpha(0.0f);
                    if (w1 != null) w1.setAlpha(0.0f);
                    if (p1 != null) {
                        CapsuleStyle.restoreElevation(p1);
                        if (Build.VERSION.SDK_INT >= 23) {
                            p1.setForeground(null);
                        }
                        restoreGradientIfNeeded(p1);
                        if (!isNotNormal && shouldCustomizeCatMedia()) {
                            clearBlurInfo(p1);
                            setCardBackgroundColor(p1, Color.TRANSPARENT);
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 23) {
                        card.setForeground(null);
                    }
                    if (!isNotNormal && shouldCustomizeCatMedia()) {
                        // Keep o1 and t1 transparent & elevation-free behind the animating expanded music page.
                        if (o1 != null && p1 != null) {
                            clearBackdropSurface(o1, false);
                        }
                        if (t1 != null) {
                            clearBackdropSurface(t1, false);
                        }
                    } else {
                        if (o1 != null) {
                            CapsuleStyle.restoreElevation(o1);
                            if (Build.VERSION.SDK_INT >= 23) {
                                o1.setForeground(null);
                            }
                            restoreGradientIfNeeded(o1);
                        }
                        HookMap activeMap = sHookMap;
                        if (activeMap != null) {
                            adapter.restoreBriefCardExtra(card, activeMap, 0.0f);
                        } else if (adapter instanceof OneUi9Adapter) {
                            try {
                                XposedHelpers.callMethod(card, "h", 0.0f);
                            } catch (Throwable ignored) {}
                        }
                    }
                    return;
                }

                if (v1 != null) v1.setAlpha(1.0f);
                if (w1 != null) w1.setAlpha(1.0f);
                if (p1 != null) {
                    CapsuleStyle.restoreElevation(p1);
                    restoreGradientIfNeeded(p1);
                    clearBlurInfo(p1);
                    setCardBackgroundColor(p1, Color.TRANSPARENT);
                }
                if (o1 != null) {
                    CapsuleStyle.restoreElevation(o1);
                    restoreGradientIfNeeded(o1);
                }
                HookMap activeMap = sHookMap;
                String blackAodMethod = (activeMap != null)
                        ? activeMap.mediaBlackAodMethod
                        : (adapter instanceof OneUi9Adapter ? "i0" : null);
                Object item = adapter.resolveCardItem(card);
                float curRadius = adapter.resolveMediaBlackAodRadius(card, 100.0f);
                if (item != null && blackAodMethod != null) {
                    try {
                        XposedHelpers.callMethod(card, blackAodMethod, item, curRadius);
                    } catch (Throwable ignored) {}
                }
                if (activeMap != null) {
                    adapter.restoreBriefCardExtra(card, activeMap, 0.0f);
                } else if (adapter instanceof OneUi9Adapter) {
                    try {
                        XposedHelpers.callMethod(card, "h", 0.0f);
                    } catch (Throwable ignored) {}
                }
                adapter.triggerShapeRefresh(card, activeMap);
            } else {
                View cardContainer = adapter.resolveCardContainer(card);
                View mpsmContainer = adapter.resolveMpsmContainer(card);
                CapsuleStyle.restoreElevation(cardContainer);
                CapsuleStyle.restoreElevation(mpsmContainer);
                restoreGradientIfNeeded(cardContainer);
                restoreGradientIfNeeded(mpsmContainer);

                View guidingBg = adapter.resolveGuidingBgView(card);
                View gradientBg = adapter.resolveGradientBgView(card);
                View outerGlow = adapter.resolveOuterGlowView(card);
                if (guidingBg != null) guidingBg.setAlpha(1.0f);
                if (gradientBg != null) {
                    gradientBg.setVisibility(View.VISIBLE);
                    gradientBg.setAlpha(1.0f);
                }
                if (outerGlow != null) outerGlow.setAlpha(1.0f);

                if (clsName.endsWith("NowBarBatteryCardView")) {
                    View progressBar = adapter.resolveBatteryProgressBar(card);
                    if (progressBar != null) progressBar.setAlpha(1.0f);
                } else if (clsName.endsWith("NowBarOngoingCardView")) {
                    View normalTextContainer = adapter.resolveNormalTextContainer(card);
                    if (normalTextContainer != null) normalTextContainer.setAlpha(1.0f);
                    View chronometerView = adapter.resolveChronometerView(card);
                    if (chronometerView != null) chronometerView.setAlpha(1.0f);

                    View nudgeContainer = adapter.resolveNudgeContainer(card);
                    View aiPresenceContainer = adapter.resolveAiPresenceContainer(card);
                    View aiPresenceView = adapter.resolveAiPresenceView(card);
                    if (nudgeContainer != null) nudgeContainer.setAlpha(1.0f);
                    if (aiPresenceContainer != null) aiPresenceContainer.setAlpha(1.0f);
                    if (aiPresenceView != null) aiPresenceView.setAlpha(1.0f);
                } else if (clsName.endsWith("NowBarBriefCardView")) {
                    View briefBg = adapter.resolveBriefBackgroundView(card);
                    CapsuleStyle.restoreElevation(briefBg);
                    restoreGradientIfNeeded(briefBg);
                    updateChildGenUiViews(card);
                    adapter.restoreBriefCardExtra(card, sHookMap, readCardDarkAmount(card));
                }

                adapter.triggerShapeRefresh(card, sHookMap);
            }
        } finally {
            XposedHelpers.setAdditionalInstanceField(card, "nb_restoring", Boolean.FALSE);
        }
    }

    private static void setCardViewColorAndStyle(View cardView, int argbColor, boolean useBlur, float radius) {
        setCardViewColorAndStyle(cardView, argbColor, useBlur, radius, null, 1.0f);
    }

    private static void setCardViewColorAndStyle(View cardView, int argbColor, boolean useBlur, float radius, int[] grad) {
        setCardViewColorAndStyle(cardView, argbColor, useBlur, radius, grad, 1.0f);
    }

    private static void setCardViewImageAndStyle(
            View view, ImageStyleSpec spec, float radius, float visibility, boolean drawBorderInForeground) {
        if (view == null || spec == null) return;
        if (visibility <= 0.005f) {
            CapsuleStyle.transparent(view);
            return;
        }
        CapsuleStyle.image(
                view,
                spec.bitmap,
                spec.domColor,
                radius,
                spec.scrimPercent,
                spec.scrimMode,
                visibility,
                drawBorderInForeground);
    }

    private static void setCardViewColorAndStyle(View cardView, int argbColor, boolean useBlur, float radius, int[] grad, float visibility) {
        int fallbackRadiusPx = useBlur ? OneUiBlurSpec.radiusPx(argbColor) : 0;
        int tintArgb = useBlur ? OneUiBlurSpec.tint(argbColor) : argbColor;
        int[] tintGrad = (useBlur && grad != null && grad.length >= 2)
                ? new int[]{OneUiBlurSpec.tint(grad[0]), OneUiBlurSpec.tint(grad[1])}
                : grad;
        setCardViewColorAndStyle(cardView, tintArgb, useBlur, fallbackRadiusPx, radius, tintGrad, visibility, false);
    }

    private static void setCardViewColorAndStyle(
            View cardView, int argbColor, boolean useBlur, int blurRadiusPx,
            float radius, int[] grad, float visibility, boolean drawBorderInForeground) {
        if (cardView == null) return;
        if (visibility <= 0.005f) {
            CapsuleStyle.transparent(cardView);
            return;
        }
        if (grad != null && grad.length >= 2) {
            setCardViewGradientAndStyle(
                    cardView, grad[0], grad[1], useBlur, blurRadiusPx, radius, visibility, drawBorderInForeground);
            return;
        }
        if (useBlur) {
            CapsuleStyle.glass(cardView, argbColor, argbColor, blurRadiusPx, radius, visibility, drawBorderInForeground);
        } else {
            clearBlurInfo(cardView);
            setCardBackgroundColor(cardView, ConfigManager.scaleAlpha(argbColor, visibility), radius);
            CapsuleStyle.setElevationScale(cardView, visibility);
        }
    }

    private static void setCardViewGradientAndStyle(
            View view, int start, int end, boolean blur, int blurRadiusPx,
            float radius, float visibility, boolean drawBorderInForeground) {
        if (visibility <= 0.005f) {
            CapsuleStyle.transparent(view);
            return;
        }
        if (blur) {
            CapsuleStyle.glass(view, start, end, blurRadiusPx, radius, visibility, drawBorderInForeground);
        } else {
            clearBlurInfo(view);
            CapsuleStyle.gradient(view,
                    ConfigManager.scaleAlpha(start, visibility),
                    ConfigManager.scaleAlpha(end, visibility),
                    radius);
            CapsuleStyle.setElevationScale(view, visibility);
        }
    }

    private static void restoreGradientIfNeeded(View view) {
        CapsuleStyle.restoreGradient(view);
        CapsuleStyle.releaseBlur(view);
    }

    private static void setCardBackgroundColor(View view, int color) {
        setCardBackgroundColor(view, color, 100f);
    }

    private static void setCardBackgroundColor(View view, int color, float radius) {
        CapsuleStyle.solid(view, color, radius);
    }

    private static void clearBlurInfo(View view) { CapsuleStyle.clearBlur(view); }

    // --- Layer 1: Immutable ResourceEntryName Lookup & Diagnostics ---

    private static final Set<String> DUMPED_CARD_CLASSES = Collections.synchronizedSet(new HashSet<String>());

    private static View findViewByEntryName(View root, String entryName) {
        return NowBarCardAdapter.findViewByEntryName(root, entryName);
    }

    private static String getResourceEntryNameSafe(View v) {
        return NowBarCardAdapter.getResourceEntryNameSafe(v);
    }

    private static boolean looksLikeNowBarCardTree(View v) {
        if (!(v instanceof ViewGroup)) return false;
        String cls = v.getClass().getName();
        if (cls.contains("NowBar") && cls.endsWith("CardView")) return true;
        return findViewByEntryName(v, "stack_background_cardview") != null
                || findViewByEntryName(v, "nowbar_cardview_background") != null
                || findViewByEntryName(v, "nowbar_cardview_background_for_now_brief") != null;
    }

    private static void syncResidualAodContainers(View card, boolean clearForAod, View primaryA, View primaryB) {
        if (card == null) return;
        syncResidualAodTarget(card, clearForAod);
        View mpsm = findViewByEntryName(card, "nowbar_cardview_mpsm_background");
        if (mpsm != null && mpsm != primaryA && mpsm != primaryB) {
            syncResidualAodTarget(mpsm, clearForAod);
        }
        View briefBg = findViewByEntryName(card, "nowbar_cardview_background_for_now_brief");
        if (briefBg != null && briefBg != primaryA && briefBg != primaryB) {
            syncResidualAodTarget(briefBg, clearForAod);
        }
        syncNamedBackgroundDescendants(card, 0, clearForAod, primaryA, primaryB);
    }

    private static void syncResidualAodTarget(View target, boolean clearForAod) {
        if (target == null) return;
        if (clearForAod) {
            boolean hasBg = target.getBackground() != null;
            boolean hasFg = Build.VERSION.SDK_INT >= 23 && target.getForeground() != null;
            boolean hasStyle = XposedHelpers.getAdditionalInstanceField(target, "nb_style") != null;
            if (hasBg || hasFg || hasStyle) {
                CapsuleStyle.transparent(target);
            }
        } else if (XposedHelpers.getAdditionalInstanceField(target, "nb_style") != null) {
            CapsuleStyle.restoreElevation(target);
            restoreGradientIfNeeded(target);
        }
    }

    private static void syncNamedBackgroundDescendants(
            View node, int depth, boolean clearForAod, View primaryA, View primaryB) {
        if (!(node instanceof ViewGroup) || depth > 2) return;
        ViewGroup vg = (ViewGroup) node;
        int count = Math.min(vg.getChildCount(), 16);
        for (int i = 0; i < count; i++) {
            View child = vg.getChildAt(i);
            if (child == null) continue;
            if (child != primaryA && child != primaryB) {
                String entry = getResourceEntryNameSafe(child);
                if (isCapsuleBackgroundEntry(entry)) {
                    syncResidualAodTarget(child, clearForAod);
                }
            }
            if (child instanceof ViewGroup && depth < 2) {
                syncNamedBackgroundDescendants(child, depth + 1, clearForAod, primaryA, primaryB);
            }
        }
    }

    private static boolean isCapsuleBackgroundEntry(String entry) {
        if (entry == null || entry.isEmpty()) return false;
        if (entry.contains("shader") || entry.contains("icon") || entry.contains("badge")
                || entry.contains("button") || entry.contains("progress")
                || entry.contains("album") || entry.contains("art")
                || entry.contains("setting") || entry.contains("expand")) {
            return false;
        }
        return entry.contains("cardview_background")
                || entry.contains("background_cardview")
                || entry.endsWith("_background");
    }

    private static void captureExpandedTreeDiagnosticsDelayed(final View card) {
        if (card == null) return;
        captureExpandedTreeDiagnosticsIfNeeded(card, false);
        Handler h = getMainHandler();
        if (h == null) return;
        final WeakReference<View> ref = new WeakReference<>(card);
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                View c = ref.get();
                if (c != null && c.isAttachedToWindow() && !isCollapsedLockscreenCapsule(c)) {
                    captureExpandedTreeDiagnosticsIfNeeded(c, true);
                }
            }
        }, 320L);
    }

    private static void captureExpandedTreeDiagnosticsIfNeeded(final View card) {
        captureExpandedTreeDiagnosticsIfNeeded(card, false);
    }

    private static void captureExpandedTreeDiagnosticsIfNeeded(final View card, boolean bypassThrottle) {
        if (card == null) return;
        try {
            long now = System.nanoTime();
            boolean first = sLastExpandedTreeDump.isEmpty();
            String expLeak = inspectMediaExpandedLeak(card);
            boolean leakChanged = !expLeak.equals(sLastExpandedLeak);
            sLastExpandedLeak = expLeak;
            if (!bypassThrottle && !first && !leakChanged && (now - sLastExpandedDiagPushNs) < 1_000_000_000L) {
                return;
            }
            sLastExpandedDiagPushNs = now;
            StringBuilder treeSb = new StringBuilder();
            Object rawTracked = XposedHelpers.getAdditionalInstanceField(card, "nb_expanded");
            Boolean nativeExp = readNativeExpandedState(card);
            float vis = getCardAodVisibility(card);
            ConfigManager.Config cfg = sConfig;
            treeSb.append("Card: ").append(card.getClass().getName())
                  .append(" (id=").append(getResourceEntryNameSafe(card))
                  .append(" vis=").append(String.format(Locale.US, "%.2f", vis))
                  .append(" hideAod=").append(cfg != null && cfg.hideBgOnAod)
                  .append(" lockMode=").append(readCardLockscreenMode(card))
                  .append(" dozing=").append(readCardDozing(card))
                  .append(" dark=").append(String.format(Locale.US, "%.2f", readCardDarkAmount(card)))
                  .append(" nativeExp=").append(nativeExp)
                  .append(" trackedExp=").append(rawTracked)
                  .append(" state=").append(getCardStateValue(card))
                  .append(" styled=").append(XposedHelpers.getAdditionalInstanceField(card, "nb_custom_applied"))
                  .append(" leak=").append(expLeak)
                  .append(")\n");
            appendViewTree(treeSb, card, 1, 14);
            sLastExpandedTreeDump = treeSb.toString();
            pushDiagnosticsReport(card);
        } catch (Throwable ignored) {}
    }

    private static void captureTreeDiagnosticsIfNeeded(final View card, boolean aodHiddenMode, float vis) {
        if (card == null) return;
        try {
            final String clsName = card.getClass().getName();
            boolean isNewClass = DUMPED_CARD_CLASSES.add(clsName);
            long now = System.nanoTime();
            boolean shouldPush = false;

            if (aodHiddenMode) {
                String leak = inspectAodSurfaceLeak(card);
                boolean leakChanged = !leak.equals(sLastAodLeak);
                sLastAodLeak = leak;
                if (sLastAodTreeDump.isEmpty() || leakChanged || (now - sLastAodDiagPushNs) >= 2_000_000_000L) {
                    sLastAodDiagPushNs = now;
                    StringBuilder treeSb = new StringBuilder();
                    treeSb.append("Card: ").append(clsName).append(" (id=").append(getResourceEntryNameSafe(card))
                          .append(" vis=").append(String.format(Locale.US, "%.2f", vis))
                          .append(" lockMode=").append(readCardLockscreenMode(card))
                          .append(" dozing=").append(readCardDozing(card))
                          .append(" leak=").append(leak).append(")\n");
                    appendViewTree(treeSb, card, 1, 6);
                    sLastAodTreeDump = treeSb.toString();
                    shouldPush = true;
                }
            } else {
                View expandRoot = clsName.endsWith("NowBarMediaCardView")
                        ? findViewByEntryName(card, "expand_content_rootview") : null;
                boolean midCollapse = expandRoot != null
                        && expandRoot.getVisibility() == View.VISIBLE
                        && expandRoot.getAlpha() > 0.05f;
                if (sLastLockTreeDump.isEmpty()
                        || (!midCollapse && (isNewClass
                                || (vis >= 0.995f && (now - sLastLockDiagPushNs) >= 3_000_000_000L)))) {
                    sLastLockDiagPushNs = now;
                    StringBuilder treeSb = new StringBuilder();
                    treeSb.append("Card: ").append(clsName).append(" (id=").append(getResourceEntryNameSafe(card))
                          .append(" vis=").append(String.format(Locale.US, "%.2f", vis))
                          .append(" lockMode=").append(readCardLockscreenMode(card))
                          .append(" dozing=").append(readCardDozing(card)).append(")\n");
                    appendViewTree(treeSb, card, 1, 6);
                    sLastLockTreeDump = treeSb.toString();
                    shouldPush = true;
                }
            }

            if (shouldPush) {
                pushDiagnosticsReport(card);
            }
        } catch (Throwable ignored) {}
    }

    private static void pushDiagnosticsReport(View card) {
        if (card == null) return;
        final Context ctx = card.getContext() != null ? card.getContext().getApplicationContext() : sSysUiContext;
        if (ctx == null) return;

        final StringBuilder sb = new StringBuilder();
        appendSystemUiTelemetry(sb);
        sb.append("\n=== Live NowBar View Tree (Lockscreen) ===\n");
        sb.append(sLastLockTreeDump.isEmpty() ? "<Waiting for Lockscreen card render>\n" : sLastLockTreeDump);
        sb.append("\n=== Live NowBar View Tree (Media Expanded) ===\n");
        sb.append(sLastExpandedTreeDump.isEmpty() ? "<No Media Expanded sample captured yet>\n" : sLastExpandedTreeDump);
        sb.append("\n=== Live NowBar View Tree (AOD Hide-Bg) ===\n");
        sb.append(sLastAodTreeDump.isEmpty() ? "<No AOD hideBgOnAod sample captured yet>\n" : sLastAodTreeDump);
        pushDiagnosticsDumpAsync(ctx, sb.toString());
    }

    private static void appendSystemUiTelemetry(StringBuilder sb) {
        sb.append("=== SystemUI Runtime Status ===\n");
        sb.append("hookTimeContext: ").append(sHookTimeContextSource)
          .append(" | sysUiContextSet=").append(sSysUiContext != null)
          .append(" | adapter=").append(activeAdapter().getAdapterName()).append('\n');
        sb.append("hookMapSource: ").append(sHookMapSource)
          .append(" | hookMapTarget=").append(sHookMapTarget).append('\n');
        sb.append("layer1Hooked=").append(sLayer1Hooked)
          .append(" | layer2CoreHooked=").append(sLayer2CoreHooked)
          .append(" | layer2WakeHooks=").append(sLayer2WakeCount)
          .append(" | layer3Hooks=").append(sLayer3Count)
          .append(" | pendingCLs=").append(PENDING_LAYER2_CLASSLOADERS.size()).append('\n');
        sb.append("activeCards=").append(ACTIVE_CARDS.size())
          .append(" | screenReceiver=").append(sReceiverRegistered)
          .append(" | wakeWatchActive=").append(System.nanoTime() < sWakeUntilNs).append('\n');
        if (sLastCardSummary != null && !sLastCardSummary.isEmpty()) {
            sb.append("lastCard: ").append(sLastCardSummary).append('\n');
        }
        if (sLastLockSummary != null && !sLastLockSummary.isEmpty()) {
            sb.append("lastLock: ").append(sLastLockSummary).append('\n');
        }
        sb.append("lastAod: ").append((sLastAodSummary != null && !sLastAodSummary.isEmpty()) ? sLastAodSummary : "<none>")
          .append(" | aodLeak=").append(sLastAodLeak)
          .append(" | expandedLeak=").append(sLastExpandedLeak).append('\n');
    }

    private static String inspectAodSurfaceLeak(View card) {
        if (card == null) return "none";
        StringBuilder leaks = new StringBuilder();
        collectAodSurfaceLeaks(card, card, 0, leaks);
        return leaks.length() == 0 ? "clean" : "AOD_BG_LEAK: " + leaks.toString();
    }

    private static String inspectMediaExpandedLeak(View card) {
        if (card == null) return "none";
        if (isCardInSettingMode(card) || !shouldCustomizeCatMedia()) return "skipped";
        NowBarCardAdapter adapter = activeAdapter();
        StringBuilder leaks = new StringBuilder();
        View o1 = adapter.resolveMediaBackdropSurface(card);
        View t1 = adapter.resolveMediaSettingBgSurface(card);
        View p1 = adapter.resolveMediaPillSurface(card);
        View expandRoot = adapter.resolveExpandRootView(card);
        View v1 = adapter.resolveMediaShaderView(card);
        View w1 = adapter.resolveMediaShaderParent(card);
        appendSurfaceLeak(o1, leaks);
        appendSurfaceLeak(t1, leaks);
        appendSurfaceLeak(p1, leaks);
        appendSurfaceLeak(expandRoot, leaks);
        if (v1 != null && v1.getVisibility() == View.VISIBLE && v1.getAlpha() > 0.05f) {
            if (leaks.length() > 0) leaks.append("; ");
            leaks.append("shader[v1].a=").append(String.format(Locale.US, "%.2f", v1.getAlpha()));
        }
        if (w1 != null && w1.getVisibility() == View.VISIBLE && w1.getAlpha() > 0.05f) {
            if (leaks.length() > 0) leaks.append("; ");
            leaks.append("shaderParent[w1].a=").append(String.format(Locale.US, "%.2f", w1.getAlpha()));
        }
        return leaks.length() == 0 ? "clean" : "MEDIA_BG_LEAK: " + leaks.toString();
    }

    private static void appendSurfaceLeak(View v, StringBuilder leaks) {
        if (v == null || v.getVisibility() != View.VISIBLE || v.getAlpha() <= 0.05f) return;
        String entry = getResourceEntryNameSafe(v);
        String label = entry.isEmpty() ? v.getClass().getSimpleName() : v.getClass().getSimpleName() + "[id/" + entry + "]";
        if (CapsuleStyle.hasBlurInfo(v)) {
            if (leaks.length() > 0) leaks.append("; ");
            leaks.append(label).append(".blur=1");
        }
        String bgLeak = describeOpaqueDrawable(v, v.getBackground(), false);
        if (bgLeak != null) {
            if (leaks.length() > 0) leaks.append("; ");
            leaks.append(label).append('=').append(bgLeak);
        }
        if (Build.VERSION.SDK_INT >= 23) {
            String fgLeak = describeOpaqueDrawable(v, v.getForeground(), true);
            if (fgLeak != null) {
                if (leaks.length() > 0) leaks.append("; ");
                leaks.append(label).append(".fg=").append(fgLeak);
            }
        }
    }

    private static void collectAodSurfaceLeaks(View rootCard, View v, int depth, StringBuilder leaks) {
        if (v == null || depth > 4) return;
        String entry = getResourceEntryNameSafe(v);
        boolean isInnerControl = !entry.isEmpty()
                && (entry.contains("action") || entry.contains("icon") || entry.contains("badge")
                        || entry.contains("button") || entry.contains("progress")
                        || entry.contains("setting") || entry.contains("expand"));
        boolean isSurface = !isInnerControl && ((v == rootCard)
                || isCapsuleBackgroundEntry(entry)
                || v.getClass().getName().contains("CardView"));
        if (isSurface) {
            appendSurfaceLeak(v, leaks);
        }
        if (v instanceof ViewGroup && depth < 4) {
            ViewGroup vg = (ViewGroup) v;
            int count = Math.min(vg.getChildCount(), 16);
            for (int i = 0; i < count; i++) {
                collectAodSurfaceLeaks(rootCard, vg.getChildAt(i), depth + 1, leaks);
            }
        }
    }

    private static String describeOpaqueDrawable(View v, android.graphics.drawable.Drawable d, boolean isForeground) {
        if (d == null) return null;
        int alpha = d.getAlpha();
        if (alpha <= 0) return null;
        if (d instanceof android.graphics.drawable.ColorDrawable) {
            int c = ((android.graphics.drawable.ColorDrawable) d).getColor();
            if ((c >>> 24) == 0) return null;
            return "ColorDrawable#" + Integer.toHexString(c) + "(a=" + alpha + ")";
        }
        if (d instanceof android.graphics.drawable.GradientDrawable) {
            if (Build.VERSION.SDK_INT >= 24) {
                android.content.res.ColorStateList csl = ((android.graphics.drawable.GradientDrawable) d).getColor();
                if (csl != null) {
                    int c = csl.getDefaultColor();
                    if ((c >>> 24) == 0) return null;
                    return "GradientDrawable#" + Integer.toHexString(c) + "(a=" + alpha + ")";
                }
            }
            return "GradientDrawable(a=" + alpha + ")";
        }
        if (!isForeground && v != null) {
            Method cardBgGetter = ReflectionCache.method(v.getClass(), "getCardBackgroundColor");
            if (cardBgGetter != null) {
                try {
                    Object res = cardBgGetter.invoke(v);
                    if (res instanceof android.content.res.ColorStateList) {
                        int c = ((android.content.res.ColorStateList) res).getDefaultColor();
                        if ((c >>> 24) == 0) return null;
                        return d.getClass().getSimpleName() + "/cardBg#" + Integer.toHexString(c) + "(a=" + alpha + ")";
                    }
                } catch (Throwable ignored) {}
            }
        }
        return d.getClass().getSimpleName() + "(a=" + alpha + ")";
    }

    private static void pushDiagnosticsDumpAsync(final Context ctx, final String dump) {
        if (ctx == null || dump == null) return;
        if (sIoHandler != null) {
            sIoHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Bundle extras = new Bundle();
                        extras.putString("dump", dump);
                        ctx.getContentResolver().call(NowBarConfigProvider.CONTENT_URI, "update_diagnostics", null, extras);
                    } catch (Throwable ignored) {}
                }
            });
        }
    }

    private static void appendDrawableSummary(
            StringBuilder sb, String label, String alphaLabel, android.graphics.drawable.Drawable d) {
        if (d == null) return;
        sb.append(' ').append(label).append('=').append(d.getClass().getSimpleName());
        if (d instanceof android.graphics.drawable.ColorDrawable) {
            sb.append('#').append(Integer.toHexString(((android.graphics.drawable.ColorDrawable) d).getColor()));
        } else if (d instanceof android.graphics.drawable.GradientDrawable && Build.VERSION.SDK_INT >= 24) {
            android.content.res.ColorStateList csl = ((android.graphics.drawable.GradientDrawable) d).getColor();
            if (csl != null) {
                sb.append('#').append(Integer.toHexString(csl.getDefaultColor()));
            }
        }
        sb.append(' ').append(alphaLabel).append('=').append(d.getAlpha());
    }

    private static void appendViewTree(StringBuilder sb, View v, int depth, int maxDepth) {
        if (v == null || depth > maxDepth) return;
        for (int i = 0; i < depth; i++) sb.append("  ");
        sb.append(v.getClass().getSimpleName());
        String entry = getResourceEntryNameSafe(v);
        if (!entry.isEmpty()) sb.append(" [id/").append(entry).append(']');
        sb.append(" sz=").append(v.getWidth()).append('x').append(v.getHeight())
          .append(" vis=").append(v.getVisibility())
          .append(" a=").append(String.format(Locale.US, "%.2f", v.getAlpha()));
        float elev = v.getElevation();
        if (elev > 0.01f) {
            sb.append(" elev=").append(String.format(Locale.US, "%.1f", elev));
        }
        if (CapsuleStyle.hasBlurInfo(v)) {
            sb.append(" blur=1");
        }
        appendDrawableSummary(sb, "bg", "bgA", v.getBackground());
        if (Build.VERSION.SDK_INT >= 23 && v.getForeground() != null) {
            appendDrawableSummary(sb, "fg", "fgA", v.getForeground());
        }
        android.content.res.ColorStateList tint = v.getBackgroundTintList();
        if (tint != null) {
            sb.append(" tint=#").append(Integer.toHexString(tint.getDefaultColor()));
        }
        Method cardBgGetter = ReflectionCache.method(v.getClass(), "getCardBackgroundColor");
        if (cardBgGetter != null) {
            try {
                Object res = cardBgGetter.invoke(v);
                if (res instanceof android.content.res.ColorStateList) {
                    sb.append(" cardBg=#").append(Integer.toHexString(((android.content.res.ColorStateList) res).getDefaultColor()));
                }
            } catch (Throwable ignored) {}
        }
        sb.append('\n');
        if (v instanceof ViewGroup && depth < maxDepth) {
            ViewGroup vg = (ViewGroup) v;
            int count = Math.min(vg.getChildCount(), 16);
            for (int i = 0; i < count; i++) {
                appendViewTree(sb, vg.getChildAt(i), depth + 1, maxDepth);
            }
        }
    }

    // --- Reflection Helpers (delegated to NowBarCardAdapter) ---

    private static Object getFieldValue(Object obj, String fieldName) {
        return NowBarCardAdapter.getFieldValue(obj, fieldName);
    }

    private static Object callMethodNoArgs(Object obj, String methodName) {
        return NowBarCardAdapter.callMethodNoArgs(obj, methodName);
    }
}
