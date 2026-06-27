package com.nadirkhoulali.ucs.network;

import com.nadirkhoulali.ucs.core.model.MapTileKey;
import net.minecraft.network.RegistryFriendlyByteBuf;

final class MapTileKeyStreamCodec {
    private static final int MAX_DIMENSION_LENGTH = 256;

    private MapTileKeyStreamCodec() {
    }

    static void write(RegistryFriendlyByteBuf buffer, MapTileKey key) {
        buffer.writeUtf(key.dimension(), MAX_DIMENSION_LENGTH);
        buffer.writeVarInt(key.zoom());
        buffer.writeInt(key.tileX());
        buffer.writeInt(key.tileZ());
    }

    static MapTileKey read(RegistryFriendlyByteBuf buffer) {
        return new MapTileKey(
                buffer.readUtf(MAX_DIMENSION_LENGTH),
                buffer.readVarInt(),
                buffer.readInt(),
                buffer.readInt()
        );
    }
}
