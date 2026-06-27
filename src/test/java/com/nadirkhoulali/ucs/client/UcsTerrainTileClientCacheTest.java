package com.nadirkhoulali.ucs.client;

import com.nadirkhoulali.ucs.core.model.MapTileKey;
import com.nadirkhoulali.ucs.map.TerrainTileResponseStatus;
import com.nadirkhoulali.ucs.map.TerrainTileStreamResponse;
import com.nadirkhoulali.ucs.network.TerrainTileResponsePayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UcsTerrainTileClientCacheTest {
    @AfterEach
    void clearCache() {
        UcsTerrainTileClientCache.clear();
    }

    @Test
    void acceptsTerrainTileResponsesByRequestAndKey() {
        MapTileKey key = new MapTileKey("minecraft:overworld", 0, 1, 2);
        byte[] payload = new byte[]{4, 5, 6};
        TerrainTileStreamResponse response = TerrainTileStreamResponse.payload(3, key, TerrainTileResponseStatus.HIT, payload, "cache hit");

        UcsTerrainTileClientCache.accept(TerrainTileResponsePayload.from(response));

        TerrainTileStreamResponse cached = UcsTerrainTileClientCache.response(3, key).orElseThrow();
        assertEquals(TerrainTileResponseStatus.HIT, cached.status());
        assertArrayEquals(payload, cached.payload());
        assertEquals(1, UcsTerrainTileClientCache.size());
    }
}
