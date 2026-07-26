package com.justwen.androidnga.base.network.response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Headers;
import okhttp3.ResponseBody;

/** Bounded transport response. Its string form deliberately omits URL, headers, and body. */
public final class RawNgaResponse {

    public static final int DEFAULT_MAXIMUM_BYTES = 1024 * 1024;

    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final byte[] rawBytes;
    private final String finalUrl;
    private final int redirectCount;

    private RawNgaResponse(
            int statusCode,
            Map<String, List<String>> headers,
            byte[] rawBytes,
            String finalUrl,
            int redirectCount
    ) {
        this.statusCode = statusCode;
        this.headers = immutableHeaders(headers);
        this.rawBytes = rawBytes.clone();
        this.finalUrl = finalUrl;
        this.redirectCount = redirectCount;
    }

    public static RawNgaResponse from(retrofit2.Response<ResponseBody> response) throws IOException {
        return from(response, DEFAULT_MAXIMUM_BYTES);
    }

    public static RawNgaResponse from(
            retrofit2.Response<ResponseBody> response,
            int maximumBytes
    ) throws IOException {
        if (response == null) {
            throw new NullPointerException("response");
        }
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("maximumBytes must be positive");
        }

        ResponseBody body = response.isSuccessful() ? response.body() : response.errorBody();
        byte[] bytes = readAndClose(body, maximumBytes);
        okhttp3.Response raw = response.raw();
        return new RawNgaResponse(
                response.code(),
                response.headers().toMultimap(),
                bytes,
                raw.request().url().toString(),
                countRedirects(raw)
        );
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public byte[] getRawBytes() {
        return rawBytes.clone();
    }

    public String getFinalUrl() {
        return finalUrl;
    }

    public int getRedirectCount() {
        return redirectCount;
    }

    public String firstHeader(String name) {
        if (name == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey()) && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "RawNgaResponse{statusCode=" + statusCode
                + ", byteCount=" + rawBytes.length
                + ", redirectCount=" + redirectCount
                + ", headers=<redacted>, finalUrl=<redacted>, rawBytes=<redacted>}";
    }

    private static byte[] readAndClose(ResponseBody body, int maximumBytes) throws IOException {
        if (body == null) {
            return new byte[0];
        }
        try (ResponseBody ignored = body;
             InputStream input = body.byteStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream(
                     Math.min(maximumBytes, 16 * 1024))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maximumBytes) {
                    throw new ResponseTooLargeException(maximumBytes);
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static int countRedirects(okhttp3.Response response) {
        int count = 0;
        okhttp3.Response previous = response.priorResponse();
        while (previous != null) {
            count++;
            previous = previous.priorResponse();
        }
        return count;
    }

    private static Map<String, List<String>> immutableHeaders(
            Map<String, List<String>> source
    ) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }
}
