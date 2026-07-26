package com.justwen.androidnga.base.network.response;

/** Stable response outcome consumed by domain-specific parsers. */
public final class ClassifiedNgaResponse {

    public enum Type {
        PAYLOAD,
        AUTH_REQUIRED,
        CHALLENGE,
        RATE_LIMIT,
        SITE_MESSAGE,
        EMPTY,
        HTTP_ERROR,
        DECODE_ERROR,
        RESPONSE_TOO_LARGE,
        NETWORK_ERROR
    }

    private final Type type;
    private final int statusCode;
    private final String payload;
    private final String retryAfter;

    private ClassifiedNgaResponse(Type type, int statusCode, String payload, String retryAfter) {
        this.type = type;
        this.statusCode = statusCode;
        this.payload = payload;
        this.retryAfter = retryAfter;
    }

    static ClassifiedNgaResponse of(Type type, int statusCode, String payload, String retryAfter) {
        return new ClassifiedNgaResponse(type, statusCode, payload, retryAfter);
    }

    public Type getType() {
        return type;
    }

    public int getStatusCode() {
        return statusCode;
    }

    /** Only PAYLOAD exposes decoded content; error/site bodies remain inside the boundary. */
    public String getPayload() {
        return payload;
    }

    public String getRetryAfter() {
        return retryAfter;
    }

    @Override
    public String toString() {
        return "ClassifiedNgaResponse{type=" + type
                + ", statusCode=" + statusCode
                + ", payload=<redacted>, retryAfter=" + (retryAfter == null ? "null" : "<redacted>")
                + "}";
    }
}
