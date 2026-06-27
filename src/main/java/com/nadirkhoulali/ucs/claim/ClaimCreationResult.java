package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.core.model.AuditEntry;

import java.util.Objects;
import java.util.Optional;

public record ClaimCreationResult(
        Optional<ClaimView> claim,
        Optional<ClaimCreationFailure> failure,
        Optional<AuditEntry> auditEntry,
        int selectedChunkCount
) {
    public ClaimCreationResult {
        claim = Objects.requireNonNull(claim, "claim");
        failure = Objects.requireNonNull(failure, "failure");
        auditEntry = Objects.requireNonNull(auditEntry, "auditEntry");
        if (claim.isPresent() == failure.isPresent()) {
            throw new IllegalArgumentException("claim creation result must contain either a claim or a failure");
        }
        if (claim.isPresent() != auditEntry.isPresent()) {
            throw new IllegalArgumentException("successful claim creation must include an audit entry");
        }
        if (selectedChunkCount < 0) {
            throw new IllegalArgumentException("selectedChunkCount must not be negative");
        }
    }

    public static ClaimCreationResult success(ClaimView claim, AuditEntry auditEntry, int selectedChunkCount) {
        return new ClaimCreationResult(
                Optional.of(claim),
                Optional.empty(),
                Optional.of(auditEntry),
                selectedChunkCount
        );
    }

    public static ClaimCreationResult failure(ClaimCreationFailure failure, int selectedChunkCount) {
        return new ClaimCreationResult(
                Optional.empty(),
                Optional.of(failure),
                Optional.empty(),
                selectedChunkCount
        );
    }
}
