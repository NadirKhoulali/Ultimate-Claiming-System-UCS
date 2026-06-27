package com.nadirkhoulali.ucs.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TerrainChunkSnapshotTest {
    @Test
    void storesSurfaceHeightsAlongsideColors() {
        int[] colors = new int[TerrainChunkSnapshot.SAMPLE_COUNT];
        int[] heights = new int[TerrainChunkSnapshot.SAMPLE_COUNT];
        colors[5] = 0xFF123456;
        heights[5] = 93;

        TerrainChunkSnapshot snapshot = new TerrainChunkSnapshot("minecraft:overworld", 2, 3, colors, heights);

        assertEquals(0xFF123456, snapshot.colorAt(5, 0));
        assertEquals(93, snapshot.heightAt(5, 0));
    }

    @Test
    void rejectsMismatchedHeightArrays() {
        int[] colors = new int[TerrainChunkSnapshot.SAMPLE_COUNT];
        int[] heights = new int[TerrainChunkSnapshot.SAMPLE_COUNT - 1];

        assertThrows(IllegalArgumentException.class, () -> new TerrainChunkSnapshot("minecraft:overworld", 0, 0, colors, heights));
    }
}
