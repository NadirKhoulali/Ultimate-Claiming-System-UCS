package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.core.model.ChunkKey;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ClaimChunkEditRequest(
        UUID playerId,
        String playerName,
        ChunkKey chunk,
        Instant requestedAt
) {
    public ClaimChunkEditRequest {
        Objects.requireNonNull(playerId, "playerId");
        playerName = requireNonBlank(playerName, "playerName");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }
}
