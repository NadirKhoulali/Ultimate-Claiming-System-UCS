package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.core.model.EconomyAuditEntry;

import java.util.Objects;
import java.util.Optional;

public record ClaimEconomyAdminResult(
        boolean success,
        String message,
        Optional<EconomyAuditEntry> auditEntry
) {
    public ClaimEconomyAdminResult {
        message = Objects.requireNonNull(message, "message").trim();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("message cannot be blank");
        }
        auditEntry = Objects.requireNonNull(auditEntry, "auditEntry");
    }

    public static ClaimEconomyAdminResult success(String message, EconomyAuditEntry auditEntry) {
        return new ClaimEconomyAdminResult(true, message, Optional.of(auditEntry));
    }

    public static ClaimEconomyAdminResult failure(String message, EconomyAuditEntry auditEntry) {
        return new ClaimEconomyAdminResult(false, message, Optional.of(auditEntry));
    }

    public static ClaimEconomyAdminResult failure(String message) {
        return new ClaimEconomyAdminResult(false, message, Optional.empty());
    }
}
