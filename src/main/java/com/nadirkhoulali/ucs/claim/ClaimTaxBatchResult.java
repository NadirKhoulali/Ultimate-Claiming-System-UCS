package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.core.model.ClaimTaxLedgerEntry;

import java.util.List;
import java.util.Objects;

public record ClaimTaxBatchResult(
        int scannedClaims,
        int billedClaims,
        int paidClaims,
        int failedClaims,
        List<ClaimTaxLedgerEntry> ledgerEntries
) {
    public ClaimTaxBatchResult {
        if (scannedClaims < 0 || billedClaims < 0 || paidClaims < 0 || failedClaims < 0) {
            throw new IllegalArgumentException("tax batch counts must be nonnegative");
        }
        ledgerEntries = List.copyOf(Objects.requireNonNull(ledgerEntries, "ledgerEntries"));
    }
}
