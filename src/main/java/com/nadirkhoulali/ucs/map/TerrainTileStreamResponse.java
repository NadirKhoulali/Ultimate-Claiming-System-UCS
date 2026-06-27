package com.nadirkhoulali.ucs.map;

import com.nadirkhoulali.ucs.core.model.MapTileKey;

import java.util.Arrays;
import java.util.Objects;

public record TerrainTileStreamResponse(
        int requestId,
        MapTileKey key,
        TerrainTileResponseStatus status,
        byte[] payload,
        String detail
) {
    public TerrainTileStreamResponse {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(status, "status");
        payload = payload == null ? new byte[0] : Arrays.copyOf(payload, payload.length);
        detail = detail == null ? "" : detail.trim();
    }

    public static TerrainTileStreamResponse payload(
            int requestId,
            MapTileKey key,
            TerrainTileResponseStatus status,
            byte[] payload,
            String detail
    ) {
        if (status == TerrainTileResponseStatus.RATE_LIMITED || status == TerrainTileResponseStatus.CANCELLED) {
            throw new IllegalArgumentException("use control response factory for control statuses");
        }
        return new TerrainTileStreamResponse(requestId, key, status, payload, detail);
    }

    public static TerrainTileStreamResponse control(
            int requestId,
            MapTileKey key,
            TerrainTileResponseStatus status,
            String detail
    ) {
        if (status != TerrainTileResponseStatus.RATE_LIMITED && status != TerrainTileResponseStatus.CANCELLED) {
            throw new IllegalArgumentException("control responses must be rate limited or cancelled");
        }
        return new TerrainTileStreamResponse(requestId, key, status, new byte[0], detail);
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }
}
