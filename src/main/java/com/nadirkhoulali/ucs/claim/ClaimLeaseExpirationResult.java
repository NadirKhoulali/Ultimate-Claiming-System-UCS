package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.core.model.AuditEntry;

import java.util.List;
import java.util.Objects;

public record ClaimLeaseExpirationResult(
        int scannedClaims,
        int expiredLeases,
        List<AuditEntry> auditEntries
) {
    public ClaimLeaseExpirationResult {
        if (scannedClaims < 0) {
            throw new IllegalArgumentException("scannedClaims must be nonnegative");
        }
        if (expiredLeases < 0) {
            throw new IllegalArgumentException("expiredLeases must be nonnegative");
        }
        auditEntries = List.copyOf(Objects.requireNonNull(auditEntries, "auditEntries"));
    }
}
