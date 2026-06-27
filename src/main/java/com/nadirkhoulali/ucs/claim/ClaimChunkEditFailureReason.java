package com.nadirkhoulali.ucs.claim;

public enum ClaimChunkEditFailureReason {
    NO_CLAIM_AT_CHUNK,
    NOT_OWNER,
    CHUNK_ALREADY_CLAIMED,
    NOT_ADJACENT,
    AMBIGUOUS_ADJACENT_CLAIMS,
    CLAIM_TOO_LARGE,
    TOO_MANY_CHUNKS,
    CANNOT_REMOVE_ONLY_CHUNK,
    WOULD_SPLIT,
    NO_MERGE_TARGETS,
    SAVE_FAILED
}
