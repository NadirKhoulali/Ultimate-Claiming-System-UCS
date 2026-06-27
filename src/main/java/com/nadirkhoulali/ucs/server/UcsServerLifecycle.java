package com.nadirkhoulali.ucs.server;

import com.nadirkhoulali.ucs.UcsMod;
import com.nadirkhoulali.ucs.command.UcsCommands;
import com.nadirkhoulali.ucs.config.UcsCommonConfig;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.config.UcsConfigValidationReport;
import com.nadirkhoulali.ucs.service.UcsServices;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public final class UcsServerLifecycle {
    private final UcsServices services;

    public UcsServerLifecycle(UcsServices services) {
        this.services = services;
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        UcsCommands.register(event.getDispatcher());
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
}
