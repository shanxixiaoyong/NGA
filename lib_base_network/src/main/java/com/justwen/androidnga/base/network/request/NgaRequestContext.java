package com.justwen.androidnga.base.network.request;

import java.util.Objects;

/** Immutable identity and intent captured when an NGA operation is created. */
public final class NgaRequestContext {

    public enum Intent {
        READ,
        MUTATION
    }

    public static final String ANONYMOUS_ACCOUNT_ID = "anonymous";

    private final String operationId;
    private final Intent intent;
    private final String accountId;
    private final long sessionGeneration;
    private final String cookieHeader;

    public NgaRequestContext(
            String operationId,
            Intent intent,
            String accountId,
            long sessionGeneration,
            String cookieHeader
    ) {
        this.operationId = requireNonBlank(operationId, "operationId");
        this.intent = Objects.requireNonNull(intent, "intent");
        this.accountId = requireNonBlank(accountId, "accountId");
        if (sessionGeneration < 0) {
            throw new IllegalArgumentException("sessionGeneration must be non-negative");
        }
        this.sessionGeneration = sessionGeneration;
        this.cookieHeader = normalizeCookie(cookieHeader);
        if (ANONYMOUS_ACCOUNT_ID.equals(accountId)
                && (sessionGeneration != 0 || this.cookieHeader != null)) {
            throw new IllegalArgumentException("anonymous context cannot contain session material");
        }
    }

    public static NgaRequestContext anonymousRead(String operationId) {
        return new NgaRequestContext(operationId, Intent.READ, ANONYMOUS_ACCOUNT_ID, 0, null);
    }

    public String getOperationId() {
        return operationId;
    }

    public Intent getIntent() {
        return intent;
    }

    public String getAccountId() {
        return accountId;
    }

    public long getSessionGeneration() {
        return sessionGeneration;
    }

    public String getCookieHeader() {
        return cookieHeader;
    }

    public boolean isAnonymous() {
        return ANONYMOUS_ACCOUNT_ID.equals(accountId);
    }

    @Override
    public String toString() {
        return "NgaRequestContext{operationId='" + operationId
                + "', intent=" + intent
                + ", accountId=<redacted>, sessionGeneration=<redacted>, cookieHeader=<redacted>}";
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String normalizeCookie(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.trim().isEmpty()) {
            return null;
        }
        if (cookieHeader.indexOf('\r') >= 0 || cookieHeader.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("cookieHeader contains a line break");
        }
        return cookieHeader;
    }
}
