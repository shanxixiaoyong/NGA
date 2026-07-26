package sp.phone.common.network;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Foundation-stage allowlist for legacy write operations.
 *
 * <p>Authorization is tied to a reviewed operation identity, never inferred
 * from an HTTP method or URL. The foundation build intentionally enables no
 * mutation operations.</p>
 */
public final class FoundationMutationGate {

    public enum Operation {
        TOPIC_POST,
        POST_COMMENT,
        AVATAR_PROFILE_UPDATE,
        AVATAR_FILE_UPLOAD
    }

    private static final Set<Operation> REVIEWED_ENABLED_OPERATIONS =
            Collections.unmodifiableSet(EnumSet.noneOf(Operation.class));

    private FoundationMutationGate() {
    }

    public static boolean isAllowed(Operation operation) {
        return operation != null && REVIEWED_ENABLED_OPERATIONS.contains(operation);
    }
}
