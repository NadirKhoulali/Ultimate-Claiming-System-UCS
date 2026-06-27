package com.nadirkhoulali.ucs.api;

import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimChunk;
import com.nadirkhoulali.ucs.core.model.ClaimId;
import com.nadirkhoulali.ucs.core.model.ClaimSpawn;
import com.nadirkhoulali.ucs.core.model.FlagId;
import com.nadirkhoulali.ucs.core.model.RoleId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record ClaimView(
        ClaimId id,
        OwnerView owner,
        Set<ChunkKey> chunks,
        String displayName,
        String description,
        Optional<ChunkKey> spawnChunk,
        Optional<ClaimSpawn> spawn,
        Instant createdAt,
        Instant updatedAt,
        Map<RoleId, Set<UUID>> roleAssignments,
        Map<RoleId, Set<UUID>> pendingRoleInvites,
        Set<FlagId> flagOverrides,
        Optional<ClaimSaleView> saleListing
) {
    public ClaimView {
        chunks = Set.copyOf(chunks);
        Objects.requireNonNull(description, "description");
        spawnChunk = Objects.requireNonNull(spawnChunk, "spawnChunk");
        spawn = Objects.requireNonNull(spawn, "spawn");
        roleAssignments = copyRoleAssignments(roleAssignments);
        pendingRoleInvites = copyRoleAssignments(pendingRoleInvites);
        flagOverrides = Set.copyOf(flagOverrides);
        saleListing = Objects.requireNonNull(saleListing, "saleListing");
    }

    public static ClaimView from(Claim claim) {
        return new ClaimView(
                claim.id(),
                OwnerView.from(claim.owner()),
                claim.chunks().stream().map(ClaimChunk::key).collect(Collectors.toUnmodifiableSet()),
                claim.metadata().displayName(),
                claim.metadata().description(),
                claim.metadata().spawnChunk(),
                claim.metadata().spawn(),
                claim.metadata().createdAt(),
                claim.metadata().updatedAt(),
                claim.roleAssignments(),
                claim.pendingRoleInvites(),
                claim.flagOverrides(),
                claim.saleListing().map(ClaimSaleView::from)
        );
    }

    private static Map<RoleId, Set<UUID>> copyRoleAssignments(Map<RoleId, Set<UUID>> roleAssignments) {
        Map<RoleId, Set<UUID>> copied = new LinkedHashMap<>();
        roleAssignments.forEach((role, players) -> copied.put(role, Set.copyOf(players)));
        return Map.copyOf(copied);
    }
}
