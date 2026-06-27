package com.nadirkhoulali.ucs.claim;

public enum ClaimCreationFailureReason {
    DIMENSION_DISABLED,
    RADIUS_TOO_LARGE,
    CLAIM_TOO_LARGE,
    TOO_MANY_CLAIMS,
    TOO_MANY_CHUNKS,
    OVERLAP,
    PAYMENT_FAILED,
    SAVE_FAILED
}
