package com.nadirkhoulali.ucs.claim;

import java.util.Objects;

public record ClaimChunkEditFailure(
        ClaimChunkEditFailureReason reason,
        String detail
) {
    public ClaimChunkEditFailure {
        Objects.requireNonNull(reason, "reason");
        detail = Objects.requireNonNull(detail, "detail");
    }
}
