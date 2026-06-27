package com.nadirkhoulali.ucs.claim;

import java.util.Objects;

public record ClaimSaleFailure(
        ClaimSaleFailureReason reason,
        String detail
) {
    public ClaimSaleFailure {
        Objects.requireNonNull(reason, "reason");
        detail = Objects.requireNonNull(detail, "detail");
    }
}
