package com.nadirkhoulali.ucs.core.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ClaimTaxState(
        ClaimId claimId,
        Instant nextDueAt,
        Optional<Instant> lastPaidAt,
        int missedPayments,
        BigDecimal outstandingDebt,
        Optional<Instant> delinquentSince,
        Optional<Instant> lastWarningAt,
        Instant updatedAt
) {
    public ClaimTaxState {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(nextDueAt, "nextDueAt");
        lastPaidAt = Objects.requireNonNull(lastPaidAt, "lastPaidAt");
        Objects.requireNonNull(outstandingDebt, "outstandingDebt");
        delinquentSince = Objects.requireNonNull(delinquentSince, "delinquentSince");
        lastWarningAt = Objects.requireNonNull(lastWarningAt, "lastWarningAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (missedPayments < 0) {
            throw new IllegalArgumentException("missedPayments must be nonnegative");
        }
        if (outstandingDebt.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("outstandingDebt must be nonnegative");
        }
        if (missedPayments == 0 && outstandingDebt.compareTo(BigDecimal.ZERO) == 0 && delinquentSince.isPresent()) {
            throw new IllegalArgumentException("delinquentSince requires missed payments or debt");
        }
    }

    public static ClaimTaxState scheduled(ClaimId claimId, Instant nextDueAt, Instant now) {
        return new ClaimTaxState(
                claimId,
                nextDueAt,
                Optional.empty(),
                0,
                BigDecimal.ZERO,
                Optional.empty(),
                Optional.empty(),
                now
        );
    }

    public ClaimTaxState recordPaid(Instant paidAt, Instant nextDueAt) {
        return new ClaimTaxState(
                claimId,
                nextDueAt,
                Optional.of(paidAt),
                0,
                BigDecimal.ZERO,
                Optional.empty(),
                Optional.empty(),
                paidAt
        );
    }

    public ClaimTaxState recordMissed(BigDecimal amount, Instant missedAt, Instant nextDueAt) {
        return new ClaimTaxState(
                claimId,
                nextDueAt,
                lastPaidAt,
                missedPayments + 1,
                outstandingDebt.add(amount),
                delinquentSince.or(() -> Optional.of(missedAt)),
                lastWarningAt,
                missedAt
        );
    }

    public ClaimTaxState markWarningSent(Instant warnedAt) {
        return new ClaimTaxState(
                claimId,
                nextDueAt,
                lastPaidAt,
                missedPayments,
                outstandingDebt,
                delinquentSince,
                Optional.of(warnedAt),
                warnedAt
        );
    }

    public ClaimTaxState clearDebt(Instant nextDueAt, Instant clearedAt) {
        return new ClaimTaxState(
                claimId,
                nextDueAt,
                lastPaidAt,
                0,
                BigDecimal.ZERO,
                Optional.empty(),
                Optional.empty(),
                clearedAt
        );
    }

    public ClaimTaxState deferAfterRestore(Instant nextDueAt, Instant restoredAt) {
        return new ClaimTaxState(
                claimId,
                nextDueAt,
                lastPaidAt,
                missedPayments,
                outstandingDebt,
                outstandingDebt.compareTo(BigDecimal.ZERO) > 0 ? Optional.of(restoredAt) : Optional.empty(),
                Optional.empty(),
                restoredAt
        );
    }
}
