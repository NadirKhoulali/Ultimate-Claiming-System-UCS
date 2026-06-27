package com.nadirkhoulali.ucs.core.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record Claim(
        ClaimId id,
        OwnerRef owner,
        Set<ClaimChunk> chunks,
        ClaimMetadata metadata,
        Map<RoleId, Set<UUID>> roleAssignments,
        Map<RoleId, Set<UUID>> pendingRoleInvites,
        Set<FlagId> flagOverrides,
        Optional<ClaimSaleListing> saleListing,
        Map<LeaseId, LeaseContract> leases
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
        pendingRoleInvites = copyRoleAssignments(pendingRoleInvites);
        flagOverrides = Set.copyOf(Objects.requireNonNull(flagOverrides, "flagOverrides"));
        saleListing = Objects.requireNonNull(saleListing, "saleListing");
        leases = copyLeases(leases);
        leases.forEach((leaseId, lease) -> {
            if (!lease.id().equals(leaseId)) {
                throw new IllegalArgumentException("lease map key must match lease id");
            }
            if (!lease.claimId().equals(id)) {
                throw new IllegalArgumentException("lease claim id must match claim id");
            }
        });
    }

    public Claim(
            ClaimId id,
            OwnerRef owner,
            Set<ClaimChunk> chunks,
            ClaimMetadata metadata,
            Map<RoleId, Set<UUID>> roleAssignments,
            Map<RoleId, Set<UUID>> pendingRoleInvites,
            Set<FlagId> flagOverrides,
            Optional<ClaimSaleListing> saleListing
    ) {
        this(id, owner, chunks, metadata, roleAssignments, pendingRoleInvites, flagOverrides, saleListing, Map.of());
    }

    public Claim(
            ClaimId id,
            OwnerRef owner,
            Set<ClaimChunk> chunks,
            ClaimMetadata metadata,
            Map<RoleId, Set<UUID>> roleAssignments,
            Map<RoleId, Set<UUID>> pendingRoleInvites,
            Set<FlagId> flagOverrides
    ) {
        this(id, owner, chunks, metadata, roleAssignments, pendingRoleInvites, flagOverrides, Optional.empty(), Map.of());
    }

    public Claim(
            ClaimId id,
            OwnerRef owner,
            Set<ClaimChunk> chunks,
            ClaimMetadata metadata,
            Map<RoleId, Set<UUID>> roleAssignments,
            Set<FlagId> flagOverrides
    ) {
        this(id, owner, chunks, metadata, roleAssignments, Map.of(), flagOverrides);
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

    private static Map<LeaseId, LeaseContract> copyLeases(Map<LeaseId, LeaseContract> leases) {
        Objects.requireNonNull(leases, "leases");
        Map<LeaseId, LeaseContract> copied = new LinkedHashMap<>();
        leases.forEach((id, lease) -> copied.put(
                Objects.requireNonNull(id, "leaseId"),
                Objects.requireNonNull(lease, "lease")
        ));
        return Map.copyOf(copied);
    }
}
