package io.github.tavataan.nowbarcolor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Process-local async loader and bounded LruCache (3 MB) for capsule images fetched over
 * NowBarConfigProvider's ParcelFileDescriptor bridge. Never recycles evicted bitmaps manually
 * to prevent mid-draw Canvas exceptions; GC reclaims unreferenced ~500 KB bitmaps safely.
 */
final class CapsuleImageCache {
    private static final int MAX_CACHE_KB = 3072;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "NowBarImageCacheIO");
            t.setDaemon(true);
            return t;
        }
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(MAX_CACHE_KB) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            if (value == null) return 1;
            int bytes = value.getAllocationByteCount();
            return Math.max(1, bytes / 1024);
        }
    };

    private static final Set<String> IN_FLIGHT = new HashSet<>();
    private static final Map<String, List<Runnable>> CALLBACKS = new HashMap<>();

    private CapsuleImageCache() {}

    private static String cacheKey(String slot, long rev) {
        return slot + "@" + rev;
    }

    static Bitmap getIfCached(String slot, long rev) {
        if (slot == null || rev <= 0L) return null;
        synchronized (LOCK) {
            return CACHE.get(cacheKey(slot, rev));
        }
    }

    /**
     * Returns the decoded Bitmap immediately on cache hit, or null while scheduling one background
     * load via ContentResolver.openFileDescriptor and invoking onLoadedMainThread once complete.
     */
    static Bitmap getOrLoadAsync(final Context ctx, final String slot, final long rev, final Runnable onLoadedMainThread) {
        if (slot == null || rev <= 0L) return null;
        final String key = cacheKey(slot, rev);
        synchronized (LOCK) {
            Bitmap cached = CACHE.get(key);
            if (cached != null && !cached.isRecycled()) {
                return cached;
            }
            if (ctx == null) return null;
            if (onLoadedMainThread != null) {
                List<Runnable> list = CALLBACKS.get(key);
                if (list == null) {
                    list = new ArrayList<>();
                    CALLBACKS.put(key, list);
                }
                if (list.size() < 24) {
                    list.add(onLoadedMainThread);
                }
            }
            if (!IN_FLIGHT.add(key)) {
                return null;
            }
        }

        final Context appCtx = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        IO.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap decoded = null;
                try {
                    Uri uri = NowBarConfigProvider.buildImageUri(slot);
                    try (ParcelFileDescriptor pfd = appCtx.getContentResolver().openFileDescriptor(uri, "r")) {
                        if (pfd != null) {
                            BitmapFactory.Options opts = new BitmapFactory.Options();
                            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                            decoded = BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor(), null, opts);
                        }
                    }
                } catch (Throwable ignored) {}

                final Bitmap result = decoded;
                final List<Runnable> pending;
                synchronized (LOCK) {
                    IN_FLIGHT.remove(key);
                    pending = CALLBACKS.remove(key);
                    if (result != null) {
                        purgeOlderRevisionsLocked(slot, key);
                        CACHE.put(key, result);
                    }
                }
                if (result != null && pending != null && !pending.isEmpty()) {
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            for (Runnable r : pending) {
                                try {
                                    r.run();
                                } catch (Throwable ignored) {}
                            }
                        }
                    });
                }
            }
        });
        return null;
    }

    private static void purgeOlderRevisionsLocked(String slot, String currentKey) {
        String prefix = slot + "@";
        Map<String, Bitmap> snapshot = CACHE.snapshot();
        Iterator<String> it = snapshot.keySet().iterator();
        while (it.hasNext()) {
            String k = it.next();
            if (k.startsWith(prefix) && !k.equals(currentKey)) {
                CACHE.remove(k);
            }
        }
    }
}
