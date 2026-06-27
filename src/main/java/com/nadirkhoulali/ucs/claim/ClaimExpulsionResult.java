package com.nadirkhoulali.ucs.claim;

import java.util.Objects;

public record ClaimExpulsionResult(
        ClaimExpulsionStatus status,
        String detail
) {
    public ClaimExpulsionResult {
        Objects.requireNonNull(status, "status");
        detail = Objects.requireNonNull(detail, "detail").trim();
    }
}
