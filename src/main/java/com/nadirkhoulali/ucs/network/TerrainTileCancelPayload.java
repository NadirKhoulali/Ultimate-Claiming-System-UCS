package com.nadirkhoulali.ucs.network;

import com.nadirkhoulali.ucs.UcsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TerrainTileCancelPayload(int requestId) implements CustomPacketPayload {
    public static final Type<TerrainTileCancelPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UcsMod.MOD_ID, "terrain_tile_cancel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerrainTileCancelPayload> STREAM_CODEC = CustomPacketPayload.codec(
            TerrainTileCancelPayload::write,
            TerrainTileCancelPayload::read
    );

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(requestId);
    }

    private static TerrainTileCancelPayload read(RegistryFriendlyByteBuf buffer) {
        return new TerrainTileCancelPayload(buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
