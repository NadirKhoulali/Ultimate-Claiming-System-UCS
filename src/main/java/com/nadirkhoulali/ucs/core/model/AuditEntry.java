package com.nadirkhoulali.ucs.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AuditEntry(
        UUID id,
        Instant occurredAt,
        String actorKey,
        AuditAction action,
        Optional<ClaimId> claimId,
        String detail
) {
    public AuditEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(occurredAt, "occurredAt");
        actorKey = IdentifierRules.requireNonBlank(actorKey, "actorKey");
        Objects.requireNonNull(action, "action");
        claimId = Objects.requireNonNull(claimId, "claimId");
        detail = IdentifierRules.requireNonBlank(detail, "detail");
    }
}
