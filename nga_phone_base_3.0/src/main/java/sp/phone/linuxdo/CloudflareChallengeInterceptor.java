package sp.phone.linuxdo;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * Detects Cloudflare's explicit challenge response before Retrofit/JSON parsing. Body-based
 * fallbacks remain in {@link LinuxDoTransportPolicy} because some edges omit this header.
 */
public final class CloudflareChallengeInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        if ("challenge".equalsIgnoreCase(response.header("cf-mitigated"))) {
            response.close();
            throw new CloudflareChallengeException(chain.request().url());
        }
        return response;
    }
}
