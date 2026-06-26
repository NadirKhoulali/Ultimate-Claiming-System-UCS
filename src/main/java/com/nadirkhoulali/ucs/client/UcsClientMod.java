package com.nadirkhoulali.ucs.client;

import com.nadirkhoulali.ucs.UcsMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = UcsMod.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = UcsMod.MOD_ID, value = Dist.CLIENT)
public final class UcsClientMod {
    public UcsClientMod(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        UcsMod.LOGGER.info("{} client setup complete.", UcsMod.MOD_NAME);
    }
}
