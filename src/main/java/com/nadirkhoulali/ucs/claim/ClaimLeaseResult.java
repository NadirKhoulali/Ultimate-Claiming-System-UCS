package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.ClaimLeaseView;
import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyResult;
import com.nadirkhoulali.ucs.core.model.AuditEntry;

import java.util.Objects;
import java.util.Optional;

public record ClaimLeaseResult(
        ClaimLeaseAction action,
        Optional<ClaimView> claim,
        Optional<ClaimLeaseView> lease,
        Optional<ClaimLeaseFailure> failure,
        Optional<AuditEntry> auditEntry,
        Optional<ClaimEconomyResult> economyResult
) {
    public ClaimLeaseResult {
        Objects.requireNonNull(action, "action");
        claim = Objects.requireNonNull(claim, "claim");
        lease = Objects.requireNonNull(lease, "lease");
        failure = Objects.requireNonNull(failure, "failure");
        auditEntry = Objects.requireNonNull(auditEntry, "auditEntry");
        economyResult = Objects.requireNonNull(economyResult, "economyResult");
        if (claim.isPresent() == failure.isPresent()) {
            throw new IllegalArgumentException("claim lease result must contain either a claim or a failure");
        }
        if (claim.isPresent() && (lease.isEmpty() || auditEntry.isEmpty())) {
            throw new IllegalArgumentException("successful claim lease result must include lease and audit entry");
        }
    }

    public static ClaimLeaseResult success(
            ClaimLeaseAction action,
            ClaimView claim,
            ClaimLeaseView lease,
            AuditEntry auditEntry
    ) {
        return new ClaimLeaseResult(
                action,
                Optional.of(claim),
                Optional.of(lease),
                Optional.empty(),
                Optional.of(auditEntry),
                Optional.empty()
        );
    }

    public static ClaimLeaseResult success(
            ClaimLeaseAction action,
            ClaimView claim,
            ClaimLeaseView lease,
            AuditEntry auditEntry,
            ClaimEconomyResult economyResult
    ) {
        return new ClaimLeaseResult(
                action,
                Optional.of(claim),
                Optional.of(lease),
                Optional.empty(),
                Optional.of(auditEntry),
                Optional.of(economyResult)
        );
    }

    public static ClaimLeaseResult failure(ClaimLeaseAction action, ClaimLeaseFailure failure) {
        return new ClaimLeaseResult(
                action,
                Optional.empty(),
                Optional.empty(),
                Optional.of(failure),
                Optional.empty(),
                Optional.empty()
        );
    }

    public static ClaimLeaseResult failure(
            ClaimLeaseAction action,
            ClaimLeaseFailure failure,
            ClaimEconomyResult economyResult
    ) {
        return new ClaimLeaseResult(
                action,
                Optional.empty(),
                Optional.empty(),
                Optional.of(failure),
                Optional.empty(),
                Optional.of(economyResult)
        );
    }
}
