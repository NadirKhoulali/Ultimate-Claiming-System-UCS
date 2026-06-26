package com.nadirkhoulali.ucs.core.model;

import java.time.Instant;
import java.util.Objects;

public record LeaseContract(
        LeaseId id,
        ClaimId claimId,
        OwnerRef tenant,
        Instant startsAt,
        Instant expiresAt
) {
    public LeaseContract {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("expiresAt must be after startsAt");
        }
    }
}
