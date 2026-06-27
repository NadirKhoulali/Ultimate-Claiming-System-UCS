package com.nadirkhoulali.ucs.claim;

import java.util.Objects;

public record ClaimRoleFailure(
        ClaimRoleFailureReason reason,
        String detail
) {
    public ClaimRoleFailure {
        Objects.requireNonNull(reason, "reason");
        detail = Objects.requireNonNull(detail, "detail").trim();
    }
}
