package com.nadirkhoulali.ucs.network;

import com.nadirkhoulali.ucs.UcsMod;
import com.nadirkhoulali.ucs.core.model.MapTileKey;
import com.nadirkhoulali.ucs.map.TerrainTileCompression;
import com.nadirkhoulali.ucs.map.TerrainTileResponseStatus;
import com.nadirkhoulali.ucs.map.TerrainTileStreamResponse;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Objects;

public record TerrainTileResponsePayload(
        int requestId,
        MapTileKey key,
        TerrainTileResponseStatus status,
        boolean compressed,
        byte[] payload,
        String detail
) implements CustomPacketPayload {
    public static final int MAX_PAYLOAD_BYTES = 256 * 1024;
    public static final int MAX_DETAIL_LENGTH = 512;
    public static final Type<TerrainTileResponsePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UcsMod.MOD_ID, "terrain_tile_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerrainTileResponsePayload> STREAM_CODEC = CustomPacketPayload.codec(
            TerrainTileResponsePayload::write,
            TerrainTileResponsePayload::read
    );

    public TerrainTileResponsePayload {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(status, "status");
        payload = payload == null ? new byte[0] : Arrays.copyOf(payload, payload.length);
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("terrain tile response payload is too large");
        }
        detail = detail == null ? "" : detail.trim();
        if (detail.length() > MAX_DETAIL_LENGTH) {
            detail = detail.substring(0, MAX_DETAIL_LENGTH);
        }
    }

    public static TerrainTileResponsePayload from(TerrainTileStreamResponse response) {
        TerrainTileCompression.CompressedPayload compressed = TerrainTileCompression.compressIfUseful(response.payload());
        return new TerrainTileResponsePayload(
                response.requestId(),
                response.key(),
                response.status(),
                compressed.compressed(),
                compressed.bytes(),
                response.detail()
        );
    }

    public TerrainTileStreamResponse toStreamResponse() {
        return new TerrainTileStreamResponse(
                requestId,
                key,
                status,
                TerrainTileCompression.decompress(payload, compressed, MAX_PAYLOAD_BYTES),
                detail
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(requestId);
        MapTileKeyStreamCodec.write(buffer, key);
        buffer.writeVarInt(status.ordinal());
        buffer.writeBoolean(compressed);
        buffer.writeByteArray(payload);
        buffer.writeUtf(detail, MAX_DETAIL_LENGTH);
    }

    private static TerrainTileResponsePayload read(RegistryFriendlyByteBuf buffer) {
        int requestId = buffer.readVarInt();
        MapTileKey key = MapTileKeyStreamCodec.read(buffer);
        TerrainTileResponseStatus[] statuses = TerrainTileResponseStatus.values();
        int statusOrdinal = buffer.readVarInt();
        if (statusOrdinal < 0 || statusOrdinal >= statuses.length) {
            throw new IllegalArgumentException("invalid terrain tile response status " + statusOrdinal);
        }
        boolean compressed = buffer.readBoolean();
        byte[] payload = buffer.readByteArray(MAX_PAYLOAD_BYTES);
        String detail = buffer.readUtf(MAX_DETAIL_LENGTH);
        return new TerrainTileResponsePayload(requestId, key, statuses[statusOrdinal], compressed, payload, detail);
    }

    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
