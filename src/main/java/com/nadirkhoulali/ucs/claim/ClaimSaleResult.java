package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyResult;
import com.nadirkhoulali.ucs.core.model.AuditEntry;

import java.util.Objects;
import java.util.Optional;

public record ClaimSaleResult(
        ClaimSaleAction action,
        Optional<ClaimView> claim,
        Optional<ClaimSaleFailure> failure,
        Optional<AuditEntry> auditEntry,
        Optional<ClaimEconomyResult> economyResult
) {
    public ClaimSaleResult {
        Objects.requireNonNull(action, "action");
        claim = Objects.requireNonNull(claim, "claim");
        failure = Objects.requireNonNull(failure, "failure");
        auditEntry = Objects.requireNonNull(auditEntry, "auditEntry");
        economyResult = Objects.requireNonNull(economyResult, "economyResult");
        if (claim.isPresent() == failure.isPresent()) {
            throw new IllegalArgumentException("claim sale result must contain either a claim or a failure");
        }
        if (claim.isPresent() != auditEntry.isPresent()) {
            throw new IllegalArgumentException("successful claim sale result must include an audit entry");
        }
    }

    public static ClaimSaleResult success(ClaimSaleAction action, ClaimView claim, AuditEntry auditEntry) {
        return new ClaimSaleResult(action, Optional.of(claim), Optional.empty(), Optional.of(auditEntry), Optional.empty());
    }

    public static ClaimSaleResult success(
            ClaimSaleAction action,
            ClaimView claim,
            AuditEntry auditEntry,
            ClaimEconomyResult economyResult
    ) {
        return new ClaimSaleResult(
                action,
                Optional.of(claim),
                Optional.empty(),
                Optional.of(auditEntry),
                Optional.of(economyResult)
        );
    }

    public static ClaimSaleResult failure(ClaimSaleAction action, ClaimSaleFailure failure) {
        return new ClaimSaleResult(action, Optional.empty(), Optional.of(failure), Optional.empty(), Optional.empty());
    }

    public static ClaimSaleResult failure(
            ClaimSaleAction action,
            ClaimSaleFailure failure,
            ClaimEconomyResult economyResult
    ) {
        return new ClaimSaleResult(
                action,
                Optional.empty(),
                Optional.of(failure),
                Optional.empty(),
                Optional.of(economyResult)
        );
    }
}
