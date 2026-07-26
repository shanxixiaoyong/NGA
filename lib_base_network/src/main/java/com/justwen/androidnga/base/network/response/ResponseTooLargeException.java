package com.justwen.androidnga.base.network.response;

import java.io.IOException;

/** Signals that a response crossed the configured in-memory boundary. */
public final class ResponseTooLargeException extends IOException {

    private final int maximumBytes;

    ResponseTooLargeException(int maximumBytes) {
        super("NGA response exceeds " + maximumBytes + " bytes");
        this.maximumBytes = maximumBytes;
    }

    public int getMaximumBytes() {
        return maximumBytes;
    }
}
