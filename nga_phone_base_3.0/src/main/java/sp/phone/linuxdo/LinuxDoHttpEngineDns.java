package sp.phone.linuxdo;

import android.content.Context;
import android.net.Uri;
import android.net.http.HttpEngine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Android 14+ Cloudflare DoH resolver backed by the built-in Chromium network stack. */
final class LinuxDoHttpEngineDns implements LinuxDoCloseableDns {

    private static final String RESOLVER_HOST = "chrome.cloudflare-dns.com";
    private static final String RESOLVER_URL =
            "https://chrome.cloudflare-dns.com/dns-query";
    private static final String DIRECT_FALLBACK_DOH =
            "https://1.1.1.1/dns-query";
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final long CACHE_TTL_MS = 3L * 60L * 1000L;
    private static final int MAX_CACHE_ENTRIES = 16;
    private static final Map<String, CacheEntry> ADDRESS_CACHE = new HashMap<>();

    private final Context mContext;
    private final Object mEngineLock = new Object();
    private HttpEngine mEngine;
    private boolean mClosed;
    private int mActiveLookups;

    LinuxDoHttpEngineDns(Context context) {
        mContext = context.getApplicationContext();
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        if (hostname == null || hostname.isEmpty()) {
            throw new UnknownHostException("empty hostname");
        }
        // linux.do itself has a stable Cloudflare anycast pair. Prefer the app-local hosts entry
        // so opening feeds, topics and account endpoints never waits for a DoH service. The
        // request still uses the linux.do URL/Host/SNI and therefore keeps normal TLS validation.
        List<InetAddress> pinned = LinuxDoPinnedHosts.lookup(hostname);
        if (!pinned.isEmpty()) return pinned;
        synchronized (mEngineLock) {
            if (mClosed) throw new UnknownHostException("resolver closed");
            mActiveLookups++;
        }
        HttpURLConnection connection = null;
        try {
            List<InetAddress> cached = cachedAddresses(hostname);
            if (cached != null) return cached;
            String queryUrl = Uri.parse(RESOLVER_URL).buildUpon()
                    .appendQueryParameter("name", hostname)
                    .appendQueryParameter("type", "A")
                    .build().toString();
            URL url = new URL(queryUrl);
            connection = (HttpURLConnection) engine().openConnection(url);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setRequestProperty("Accept", "application/dns-json");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw failure(hostname, "DoH HTTP " + status, null);
            }
            byte[] body = readBounded(connection.getInputStream());
            JSONObject root = new JSONObject(new String(body, StandardCharsets.UTF_8));
            if (root.optInt("Status", -1) != 0) {
                throw failure(hostname, "DoH status " + root.optInt("Status", -1), null);
            }
            JSONArray answers = root.optJSONArray("Answer");
            List<InetAddress> result = new ArrayList<>();
            if (answers != null) {
                for (int index = 0; index < answers.length(); index++) {
                    JSONObject answer = answers.optJSONObject(index);
                    if (answer == null || answer.optInt("type", -1) != 1) continue;
                    byte[] address = parseIpv4(answer.optString("data", ""));
                    if (address != null) {
                        result.add(InetAddress.getByAddress(hostname, address));
                    }
                }
            }
            if (result.isEmpty()) throw failure(hostname, "no IPv4 answer", null);
            cacheAddresses(hostname, result);
            return new ArrayList<>(result);
        } catch (Exception primaryError) {
            LinuxDoLoginDohResolver fallback = null;
            try {
                fallback = new LinuxDoLoginDohResolver(mContext, DIRECT_FALLBACK_DOH);
                List<InetAddress> result = fallback.lookup(hostname);
                if (result == null || result.isEmpty()) {
                    throw failure(hostname, "fallback DoH returned no address", primaryError);
                }
                cacheAddresses(hostname, result);
                return new ArrayList<>(result);
            } catch (Exception fallbackError) {
                throw failure(hostname, "secure DNS endpoints unavailable", fallbackError);
            } finally {
                if (fallback != null) fallback.close();
            }
        } finally {
            if (connection != null) connection.disconnect();
            finishLookup();
        }
    }

    private static List<InetAddress> cachedAddresses(String hostname) {
        long now = android.os.SystemClock.elapsedRealtime();
        synchronized (ADDRESS_CACHE) {
            CacheEntry entry = ADDRESS_CACHE.get(hostname);
            if (entry == null) return null;
            if (now - entry.storedAt >= CACHE_TTL_MS) {
                ADDRESS_CACHE.remove(hostname);
                return null;
            }
            return new ArrayList<>(entry.addresses);
        }
    }

    private static void cacheAddresses(String hostname, List<InetAddress> addresses) {
        synchronized (ADDRESS_CACHE) {
            if (ADDRESS_CACHE.size() >= MAX_CACHE_ENTRIES
                    && !ADDRESS_CACHE.containsKey(hostname)) {
                String oldestHost = null;
                long oldestAt = Long.MAX_VALUE;
                for (Map.Entry<String, CacheEntry> entry : ADDRESS_CACHE.entrySet()) {
                    if (entry.getValue().storedAt < oldestAt) {
                        oldestAt = entry.getValue().storedAt;
                        oldestHost = entry.getKey();
                    }
                }
                if (oldestHost != null) ADDRESS_CACHE.remove(oldestHost);
            }
            ADDRESS_CACHE.put(hostname, new CacheEntry(
                    android.os.SystemClock.elapsedRealtime(),
                    new ArrayList<>(addresses)));
        }
    }

    private static final class CacheEntry {
        final long storedAt;
        final List<InetAddress> addresses;

        CacheEntry(long storedAt, List<InetAddress> addresses) {
            this.storedAt = storedAt;
            this.addresses = addresses;
        }
    }

    @Override
    public void close() {
        HttpEngine engine = null;
        synchronized (mEngineLock) {
            if (mClosed) return;
            mClosed = true;
            // Cronet rejects shutdown while a request is active. Leave the engine owned by
            // the resolver until the last lookup returns, then shut it down safely.
            if (mActiveLookups == 0) {
                engine = mEngine;
                mEngine = null;
            }
        }
        shutdownQuietly(engine);
    }

    private HttpEngine engine() {
        synchronized (mEngineLock) {
            if (mClosed) throw new IllegalStateException("resolver closed");
            if (mEngine == null) {
                mEngine = new HttpEngine.Builder(mContext)
                        .setEnableQuic(true)
                        .addQuicHint(RESOLVER_HOST, 443, 443)
                        .build();
            }
            return mEngine;
        }
    }

    private void finishLookup() {
        HttpEngine engine = null;
        synchronized (mEngineLock) {
            if (mActiveLookups > 0) mActiveLookups--;
            if (mClosed && mActiveLookups == 0) {
                engine = mEngine;
                mEngine = null;
            }
        }
        shutdownQuietly(engine);
    }

    private static void shutdownQuietly(HttpEngine engine) {
        if (engine == null) return;
        try {
            engine.shutdown();
        } catch (Throwable ignored) {
            // Android's Cronet wrapper may still report an asynchronous request after the
            // response stream is closed. Teardown must never terminate the application.
        }
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        if (input == null) throw new IOException("empty response");
        try (InputStream closeable = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[1024];
            int read;
            while ((read = closeable.read(chunk)) != -1) {
                if (output.size() + read > MAX_RESPONSE_BYTES) {
                    throw new IOException("DoH response too large");
                }
                output.write(chunk, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static byte[] parseIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return null;
        byte[] result = new byte[4];
        for (int index = 0; index < parts.length; index++) {
            if (parts[index].isEmpty() || parts[index].length() > 3) return null;
            int octet;
            try {
                octet = Integer.parseInt(parts[index]);
            } catch (NumberFormatException ignored) {
                return null;
            }
            if (octet < 0 || octet > 255) return null;
            result[index] = (byte) octet;
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
