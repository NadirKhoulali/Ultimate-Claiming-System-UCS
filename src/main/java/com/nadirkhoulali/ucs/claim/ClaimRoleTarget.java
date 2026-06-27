package com.nadirkhoulali.ucs.claim;

import java.util.Objects;
import java.util.UUID;

public record ClaimRoleTarget(
        UUID playerId,
        String playerName
) {
    public ClaimRoleTarget {
        Objects.requireNonNull(playerId, "playerId");
        playerName = requireNonBlank(playerName, "playerName");
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
