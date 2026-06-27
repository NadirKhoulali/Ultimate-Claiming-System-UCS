package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.core.model.ChunkKey;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ClaimRoleRequest(
        UUID playerId,
        String playerName,
        ChunkKey chunk,
        Instant requestedAt,
        boolean adminOverride
) {
    public ClaimRoleRequest(UUID playerId, String playerName, ChunkKey chunk, Instant requestedAt) {
        this(playerId, playerName, chunk, requestedAt, false);
    }

    public ClaimRoleRequest {
        Objects.requireNonNull(playerId, "playerId");
        playerName = requireNonBlank(playerName, "playerName");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return trimmed;
    }
}
