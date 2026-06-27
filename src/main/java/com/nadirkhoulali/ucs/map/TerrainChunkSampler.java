package com.nadirkhoulali.ucs.map;

import java.util.Optional;

@FunctionalInterface
public interface TerrainChunkSampler {
    Optional<TerrainChunkSnapshot> sampleLoadedChunk(String dimension, int chunkX, int chunkZ);
}
