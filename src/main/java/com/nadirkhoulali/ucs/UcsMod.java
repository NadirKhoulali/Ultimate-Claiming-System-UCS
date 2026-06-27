package com.nadirkhoulali.ucs;

import com.mojang.logging.LogUtils;
import com.nadirkhoulali.ucs.config.UcsCommonConfig;
import com.nadirkhoulali.ucs.network.UcsNetwork;
import com.nadirkhoulali.ucs.server.UcsServerLifecycle;
import com.nadirkhoulali.ucs.service.UcsServices;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@Mod(UcsMod.MOD_ID)
public final class UcsMod {
    public static final String MOD_ID = "ucs";
    public static final String MOD_NAME = "Ultimate Claiming System";
    public static final Logger LOGGER = LogUtils.getLogger();

    private final UcsServices services;

    public UcsMod(IEventBus modEventBus, ModContainer modContainer) {
        this.services = new UcsServices();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);
        modContainer.registerConfig(ModConfig.Type.COMMON, UcsCommonConfig.SPEC);

        NeoForge.EVENT_BUS.register(new UcsServerLifecycle(services));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("{} common setup complete.", MOD_NAME);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        UcsNetwork.registerPayloads(event, services);
    }

    public UcsServices services() {
        return services;
    }
}
