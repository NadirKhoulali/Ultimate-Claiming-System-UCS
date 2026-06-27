package com.nadirkhoulali.ucs.map;

import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.MapTileKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrainTileStreamServiceTest {
    private final TerrainTileStreamService service = new TerrainTileStreamService();

    @Test
    void streamAppliesPerPlayerAndGlobalLimits() {
        UUID player = UUID.randomUUID();
        List<MapTileKey> keys = List.of(
                key(0),
                key(1),
                key(2)
        );

        List<TerrainTileStreamResponse> responses = service.stream(
                player,
                7,
                keys,
                new UcsConfigSnapshot.MapCachePolicy(1024, 30, 2, 1),
                (requestId, key) -> TerrainTileStreamResponse.payload(requestId, key, TerrainTileResponseStatus.HIT, new byte[]{1}, "hit")
        );

        assertEquals(TerrainTileResponseStatus.HIT, responses.get(0).status());
        assertEquals(TerrainTileResponseStatus.RATE_LIMITED, responses.get(1).status());
        assertEquals(TerrainTileResponseStatus.RATE_LIMITED, responses.get(2).status());
    }

    @Test
    void cancelledRequestReturnsCancelledResponsesWithoutResolving() {
        UUID player = UUID.randomUUID();
        service.cancel(player, 11);

        List<TerrainTileStreamResponse> responses = service.stream(
                player,
                11,
                List.of(key(0), key(1)),
                new UcsConfigSnapshot.MapCachePolicy(1024, 30, 10, 10),
                (requestId, key) -> {
                    throw new AssertionError("cancelled requests should not resolve tiles");
                }
        );

        assertEquals(TerrainTileResponseStatus.CANCELLED, responses.get(0).status());
        assertEquals(TerrainTileResponseStatus.CANCELLED, responses.get(1).status());
    }

    @Test
    void compressionRoundTripPreservesPayload() {
        byte[] payload = new byte[4096];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 4);
        }

        TerrainTileCompression.CompressedPayload compressed = TerrainTileCompression.compressIfUseful(payload);

        assertEquals(true, compressed.compressed());
        assertArrayEquals(payload, TerrainTileCompression.decompress(compressed.bytes(), true, 8192));
    }

    private static MapTileKey key(int tileX) {
        return new MapTileKey("minecraft:overworld", 0, tileX, 0);
    }
}
