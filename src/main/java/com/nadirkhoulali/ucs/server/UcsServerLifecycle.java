package com.nadirkhoulali.ucs.server;

import com.nadirkhoulali.ucs.UcsMod;
import com.nadirkhoulali.ucs.command.ClaimCommands;
import com.nadirkhoulali.ucs.command.UcsCommands;
import com.nadirkhoulali.ucs.config.UcsCommonConfig;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.config.UcsConfigValidationReport;
import com.nadirkhoulali.ucs.permission.UcsPermissionNodes;
import com.nadirkhoulali.ucs.service.UcsServices;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

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

    private static void sendProtectionDenial(Player player, UcsConfigSnapshot config, String flagId, String reason) {
        player.displayClientMessage(
                Component.translatable("command.ucs.protection.denied", flagId, reason),
                config.messages().sendActionBarDenials()
        );
    }
}
