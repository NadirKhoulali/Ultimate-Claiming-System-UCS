package com.nadirkhoulali.ucs.claim;

public record ClaimNonpaymentResult(
        int scannedClaims,
        int warningsSent,
        int archivedClaims
) {
    public ClaimNonpaymentResult {
        if (scannedClaims < 0 || warningsSent < 0 || archivedClaims < 0) {
            throw new IllegalArgumentException("nonpayment counts must be nonnegative");
        }
    }
}
