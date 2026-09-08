package sp.phone.linuxdo;

import android.content.Context;

import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.ExperimentalCronetEngine;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlResponseInfo;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;

/** Bootstrap-safe HTTP/3 DoH used by the LinuxDo-only WebView tunnel. */
final class LinuxDoCronetDohResolver implements LinuxDoCloseableDns {

    private static final int MAX_BYTES = 64 * 1024;
    private static final int TIMEOUT_SECONDS = 12;

    private final CronetEngine mEngine;
    private final ExecutorService mCallbacks = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "linuxdo-browser-doh");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpUrl mResolverUrl;
    private final ConcurrentMap<UrlRequest, Boolean> mRequests = new ConcurrentHashMap<>();
    private volatile boolean mClosed;

    LinuxDoCronetDohResolver(Context context, HttpUrl resolverUrl, InetAddress bootstrap) {
        if (context == null || resolverUrl == null || bootstrap == null) {
            throw new IllegalArgumentException("missing Cronet DoH bootstrap");
        }
        mResolverUrl = resolverUrl;
        String host = resolverUrl.host();
        String address = bootstrap.getHostAddress();
        String options = "{\"HostResolverRules\":{\"host_resolver_rules\":\"MAP "
                + host + ' ' + address + "\"}}";
        mEngine = new ExperimentalCronetEngine.Builder(context.getApplicationContext())
                .setUserAgent("NGA-LinuxDo-DoH")
                .enableQuic(true)
                .addQuicHint(host, 443, 443)
                .setExperimentalOptions(options)
                .build();
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        if (mClosed) throw failure(hostname, "resolver closed", null);
        HttpUrl query = mResolverUrl.newBuilder()
                .addQueryParameter("name", hostname)
                .addQueryParameter("type", "A")
                .build();
        Result result = new Result(hostname);
        UrlRequest request = mEngine.newUrlRequestBuilder(
                        query.toString(), result, mCallbacks)
                .addHeader("Accept", "application/dns-json")
                .setPriority(UrlRequest.Builder.REQUEST_PRIORITY_HIGHEST)
                .build();
        result.request = request;
        mRequests.put(request, Boolean.TRUE);
        request.start();
        try {
            if (!result.done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                request.cancel();
                throw failure(hostname, "DoH timeout", null);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            request.cancel();
            throw failure(hostname, "DoH interrupted", error);
        } finally {
            mRequests.remove(request);
        }
        if (result.error != null) throw failure(hostname, "DoH request failed", result.error);
        if (result.status < 200 || result.status >= 300) {
            throw failure(hostname, "DoH HTTP " + result.status, null);
        }
        try {
            JSONObject root = new JSONObject(result.body.toString(StandardCharsets.UTF_8.name()));
            if (root.optInt("Status", -1) != 0) {
                throw failure(hostname, "DoH status " + root.optInt("Status", -1), null);
            }
            JSONArray answers = root.optJSONArray("Answer");
            List<InetAddress> addresses = new ArrayList<>();
            if (answers != null) for (int index = 0; index < answers.length(); index++) {
                JSONObject answer = answers.optJSONObject(index);
                if (answer == null || answer.optInt("type", -1) != 1) continue;
                byte[] raw = parseIpv4(answer.optString("data", ""));
                if (raw != null) addresses.add(InetAddress.getByAddress(hostname, raw));
            }
            if (addresses.isEmpty()) throw failure(hostname, "no IPv4 answer", null);
            return addresses;
        } catch (UnknownHostException error) {
            throw error;
        } catch (Exception error) {
            throw failure(hostname, "invalid DoH response", error);
        }
    }

    @Override
    public void close() {
        if (mClosed) return;
        mClosed = true;
        for (UrlRequest request : mRequests.keySet()) request.cancel();
        mRequests.clear();
        mCallbacks.execute(() -> {
            try {
                mEngine.shutdown();
            } catch (RuntimeException ignored) {
                // Cancellation callbacks can finish just after teardown; no app state depends on
                // this short-lived browser resolver once its proxy has closed.
            } finally {
                mCallbacks.shutdown();
            }
        });
    }

    private final class Result extends UrlRequest.Callback {
        final String hostname;
        final CountDownLatch done = new CountDownLatch(1);
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        final ByteBuffer buffer = ByteBuffer.allocateDirect(8 * 1024);
        volatile UrlRequest request;
        volatile Throwable error;
        volatile int status;

        Result(String hostname) {
            this.hostname = hostname;
        }

        @Override public void onRedirectReceived(
                UrlRequest request, UrlResponseInfo info, String newLocationUrl) {
            error = new IllegalStateException("unexpected DoH redirect");
            request.cancel();
        }

        @Override public void onResponseStarted(UrlRequest request, UrlResponseInfo info) {
            status = info.getHttpStatusCode();
            request.read(buffer);
        }

        @Override public void onReadCompleted(
                UrlRequest request, UrlResponseInfo info, ByteBuffer byteBuffer) {
            byteBuffer.flip();
            int count = byteBuffer.remaining();
            if (body.size() + count > MAX_BYTES) {
                error = new IllegalStateException("DoH response too large");
                request.cancel();
                return;
            }
            byte[] bytes = new byte[count];
            byteBuffer.get(bytes);
            body.write(bytes, 0, bytes.length);
            byteBuffer.clear();
            request.read(byteBuffer);
        }

        @Override public void onSucceeded(UrlRequest request, UrlResponseInfo info) {
            done.countDown();
        }

        @Override public void onFailed(
                UrlRequest request, UrlResponseInfo info, CronetException failure) {
            error = failure;
            done.countDown();
        }

        @Override public void onCanceled(UrlRequest request, UrlResponseInfo info) {
            if (error == null) error = new IllegalStateException("DoH canceled");
            done.countDown();
        }
    }

    private static byte[] parseIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return null;
        byte[] result = new byte[4];
        for (int index = 0; index < parts.length; index++) {
            try {
                int octet = Integer.parseInt(parts[index]);
                if (octet < 0 || octet > 255) return null;
                result[index] = (byte) octet;
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return result;
    }

    private static UnknownHostException failure(
            String hostname, String message, Throwable cause) {
        UnknownHostException error = new UnknownHostException(hostname + ": " + message);
        if (cause != null) error.initCause(cause);
        return error;
    }
}
