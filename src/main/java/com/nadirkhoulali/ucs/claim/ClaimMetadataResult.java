package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.core.model.AuditEntry;

import java.util.Objects;
import java.util.Optional;

public record ClaimMetadataResult(
        ClaimMetadataAction action,
        Optional<ClaimView> claim,
        Optional<ClaimMetadataFailure> failure,
        Optional<AuditEntry> auditEntry
) {
    public ClaimMetadataResult {
        Objects.requireNonNull(action, "action");
        claim = Objects.requireNonNull(claim, "claim");
        failure = Objects.requireNonNull(failure, "failure");
        auditEntry = Objects.requireNonNull(auditEntry, "auditEntry");
        if (claim.isPresent() == failure.isPresent()) {
            throw new IllegalArgumentException("claim metadata result must contain either a claim or a failure");
        }
        if (claim.isPresent() != auditEntry.isPresent()) {
            throw new IllegalArgumentException("successful claim metadata update must include an audit entry");
        }
    }

    public static ClaimMetadataResult success(ClaimMetadataAction action, ClaimView claim, AuditEntry auditEntry) {
        return new ClaimMetadataResult(action, Optional.of(claim), Optional.empty(), Optional.of(auditEntry));
    }

    public static ClaimMetadataResult failure(ClaimMetadataAction action, ClaimMetadataFailure failure) {
        return new ClaimMetadataResult(action, Optional.empty(), Optional.of(failure), Optional.empty());
    }
}
