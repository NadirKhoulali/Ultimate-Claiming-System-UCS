package com.nadirkhoulali.ucs.core.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record Claim(
        ClaimId id,
        OwnerRef owner,
        Set<ClaimChunk> chunks,
        ClaimMetadata metadata,
        Map<RoleId, Set<UUID>> roleAssignments,
        Set<FlagId> flagOverrides
) {
    public Claim {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        chunks = Set.copyOf(Objects.requireNonNull(chunks, "chunks"));
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Claim must contain at least one chunk");
        }
        metadata = Objects.requireNonNull(metadata, "metadata");
        roleAssignments = copyRoleAssignments(roleAssignments);
        flagOverrides = Set.copyOf(Objects.requireNonNull(flagOverrides, "flagOverrides"));
    }

    public boolean contains(ChunkKey key) {
        Objects.requireNonNull(key, "key");
        return chunks.stream().anyMatch(chunk -> chunk.key().equals(key));
    }

    private static Map<RoleId, Set<UUID>> copyRoleAssignments(Map<RoleId, Set<UUID>> assignments) {
        Objects.requireNonNull(assignments, "roleAssignments");
        Map<RoleId, Set<UUID>> copied = new LinkedHashMap<>();
        assignments.forEach((role, players) -> copied.put(
                Objects.requireNonNull(role, "role"),
                Set.copyOf(Objects.requireNonNull(players, "players"))
        ));
        return Map.copyOf(copied);
    }
}
