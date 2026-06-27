package com.nadirkhoulali.ucs.claim;

import java.util.Objects;

public record ClaimLeaseFailure(
        ClaimLeaseFailureReason reason,
        String detail
) {
    public ClaimLeaseFailure {
        Objects.requireNonNull(reason, "reason");
        detail = Objects.requireNonNull(detail, "detail");
    }
}
