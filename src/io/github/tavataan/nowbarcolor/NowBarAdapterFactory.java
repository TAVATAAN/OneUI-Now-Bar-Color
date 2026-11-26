package io.github.tavataan.nowbarcolor;

import android.os.Build;

/**
 * Selects the appropriate isolated branch adapter from live AOD capabilities,
 * target AOD version code, and Android SDK level.
 */
public final class NowBarAdapterFactory {
    static final long ONE_UI_85_AOD_MIN_VERSION = 893000000L;
    static final long ONE_UI_9_AOD_MIN_VERSION = 896000000L;

    private NowBarAdapterFactory() {
    }

    public static NowBarCardAdapter resolve(Class<?> baseCardClass, HookMap map) {
        if (baseCardClass != null
                && ReflectionCache.method(baseCardClass, "getCardViewMpsmBackground") != null) {
            return OneUi9Adapter.INSTANCE;
        }
        if (map != null) {
            if (map.baseClearContainerBgMethod != null || map.aodVersionCode >= ONE_UI_9_AOD_MIN_VERSION) {
                return OneUi9Adapter.INSTANCE;
            }
            if (map.aodVersionCode >= ONE_UI_85_AOD_MIN_VERSION
                    || map.mediaForegroundMethod != null
                    || map.briefCardClass != null
                    || map.baseProgressBgMethod != null) {
                return OneUi85Adapter.INSTANCE;
            }
            if (map.aodVersionCode > 0L) {
                return OneUi80Adapter.INSTANCE;
            }
        }
        if (baseCardClass != null
                && ReflectionCache.method(baseCardClass, "getCardOuterGlowView") == null
                && ReflectionCache.method(baseCardClass, "getCardGuidingEffectView") != null
                && ReflectionCache.method(baseCardClass, "isExpanded") == null) {
            return OneUi80Adapter.INSTANCE;
        }
        return fallbackForSdk();
    }

    public static NowBarCardAdapter fallbackForSdk() {
        return Build.VERSION.SDK_INT >= 37 ? OneUi9Adapter.INSTANCE : OneUi85Adapter.INSTANCE;
    }
}
