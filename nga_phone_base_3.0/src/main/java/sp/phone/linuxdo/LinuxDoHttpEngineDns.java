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
import java.util.List;

/** Android 14+ Cloudflare DoH resolver backed by the built-in Chromium network stack. */
final class LinuxDoHttpEngineDns implements LinuxDoCloseableDns {

    private static final String RESOLVER_HOST = "chrome.cloudflare-dns.com";
    private static final String RESOLVER_URL =
            "https://chrome.cloudflare-dns.com/dns-query";
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private final HttpEngine mEngine;

    LinuxDoHttpEngineDns(Context context) {
        mEngine = new HttpEngine.Builder(context.getApplicationContext())
                .setEnableQuic(true)
                .addQuicHint(RESOLVER_HOST, 443, 443)
                .build();
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        if (hostname == null || hostname.isEmpty()) {
            throw new UnknownHostException("empty hostname");
        }
        HttpURLConnection connection = null;
        try {
            String queryUrl = Uri.parse(RESOLVER_URL).buildUpon()
                    .appendQueryParameter("name", hostname)
                    .appendQueryParameter("type", "A")
                    .build().toString();
            URL url = new URL(queryUrl);
            connection = (HttpURLConnection) mEngine.openConnection(url);
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
            return result;
        } catch (UnknownHostException error) {
            throw error;
        } catch (Exception error) {
            throw failure(hostname, "Cloudflare DoH failed", error);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    @Override
    public void close() {
        mEngine.shutdown();
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
