package com.nadirkhoulali.ucs.claim;

import java.util.Objects;

public record ClaimMetadataFailure(
        ClaimMetadataFailureReason reason,
        String detail
) {
    public ClaimMetadataFailure {
        Objects.requireNonNull(reason, "reason");
        detail = Objects.requireNonNull(detail, "detail").trim();
    }
}
