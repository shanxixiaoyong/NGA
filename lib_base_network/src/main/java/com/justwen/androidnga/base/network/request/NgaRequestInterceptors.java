package com.justwen.androidnga.base.network.request;

import java.io.IOException;

import gov.anzong.androidnga.common.util.NgaRequestPolicy;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/** Interceptors that enforce request intent before dispatch and credentials on every exchange. */
public final class NgaRequestInterceptors {

    private NgaRequestInterceptors() {
    }

    public static Interceptor foundationGate(FoundationAccessPolicy policy) {
        if (policy == null) {
            throw new NullPointerException("policy");
        }
        return chain -> {
            Request request = chain.request();
            policy.requireAllowed(request.tag(NgaRequestContext.class));
            return chain.proceed(request);
        };
    }

    public static Interceptor credentialBoundary(String userAgent) {
        if (userAgent == null || userAgent.trim().isEmpty()) {
            throw new IllegalArgumentException("userAgent must not be blank");
        }
        return chain -> chain.proceed(secureExchange(chain.request(), userAgent));
    }

    static Request secureExchange(Request original, String userAgent) throws IOException {
        NgaRequestContext context = original.tag(NgaRequestContext.class);
        if (context == null) {
            throw new FoundationAccessDeniedException(
                    FoundationAccessDeniedException.Reason.MISSING_CONTEXT);
        }

        Request.Builder builder = original.newBuilder()
                .removeHeader("X-User-Agent")
                .header("User-Agent", userAgent)
                .removeHeader("Cookie");

        if (isCredentialTarget(original.url())) {
            String cookie = context.getCookieHeader();
            if (cookie != null) {
                builder.header("Cookie", cookie);
            }
        } else {
            builder.removeHeader("Authorization");
        }
        return builder.method(original.method(), original.body()).build();
    }

    public static boolean isCredentialTarget(HttpUrl url) {
        return url != null
                && NgaRequestPolicy.isTrustedHttps(url.scheme(), url.host())
                && url.port() == 443
                && url.username().isEmpty()
                && url.password().isEmpty();
    }
}
