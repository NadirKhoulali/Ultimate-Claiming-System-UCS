package com.nadirkhoulali.ucs.core.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ClaimTaxLedgerEntry(
        UUID id,
        ClaimId claimId,
        String ownerKey,
        BigDecimal amount,
        Instant dueAt,
        Instant processedAt,
        String reference,
        ClaimTaxLedgerStatus status,
        String providerReference,
        String detail
) {
    public ClaimTaxLedgerEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claimId, "claimId");
        ownerKey = IdentifierRules.requireNonBlank(ownerKey, "ownerKey");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(dueAt, "dueAt");
        Objects.requireNonNull(processedAt, "processedAt");
        reference = IdentifierRules.requireNonBlank(reference, "reference");
        Objects.requireNonNull(status, "status");
        providerReference = providerReference == null ? "" : providerReference.trim();
        detail = IdentifierRules.requireNonBlank(detail, "detail");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("amount must be nonnegative");
        }
    }
}
