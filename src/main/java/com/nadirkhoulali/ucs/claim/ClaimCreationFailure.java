package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.core.model.ChunkKey;

import java.util.Objects;
import java.util.Optional;

public record ClaimCreationFailure(
        ClaimCreationFailureReason reason,
        String detail,
        Optional<ChunkKey> conflictChunk
) {
    public ClaimCreationFailure {
        Objects.requireNonNull(reason, "reason");
        detail = Objects.requireNonNull(detail, "detail");
        conflictChunk = Objects.requireNonNull(conflictChunk, "conflictChunk");
    }

    public static ClaimCreationFailure simple(ClaimCreationFailureReason reason, String detail) {
        return new ClaimCreationFailure(reason, detail, Optional.empty());
    }

    public static ClaimCreationFailure conflict(ChunkKey chunk) {
        return new ClaimCreationFailure(
                ClaimCreationFailureReason.OVERLAP,
                chunk.storageKey(),
                Optional.of(chunk)
        );
    }
}
