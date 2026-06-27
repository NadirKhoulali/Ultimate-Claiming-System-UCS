package com.nadirkhoulali.ucs.claim;

public enum ClaimSaleFailureReason {
    NO_CLAIM_AT_CHUNK,
    NOT_OWNER,
    NOT_PLAYER_OWNED,
    ALREADY_LISTED,
    NOT_LISTED,
    PRICE_TOO_LOW,
    PRICE_TOO_HIGH,
    SELF_PURCHASE,
    STALE_LISTING,
    BUYER_LIMIT_EXCEEDED,
    PAYMENT_FAILED,
    SAVE_FAILED
}
