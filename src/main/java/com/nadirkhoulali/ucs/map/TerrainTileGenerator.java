package com.nadirkhoulali.ucs.map;

import com.nadirkhoulali.ucs.core.model.MapTileKey;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TerrainTileGenerator {
    public static final int TILE_SIZE = 128;
    public static final int UNKNOWN_COLOR = 0xFF2B2F36;

    public TerrainTileGenerationResult generate(
            MapTileKey key,
            TerrainChunkSampler sampler,
            FileMapTileCache cache,
            int maxChunkSamples,
            Instant now
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(sampler, "sampler");
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(now, "now");
        if (maxChunkSamples <= 0) {
            throw new IllegalArgumentException("maxChunkSamples must be greater than zero");
        }

        int scale = 1 << key.zoom();
        int originBlockX = key.tileX() * TILE_SIZE * scale;
        int originBlockZ = key.tileZ() * TILE_SIZE * scale;
        Map<ChunkCoord, Optional<TerrainChunkSnapshot>> snapshots = new HashMap<>();
        int[] colors = new int[TILE_SIZE * TILE_SIZE];
        int[] heights = new int[TILE_SIZE * TILE_SIZE];
        int sampledChunks = 0;
        int unavailableChunks = 0;
        int deferredChunks = 0;
        int knownPixels = 0;
        int unknownPixels = 0;

        for (int z = 0; z < TILE_SIZE; z++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                int blockX = originBlockX + x * scale;
                int blockZ = originBlockZ + z * scale;
                int chunkX = Math.floorDiv(blockX, TerrainChunkSnapshot.CHUNK_SIZE);
                int chunkZ = Math.floorDiv(blockZ, TerrainChunkSnapshot.CHUNK_SIZE);
                int localX = Math.floorMod(blockX, TerrainChunkSnapshot.CHUNK_SIZE);
                int localZ = Math.floorMod(blockZ, TerrainChunkSnapshot.CHUNK_SIZE);
                ChunkCoord coord = new ChunkCoord(chunkX, chunkZ);

                Optional<TerrainChunkSnapshot> snapshot = snapshots.get(coord);
                if (snapshot == null) {
                    if (sampledChunks >= maxChunkSamples) {
                        snapshots.put(coord, Optional.empty());
                        deferredChunks++;
                        colors[z * TILE_SIZE + x] = UNKNOWN_COLOR;
                        heights[z * TILE_SIZE + x] = TerrainChunkSnapshot.UNKNOWN_HEIGHT;
                        unknownPixels++;
                        continue;
                    }
                    snapshot = sampler.sampleLoadedChunk(key.dimension(), chunkX, chunkZ);
                    snapshots.put(coord, snapshot);
                    sampledChunks++;
                    if (snapshot.isEmpty()) {
                        unavailableChunks++;
                    }
                }

                if (snapshot.isPresent()) {
                    TerrainChunkSnapshot chunkSnapshot = snapshot.orElseThrow();
                    colors[z * TILE_SIZE + x] = chunkSnapshot.colorAt(localX, localZ);
                    heights[z * TILE_SIZE + x] = chunkSnapshot.heightAt(localX, localZ);
                    knownPixels++;
                } else {
                    colors[z * TILE_SIZE + x] = UNKNOWN_COLOR;
                    heights[z * TILE_SIZE + x] = TerrainChunkSnapshot.UNKNOWN_HEIGHT;
                    unknownPixels++;
                }
            }
        }

        TerrainColorEnhancer.applyReliefShading(colors, heights, TILE_SIZE, key.zoom());
        Path path = cache.write(key, new TerrainTilePayload(key, TILE_SIZE, colors).encode(), now);
        TerrainTileGenerationStatus status = deferredChunks == 0
                ? TerrainTileGenerationStatus.COMPLETE
                : TerrainTileGenerationStatus.PARTIAL_BUDGET_EXHAUSTED;
        return new TerrainTileGenerationResult(
                key,
                status,
                sampledChunks,
                unavailableChunks,
                deferredChunks,
                knownPixels,
                unknownPixels,
                path
        );
    }

    public int tileBlockSize(MapTileKey key) {
        Objects.requireNonNull(key, "key");
        return TILE_SIZE * (1 << key.zoom());
    }

    private record ChunkCoord(int x, int z) {
    }
}
