package sp.phone.linuxdo;

import android.text.TextUtils;
import android.util.LruCache;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gov.anzong.androidnga.http.OnHttpCallBack;

/** Bounded visible-row locality enrichment for Discourse user profiles. */
public final class LinuxDoLocalityRepository {

    public interface Callback {
        void onLocality(int authorId, String locality);
    }

    private static final String NO_LOCALITY = "\u0000";
    private static final LinuxDoLocalityRepository INSTANCE = new LinuxDoLocalityRepository();

    private final LruCache<String, String> mCache = new LruCache<>(128);
    private final ArrayDeque<Request> mPending = new ArrayDeque<>();
    private final Map<String, List<Request>> mWaiting = new HashMap<>();
    private int mActive;

    private LinuxDoLocalityRepository() {
    }

    public static LinuxDoLocalityRepository getInstance() {
        return INSTANCE;
    }

    public void request(int authorId, String username, Callback callback) {
        if (authorId <= 0 || TextUtils.isEmpty(username) || callback == null) return;
        String key = username.trim().toLowerCase(java.util.Locale.ROOT);
        String cached = mCache.get(key);
        if (cached != null) {
            callback.onLocality(authorId, NO_LOCALITY.equals(cached) ? null : cached);
            return;
        }
        Request request = new Request(authorId, username, key, callback);
        List<Request> waiters = mWaiting.get(key);
        if (waiters != null) {
            waiters.add(request);
            return;
        }
        waiters = new ArrayList<>();
        waiters.add(request);
        mWaiting.put(key, waiters);
        mPending.add(request);
        pump();
    }

    public void removeCallback(Callback callback) {
        if (callback == null) return;
        for (List<Request> requests : mWaiting.values()) {
            requests.removeIf(request -> request.callback == callback);
        }
    }

    private void pump() {
        while (mActive < 2 && !mPending.isEmpty()) {
            Request request = mPending.removeFirst();
            mActive++;
            LinuxDoRepository.getInstance().loadUserLocation(
                    request.username, new OnHttpCallBack<String>() {
                        @Override
                        public void onSuccess(String locality) {
                            finish(request.key, locality);
                        }

                        @Override
                        public void onError(String text) {
                            finish(request.key, null);
                        }
                    });
        }
    }

    private void finish(String key, String locality) {
        mActive = Math.max(0, mActive - 1);
        String normalized = TextUtils.isEmpty(locality) ? null : locality.trim();
        mCache.put(key, TextUtils.isEmpty(normalized) ? NO_LOCALITY : normalized);
        List<Request> waiters = mWaiting.remove(key);
        if (waiters != null) {
            for (Request waiter : new ArrayList<>(waiters)) {
                waiter.callback.onLocality(waiter.authorId, normalized);
            }
        }
        pump();
    }

    private static final class Request {
        final int authorId;
        final String username;
        final String key;
        final Callback callback;

        Request(int authorId, String username, String key, Callback callback) {
            this.authorId = authorId;
            this.username = username;
            this.key = key;
            this.callback = callback;
        }
    }
}
