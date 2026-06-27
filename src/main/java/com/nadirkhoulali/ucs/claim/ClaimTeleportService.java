package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimId;
import com.nadirkhoulali.ucs.core.model.ClaimOwnership;
import com.nadirkhoulali.ucs.core.model.ClaimSpawn;
import com.nadirkhoulali.ucs.core.model.PlayerOwner;
import com.nadirkhoulali.ucs.storage.ClaimRepository;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ClaimTeleportService {
    private final Map<UUID, PendingTeleport> pendingTeleports = new LinkedHashMap<>();

    public ClaimTeleportStartResult requestHomeTeleport(
            ClaimRepository repository,
            UcsConfigSnapshot config,
            ServerPlayer player,
            Instant requestedAt
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(requestedAt, "requestedAt");

        PlayerOwner owner = ClaimOwnership.player(player.getUUID(), player.getGameProfile().getName());
        ChunkKey sourceChunk = chunkAt(player);
        Optional<Claim> candidate = teleportClaim(repository, owner, sourceChunk);
        if (candidate.isEmpty()) {
            return failure(ClaimTeleportFailureReason.NO_OWNED_SPAWN, owner.stableKey());
        }

        Claim claim = candidate.orElseThrow();
        ClaimSpawn spawn = claim.metadata().spawn().orElseThrow();
        Optional<ClaimTeleportFailure> validation = validateDestination(player.server, repository, config, owner, claim.id(), spawn);
        if (validation.isPresent()) {
            return ClaimTeleportStartResult.failure(validation.orElseThrow());
        }

        int delaySeconds = config.claimTeleport().delaySeconds();
        if (delaySeconds == 0) {
            teleport(player, levelFor(player.server, spawn.chunk().dimension()).orElseThrow(), spawn);
            return ClaimTeleportStartResult.success(ClaimView.from(claim), 0, true);
        }

        PendingTeleport pending = new PendingTeleport(
                player.getUUID(),
                claim.id(),
                spawn,
                player.blockPosition(),
                player.serverLevel().dimension().location().toString(),
                player.server.getTickCount() + delaySeconds * 20
        );
        pendingTeleports.put(player.getUUID(), pending);
        return ClaimTeleportStartResult.success(ClaimView.from(claim), delaySeconds, false);
    }

    public void tick(MinecraftServer server, ClaimRepository repository, UcsConfigSnapshot config) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(config, "config");

        if (pendingTeleports.isEmpty()) {
            return;
        }

        for (PendingTeleport pending : new ArrayList<>(pendingTeleports.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId());
            if (player == null) {
                pendingTeleports.remove(pending.playerId());
                continue;
            }

            if (config.claimTeleport().cancelOnMove() && hasMoved(player, pending)) {
                pendingTeleports.remove(pending.playerId());
                player.sendSystemMessage(Component.translatable("command.ucs.claim.teleport.cancelled.moved"));
                continue;
            }

            if (server.getTickCount() < pending.executeTick()) {
                continue;
            }

            PlayerOwner owner = ClaimOwnership.player(player.getUUID(), player.getGameProfile().getName());
            Optional<ClaimTeleportFailure> validation = validateDestination(
                    server,
                    repository,
                    config,
                    owner,
                    pending.claimId(),
                    pending.spawn()
            );
            if (validation.isPresent()) {
                pendingTeleports.remove(pending.playerId());
                player.sendSystemMessage(teleportFailureMessage(validation.orElseThrow()));
                continue;
            }

            pendingTeleports.remove(pending.playerId());
            teleport(player, levelFor(server, pending.spawn().chunk().dimension()).orElseThrow(), pending.spawn());
            player.sendSystemMessage(Component.translatable("command.ucs.claim.teleport.completed"));
        }
    }

    public void clear() {
        pendingTeleports.clear();
    }

    private static Optional<Claim> teleportClaim(ClaimRepository repository, PlayerOwner owner, ChunkKey sourceChunk) {
        Optional<Claim> currentClaim = repository.findByChunk(sourceChunk)
                .filter(claim -> ClaimOwnership.isOwnedBy(claim, owner))
                .filter(claim -> claim.metadata().spawn().isPresent());
        if (currentClaim.isPresent()) {
            return currentClaim;
        }

        return repository.claims().stream()
                .filter(claim -> ClaimOwnership.isOwnedBy(claim, owner))
                .filter(claim -> claim.metadata().spawn().isPresent())
                .sorted(Comparator.comparing(claim -> claim.metadata().createdAt()))
                .findFirst();
    }

    private static Optional<ClaimTeleportFailure> validateDestination(
            MinecraftServer server,
            ClaimRepository repository,
            UcsConfigSnapshot config,
            PlayerOwner owner,
            ClaimId claimId,
            ClaimSpawn spawn
    ) {
        Optional<ServerLevel> level = levelFor(server, spawn.chunk().dimension());
        if (level.isEmpty()) {
            return Optional.of(new ClaimTeleportFailure(ClaimTeleportFailureReason.DESTINATION_DIMENSION_MISSING, spawn.chunk().dimension()));
        }

        Optional<Claim> destination = repository.findByChunk(spawn.chunk());
        if (destination.isEmpty() || !destination.orElseThrow().id().equals(claimId)) {
            return Optional.of(new ClaimTeleportFailure(ClaimTeleportFailureReason.DESTINATION_NO_LONGER_CLAIMED, spawn.chunk().storageKey()));
        }
        if (!ClaimOwnership.isOwnedBy(destination.orElseThrow(), owner)) {
            return Optional.of(new ClaimTeleportFailure(ClaimTeleportFailureReason.DESTINATION_NOT_ALLOWED, spawn.chunk().storageKey()));
        }
        if (config.claimTeleport().requireSafeLanding() && !isSafeLanding(level.orElseThrow(), spawn)) {
            return Optional.of(new ClaimTeleportFailure(ClaimTeleportFailureReason.UNSAFE_DESTINATION, spawn.chunk().storageKey()));
        }
        return Optional.empty();
    }

    private static boolean isSafeLanding(ServerLevel level, ClaimSpawn spawn) {
        BlockPos feet = BlockPos.containing(spawn.x(), spawn.y(), spawn.z());
        BlockPos head = feet.above();
        BlockPos below = feet.below();
        if (!level.getWorldBorder().isWithinBounds(feet)) {
            return false;
        }

        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);
        BlockState belowState = level.getBlockState(below);
        return feetState.getCollisionShape(level, feet).isEmpty()
                && feetState.getFluidState().isEmpty()
                && headState.getCollisionShape(level, head).isEmpty()
                && headState.getFluidState().isEmpty()
                && !belowState.getCollisionShape(level, below).isEmpty();
    }

    private static void teleport(ServerPlayer player, ServerLevel level, ClaimSpawn spawn) {
        player.stopRiding();
        player.stopFallFlying();
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.teleportTo(level, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
        player.resetFallDistance();
    }

    private static boolean hasMoved(ServerPlayer player, PendingTeleport pending) {
        return !player.blockPosition().equals(pending.startBlock())
                || !player.serverLevel().dimension().location().toString().equals(pending.startDimension());
    }

    private static ChunkKey chunkAt(ServerPlayer player) {
        ChunkPos chunkPos = new ChunkPos(player.blockPosition());
        return new ChunkKey(player.serverLevel().dimension().location().toString(), chunkPos.x, chunkPos.z);
    }

    private static Optional<ServerLevel> levelFor(MinecraftServer server, String dimension) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension));
        return Optional.ofNullable(server.getLevel(key));
    }

    private static ClaimTeleportStartResult failure(ClaimTeleportFailureReason reason, String detail) {
        return ClaimTeleportStartResult.failure(new ClaimTeleportFailure(reason, detail));
    }

    public static Component teleportFailureMessage(ClaimTeleportFailure failure) {
        return switch (failure.reason()) {
            case NO_OWNED_SPAWN -> Component.translatable("command.ucs.claim.teleport.denied.no_owned_spawn");
            case DESTINATION_DIMENSION_MISSING -> Component.translatable("command.ucs.claim.teleport.denied.missing_dimension", failure.detail());
            case DESTINATION_NO_LONGER_CLAIMED -> Component.translatable("command.ucs.claim.teleport.denied.no_longer_claimed", failure.detail());
            case DESTINATION_NOT_ALLOWED -> Component.translatable("command.ucs.claim.teleport.denied.not_allowed", failure.detail());
            case UNSAFE_DESTINATION -> Component.translatable("command.ucs.claim.teleport.denied.unsafe", failure.detail());
        };
    }

    private record PendingTeleport(
            UUID playerId,
            ClaimId claimId,
            ClaimSpawn spawn,
            BlockPos startBlock,
            String startDimension,
            int executeTick
    ) {
    }
}
