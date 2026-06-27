package com.nadirkhoulali.ucs.protection;

import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.api.UcsClaimService;
import com.nadirkhoulali.ucs.api.event.UcsProtectionDecisionEvent;
import com.nadirkhoulali.ucs.api.protection.ProtectionDecision;
import com.nadirkhoulali.ucs.api.protection.ProtectionDecisionType;
import com.nadirkhoulali.ucs.api.protection.ProtectionFlagEvaluator;
import com.nadirkhoulali.ucs.api.protection.ProtectionFlagRegistry;
import com.nadirkhoulali.ucs.api.protection.UcsBuiltInProtectionFlags;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.FlagId;
import com.nadirkhoulali.ucs.core.model.RoleId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ClaimProtectionService {
    public ProtectionDecision checkBlockBreak(
            UcsClaimService claimService,
            ProtectionFlagRegistry registry,
            UcsConfigSnapshot config,
            ServerLevel level,
            BlockPos position,
            BlockState state,
            @Nullable Player player
    ) {
        String blockId = blockId(state);
        if (isIgnoredBlock(config, blockId)) {
            return ProtectionDecision.abstain(UcsBuiltInProtectionFlags.BLOCK_BREAK, "ignored_block", Set.of());
        }
        if (isAllowedBlock(config, blockId)) {
            return ProtectionDecision.allow(UcsBuiltInProtectionFlags.BLOCK_BREAK, "allowed_block", Set.of());
        }

        FlagId flagId = breakFlagForBlock(config, blockId);
        return checkClaimAction(claimService, registry, config, level, position, flagId, player);
    }

    public ProtectionDecision checkBlockPlace(
            UcsClaimService claimService,
            ProtectionFlagRegistry registry,
            UcsConfigSnapshot config,
            ServerLevel level,
            BlockPos position,
            BlockState state,
            @Nullable Entity actor
    ) {
        String blockId = blockId(state);
        if (isIgnoredBlock(config, blockId)) {
            return ProtectionDecision.abstain(UcsBuiltInProtectionFlags.BLOCK_PLACE, "ignored_block", Set.of());
        }
        if (isAllowedBlock(config, blockId)) {
            return ProtectionDecision.allow(UcsBuiltInProtectionFlags.BLOCK_PLACE, "allowed_block", Set.of());
        }

        Player player = actor instanceof Player candidate ? candidate : null;
        return checkClaimAction(claimService, registry, config, level, position, UcsBuiltInProtectionFlags.BLOCK_PLACE, player);
    }

    public ProtectionDecision evaluateClaimAction(
            ProtectionFlagRegistry registry,
            UcsConfigSnapshot config,
            ClaimView claim,
            FlagId flagId,
            Optional<UUID> actor,
            boolean actorPresent
    ) {
        Set<RoleId> roles = actor.map(playerId -> effectiveRoles(claim, playerId, config)).orElseGet(Set::of);
        return ProtectionFlagEvaluator.evaluate(registry, config, claim, flagId, roles, actorPresent);
    }

    public FlagId breakFlagForBlock(UcsConfigSnapshot config, String blockId) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(blockId, "blockId");
        return config.protection().specialBlockIds().contains(blockId)
                ? UcsBuiltInProtectionFlags.SPECIAL_BLOCK_USE
                : UcsBuiltInProtectionFlags.BLOCK_BREAK;
    }

    public boolean isIgnoredBlock(UcsConfigSnapshot config, String blockId) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(blockId, "blockId");
        return config.protection().ignoredBlockIds().contains(blockId);
    }

    public boolean isAllowedBlock(UcsConfigSnapshot config, String blockId) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(blockId, "blockId");
        return config.protection().allowedBlockIds().contains(blockId);
    }

    private ProtectionDecision checkClaimAction(
            UcsClaimService claimService,
            ProtectionFlagRegistry registry,
            UcsConfigSnapshot config,
            ServerLevel level,
            BlockPos position,
            FlagId flagId,
            @Nullable Player player
    ) {
        ChunkKey chunk = chunkAt(level, position);
        Optional<ClaimView> claim = claimService.findClaim(chunk);
        if (claim.isEmpty()) {
            return ProtectionDecision.abstain(flagId, "unclaimed_chunk", Set.of());
        }

        Optional<UUID> actor = Optional.ofNullable(player).map(Entity::getUUID);
        ProtectionDecision decision = evaluateClaimAction(registry, config, claim.orElseThrow(), flagId, actor, player != null);
        if (decision.abstained()) {
            return decision;
        }

        UcsProtectionDecisionEvent event = new UcsProtectionDecisionEvent(
                chunk,
                claim,
                flagId,
                actor,
                decision.allowed(),
                decision.reason()
        );
        NeoForge.EVENT_BUS.post(event);
        return event.allowed()
                ? ProtectionDecision.allow(flagId, event.reason(), decision.effectiveRoles())
                : ProtectionDecision.deny(flagId, event.reason(), decision.effectiveRoles());
    }

    private static Set<RoleId> effectiveRoles(ClaimView claim, UUID playerId, UcsConfigSnapshot config) {
        RoleId banned = new RoleId(config.roles().bannedRoleId());
        if (claim.roleAssignments().getOrDefault(banned, Set.of()).contains(playerId)) {
            return Set.of(banned);
        }

        Set<RoleId> roles = new LinkedHashSet<>();
        if (claim.owner().stableKey().equals("player:" + playerId)
                || claim.roleAssignments().getOrDefault(new RoleId("owner"), Set.of()).contains(playerId)) {
            roles.add(new RoleId("owner"));
        }
        claim.roleAssignments().forEach((role, players) -> {
            if (players.contains(playerId)) {
                roles.add(role);
            }
        });
        if (roles.isEmpty()) {
            roles.add(new RoleId("visitor"));
        }
        return Set.copyOf(roles);
    }

    private static ChunkKey chunkAt(ServerLevel level, BlockPos position) {
        ChunkPos chunkPos = new ChunkPos(position);
        return new ChunkKey(level.dimension().location().toString(), chunkPos.x, chunkPos.z);
    }

    private static String blockId(BlockState state) {
        Objects.requireNonNull(state, "state");
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }
}
