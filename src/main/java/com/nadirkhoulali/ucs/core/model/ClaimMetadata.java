package com.nadirkhoulali.ucs.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ClaimMetadata(
        String displayName,
        String description,
        Optional<ClaimSpawn> spawn,
        Instant createdAt,
        Instant updatedAt
) {
    public ClaimMetadata {
        displayName = IdentifierRules.requireNonBlank(displayName, "displayName");
        description = Objects.requireNonNull(description, "description").trim();
        spawn = Objects.requireNonNull(spawn, "spawn");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
    }

    public static ClaimMetadata create(String displayName, Instant timestamp) {
        return new ClaimMetadata(displayName, "", Optional.empty(), timestamp, timestamp);
    }

    public Optional<ChunkKey> spawnChunk() {
        return spawn.map(ClaimSpawn::chunk);
    }
}
