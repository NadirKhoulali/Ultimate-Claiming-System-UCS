package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyResult;
import com.nadirkhoulali.ucs.core.model.AuditEntry;

import java.util.Objects;
import java.util.Optional;

public record ClaimCreationResult(
        Optional<ClaimView> claim,
        Optional<ClaimCreationFailure> failure,
        Optional<AuditEntry> auditEntry,
        Optional<ClaimEconomyResult> economyResult,
        int selectedChunkCount
) {
    public ClaimCreationResult {
        claim = Objects.requireNonNull(claim, "claim");
        failure = Objects.requireNonNull(failure, "failure");
        auditEntry = Objects.requireNonNull(auditEntry, "auditEntry");
        economyResult = Objects.requireNonNull(economyResult, "economyResult");
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
        return success(claim, auditEntry, selectedChunkCount, Optional.empty());
    }

    public static ClaimCreationResult success(
            ClaimView claim,
            AuditEntry auditEntry,
            int selectedChunkCount,
            ClaimEconomyResult economyResult
    ) {
        return success(claim, auditEntry, selectedChunkCount, Optional.of(economyResult));
    }

    private static ClaimCreationResult success(
            ClaimView claim,
            AuditEntry auditEntry,
            int selectedChunkCount,
            Optional<ClaimEconomyResult> economyResult
    ) {
        return new ClaimCreationResult(
                Optional.of(claim),
                Optional.empty(),
                Optional.of(auditEntry),
                economyResult,
                selectedChunkCount
        );
    }

    public static ClaimCreationResult failure(ClaimCreationFailure failure, int selectedChunkCount) {
        return failure(failure, selectedChunkCount, Optional.empty());
    }

    public static ClaimCreationResult failure(
            ClaimCreationFailure failure,
            int selectedChunkCount,
            ClaimEconomyResult economyResult
    ) {
        return failure(failure, selectedChunkCount, Optional.of(economyResult));
    }

    private static ClaimCreationResult failure(
            ClaimCreationFailure failure,
            int selectedChunkCount,
            Optional<ClaimEconomyResult> economyResult
    ) {
        return new ClaimCreationResult(
                Optional.empty(),
                Optional.of(failure),
                Optional.empty(),
                economyResult,
                selectedChunkCount
        );
    }
}
