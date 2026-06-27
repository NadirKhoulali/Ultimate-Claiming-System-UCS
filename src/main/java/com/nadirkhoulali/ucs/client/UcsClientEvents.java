package com.nadirkhoulali.ucs.client;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

final class UcsClientEvents {
    UcsClientEvents() {
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        while (UcsClientMod.openMapKey().consumeClick()) {
            UcsTerrainMapClient.openAtPlayer();
        }
    }

    @SubscribeEvent
    public void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        UcsTerrainTileClientCache.clear();
        UcsClaimOverlayClientCache.clear();
    }

    @SubscribeEvent
    public void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ucsmap")
                .executes(context -> {
                    UcsTerrainMapClient.openAtPlayer();
                    return Command.SINGLE_SUCCESS;
                }));
    }
}
