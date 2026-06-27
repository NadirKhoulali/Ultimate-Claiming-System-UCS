package com.nadirkhoulali.ucs.client;

import com.nadirkhoulali.ucs.core.model.MapTileKey;
import com.nadirkhoulali.ucs.map.TerrainTileStreamResponse;
import com.nadirkhoulali.ucs.network.TerrainTileResponsePayload;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class UcsTerrainTileClientCache {
    private static final Map<ClientTileKey, TerrainTileStreamResponse> RESPONSES = new ConcurrentHashMap<>();

    private UcsTerrainTileClientCache() {
    }

    public static void accept(TerrainTileResponsePayload payload) {
        TerrainTileStreamResponse response = payload.toStreamResponse();
        RESPONSES.put(new ClientTileKey(response.requestId(), response.key()), response);
    }

    public static Optional<TerrainTileStreamResponse> response(int requestId, MapTileKey key) {
        return Optional.ofNullable(RESPONSES.get(new ClientTileKey(requestId, key)));
    }

    public static int size() {
        return RESPONSES.size();
    }

    public static void clear() {
        RESPONSES.clear();
    }

    private record ClientTileKey(int requestId, MapTileKey key) {
    }
}
