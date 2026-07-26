package com.justwent.androidnga.bu.session;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Immutable account session material captured for one operation.
 *
 * <p>The cookie is intentionally available to the transport boundary, but is
 * excluded from {@link #toString()}, {@link #equals(Object)}, and
 * {@link #hashCode()} so logging and value comparisons cannot disclose it.</p>
 */
public final class AccountSessionSnapshot {

    public static final String ANONYMOUS_ACCOUNT_ID = "anonymous";

    private final String accountId;
    private final long sessionGeneration;
    private final String uid;
    private final String cookieHeader;
    private final boolean anonymous;

    private AccountSessionSnapshot(
            String accountId,
            long sessionGeneration,
            String uid,
            String cookieHeader,
            boolean anonymous) {
        if (sessionGeneration < 0) {
            throw new IllegalArgumentException("sessionGeneration must not be negative");
        }
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.sessionGeneration = sessionGeneration;
        this.uid = Objects.requireNonNull(uid, "uid");
        this.cookieHeader = Objects.requireNonNull(cookieHeader, "cookieHeader");
        this.anonymous = anonymous;
    }

    @NonNull
    public static AccountSessionSnapshot authenticated(
            @NonNull String accountId,
            long sessionGeneration,
            @NonNull String uid,
            @NonNull String cookieHeader) {
        if (accountId.isEmpty() || ANONYMOUS_ACCOUNT_ID.equals(accountId)) {
            throw new IllegalArgumentException("Authenticated accountId is invalid");
        }
        if (uid.isEmpty() || cookieHeader.isEmpty()) {
            throw new IllegalArgumentException("Authenticated session material is missing");
        }
        return new AccountSessionSnapshot(
                accountId, sessionGeneration, uid, cookieHeader, false);
    }

    @NonNull
    public static AccountSessionSnapshot anonymous(long sessionGeneration) {
        return new AccountSessionSnapshot(
                ANONYMOUS_ACCOUNT_ID, sessionGeneration, "", "", true);
    }

    @NonNull
    public String getAccountId() {
        return accountId;
    }

    public long getSessionGeneration() {
        return sessionGeneration;
    }

    @NonNull
    public String getUid() {
        return uid;
    }

    /** Returns the already-captured Cookie header for the trusted transport boundary. */
    @NonNull
    public String getCookieHeader() {
        return cookieHeader;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AccountSessionSnapshot)) {
            return false;
        }
        AccountSessionSnapshot that = (AccountSessionSnapshot) other;
        return sessionGeneration == that.sessionGeneration
                && anonymous == that.anonymous
                && accountId.equals(that.accountId)
                && uid.equals(that.uid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, sessionGeneration, uid, anonymous);
    }

    @NonNull
    @Override
    public String toString() {
        return anonymous
                ? "AccountSessionSnapshot{anonymous}"
                : "AccountSessionSnapshot{authenticated}";
    }
}
