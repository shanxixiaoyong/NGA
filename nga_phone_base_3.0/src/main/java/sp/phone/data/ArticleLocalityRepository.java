package sp.phone.data;

import android.text.TextUtils;
import android.util.LruCache;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gov.anzong.androidnga.http.OnHttpCallBack;
import sp.phone.http.bean.ProfileData;
import sp.phone.task.JsonProfileLoadTask;

/**
 * Process-local, bounded USER.PROFILE locality enrichment.
 *
 * Requests are started only by a screen coordinator after scrolling is idle;
 * adapters never call this repository while binding rows.
 */
public final class ArticleLocalityRepository {

    public interface Callback {
        void onLocality(int authorId, String locality);
    }

    private static final int MAX_CACHE_ENTRIES = 128;
    private static final int MAX_CONCURRENT_REQUESTS = 2;
    private static final String NO_LOCALITY = "\u0000";

    private static final ArticleLocalityRepository INSTANCE =
            new ArticleLocalityRepository();

    private final LruCache<Integer, String> mCache =
            new LruCache<>(MAX_CACHE_ENTRIES);
    private final ArrayDeque<Integer> mPending = new ArrayDeque<>();
    private final Map<Integer, List<Callback>> mWaiting = new HashMap<>();
    private final Map<Integer, JsonProfileLoadTask> mActive = new HashMap<>();

    private ArticleLocalityRepository() {
    }

    public static ArticleLocalityRepository getInstance() {
        return INSTANCE;
    }

    /** Must be called on the main thread. */
    public void request(int authorId, Callback callback) {
        if (authorId <= 0 || callback == null) return;
        String cached = mCache.get(authorId);
        if (cached != null) {
            callback.onLocality(authorId, NO_LOCALITY.equals(cached) ? null : cached);
            return;
        }
        List<Callback> callbacks = mWaiting.get(authorId);
        if (callbacks != null) {
            if (!callbacks.contains(callback)) callbacks.add(callback);
            return;
        }
        callbacks = new ArrayList<>();
        callbacks.add(callback);
        mWaiting.put(authorId, callbacks);
        mPending.add(authorId);
        pump();
    }

    /** Prevents a destroyed Fragment from being retained by pending callbacks. */
    public void removeCallback(Callback callback) {
        if (callback == null) return;
        for (List<Callback> callbacks : mWaiting.values()) {
            callbacks.remove(callback);
        }
    }

    private void pump() {
        while (mActive.size() < MAX_CONCURRENT_REQUESTS && !mPending.isEmpty()) {
            final int authorId = mPending.removeFirst();
            JsonProfileLoadTask task = new JsonProfileLoadTask(new OnHttpCallBack<ProfileData>() {
                @Override
                public void onSuccess(ProfileData data) {
                    finish(authorId, data == null ? null : data.ipLoc);
                }

                @Override
                public void onError(String text) {
                    finish(authorId, null);
                }

                @Override
                public void onError(String text, Throwable throwable) {
                    finish(authorId, null);
                }
            });
            mActive.put(authorId, task);
            task.execute("uid=" + authorId);
        }
    }

    private void finish(int authorId, String locality) {
        mActive.remove(authorId);
        String normalized = TextUtils.isEmpty(locality) ? null : locality.trim();
        mCache.put(authorId, TextUtils.isEmpty(normalized) ? NO_LOCALITY : normalized);
        List<Callback> callbacks = mWaiting.remove(authorId);
        if (callbacks != null) {
            for (Callback callback : new ArrayList<>(callbacks)) {
                callback.onLocality(authorId, normalized);
            }
        }
        pump();
    }
}
