package io.github.tavataan.nowbarcolor;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.matchers.MethodsMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.ClassDataList;
import org.luckypray.dexkit.result.FieldData;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;
import org.luckypray.dexkit.result.UsingFieldData;

import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scans installed AOD_Service APK bytecode using DexKit with strict 1-match validation,
 * detailed per-slot status reporting (OK / MISSING / AMBIGUOUS), and package-scoped queries.
 * MUST ONLY be called from the companion app process (MainActivity / NowBarConfigProvider),
 * never inside SystemUI or AOD_Service.
 */
public final class AodScanner {
    public static final String AOD_PKG = "com.samsung.android.app.aodservice";
    private static final String SEARCH_PKG = "com.samsung.android.app";
    private static final Object SCAN_LOCK = new Object();

    private static boolean sNativeLoaded = false;

    private static synchronized void ensureNativeLoaded() {
        if (!sNativeLoaded) {
            System.loadLibrary("dexkit");
            sNativeLoaded = true;
        }
    }

    public static final class ScanResult {
        public final HookMap map;
        public final String error;

        ScanResult(HookMap map, String error) {
            this.map = map;
            this.error = error;
        }
    }

    public static ScanResult scanInstalledAod(Context ctx) {
        synchronized (SCAN_LOCK) {
            PackageInfo pi;
            try {
                pi = ctx.getPackageManager().getPackageInfo(AOD_PKG, 0);
            } catch (PackageManager.NameNotFoundException e) {
                return new ScanResult(null, "AOD_Service not installed");
            }

            String apkPath = pi.applicationInfo != null ? pi.applicationInfo.sourceDir : null;
            if (apkPath == null || apkPath.isEmpty()) {
                return new ScanResult(null, "AOD_Service sourceDir unavailable");
            }

            long verCode = pi.getLongVersionCode();
            String verName = pi.versionName != null ? pi.versionName : "";
            long lastUpdate = pi.lastUpdateTime;

            try {
                ensureNativeLoaded();
            } catch (Throwable t) {
                return new ScanResult(null, "Failed to load libdexkit.so: " + t.getMessage());
            }

            Map<String, String> statuses = new LinkedHashMap<>();

            try (DexKitBridge bridge = DexKitBridge.create(apkPath)) {
                if (bridge == null) {
                    return new ScanResult(null, "Unable to open DEX: " + apkPath);
                }

                // 1. Media card & full superclass-chain walk for baseCardClass
                ClassData mediaCd = findUniqueClass(bridge, FindClass.create()
                        .searchPackages(SEARCH_PKG)
                        .matcher(new ClassMatcher().className(".NowBarMediaCardView", StringMatchType.EndsWith, false)),
                        "mediaCardClass", statuses);
                String mediaCardClass = mediaCd != null ? mediaCd.getName() : null;

                ClassData baseCd = resolveBaseCardClass(bridge, mediaCd, statuses);
                String baseCardClass = baseCd != null ? baseCd.getName() : null;

                // 2. Core + Wake/Transition methods on baseCardClass (using ClassData.findMethod)
                String baseBindMethod = null;
                String baseBlurMethod = null;
                String baseColorMethod = null;
                String baseShapeMethod = null;
                String baseProgressBgMethod = null;
                String baseStateMethod = null;
                String baseExpandMethod = null;
                String baseSetExpandStateMethod = null;
                String baseDarkModeMethod = null;
                String baseClearContainerBgMethod = null;
                String baseAodDrawableMethod = null;
                String baseLockscreenModeField = null;
                String baseDozingField = null;
                String baseExpandField = null;

                if (baseCd != null) {
                    baseBindMethod = findUniqueMethod(baseCd, FindMethod.create()
                            .matcher(new MethodMatcher()
                                    .returnType("void")
                                    .paramTypes("com.android.systemui.plugins.keyguardstatusview.NowBarItem", "java.lang.Integer")),
                            "baseBindMethod", statuses);

                    MethodData blurMd = findUniqueMethodData(baseCd, FindMethod.create()
                            .matcher(new MethodMatcher()
                                    .returnType("void")
                                    .paramTypes("com.android.systemui.plugins.keyguardstatusview.NowBarItem", "android.view.View", "float", "int")),
                            "baseBlurMethod", statuses);
                    if (blurMd == null && "MISSING(0)".equals(statuses.get("baseBlurMethod"))) {
                        blurMd = findUniqueMethodData(baseCd, FindMethod.create()
                                .matcher(new MethodMatcher()
                                        .returnType("void")
                                        .paramTypes("com.android.systemui.plugins.keyguardstatusview.NowBarItem", "androidx.cardview.widget.CardView", "float", "int")),
                                "baseBlurMethod", statuses);
                    }
                    baseBlurMethod = blurMd != null ? blurMd.getMethodName() : null;

                    baseColorMethod = findUniqueMethod(baseCd, FindMethod.create()
                            .matcher(new MethodMatcher()
                                    .modifiers(Modifier.PUBLIC)
                                    .returnType("int")
                                    .paramTypes("float", "float")
                                    .invokeMethods(MethodsMatcher.create().add(
                                            new MethodMatcher().declaredClass("android.graphics.Color").name("argb")))),
                            "baseColorMethod", statuses);

                    baseShapeMethod = findUniqueMethod(baseCd, FindMethod.create()
                            .matcher(new MethodMatcher()
                                    .modifiers(Modifier.PUBLIC)
                                    .returnType("void")
                                    .paramCount(0)
                                    .usingStrings("onShapeAnimationValueChanged skipped radius")),
                            "baseShapeMethod", statuses);

                    if (baseColorMethod != null && baseBlurMethod != null) {
                        baseProgressBgMethod = findUniqueMethod(baseCd, FindMethod.create()
                                .matcher(new MethodMatcher()
                                        .modifiers(Modifier.PUBLIC)
                                        .returnType("void")
                                        .paramTypes("float")
                                        .invokeMethods(MethodsMatcher.create().add(
                                                new MethodMatcher().declaredClass(baseCardClass).name(baseColorMethod))
                                                .add(new MethodMatcher().declaredClass(baseCardClass).name(baseBlurMethod)))),
                                "baseProgressBgMethod", statuses);
                        if (baseProgressBgMethod == null
                                && verCode > 0L && verCode < NowBarAdapterFactory.ONE_UI_85_AOD_MIN_VERSION
                                && "MISSING(0)".equals(statuses.get("baseProgressBgMethod"))) {
                            statuses.put("baseProgressBgMethod", HookMap.STATUS_NA_PREFIX + " (OneUI <8.5)");
                        }
                    } else {
                        statuses.put("baseProgressBgMethod", "SKIPPED(baseColor/Blur missing)");
                    }

                    baseStateMethod = findUniqueMethod(baseCd, FindMethod.create()
                            .matcher(new MethodMatcher()
                                    .modifiers(Modifier.PUBLIC)
                                    .returnType("void")
                                    .paramTypes("int", "boolean", "boolean")
                                    .usingStrings("setCardSettingViewState state ")),
                            "baseStateMethod", statuses);
                    if (baseStateMethod == null && "MISSING(0)".equals(statuses.get("baseStateMethod"))) {
                        baseStateMethod = findUniqueMethod(baseCd, FindMethod.create()
                                .matcher(new MethodMatcher()
                                        .modifiers(Modifier.PUBLIC)
                                        .returnType("void")
                                        .paramTypes("int", "boolean")
                                        .usingStrings("setCardSettingViewState state ")),
                                "baseStateMethod", statuses);
                    }

                    baseExpandMethod = findUniqueMethod(baseCd, FindMethod.create()
                            .matcher(new MethodMatcher()
                                    .modifiers(Modifier.PUBLIC)
                                    .returnType("void")
                                    .paramTypes("boolean")
                                    .usingStrings("onExpandStateChanged isExpanded ")),
                            "baseExpandMethod", statuses);

                    baseSetExpandStateMethod = findUniqueMethod(baseCd, FindMethod.create()
                            .matcher(new MethodMatcher()
                                    .modifiers(Modifier.PUBLIC)
                                    .returnType("void")
                                    .paramTypes("com.android.systemui.plugins.keyguardstatusview.NowBarItem", "boolean", "boolean")
                                    .usingStrings("setExpandState ")),
                            "baseSetExpandStateMethod", statuses);

                    baseDarkModeMethod = findUniqueMethod(baseCd, FindMethod.create()
                            .matcher(new MethodMatcher()
                                    .modifiers(Modifier.PUBLIC)
                                    .returnType("void")
                                    .paramTypes("boolean", "boolean", "boolean")),
                            "baseDarkModeMethod", statuses);

                    baseClearContainerBgMethod = resolveBaseClearContainerMethod(
                            blurMd, baseCardClass, verCode, statuses);

                    baseAodDrawableMethod = resolveBaseAodDrawableMethod(baseCd, statuses);

                    baseLockscreenModeField = resolveWrittenBooleanField(
                            baseCd, baseCardClass, "setLockscreenViewMode", "baseLockscreenModeField", statuses);
                    baseDozingField = resolveWrittenBooleanField(
                            baseCd, baseCardClass, "setDozing", "baseDozingField", statuses);
                    baseExpandField = resolveWrittenBooleanField(
                            baseCd, baseCardClass, "setExpanded", "baseExpandField", statuses);
                } else {
                    statuses.put("baseBindMethod", "SKIPPED(baseCardClass missing)");
                    statuses.put("baseBlurMethod", "SKIPPED(baseCardClass missing)");
                    statuses.put("baseColorMethod", "SKIPPED(baseCardClass missing)");
                    statuses.put("baseShapeMethod", "SKIPPED(baseCardClass missing)");
                    statuses.put("baseProgressBgMethod", "SKIPPED(baseCardClass missing)");
                    statuses.put("baseStateMethod", "SKIPPED(baseCardClass missing)");
                    statuses.put("baseExpandMethod", "SKIPPED(baseCardClass missing)");
                    statuses.put("baseSetExpandStateMethod", "SKIPPED(baseCardClass missing)");
                    statuses.put("baseDarkModeMethod", "SKIPPED(baseCardClass missing)");
                    statuses.put("baseClearContainerBgMethod", "SKIPPED(baseCardClass missing)");
                    statuses.put("baseAodDrawableMethod", "SKIPPED(baseCardClass missing)");
                    statuses.put("baseLockscreenModeField", "SKIPPED(baseCardClass missing)");
                    statuses.put("baseDozingField", "SKIPPED(baseCardClass missing)");
                    statuses.put("baseExpandField", "SKIPPED(baseCardClass missing)");
                }

                // 3. NowBarCardStackView & refreshAllViews(CopyOnWriteArrayList)
                ClassData stackCd = findUniqueClass(bridge, FindClass.create()
                        .searchPackages(SEARCH_PKG)
                        .matcher(new ClassMatcher().className(".NowBarCardStackView", StringMatchType.EndsWith, false)),
                        "stackViewClass", statuses);
                String stackViewClass = stackCd != null ? stackCd.getName() : null;
                String stackRefreshMethod = null;
                if (stackCd != null) {
                    stackRefreshMethod = findUniqueMethod(stackCd, FindMethod.create()
                            .matcher(new MethodMatcher()
                                    .modifiers(Modifier.PUBLIC)
                                    .returnType("void")
                                    .paramTypes("java.util.concurrent.CopyOnWriteArrayList")),
                            "stackRefreshMethod", statuses);
                } else {
                    statuses.put("stackRefreshMethod", "SKIPPED(stackViewClass missing)");
                }

                // 4. Enhancements layer (strict 1-match with semantic constraints)
                String mediaForegroundMethod = null;
                String mediaBlackAodMethod = null;
                if (mediaCd != null) {
                    mediaForegroundMethod = findUniqueMethod(mediaCd, FindMethod.create()
                            .matcher(new MethodMatcher()
                                    .modifiers(Modifier.PUBLIC)
                                    .returnType("void")
                                    .paramTypes("int", "float", "int", "int")
                                    .invokeMethods(MethodsMatcher.create().add(
                                            new MethodMatcher().declaredClass("android.view.View").name("setForeground")))),
                            "mediaForegroundMethod", statuses);
                    if (mediaForegroundMethod == null
                            && verCode > 0L && verCode < NowBarAdapterFactory.ONE_UI_85_AOD_MIN_VERSION
                            && "MISSING(0)".equals(statuses.get("mediaForegroundMethod"))) {
                        statuses.put("mediaForegroundMethod", HookMap.STATUS_NA_PREFIX + " (OneUI <8.5)");
                    }

                    mediaBlackAodMethod = findUniqueMethod(mediaCd, FindMethod.create()
                            .matcher(new MethodMatcher()
                                    .returnType("void")
                                    .paramTypes("com.android.systemui.plugins.keyguardstatusview.NowBarItem", "float")
                                    .invokeMethods(MethodsMatcher.create().add(
                                            new MethodMatcher().declaredClass(mediaCardClass).name("getBgColorForBlackAod")))),
                            "mediaBlackAodMethod", statuses);
                } else {
                    statuses.put("mediaForegroundMethod", "SKIPPED(mediaCardClass missing)");
                    statuses.put("mediaBlackAodMethod", "SKIPPED(mediaCardClass missing)");
                }

                ClassData briefCd = findUniqueClass(bridge, FindClass.create()
                        .searchPackages(SEARCH_PKG)
                        .matcher(new ClassMatcher().className(".NowBarBriefCardView", StringMatchType.EndsWith, false)),
                        "briefCardClass", statuses);
                String briefCardClass = briefCd != null ? briefCd.getName() : null;
                String briefBgColorMethod = null;
                String briefResetBgMethod = null;
                if (briefCd != null) {
                    MethodMatcher briefBgMatcher = new MethodMatcher()
                            .modifiers(Modifier.PUBLIC)
                            .returnType("int")
                            .paramTypes("float");
                    if (baseCardClass != null && baseColorMethod != null) {
                        briefBgMatcher.invokeMethods(MethodsMatcher.create().add(
                                new MethodMatcher().declaredClass(baseCardClass).name(baseColorMethod)));
                    } else {
                        briefBgMatcher.invokeMethods(MethodsMatcher.create().add(
                                new MethodMatcher().name("getItem")));
                    }
                    briefBgColorMethod = findUniqueMethod(briefCd, FindMethod.create().matcher(briefBgMatcher),
                            "briefBgColorMethod", statuses);

                    briefResetBgMethod = findUniqueMethod(briefCd, FindMethod.create()
                            .matcher(new MethodMatcher()
                                    .modifiers(Modifier.STATIC)
                                    .returnType("void")
                                    .paramTypes(briefCardClass, "boolean")
                                    .usingStrings("applyBlurToSettingCard isBlur:")),
                            "briefResetBgMethod", statuses);
                } else if (verCode > 0L && verCode < NowBarAdapterFactory.ONE_UI_85_AOD_MIN_VERSION
                        && "MISSING(0)".equals(statuses.get("briefCardClass"))) {
                    String naReason = HookMap.STATUS_NA_PREFIX + " (OneUI <8.5)";
                    statuses.put("briefCardClass", naReason);
                    statuses.put("briefBgColorMethod", naReason);
                    statuses.put("briefResetBgMethod", naReason);
                } else {
                    statuses.put("briefBgColorMethod", "SKIPPED(briefCardClass missing)");
                    statuses.put("briefResetBgMethod", "SKIPPED(briefCardClass missing)");
                }

                ClassData gradCd = findUniqueClass(bridge, FindClass.create()
                        .searchPackages(SEARCH_PKG)
                        .matcher(new ClassMatcher().className(".NowBarCardGradientView", StringMatchType.EndsWith, false)),
                        "gradViewClass", statuses);
                String gradViewClass = gradCd != null ? gradCd.getName() : null;
                String gradHideMethod = null;
                if (gradCd != null) {
                    gradHideMethod = findUniqueMethod(gradCd, FindMethod.create()
                            .matcher(new MethodMatcher()
                                    .modifiers(Modifier.PUBLIC)
                                    .returnType("void")
                                    .paramTypes("int", "int")
                                    .usingStrings("showGradientView  adjustX :")),
                            "gradHideMethod", statuses);
                } else {
                    statuses.put("gradHideMethod", "SKIPPED(gradViewClass missing)");
                }

                ClassData settingsCd = findUniqueClass(bridge, FindClass.create()
                        .searchPackages(SEARCH_PKG)
                        .matcher(new ClassMatcher().className(".SettingsHelper", StringMatchType.EndsWith, false)),
                        "settingsHelperClass", statuses);
                String settingsHelperClass = settingsCd != null ? settingsCd.getName() : null;
                String showNotiLockscreenMethod = resolveShowNotiLockscreenMethod(
                        bridge, settingsHelperClass, statuses);

                HookMap map = new HookMap(
                        HookMap.SCHEMA_VERSION,
                        verCode,
                        verName,
                        lastUpdate,
                        baseCardClass,
                        baseBindMethod,
                        baseBlurMethod,
                        baseColorMethod,
                        stackViewClass,
                        stackRefreshMethod,
                        baseShapeMethod,
                        baseProgressBgMethod,
                        baseStateMethod,
                        baseExpandMethod,
                        baseSetExpandStateMethod,
                        baseDarkModeMethod,
                        baseClearContainerBgMethod,
                        baseAodDrawableMethod,
                        baseLockscreenModeField,
                        baseDozingField,
                        baseExpandField,
                        mediaCardClass,
                        mediaForegroundMethod,
                        mediaBlackAodMethod,
                        briefCardClass,
                        briefBgColorMethod,
                        briefResetBgMethod,
                        gradViewClass,
                        gradHideMethod,
                        settingsHelperClass,
                        showNotiLockscreenMethod,
                        statuses
                );
                return new ScanResult(map, null);
            } catch (Throwable t) {
                return new ScanResult(null, t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    private static ClassData resolveBaseCardClass(
            DexKitBridge bridge, ClassData mediaCd, Map<String, String> statuses) {
        ClassData firstNonFrameworkSuper = null;
        if (mediaCd != null) {
            ClassData cur = mediaCd.getSuperClass();
            while (cur != null) {
                String name = cur.getName();
                if (name == null || name.startsWith("android.") || name.startsWith("androidx.")
                        || "java.lang.Object".equals(name)) {
                    break;
                }
                if (firstNonFrameworkSuper == null) {
                    firstNonFrameworkSuper = cur;
                }
                MethodDataList bindHits = cur.findMethod(FindMethod.create().matcher(new MethodMatcher()
                        .returnType("void")
                        .paramTypes("com.android.systemui.plugins.keyguardstatusview.NowBarItem", "java.lang.Integer")));
                if (bindHits != null && bindHits.size() == 1) {
                    statuses.put("baseCardClass", "OK");
                    return cur;
                }
                cur = cur.getSuperClass();
            }
        }

        ClassData fallback = findUniqueClass(bridge, FindClass.create()
                .searchPackages(SEARCH_PKG)
                .matcher(new ClassMatcher()
                        .superClass("androidx.constraintlayout.widget.ConstraintLayout", StringMatchType.Equals, false)
                        .usingStrings("getCardViewContainer")),
                "baseCardClass", statuses);
        if (fallback != null) {
            return fallback;
        }
        if (firstNonFrameworkSuper != null) {
            statuses.put("baseCardClass", "OK");
            return firstNonFrameworkSuper;
        }
        return null;
    }

    private static String selectUniqueCandidate(
            Set<String> candidates, String slotKey, Map<String, String> statuses) {
        if (candidates.size() == 1) {
            statuses.put(slotKey, "OK");
            return candidates.iterator().next();
        } else if (candidates.isEmpty()) {
            statuses.put(slotKey, "MISSING(0)");
            return null;
        } else {
            statuses.put(slotKey, formatAmbiguous(candidates.toArray(new String[0])));
            return null;
        }
    }

    private static String resolveWrittenBooleanField(
            ClassData baseCd, String baseCardClass, String setterName, String slotKey, Map<String, String> statuses) {
        MethodDataList setters = baseCd.findMethod(FindMethod.create()
                .matcher(new MethodMatcher()
                        .name(setterName)
                        .returnType("void")
                        .paramTypes("boolean")));
        if (setters == null || setters.isEmpty()) {
            statuses.put(slotKey, "MISSING(setter " + setterName + ")");
            return null;
        }
        Set<String> writes = new LinkedHashSet<>();
        for (int i = 0; i < setters.size(); i++) {
            List<UsingFieldData> used = setters.get(i).getUsingFields();
            if (used == null) continue;
            for (UsingFieldData uf : used) {
                FieldData f = uf.getField();
                if (f != null
                        && uf.getUsingType() != null && uf.getUsingType().isWrite()
                        && baseCardClass.equals(f.getDeclaredClassName())
                        && "boolean".equals(f.getTypeName())) {
                    writes.add(f.getFieldName());
                }
            }
        }
        return selectUniqueCandidate(writes, slotKey, statuses);
    }

    private static String resolveBaseClearContainerMethod(
            MethodData blurMd, String baseCardClass, long verCode, Map<String, String> statuses) {
        if (blurMd == null || baseCardClass == null) {
            statuses.put("baseClearContainerBgMethod", "SKIPPED(baseBlurMethod missing)");
            return null;
        }
        MethodDataList invokes = blurMd.getInvokes();
        Set<String> candidates = new LinkedHashSet<>();
        if (invokes != null) {
            for (int i = 0; i < invokes.size(); i++) {
                MethodData inv = invokes.get(i);
                if (baseCardClass.equals(inv.getDeclaredClassName())
                        && "void".equals(inv.getReturnTypeName())
                        && inv.getParamCount() == 0) {
                    candidates.add(inv.getMethodName());
                }
            }
        }
        if (candidates.isEmpty() && verCode > 0L && verCode < NowBarAdapterFactory.ONE_UI_9_AOD_MIN_VERSION) {
            statuses.put("baseClearContainerBgMethod", HookMap.STATUS_NA_PREFIX + " (OneUI <9)");
            return null;
        }
        return selectUniqueCandidate(candidates, "baseClearContainerBgMethod", statuses);
    }

    /**
     * Resolves presentation.a.M()V (AOD black GradientDrawable setup) while excluding a.Z()
     * which builds GradientDrawable strokes with Color.argb / setStroke.
     */
    private static String resolveBaseAodDrawableMethod(
            ClassData baseCd, Map<String, String> statuses) {
        if (baseCd == null) {
            statuses.put("baseAodDrawableMethod", "SKIPPED(baseCardClass missing)");
            return null;
        }
        MethodDataList list = baseCd.findMethod(FindMethod.create()
                .matcher(new MethodMatcher()
                        .modifiers(Modifier.PUBLIC)
                        .returnType("void")
                        .paramCount(0)
                        .invokeMethods(MethodsMatcher.create()
                                .add(new MethodMatcher().declaredClass("android.graphics.drawable.GradientDrawable").name("setCornerRadius"))
                                .add(new MethodMatcher().declaredClass("android.view.View").name("setBackground"))
                                .add(new MethodMatcher().declaredClass("android.view.View").name("setVisibility")))));
        Set<String> candidates = new LinkedHashSet<>();
        Set<String> finalCandidates = new LinkedHashSet<>();
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                MethodData md = list.get(i);
                MethodDataList invokes = md.getInvokes();
                boolean hasStrokeOrArgb = false;
                if (invokes != null) {
                    for (int j = 0; j < invokes.size(); j++) {
                        MethodData inv = invokes.get(j);
                        String cls = inv.getDeclaredClassName();
                        String mName = inv.getMethodName();
                        if (("android.graphics.drawable.GradientDrawable".equals(cls) && "setStroke".equals(mName))
                                || ("android.graphics.Color".equals(cls) && "argb".equals(mName))) {
                            hasStrokeOrArgb = true;
                            break;
                        }
                    }
                }
                if (!hasStrokeOrArgb) {
                    candidates.add(md.getMethodName());
                    if (Modifier.isFinal(md.getModifiers())) {
                        finalCandidates.add(md.getMethodName());
                    }
                }
            }
        }
        if (candidates.size() == 1) {
            statuses.put("baseAodDrawableMethod", "OK");
            return candidates.iterator().next();
        } else if (candidates.isEmpty()) {
            statuses.put("baseAodDrawableMethod", "MISSING(0)");
            return null;
        } else if (finalCandidates.size() == 1) {
            statuses.put("baseAodDrawableMethod", "OK");
            return finalCandidates.iterator().next();
        } else {
            statuses.put("baseAodDrawableMethod", formatAmbiguous(candidates.toArray(new String[0])));
            return null;
        }
    }

    /**
     * Resolves SettingsHelper.A() via AODCommonUtils ("isAODNotificationEnabled: AOD - "),
     * which uniquely invokes SettingsHelper's zero-arg boolean notification visibility check.
     */
    private static String resolveShowNotiLockscreenMethod(
            DexKitBridge bridge, String settingsHelperClass, Map<String, String> statuses) {
        if (settingsHelperClass == null) {
            statuses.put("showNotiLockscreenMethod", "SKIPPED(settingsHelperClass missing)");
            return null;
        }
        MethodDataList callers = bridge.findMethod(FindMethod.create()
                .searchPackages(SEARCH_PKG)
                .matcher(new MethodMatcher().usingStrings("isAODNotificationEnabled: AOD - ")));
        if (callers == null || callers.isEmpty()) {
            callers = bridge.findMethod(FindMethod.create()
                    .matcher(new MethodMatcher().usingStrings("isAODNotificationEnabled: AOD - ")));
        }
        if (callers == null || callers.isEmpty()) {
            callers = bridge.findMethod(FindMethod.create()
                    .searchPackages(SEARCH_PKG)
                    .matcher(new MethodMatcher().usingStrings("lock_screen_show_notifications_on_keyguard")));
        }
        if (callers == null || callers.isEmpty()) {
            statuses.put("showNotiLockscreenMethod", "MISSING(0)");
            return null;
        }

        Set<String> candidates = new LinkedHashSet<>();
        for (int i = 0; i < callers.size(); i++) {
            MethodData caller = callers.get(i);
            MethodDataList invokes = caller.getInvokes();
            if (invokes == null) continue;
            for (int j = 0; j < invokes.size(); j++) {
                MethodData inv = invokes.get(j);
                if (settingsHelperClass.equals(inv.getDeclaredClassName())
                        && "boolean".equals(inv.getReturnTypeName())
                        && inv.getParamCount() == 0) {
                    candidates.add(inv.getMethodName());
                }
            }
        }
        return selectUniqueCandidate(candidates, "showNotiLockscreenMethod", statuses);
    }

    private static ClassData findUniqueClass(
            DexKitBridge bridge, FindClass query, String slotKey, Map<String, String> statuses) {
        ClassDataList list = bridge.findClass(query);
        int count = list != null ? list.size() : 0;
        if (count == 1) {
            statuses.put(slotKey, "OK");
            return list.get(0);
        } else if (count == 0) {
            statuses.put(slotKey, "MISSING(0)");
            return null;
        } else {
            String[] names = new String[Math.min(count, 4)];
            for (int i = 0; i < names.length; i++) {
                names[i] = list.get(i).getName();
            }
            statuses.put(slotKey, "AMBIGUOUS(" + count + ": " + joinSamples(names, count) + ")");
            return null;
        }
    }

    private static String findUniqueMethod(
            ClassData cls, FindMethod query, String slotKey, Map<String, String> statuses) {
        MethodData md = findUniqueMethodData(cls, query, slotKey, statuses);
        return md != null ? md.getMethodName() : null;
    }

    private static MethodData findUniqueMethodData(
            ClassData cls, FindMethod query, String slotKey, Map<String, String> statuses) {
        MethodDataList list = cls.findMethod(query);
        int count = list != null ? list.size() : 0;
        if (count == 1) {
            statuses.put(slotKey, "OK");
            return list.get(0);
        } else if (count == 0) {
            statuses.put(slotKey, "MISSING(0)");
            return null;
        } else {
            String[] names = new String[Math.min(count, 4)];
            for (int i = 0; i < names.length; i++) {
                names[i] = list.get(i).getMethodName();
            }
            statuses.put(slotKey, "AMBIGUOUS(" + count + ": " + joinSamples(names, count) + ")");
            return null;
        }
    }

    private static String formatAmbiguous(String[] names) {
        return "AMBIGUOUS(" + names.length + ": " + joinSamples(names, names.length) + ")";
    }

    private static String joinSamples(String[] samples, int totalCount) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < samples.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(samples[i]);
        }
        if (totalCount > samples.length) sb.append(", ...");
        return sb.toString();
    }
}
