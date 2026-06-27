package com.nadirkhoulali.ucs.claim;

public enum ClaimRoleFailureReason {
    NO_CLAIM_AT_CHUNK,
    NOT_OWNER,
    TARGET_IS_SELF,
    TARGET_IS_OWNER,
    ROLE_NOT_CONFIGURED,
    TARGET_BANNED,
    NO_PENDING_INVITE,
    SAVE_FAILED
}
