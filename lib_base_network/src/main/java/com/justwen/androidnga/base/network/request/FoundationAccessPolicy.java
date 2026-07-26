package com.justwen.androidnga.base.network.request;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Default-deny foundation gate. HTTP methods are deliberately not part of the decision. */
public final class FoundationAccessPolicy {

    public static final String READ_BOARD_LIST = "board.list";
    public static final String READ_TOPIC_LIST = "topic.list";
    public static final String READ_ARTICLE_LIST = "article.list";

    private static final Set<String> REVIEWED_READ_OPERATIONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    READ_BOARD_LIST,
                    READ_TOPIC_LIST,
                    READ_ARTICLE_LIST
            ))
    );

    private final boolean readAccessEnabled;

    public FoundationAccessPolicy(boolean readAccessEnabled) {
        this.readAccessEnabled = readAccessEnabled;
    }

    public static FoundationAccessPolicy disabled() {
        return new FoundationAccessPolicy(false);
    }

    public static FoundationAccessPolicy enabledForReviewedReads() {
        return new FoundationAccessPolicy(true);
    }

    public boolean isReadAccessEnabled() {
        return readAccessEnabled;
    }

    public Set<String> reviewedReadOperations() {
        return REVIEWED_READ_OPERATIONS;
    }

    public void requireAllowed(NgaRequestContext context) throws FoundationAccessDeniedException {
        if (context == null) {
            throw new FoundationAccessDeniedException(
                    FoundationAccessDeniedException.Reason.MISSING_CONTEXT);
        }
        if (context.getIntent() != NgaRequestContext.Intent.READ) {
            throw new FoundationAccessDeniedException(
                    FoundationAccessDeniedException.Reason.MUTATION_DENIED);
        }
        if (!REVIEWED_READ_OPERATIONS.contains(context.getOperationId())) {
            throw new FoundationAccessDeniedException(
                    FoundationAccessDeniedException.Reason.UNKNOWN_OPERATION);
        }
        if (!readAccessEnabled) {
            throw new FoundationAccessDeniedException(
                    FoundationAccessDeniedException.Reason.READ_ACCESS_DISABLED);
        }
    }
}
