package com.nadirkhoulali.ucs.server;

import com.nadirkhoulali.ucs.UcsMod;
import com.nadirkhoulali.ucs.command.UcsCommands;
import com.nadirkhoulali.ucs.config.UcsCommonConfig;
import com.nadirkhoulali.ucs.service.UcsServices;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

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
        if (UcsCommonConfig.LOG_STARTUP_SUMMARY.get()) {
            UcsMod.LOGGER.info(
                    "{} server lifecycle ready. configSchema={}, services={}",
                    UcsMod.MOD_NAME,
                    UcsCommonConfig.CONFIG_SCHEMA_VERSION.get(),
                    services.summary()
            );
        }
    }
}
