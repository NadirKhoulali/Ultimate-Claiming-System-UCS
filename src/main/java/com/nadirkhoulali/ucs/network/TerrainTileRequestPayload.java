package com.nadirkhoulali.ucs.network;

import com.nadirkhoulali.ucs.UcsMod;
import com.nadirkhoulali.ucs.core.model.MapTileKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record TerrainTileRequestPayload(int requestId, List<MapTileKey> keys) implements CustomPacketPayload {
    public static final int MAX_KEYS_PER_PAYLOAD = 512;
    public static final Type<TerrainTileRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UcsMod.MOD_ID, "terrain_tile_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerrainTileRequestPayload> STREAM_CODEC = CustomPacketPayload.codec(
            TerrainTileRequestPayload::write,
            TerrainTileRequestPayload::read
    );

    public TerrainTileRequestPayload {
        keys = List.copyOf(Objects.requireNonNull(keys, "keys"));
        if (keys.size() > MAX_KEYS_PER_PAYLOAD) {
            throw new IllegalArgumentException("too many map tile keys in one request");
        }
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(requestId);
        buffer.writeVarInt(keys.size());
        keys.forEach(key -> MapTileKeyStreamCodec.write(buffer, key));
    }

    private static TerrainTileRequestPayload read(RegistryFriendlyByteBuf buffer) {
        int requestId = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_KEYS_PER_PAYLOAD) {
            throw new IllegalArgumentException("invalid terrain tile request count " + count);
        }
        List<MapTileKey> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            keys.add(MapTileKeyStreamCodec.read(buffer));
        }
        return new TerrainTileRequestPayload(requestId, keys);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
