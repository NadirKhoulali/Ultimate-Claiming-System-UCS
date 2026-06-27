package com.nadirkhoulali.ucs.map;

import com.nadirkhoulali.ucs.core.model.MapTileKey;

import java.util.Arrays;
import java.util.Objects;

public record TerrainChunkSnapshot(
        String dimension,
        int chunkX,
        int chunkZ,
        int[] argb,
        int[] heights
) {
    public static final int CHUNK_SIZE = 16;
    public static final int SAMPLE_COUNT = CHUNK_SIZE * CHUNK_SIZE;
    public static final int UNKNOWN_HEIGHT = Integer.MIN_VALUE;

    public TerrainChunkSnapshot(String dimension, int chunkX, int chunkZ, int[] argb) {
        this(dimension, chunkX, chunkZ, argb, flatHeights());
    }

    public TerrainChunkSnapshot {
        dimension = new MapTileKey(dimension, 0, 0, 0).dimension();
        Objects.requireNonNull(argb, "argb");
        if (argb.length != SAMPLE_COUNT) {
            throw new IllegalArgumentException("argb must contain 256 samples");
        }
        Objects.requireNonNull(heights, "heights");
        if (heights.length != SAMPLE_COUNT) {
            throw new IllegalArgumentException("heights must contain 256 samples");
        }
        argb = Arrays.copyOf(argb, argb.length);
        heights = Arrays.copyOf(heights, heights.length);
    }

    public int colorAt(int localX, int localZ) {
        if (localX < 0 || localX >= CHUNK_SIZE || localZ < 0 || localZ >= CHUNK_SIZE) {
            throw new IllegalArgumentException("local coordinates must be inside the chunk");
        }
        return argb[localZ * CHUNK_SIZE + localX];
    }

    public int heightAt(int localX, int localZ) {
        if (localX < 0 || localX >= CHUNK_SIZE || localZ < 0 || localZ >= CHUNK_SIZE) {
            throw new IllegalArgumentException("local coordinates must be inside the chunk");
        }
        return heights[localZ * CHUNK_SIZE + localX];
    }

    @Override
    public int[] argb() {
        return Arrays.copyOf(argb, argb.length);
    }

    @Override
    public int[] heights() {
        return Arrays.copyOf(heights, heights.length);
    }

    private static int[] flatHeights() {
        int[] values = new int[SAMPLE_COUNT];
        Arrays.fill(values, 0);
        return values;
    }
}
