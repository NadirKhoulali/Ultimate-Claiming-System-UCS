package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.core.model.ChunkKey;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ClaimCreationRequest(
        UUID playerId,
        String playerName,
        ChunkKey center,
        int radius,
        Instant requestedAt
) {
    public ClaimCreationRequest {
        Objects.requireNonNull(playerId, "playerId");
        playerName = requireNonBlank(playerName, "playerName");
        Objects.requireNonNull(center, "center");
        if (radius < 0) {
            throw new IllegalArgumentException("radius must not be negative");
        }
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
