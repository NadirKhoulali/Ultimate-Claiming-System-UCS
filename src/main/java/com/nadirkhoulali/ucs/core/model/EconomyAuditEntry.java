package com.nadirkhoulali.ucs.core.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record EconomyAuditEntry(
        UUID id,
        Instant occurredAt,
        String actorKey,
        EconomyAuditAction action,
        EconomyAuditStatus status,
        Optional<ClaimId> claimId,
        String ownerKey,
        BigDecimal amount,
        String reference,
        String providerId,
        String providerReference,
        String reason,
        String detail
) {
    public EconomyAuditEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(occurredAt, "occurredAt");
        actorKey = IdentifierRules.requireNonBlank(actorKey, "actorKey");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(status, "status");
        claimId = Objects.requireNonNull(claimId, "claimId");
        ownerKey = IdentifierRules.requireNonBlank(ownerKey, "ownerKey");
        amount = amount == null ? BigDecimal.ZERO : amount;
        reference = IdentifierRules.requireNonBlank(reference, "reference");
        providerId = providerId == null ? "" : providerId.trim();
        providerReference = providerReference == null ? "" : providerReference.trim();
        reason = IdentifierRules.requireNonBlank(reason, "reason");
        detail = IdentifierRules.requireNonBlank(detail, "detail");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("amount must be nonnegative");
        }
    }
}
