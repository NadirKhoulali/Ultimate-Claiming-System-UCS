package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.ClaimChunk;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimShapeTest {
    @Test
    void usesCardinalConnectivity() {
        Set<ClaimChunk> chunks = Set.of(
                chunk(0, 0),
                chunk(1, 0),
                chunk(1, 1)
        );

        assertTrue(ClaimShape.isConnected(chunks));
    }

    @Test
    void rejectsDiagonalOnlyConnectivity() {
        Set<ClaimChunk> chunks = Set.of(
                chunk(0, 0),
                chunk(1, 1)
        );

        assertFalse(ClaimShape.isConnected(chunks));
        assertEquals(2, ClaimShape.connectedComponents(chunks).size());
    }

    private ClaimChunk chunk(int x, int z) {
        return new ClaimChunk(new ChunkKey("minecraft:overworld", x, z));
    }
}
