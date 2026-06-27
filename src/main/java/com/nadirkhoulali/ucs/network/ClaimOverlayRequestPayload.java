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

public record ClaimOverlayRequestPayload(
        int requestId,
        String dimension,
        List<MapTileKey> tiles
) implements CustomPacketPayload {
    public static final int MAX_DIMENSION_LENGTH = 128;
    public static final int MAX_TILE_KEYS = 512;
    public static final Type<ClaimOverlayRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UcsMod.MOD_ID, "claim_overlay_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimOverlayRequestPayload> STREAM_CODEC = CustomPacketPayload.codec(
            ClaimOverlayRequestPayload::write,
            ClaimOverlayRequestPayload::read
    );

    public ClaimOverlayRequestPayload {
        dimension = Objects.requireNonNull(dimension, "dimension").trim();
        if (dimension.isBlank() || dimension.length() > MAX_DIMENSION_LENGTH) {
            throw new IllegalArgumentException("invalid overlay request dimension");
        }
        tiles = List.copyOf(Objects.requireNonNull(tiles, "tiles"));
        if (tiles.size() > MAX_TILE_KEYS) {
            throw new IllegalArgumentException("too many claim overlay tile keys in one request");
        }
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(requestId);
        buffer.writeUtf(dimension, MAX_DIMENSION_LENGTH);
        buffer.writeVarInt(tiles.size());
        for (MapTileKey key : tiles) {
            MapTileKeyStreamCodec.write(buffer, key);
        }
    }

    private static ClaimOverlayRequestPayload read(RegistryFriendlyByteBuf buffer) {
        int requestId = buffer.readVarInt();
        String dimension = buffer.readUtf(MAX_DIMENSION_LENGTH);
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_TILE_KEYS) {
            throw new IllegalArgumentException("invalid claim overlay tile key count " + size);
        }
        List<MapTileKey> tiles = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            tiles.add(MapTileKeyStreamCodec.read(buffer));
        }
        return new ClaimOverlayRequestPayload(requestId, dimension, tiles);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
