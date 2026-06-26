package com.nadirkhoulali.ucs.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkKeyTest {
    @Test
    void storesDimensionAndChunkCoordinates() {
        ChunkKey key = new ChunkKey("minecraft:overworld", -2, 7);

        assertEquals("minecraft:overworld", key.dimension());
        assertEquals(-2, key.x());
        assertEquals(7, key.z());
        assertEquals("minecraft:overworld:-2,7", key.storageKey());
    }

    @Test
    void rejectsNonNamespacedDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new ChunkKey("overworld", 0, 0));
    }
}
