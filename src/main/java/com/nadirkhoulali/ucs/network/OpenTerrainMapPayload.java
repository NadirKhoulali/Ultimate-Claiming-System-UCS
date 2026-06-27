package com.nadirkhoulali.ucs.network;

import com.nadirkhoulali.ucs.UcsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record OpenTerrainMapPayload(
        String dimension,
        int centerBlockX,
        int centerBlockZ,
        int zoom
) implements CustomPacketPayload {
    public static final int MAX_DIMENSION_LENGTH = 128;
    public static final Type<OpenTerrainMapPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UcsMod.MOD_ID, "open_terrain_map"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTerrainMapPayload> STREAM_CODEC = CustomPacketPayload.codec(
            OpenTerrainMapPayload::write,
            OpenTerrainMapPayload::read
    );

    public OpenTerrainMapPayload {
        if (dimension == null || dimension.isBlank()) {
            throw new IllegalArgumentException("dimension must be nonblank");
        }
        dimension = dimension.trim();
        if (dimension.length() > MAX_DIMENSION_LENGTH) {
            throw new IllegalArgumentException("dimension is too long");
        }
    }

    public static OpenTerrainMapPayload forPlayer(ServerPlayer player, int zoom) {
        return new OpenTerrainMapPayload(
                player.serverLevel().dimension().location().toString(),
                player.getBlockX(),
                player.getBlockZ(),
                zoom
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(dimension, MAX_DIMENSION_LENGTH);
        buffer.writeVarInt(centerBlockX);
        buffer.writeVarInt(centerBlockZ);
        buffer.writeVarInt(zoom);
    }

    private static OpenTerrainMapPayload read(RegistryFriendlyByteBuf buffer) {
        return new OpenTerrainMapPayload(
                buffer.readUtf(MAX_DIMENSION_LENGTH),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
