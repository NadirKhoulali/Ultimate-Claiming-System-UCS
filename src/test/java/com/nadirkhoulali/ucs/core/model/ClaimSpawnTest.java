package com.nadirkhoulali.ucs.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ClaimSpawnTest {
    @Test
    void rejectsPositionOutsideDeclaredChunk() {
        ChunkKey chunk = new ChunkKey("minecraft:overworld", 0, 0);

        assertThrows(IllegalArgumentException.class, () -> new ClaimSpawn(chunk, 16.0D, 64.0D, 8.0D, 0.0F, 0.0F));
    }
}
