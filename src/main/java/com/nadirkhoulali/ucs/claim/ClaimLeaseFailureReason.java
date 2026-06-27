package com.nadirkhoulali.ucs.claim;

public enum ClaimLeaseFailureReason {
    NO_CLAIM_AT_CHUNK,
    NO_LEASE,
    NOT_OWNER,
    NOT_PLAYER_OWNED,
    TENANT_IS_OWNER,
    ALREADY_HAS_LEASE,
    TENANT_BANNED,
    ROLE_NOT_CONFIGURED,
    PRICE_TOO_LOW,
    DURATION_TOO_SHORT,
    NOT_TENANT,
    NOT_OFFERED,
    NOT_ACTIVE,
    PAYMENT_FAILED,
    SAVE_FAILED
}
