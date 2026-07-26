package sp.phone.param;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import gov.anzong.androidnga.common.util.NgaRequestPolicy;
import sp.phone.common.network.FoundationMutationGate;

public class HttpPostClient {

    static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    static final int READ_TIMEOUT_MILLIS = 10_000;

    private final String urlString;
    private final FoundationMutationGate.Operation operation;
    private final ConnectionOpener connectionOpener;
    private String cookie;

    public HttpPostClient(
            String urlString,
            FoundationMutationGate.Operation operation
    ) {
        this(urlString, null, operation, HttpPostClient::openConnection);
    }

    public HttpPostClient(
            String urlString,
            String cookie,
            FoundationMutationGate.Operation operation
    ) {
        this(urlString, cookie, operation, HttpPostClient::openConnection);
    }

    HttpPostClient(
            String urlString,
            String cookie,
            FoundationMutationGate.Operation operation,
            ConnectionOpener connectionOpener
    ) {
        this.urlString = urlString;
        this.cookie = cookie;
        this.operation = operation;
        this.connectionOpener = Objects.requireNonNull(connectionOpener, "connectionOpener");
    }

    /**
     * @return the cookie
     */
    public String getCookie() {
        return cookie;
    }

    /**
     * @param cookie the cookie to set
     */
    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    public HttpURLConnection post_body(String body) {
        if (!FoundationMutationGate.isAllowed(operation)) {
            return null;
        }
        URL url = parseTrustedNgaUrl(urlString);
        if (url == null || body == null || containsHeaderControlCharacter(cookie)) {
            return null;
        }

        HttpURLConnection conn = null;
        try {
            conn = connectionOpener.open(url);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            conn.setReadTimeout(READ_TIMEOUT_MILLIS);
            conn.setInstanceFollowRedirects(false);
            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");

            if (cookie != null && !cookie.isEmpty()) {
                conn.setRequestProperty("Cookie", cookie);
            }

            conn.setRequestProperty("User-Agent", NgaRequestPolicy.DEFAULT_USER_AGENT);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Accept-Charset", "GBK");

            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bodyBytes.length);

            conn.connect();
            try (OutputStream output = conn.getOutputStream()) {
                output.write(bodyBytes);
                output.flush();
            }
            return conn;
        } catch (IOException | RuntimeException ignored) {
            if (conn != null) {
                conn.disconnect();
            }
            return null;
        }
    }

    static boolean isTrustedNgaUrl(String value) {
        return parseTrustedNgaUrl(value) != null;
    }

    private static URL parseTrustedNgaUrl(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            URL url = new URL(value);
            int port = url.getPort();
            if (url.getUserInfo() != null
                    || (port != -1 && port != 443)
                    || !NgaRequestPolicy.isTrustedHttps(url.getProtocol(), url.getHost())) {
                return null;
            }
            return url;
        } catch (MalformedURLException ignored) {
            return null;
        }
    }

    private static boolean containsHeaderControlCharacter(String value) {
        return value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0);
    }

    private static HttpURLConnection openConnection(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }

    interface ConnectionOpener {
        HttpURLConnection open(URL url) throws IOException;
    }
}
