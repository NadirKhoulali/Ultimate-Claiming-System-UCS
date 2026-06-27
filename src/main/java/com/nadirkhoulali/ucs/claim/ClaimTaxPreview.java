package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.core.model.ClaimId;
import com.nadirkhoulali.ucs.core.model.ClaimTaxState;
import com.nadirkhoulali.ucs.core.model.OwnerRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record ClaimTaxPreview(
        ClaimId claimId,
        String displayName,
        OwnerRef owner,
        int chunkCount,
        BigDecimal amount,
        Instant dueAt,
        boolean dueNow,
        boolean warningWindow,
        ClaimTaxState state
) {
    public ClaimTaxPreview {
        Objects.requireNonNull(claimId, "claimId");
        displayName = Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(dueAt, "dueAt");
        Objects.requireNonNull(state, "state");
        if (chunkCount <= 0) {
            throw new IllegalArgumentException("chunkCount must be positive");
        }
    }
}
