package com.nadirkhoulali.ucs.protection;

import com.nadirkhoulali.ucs.api.UcsClaimService;
import com.nadirkhoulali.ucs.api.protection.ProtectionDecision;
import com.nadirkhoulali.ucs.api.protection.ProtectionFlagRegistry;
import com.nadirkhoulali.ucs.api.protection.UcsBuiltInProtectionFlags;
import com.nadirkhoulali.ucs.claim.ClaimExpulsionResult;
import com.nadirkhoulali.ucs.claim.ClaimExpulsionService;
import com.nadirkhoulali.ucs.claim.ClaimExpulsionStatus;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.storage.ClaimRepository;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ClaimMovementService {
    private static final int MOVEMENT_MESSAGE_COOLDOWN_TICKS = 40;

    private final Set<UUID> ucsFlyGrants = new HashSet<>();
    private final Map<UUID, Integer> lastEntryDenialTick = new HashMap<>();
    private final Map<UUID, Integer> lastMovementMessageTick = new HashMap<>();

    public void tick(
            MinecraftServer server,
            ClaimRepository repository,
            UcsClaimService claimService,
            ProtectionFlagRegistry registry,
            UcsConfigSnapshot config,
            ClaimProtectionService protection,
            ClaimExpulsionService expulsion
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(claimService, "claimService");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(protection, "protection");
        Objects.requireNonNull(expulsion, "expulsion");

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            enforceEntry(server, repository, claimService, registry, config, protection, expulsion, player);
            enforceClaimFly(claimService, registry, config, protection, player);
            enforceElytra(claimService, registry, config, protection, player);
            enforceWindChargeDrift(claimService, registry, config, protection, player);
        }
    }

    public void clear() {
        ucsFlyGrants.clear();
        lastEntryDenialTick.clear();
        lastMovementMessageTick.clear();
    }

    private void enforceEntry(
            MinecraftServer server,
            ClaimRepository repository,
            UcsClaimService claimService,
            ProtectionFlagRegistry registry,
            UcsConfigSnapshot config,
            ClaimProtectionService protection,
            ClaimExpulsionService expulsion,
            ServerPlayer player
    ) {
        ChunkKey chunk = chunkAt(player);
        var claim = repository.findByChunk(chunk);
        if (claim.isEmpty()) {
            return;
        }
        ProtectionDecision decision = protection.checkPlayerAction(
                claimService,
                registry,
                config,
                player.serverLevel(),
                player.blockPosition(),
                UcsBuiltInProtectionFlags.ENTRY,
                player
        );
        if (!decision.denied()) {
            return;
        }
        int tick = server.getTickCount();
        Integer lastTick = lastEntryDenialTick.get(player.getUUID());
        if (lastTick != null && tick - lastTick < config.bans().expulsionCooldownTicks()) {
            return;
        }
        lastEntryDenialTick.put(player.getUUID(), tick);

        ClaimExpulsionResult result = expulsion.expelWithEvent(
                player,
                claim.orElseThrow(),
                config,
                UcsBuiltInProtectionFlags.ENTRY,
                decision.reason()
        );
        if (result.status() == ClaimExpulsionStatus.EXPELLED) {
            player.sendSystemMessage(Component.translatable("command.ucs.claim.bans.entry_prevented"));
        } else if (result.status() == ClaimExpulsionStatus.NO_SAFE_LOCATION) {
            player.sendSystemMessage(Component.translatable("command.ucs.claim.bans.no_safe_location"));
        }
    }

    private void enforceClaimFly(
            UcsClaimService claimService,
            ProtectionFlagRegistry registry,
            UcsConfigSnapshot config,
            ClaimProtectionService protection,
            ServerPlayer player
    ) {
        ProtectionDecision decision = protection.checkPlayerAction(
                claimService,
                registry,
                config,
                player.serverLevel(),
                player.blockPosition(),
                UcsBuiltInProtectionFlags.FLY,
                player
        );
        boolean managedPlayer = !player.isCreative() && !player.isSpectator();
        if (managedPlayer && decision.allowed() && !player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            ucsFlyGrants.add(player.getUUID());
            player.onUpdateAbilities();
            return;
        }
        if ((!managedPlayer || !decision.allowed()) && ucsFlyGrants.remove(player.getUUID())) {
            if (managedPlayer) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }
    }

    private void enforceElytra(
            UcsClaimService claimService,
            ProtectionFlagRegistry registry,
            UcsConfigSnapshot config,
            ClaimProtectionService protection,
            ServerPlayer player
    ) {
        if (!player.isFallFlying()) {
            return;
        }
        ProtectionDecision decision = protection.checkPlayerAction(
                claimService,
                registry,
                config,
                player.serverLevel(),
                player.blockPosition(),
                UcsBuiltInProtectionFlags.ELYTRA,
                player
        );
        if (decision.denied()) {
            player.stopFallFlying();
            player.setDeltaMovement(player.getDeltaMovement().multiply(0.35D, 0.0D, 0.35D));
            sendMovementDenial(player, config, decision);
        }
    }

    private void enforceWindChargeDrift(
            UcsClaimService claimService,
            ProtectionFlagRegistry registry,
            UcsConfigSnapshot config,
            ClaimProtectionService protection,
            ServerPlayer player
    ) {
        if (player.onGround() || player.getDeltaMovement().lengthSqr() < 3.0D) {
            return;
        }
        ProtectionDecision decision = protection.checkPlayerAction(
                claimService,
                registry,
                config,
                player.serverLevel(),
                player.blockPosition(),
                UcsBuiltInProtectionFlags.WIND_CHARGE,
                player
        );
        if (decision.denied()) {
            player.setDeltaMovement(player.getDeltaMovement().multiply(0.5D, 0.0D, 0.5D));
            sendMovementDenial(player, config, decision);
        }
    }

    private void sendMovementDenial(ServerPlayer player, UcsConfigSnapshot config, ProtectionDecision decision) {
        int currentTick = player.server.getTickCount();
        Integer lastTick = lastMovementMessageTick.get(player.getUUID());
        if (lastTick != null && currentTick - lastTick < MOVEMENT_MESSAGE_COOLDOWN_TICKS) {
            return;
        }
        lastMovementMessageTick.put(player.getUUID(), currentTick);
        player.displayClientMessage(
                Component.translatable("command.ucs.protection.denied", decision.flagId().value(), decision.reason()),
                config.messages().sendActionBarDenials()
        );
    }

    private static ChunkKey chunkAt(ServerPlayer player) {
        ChunkPos chunkPos = new ChunkPos(player.blockPosition());
        return new ChunkKey(player.serverLevel().dimension().location().toString(), chunkPos.x, chunkPos.z);
    }
}
