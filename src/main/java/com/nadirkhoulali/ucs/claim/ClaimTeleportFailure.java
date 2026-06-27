package com.nadirkhoulali.ucs.claim;

import java.util.Objects;

public record ClaimTeleportFailure(
        ClaimTeleportFailureReason reason,
        String detail
) {
    public ClaimTeleportFailure {
        Objects.requireNonNull(reason, "reason");
        detail = Objects.requireNonNull(detail, "detail").trim();
    }
}
