package com.nadirkhoulali.ucs.client;

import com.nadirkhoulali.ucs.core.model.MapTileKey;
import com.nadirkhoulali.ucs.network.OpenTerrainMapPayload;
import com.nadirkhoulali.ucs.network.TerrainTileCancelPayload;
import com.nadirkhoulali.ucs.network.TerrainTileRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class UcsTerrainMapClient {
    private static final AtomicInteger REQUEST_IDS = new AtomicInteger(1);

    private UcsTerrainMapClient() {
    }

    public static void openFromServer(OpenTerrainMapPayload payload) {
        open(payload.dimension(), payload.centerBlockX(), payload.centerBlockZ(), payload.zoom());
    }

    public static void openAtPlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        open(
                player.level().dimension().location().toString(),
                player.getBlockX(),
                player.getBlockZ(),
                UcsTerrainMapViewport.MIN_ZOOM
        );
    }

    public static int nextRequestId() {
        return REQUEST_IDS.updateAndGet(current -> current == Integer.MAX_VALUE ? 1 : current + 1);
    }

    public static boolean requestTiles(int requestId, List<MapTileKey> keys) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null || keys.isEmpty()) {
            return false;
        }
        PacketDistributor.sendToServer(new TerrainTileRequestPayload(requestId, keys));
        return true;
    }

    public static void cancelRequest(int requestId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(new TerrainTileCancelPayload(requestId));
        }
    }

    private static void open(String dimension, int centerBlockX, int centerBlockZ, int zoom) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new UcsTerrainMapScreen(
                dimension,
                centerBlockX,
                centerBlockZ,
                UcsTerrainMapViewport.clampZoom(zoom)
        ));
    }
}
