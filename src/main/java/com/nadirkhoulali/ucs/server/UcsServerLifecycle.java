package com.nadirkhoulali.ucs.server;

import com.nadirkhoulali.ucs.UcsMod;
import com.nadirkhoulali.ucs.command.ClaimCommands;
import com.nadirkhoulali.ucs.command.UcsCommands;
import com.nadirkhoulali.ucs.config.UcsCommonConfig;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.config.UcsConfigValidationReport;
import com.nadirkhoulali.ucs.api.protection.UcsBuiltInProtectionFlags;
import com.nadirkhoulali.ucs.permission.UcsPermissionNodes;
import com.nadirkhoulali.ucs.service.UcsServices;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.level.block.CreateFluidSourceEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class UcsServerLifecycle {
    private final UcsServices services;

    public UcsServerLifecycle(UcsServices services) {
        this.services = services;
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        UcsCommands.register(event.getDispatcher(), services);
        ClaimCommands.register(event.getDispatcher(), services);
    }

    @SubscribeEvent
    public void onGatherPermissionNodes(PermissionGatherEvent.Nodes event) {
        UcsPermissionNodes.register(event);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        UcsConfigSnapshot config = UcsCommonConfig.snapshot();
        UcsConfigValidationReport validation = config.validate();
        validation.warnings().forEach(warning -> UcsMod.LOGGER.warn("UCS config warning: {}", warning));
        if (!validation.valid()) {
            validation.errors().forEach(error -> UcsMod.LOGGER.error("UCS config error: {}", error));
            throw new IllegalStateException("UCS common config is invalid. Fix the logged config errors and restart.");
        }

        if (config.economy().enableWhenProviderExists() && config.economy().warnAboutDefaultsOnFirstRun()) {
            UcsMod.LOGGER.warn(
                    "UCS economy will activate automatically when a compatible provider exists. Review economy defaults before using UCS on an established server."
            );
        }

        services.initializeClaimRepository(event.getServer());

        if (UcsCommonConfig.LOG_STARTUP_SUMMARY.get()) {
            UcsMod.LOGGER.info(
                    "{} server lifecycle ready. configSchema={}, enabledDimensions={}, services={}",
                    UcsMod.MOD_NAME,
                    config.schemaVersion(),
                    config.dimensions().enabledDimensions(),
                    services.summary()
            );
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        services.clearServerState();
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        services.claimRepository().ifPresent(repository -> {
            UcsConfigSnapshot config = UcsCommonConfig.snapshot();
            services.claimTeleport().tick(event.getServer(), repository, config);
            services.claimExpulsion().tick(event.getServer(), repository, config);
        });
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || services.claimService().isEmpty()) {
            return;
        }
        UcsConfigSnapshot config = UcsCommonConfig.snapshot();
        var decision = services.claimProtection().checkBlockBreak(
                services.claimService().orElseThrow(),
                services.protectionFlags(),
                config,
                level,
                event.getPos(),
                event.getState(),
                event.getPlayer()
        );
        if (decision.denied()) {
            event.setCanceled(true);
            sendProtectionDenial(event.getPlayer(), config, decision.flagId().value(), decision.reason());
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || services.claimService().isEmpty()) {
            return;
        }
        UcsConfigSnapshot config = UcsCommonConfig.snapshot();
        var decision = services.claimProtection().checkBlockPlace(
                services.claimService().orElseThrow(),
                services.protectionFlags(),
                config,
                level,
                event.getPos(),
                event.getPlacedBlock(),
                event.getEntity()
        );
        if (decision.denied()) {
            event.setCanceled(true);
            if (event.getEntity() instanceof Player player) {
                sendProtectionDenial(player, config, decision.flagId().value(), decision.reason());
            }
        }
    }

    @SubscribeEvent
    public void onBlockInteraction(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || services.claimService().isEmpty()) {
            return;
        }
        UcsConfigSnapshot config = UcsCommonConfig.snapshot();
        var decision = services.claimProtection().checkBlockInteraction(
                services.claimService().orElseThrow(),
                services.protectionFlags(),
                config,
                level,
                event.getPos(),
                level.getBlockState(event.getPos()),
                event.getEntity()
        );
        if (decision.denied()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            sendProtectionDenial(event.getEntity(), config, decision.flagId().value(), decision.reason());
        }
    }

    @SubscribeEvent
    public void onEntityInteraction(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getLevel() instanceof ServerLevel level) || services.claimService().isEmpty()) {
            return;
        }
        UcsConfigSnapshot config = UcsCommonConfig.snapshot();
        var decision = services.claimProtection().checkEntityInteraction(
                services.claimService().orElseThrow(),
                services.protectionFlags(),
                config,
                level,
                event.getTarget(),
                event.getEntity()
        );
        if (decision.denied()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            sendProtectionDenial(event.getEntity(), config, decision.flagId().value(), decision.reason());
        }
    }

    @SubscribeEvent
    public void onEntityInteractionSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getLevel() instanceof ServerLevel level) || services.claimService().isEmpty()) {
            return;
        }
        UcsConfigSnapshot config = UcsCommonConfig.snapshot();
        var decision = services.claimProtection().checkEntityInteraction(
                services.claimService().orElseThrow(),
                services.protectionFlags(),
                config,
                level,
                event.getTarget(),
                event.getEntity()
        );
        if (decision.denied()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            sendProtectionDenial(event.getEntity(), config, decision.flagId().value(), decision.reason());
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getTarget().level() instanceof ServerLevel level) || services.claimService().isEmpty()) {
            return;
        }
        UcsConfigSnapshot config = UcsCommonConfig.snapshot();
        var decision = services.claimProtection().checkEntityDamage(
                services.claimService().orElseThrow(),
                services.protectionFlags(),
                config,
                level,
                event.getTarget(),
                event.getEntity()
        );
        if (decision.denied()) {
            event.setCanceled(true);
            sendProtectionDenial(event.getEntity(), config, decision.flagId().value(), decision.reason());
        }
    }

    @SubscribeEvent
    public void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || services.claimService().isEmpty()) {
            return;
        }
        UcsConfigSnapshot config = UcsCommonConfig.snapshot();
        Optional<Player> actorOptional = services.claimProtection().playerActorFromDamageSource(event.getSource());
        if (event.getEntity() instanceof Player target && actorOptional.isPresent()) {
            Player actor = actorOptional.orElseThrow();
            var pvpDecision = services.claimProtection().checkPvP(
                    services.claimService().orElseThrow(),
                    services.protectionFlags(),
                    config,
                    level,
                    target,
                    actor
            );
            if (pvpDecision.denied()) {
                event.setCanceled(true);
                sendProtectionDenial(actor, config, pvpDecision.flagId().value(), pvpDecision.reason());
                return;
            }
        }

        Player actor = actorOptional.orElse(null);
        var decision = services.claimProtection().checkEntityDamage(
                services.claimService().orElseThrow(),
                services.protectionFlags(),
                config,
                level,
                event.getEntity(),
                actor
        );
        if (decision.denied()) {
            event.setCanceled(true);
            if (actor != null) {
                sendProtectionDenial(actor, config, decision.flagId().value(), decision.reason());
            }
        }
    }

    @SubscribeEvent
    public void onEntityMount(EntityMountEvent event) {
        Entity mounted = event.getEntityBeingMounted();
        if (!event.isMounting()
                || mounted == null
                || !(event.getLevel() instanceof ServerLevel level)
                || services.claimService().isEmpty()) {
            return;
        }
        Player actor = event.getEntityMounting() instanceof Player player ? player : null;
        UcsConfigSnapshot config = UcsCommonConfig.snapshot();
        var decision = services.claimProtection().checkVehicleUse(
                services.claimService().orElseThrow(),
                services.protectionFlags(),
                config,
                level,
                mounted,
                actor
        );
        if (decision.denied()) {
            event.setCanceled(true);
            if (actor != null) {
                sendProtectionDenial(actor, config, decision.flagId().value(), decision.reason());
            }
        }
    }

    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getItemEntity().level() instanceof ServerLevel level) || services.claimService().isEmpty()) {
            return;
        }
        UcsConfigSnapshot config = UcsCommonConfig.snapshot();
        var decision = services.claimProtection().checkItemPickup(
                services.claimService().orElseThrow(),
                services.protectionFlags(),
                config,
                level,
                event.getItemEntity(),
                event.getPlayer()
        );
        if (decision.denied()) {
            event.setCanPickup(TriState.FALSE);
            sendProtectionDenial(event.getPlayer(), config, decision.flagId().value(), decision.reason());
        }
    }

    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || services.claimService().isEmpty()) {
            return;
        }
        UcsConfigSnapshot config = UcsCommonConfig.snapshot();
        var decision = services.claimProtection().checkItemDrop(
                services.claimService().orElseThrow(),
                services.protectionFlags(),
                config,
                level,
                event.getEntity(),
                event.getPlayer()
        );
        if (decision.denied() && restoreTossedItem(event)) {
            event.setCanceled(true);
            sendProtectionDenial(event.getPlayer(), config, decision.flagId().value(), decision.reason());
        }
    }

    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || services.claimService().isEmpty()) {
            return;
        }
        UcsConfigSnapshot config = UcsCommonConfig.snapshot();
        event.getAffectedBlocks().removeIf(position -> services.claimProtection().checkNaturalAction(
                services.claimService().orElseThrow(),
                services.protectionFlags(),
                config,
                level,
                position,
                UcsBuiltInProtectionFlags.EXPLOSION
        ).denied());
        event.getAffectedEntities().removeIf(entity -> services.claimProtection().checkNaturalAction(
                services.claimService().orElseThrow(),
                services.protectionFlags(),
                config,
                level,
                entity.blockPosition(),
                UcsBuiltInProtectionFlags.EXPLOSION
        ).denied());
    }

    @SubscribeEvent
    public void onFluidPlaceBlock(BlockEvent.FluidPlaceBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || services.claimService().isEmpty()) {
            return;
        }
        UcsConfigSnapshot config = UcsCommonConfig.snapshot();
        var flag = event.getNewState().getBlock() instanceof BaseFireBlock
                ? UcsBuiltInProtectionFlags.FIRE_SPREAD
                : UcsBuiltInProtectionFlags.LIQUID_FLOW;
        var decision = services.claimProtection().checkNaturalBoundary(
                services.claimService().orElseThrow(),
                services.protectionFlags(),
                config,
                level,
                event.getLiquidPos(),
                List.of(event.getPos()),
                flag
        );
        if (decision.denied()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onCreateFluidSource(CreateFluidSourceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || services.claimService().isEmpty()) {
            return;
        }
        UcsConfigSnapshot config = UcsCommonConfig.snapshot();
        var decision = services.claimProtection().checkNaturalAction(
                services.claimService().orElseThrow(),
                services.protectionFlags(),
                config,
                level,
                event.getPos(),
                UcsBuiltInProtectionFlags.LIQUID_FLOW
        );
        if (decision.denied()) {
            event.setCanConvert(false);
        }
    }

    @SubscribeEvent
    public void onMobSpawnPosition(MobSpawnEvent.PositionCheck event) {
        if (services.claimService().isEmpty()) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        UcsConfigSnapshot config = UcsCommonConfig.snapshot();
        var decision = services.claimProtection().checkNaturalAction(
                services.claimService().orElseThrow(),
                services.protectionFlags(),
                config,
                level,
                BlockPos.containing(event.getX(), event.getY(), event.getZ()),
                UcsBuiltInProtectionFlags.MOB_SPAWN
        );
        if (decision.denied()) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    @SubscribeEvent
    public void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (!(event.getLevel() instanceof ServerLevel level) || services.claimService().isEmpty()) {
            return;
        }
        UcsConfigSnapshot config = UcsCommonConfig.snapshot();
        var decision = services.claimProtection().checkNaturalAction(
                services.claimService().orElseThrow(),
                services.protectionFlags(),
                config,
                level,
                event.getPos(),
                UcsBuiltInProtectionFlags.MOB_SPAWN
        );
        if (decision.denied()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityMobGriefing(EntityMobGriefingEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || services.claimService().isEmpty()) {
            return;
        }
        UcsConfigSnapshot config = UcsCommonConfig.snapshot();
        var decision = services.claimProtection().checkNaturalAction(
                services.claimService().orElseThrow(),
                services.protectionFlags(),
                config,
                level,
                event.getEntity().blockPosition(),
                UcsBuiltInProtectionFlags.MOB_GRIEFING
        );
        if (decision.denied()) {
            event.setCanGrief(false);
        }
    }

    @SubscribeEvent
    public void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getEntity() instanceof Player
                || !(event.getLevel() instanceof ServerLevel level)
                || services.claimService().isEmpty()) {
            return;
        }
        UcsConfigSnapshot config = UcsCommonConfig.snapshot();
        var decision = services.claimProtection().checkNaturalAction(
                services.claimService().orElseThrow(),
                services.protectionFlags(),
                config,
                level,
                event.getPos(),
                UcsBuiltInProtectionFlags.MOB_GRIEFING
        );
        if (decision.denied()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || services.claimService().isEmpty()) {
            return;
        }
        List<BlockPos> affectedPositions = event.getNotifiedSides().stream()
                .map(side -> event.getPos().relative(side))
                .toList();
        UcsConfigSnapshot config = UcsCommonConfig.snapshot();
        var decision = services.claimProtection().checkRedstoneInfluence(
                services.claimService().orElseThrow(),
                services.protectionFlags(),
                config,
                level,
                event.getPos(),
                event.getState(),
                affectedPositions,
                event.getForceRedstoneUpdate()
        );
        if (decision.denied()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPistonPre(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level) || services.claimService().isEmpty()) {
            return;
        }
        List<BlockPos> affectedPositions = pistonAffectedPositions(event);
        if (affectedPositions.isEmpty()) {
            return;
        }
        UcsConfigSnapshot config = UcsCommonConfig.snapshot();
        var decision = services.claimProtection().checkRedstoneInfluence(
                services.claimService().orElseThrow(),
                services.protectionFlags(),
                config,
                level,
                event.getPos(),
                event.getState(),
                affectedPositions,
                true
        );
        if (decision.denied()) {
            event.setCanceled(true);
        }
    }

    private static void sendProtectionDenial(Player player, UcsConfigSnapshot config, String flagId, String reason) {
        player.displayClientMessage(
                Component.translatable("command.ucs.protection.denied", flagId, reason),
                config.messages().sendActionBarDenials()
        );
    }

    private static List<BlockPos> pistonAffectedPositions(PistonEvent.Pre event) {
        List<BlockPos> affectedPositions = new ArrayList<>();
        affectedPositions.add(event.getFaceOffsetPos());
        var structure = event.getStructureHelper();
        if (structure != null && structure.resolve()) {
            for (BlockPos pushedPosition : structure.getToPush()) {
                affectedPositions.add(pushedPosition);
                affectedPositions.add(pushedPosition.relative(event.getDirection()));
            }
            affectedPositions.addAll(structure.getToDestroy());
        }
        return affectedPositions;
    }

    private static boolean restoreTossedItem(ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem().copy();
        if (stack.isEmpty() || event.getPlayer().getInventory().getFreeSlot() < 0) {
            return false;
        }
        return event.getPlayer().getInventory().add(stack);
    }
}
