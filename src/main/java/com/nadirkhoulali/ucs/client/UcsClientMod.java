package com.nadirkhoulali.ucs.client;

import com.nadirkhoulali.ucs.UcsMod;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = UcsMod.MOD_ID, dist = Dist.CLIENT)
public final class UcsClientMod {
    private static final KeyMapping OPEN_MAP_KEY = new KeyMapping(
            "key.ucs.map",
            GLFW.GLFW_KEY_M,
            "key.categories.ucs"
    );

    public UcsClientMod(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(UcsClientMod::onClientSetup);
        modEventBus.addListener(UcsClientMod::onRegisterKeyMappings);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.register(new UcsClientEvents());
    }

    public static KeyMapping openMapKey() {
        return OPEN_MAP_KEY;
    }

    static void onClientSetup(FMLClientSetupEvent event) {
        UcsMod.LOGGER.info("{} client setup complete.", UcsMod.MOD_NAME);
    }

    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MAP_KEY);
    }
}
