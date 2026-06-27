package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.api.event.UcsProtectionDecisionEvent;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.FlagId;
import com.nadirkhoulali.ucs.core.model.RoleId;
import com.nadirkhoulali.ucs.storage.ClaimRepository;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ClaimExpulsionService {
    private static final FlagId ENTRY_FLAG = new FlagId("ucs:entry");
    private static final FlagId EXPEL_FLAG = new FlagId("ucs:expel");
    private final Map<UUID, Integer> lastAutomaticExpulsionTick = new LinkedHashMap<>();

    public void tick(MinecraftServer server, ClaimRepository repository, UcsConfigSnapshot config) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(config, "config");

        if (!config.bans().preventEntry()) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ChunkKey chunk = chunkAt(player.serverLevel(), player.blockPosition());
            Optional<Claim> claim = repository.findByChunk(chunk);
            if (claim.isEmpty()) {
                continue;
            }

            if (!ClaimRoleResolver.effectiveRoles(claim.orElseThrow(), player.getUUID(), config)
                    .contains(new RoleId(config.roles().bannedRoleId()))) {
                continue;
            }

            int tick = server.getTickCount();
            int lastTick = lastAutomaticExpulsionTick.getOrDefault(player.getUUID(), Integer.MIN_VALUE);
            if (tick - lastTick < config.bans().expulsionCooldownTicks()) {
                continue;
            }

            ClaimExpulsionResult result = expelWithEvent(player, claim.orElseThrow(), config, ENTRY_FLAG, "banned_entry");
            lastAutomaticExpulsionTick.put(player.getUUID(), tick);
            if (result.status() == ClaimExpulsionStatus.EXPELLED) {
                player.sendSystemMessage(Component.translatable("command.ucs.claim.bans.entry_prevented"));
            } else if (result.status() == ClaimExpulsionStatus.NO_SAFE_LOCATION) {
                player.sendSystemMessage(Component.translatable("command.ucs.claim.bans.no_safe_location"));
            }
        }
    }

    public ClaimExpulsionResult expelWithEvent(ServerPlayer target, Claim claim, UcsConfigSnapshot config, FlagId flag, String reason) {
        UcsProtectionDecisionEvent event = new UcsProtectionDecisionEvent(
                chunkAt(target.serverLevel(), target.blockPosition()),
                Optional.of(ClaimView.from(claim)),
                flag,
                Optional.of(target.getUUID()),
                false,
                reason
        );
        NeoForge.EVENT_BUS.post(event);
        if (event.allowed()) {
            return new ClaimExpulsionResult(ClaimExpulsionStatus.SKIPPED_BY_EVENT, event.reason());
        }
        return expel(target, claim, config);
    }

    public ClaimExpulsionResult expel(ServerPlayer target, Claim claim, UcsConfigSnapshot config) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(config, "config");

        ServerLevel level = target.serverLevel();
        ChunkKey targetChunk = chunkAt(level, target.blockPosition());
        if (!claim.contains(targetChunk)) {
            return new ClaimExpulsionResult(ClaimExpulsionStatus.NOT_IN_CLAIM, targetChunk.storageKey());
        }

        Optional<BlockPos> safeExit = findSafeExit(level, claim, target.blockPosition(), config.bans().expulsionSearchRadiusBlocks());
        if (safeExit.isEmpty()) {
            return new ClaimExpulsionResult(ClaimExpulsionStatus.NO_SAFE_LOCATION, targetChunk.storageKey());
        }

        BlockPos destination = safeExit.orElseThrow();
        target.stopRiding();
        target.stopFallFlying();
        target.setDeltaMovement(0.0D, 0.0D, 0.0D);
        target.teleportTo(level, destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D, target.getYRot(), target.getXRot());
        target.resetFallDistance();
        return new ClaimExpulsionResult(ClaimExpulsionStatus.EXPELLED, chunkAt(level, destination).storageKey());
    }

    public void clear() {
        lastAutomaticExpulsionTick.clear();
    }

    private static Optional<BlockPos> findSafeExit(ServerLevel level, Claim claim, BlockPos origin, int maxRadius) {
        BlockPos sharedSpawn = level.getSharedSpawnPos();
        if (isOutsideClaim(claim, level, sharedSpawn) && isSafeLanding(level, sharedSpawn)) {
            return Optional.of(sharedSpawn);
        }

        for (int radius = 1; radius <= maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    for (int dy = -4; dy <= 4; dy++) {
                        BlockPos candidate = origin.offset(dx, dy, dz);
                        if (isOutsideClaim(claim, level, candidate) && isSafeLanding(level, candidate)) {
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isOutsideClaim(Claim claim, ServerLevel level, BlockPos position) {
        return !claim.contains(chunkAt(level, position));
    }

    private static boolean isSafeLanding(ServerLevel level, BlockPos feet) {
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

    private static ChunkKey chunkAt(ServerLevel level, BlockPos position) {
        ChunkPos chunkPos = new ChunkPos(position);
        return new ChunkKey(level.dimension().location().toString(), chunkPos.x, chunkPos.z);
    }

    public static Component expulsionFailureMessage(ClaimExpulsionResult result) {
        return switch (result.status()) {
            case EXPELLED -> Component.translatable("command.ucs.claim.bans.expelled");
            case NOT_IN_CLAIM -> Component.translatable("command.ucs.claim.bans.denied.not_in_claim", result.detail());
            case NO_SAFE_LOCATION -> Component.translatable("command.ucs.claim.bans.no_safe_location");
            case SKIPPED_BY_EVENT -> Component.translatable("command.ucs.claim.bans.denied.event", result.detail());
            case COOLDOWN -> Component.translatable("command.ucs.claim.bans.denied.cooldown");
        };
    }
}
