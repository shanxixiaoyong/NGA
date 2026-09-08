package sp.phone.linuxdo;

import java.io.IOException;

import okhttp3.HttpUrl;

/** Raised before an OkHttp response can reach the JSON reader when Cloudflare asks for a check. */
public final class CloudflareChallengeException extends IOException {
    private final HttpUrl url;

    public CloudflareChallengeException(HttpUrl url) {
        super("Cloudflare challenge required: " + url);
        this.url = url;
    }

    public HttpUrl url() {
        return url;
    }
}
