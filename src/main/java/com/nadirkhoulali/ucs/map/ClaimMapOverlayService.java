package com.nadirkhoulali.ucs.map;

import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimChunk;
import com.nadirkhoulali.ucs.core.model.ClaimOwnership;
import com.nadirkhoulali.ucs.core.model.LeaseStatus;
import com.nadirkhoulali.ucs.core.model.MapTileKey;
import com.nadirkhoulali.ucs.core.model.OwnerRef;
import com.nadirkhoulali.ucs.core.model.OwnerType;
import com.nadirkhoulali.ucs.core.model.PlayerOwner;
import com.nadirkhoulali.ucs.core.model.RoleId;
import com.nadirkhoulali.ucs.storage.ClaimRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ClaimMapOverlayService {
    public static final int MAX_OVERLAY_CLAIMS = 512;
    public static final int MAX_OVERLAY_CHUNKS = 8192;

    public List<ClaimMapOverlayEntry> visibleOverlays(
            ClaimRepository repository,
            UcsConfigSnapshot config,
            UUID viewerId,
            String dimension,
            Collection<MapTileKey> requestedTiles,
            boolean canViewServerClaims
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(requestedTiles, "requestedTiles");

        List<TileChunkBounds> tileBounds = requestedTiles.stream()
                .filter(tile -> tile.dimension().equals(dimension))
                .map(ClaimMapOverlayService::chunkBounds)
                .toList();
        if (tileBounds.isEmpty()) {
            return List.of();
        }

        List<ClaimMapOverlayEntry> entries = new ArrayList<>();
        int emittedChunks = 0;
        for (Claim claim : repository.claims().stream().sorted(Comparator.comparing(claim -> claim.id().value())).toList()) {
            Optional<ClaimMapOverlayEntry> entry = visibleEntry(
                    claim,
                    config,
                    viewerId,
                    dimension,
                    tileBounds,
                    canViewServerClaims,
                    MAX_OVERLAY_CHUNKS - emittedChunks
            );
            if (entry.isEmpty()) {
                continue;
            }
            entries.add(entry.orElseThrow());
            emittedChunks += entry.orElseThrow().chunks().size();
            if (entries.size() >= MAX_OVERLAY_CLAIMS || emittedChunks >= MAX_OVERLAY_CHUNKS) {
                break;
            }
        }
        return List.copyOf(entries);
    }

    private static Optional<ClaimMapOverlayEntry> visibleEntry(
            Claim claim,
            UcsConfigSnapshot config,
            UUID viewerId,
            String dimension,
            List<TileChunkBounds> tileBounds,
            boolean canViewServerClaims,
            int remainingChunkBudget
    ) {
        if (remainingChunkBudget <= 0) {
            return Optional.empty();
        }
        if (claim.owner().type() == OwnerType.SERVER && !canViewServerClaims) {
            return Optional.empty();
        }

        List<ClaimMapOverlayChunk> chunks = new ArrayList<>();
        for (ClaimChunk chunk : claim.chunks()) {
            ChunkKey key = chunk.key();
            if (!key.dimension().equals(dimension) || tileBounds.stream().noneMatch(bounds -> bounds.contains(key))) {
                continue;
            }
            chunks.add(new ClaimMapOverlayChunk(key.x(), key.z()));
            if (chunks.size() >= remainingChunkBudget) {
                break;
            }
        }
        if (chunks.isEmpty()) {
            return Optional.empty();
        }

        ClaimMapOverlayRelation relation = relationFor(claim, config, viewerId);
        UcsConfigSnapshot.MapOverlayPolicy colors = config.mapOverlay();
        return Optional.of(new ClaimMapOverlayEntry(
                claim.id().value().toString(),
                claim.metadata().displayName(),
                claim.owner().stableKey(),
                claim.owner().type(),
                relation,
                chunks,
                claim.saleListing().isPresent(),
                hasVisibleLease(claim),
                colorFor(colors, relation),
                colors.borderColor(),
                colors.saleAccentColor(),
                colors.leaseAccentColor()
        ));
    }

    private static ClaimMapOverlayRelation relationFor(Claim claim, UcsConfigSnapshot config, UUID viewerId) {
        RoleId bannedRole = new RoleId(config.roles().bannedRoleId());
        if (ClaimOwnership.isPlayerOwnedBy(claim, viewerId)) {
            return ClaimMapOverlayRelation.OWNER;
        }
        if (claim.roleAssignments().getOrDefault(bannedRole, Set.of()).contains(viewerId)) {
            return ClaimMapOverlayRelation.BANNED;
        }
        if (isActiveTenant(claim, viewerId)) {
            return ClaimMapOverlayRelation.TENANT;
        }
        if (isAssignedMember(claim, viewerId, bannedRole)) {
            return ClaimMapOverlayRelation.MEMBER;
        }
        if (claim.owner().type() == OwnerType.SERVER) {
            return ClaimMapOverlayRelation.SERVER;
        }
        return ClaimMapOverlayRelation.VISITOR;
    }

    private static boolean isAssignedMember(Claim claim, UUID viewerId, RoleId bannedRole) {
        return claim.roleAssignments().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(bannedRole))
                .anyMatch(entry -> entry.getValue().contains(viewerId));
    }

    private static boolean isActiveTenant(Claim claim, UUID viewerId) {
        return claim.leases().values().stream()
                .filter(lease -> lease.status() == LeaseStatus.ACTIVE)
                .map(lease -> playerId(lease.tenant()))
                .flatMap(Optional::stream)
                .anyMatch(viewerId::equals);
    }

    private static boolean hasVisibleLease(Claim claim) {
        return claim.leases().values().stream()
                .anyMatch(lease -> lease.status() == LeaseStatus.ACTIVE || lease.status() == LeaseStatus.OFFERED);
    }

    private static Optional<UUID> playerId(OwnerRef owner) {
        if (owner instanceof PlayerOwner playerOwner) {
            return Optional.of(playerOwner.playerId());
        }
        return Optional.empty();
    }

    private static int colorFor(UcsConfigSnapshot.MapOverlayPolicy colors, ClaimMapOverlayRelation relation) {
        return switch (relation) {
            case OWNER -> colors.ownerColor();
            case MEMBER -> colors.memberColor();
            case TENANT -> colors.tenantColor();
            case VISITOR -> colors.visitorColor();
            case BANNED -> colors.bannedColor();
            case SERVER -> colors.serverColor();
        };
    }

    private static TileChunkBounds chunkBounds(MapTileKey tile) {
        long scale = 1L << Math.min(30, tile.zoom());
        long tileBlockSize = (long) TerrainTileGenerator.TILE_SIZE * scale;
        long minBlockX = (long) tile.tileX() * tileBlockSize;
        long minBlockZ = (long) tile.tileZ() * tileBlockSize;
        long maxBlockX = minBlockX + tileBlockSize - 1L;
        long maxBlockZ = minBlockZ + tileBlockSize - 1L;
        return new TileChunkBounds(
                Math.floorDiv(minBlockX, 16L),
                Math.floorDiv(maxBlockX, 16L),
                Math.floorDiv(minBlockZ, 16L),
                Math.floorDiv(maxBlockZ, 16L)
        );
    }

    private record TileChunkBounds(long minX, long maxX, long minZ, long maxZ) {
        boolean contains(ChunkKey key) {
            return key.x() >= minX && key.x() <= maxX && key.z() >= minZ && key.z() <= maxZ;
        }
    }
}
