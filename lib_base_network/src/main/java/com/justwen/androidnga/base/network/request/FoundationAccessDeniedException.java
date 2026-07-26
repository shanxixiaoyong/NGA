package com.justwen.androidnga.base.network.request;

import java.io.IOException;

/** Typed pre-dispatch failure for requests outside the foundation read boundary. */
public final class FoundationAccessDeniedException extends IOException {

    public enum Reason {
        READ_ACCESS_DISABLED,
        MISSING_CONTEXT,
        MUTATION_DENIED,
        UNKNOWN_OPERATION
    }

    private final Reason reason;

    FoundationAccessDeniedException(Reason reason) {
        super("Foundation access denied: " + reason.name());
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
