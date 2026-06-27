package com.nadirkhoulali.ucs.map;

import com.nadirkhoulali.ucs.core.model.MapTileKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainTileGeneratorTest {
    private static final int RED = 0xFFFF0000;
    private final TerrainTileGenerator generator = new TerrainTileGenerator();

    @TempDir
    Path tempDir;

    @Test
    void tileBlockSizeScalesByZoom() {
        assertEquals(128, generator.tileBlockSize(new MapTileKey("minecraft:overworld", 0, 0, 0)));
        assertEquals(1024, generator.tileBlockSize(new MapTileKey("minecraft:overworld", 3, 0, 0)));
    }

    @Test
    void generatesTileFromSampledChunksAndWritesCache() {
        FileMapTileCache cache = new FileMapTileCache(tempDir);
        MapTileKey key = new MapTileKey("minecraft:overworld", 0, 0, 0);
        TerrainChunkSampler sampler = (dimension, chunkX, chunkZ) -> chunkX == 0 && chunkZ == 0
                ? Optional.of(solidSnapshot(dimension, chunkX, chunkZ, RED))
                : Optional.empty();

        TerrainTileGenerationResult result = generator.generate(key, sampler, cache, 64, Instant.EPOCH);
        TerrainTilePayload payload = TerrainTilePayload.decode(cache.read(key).payloadBytes());

        assertEquals(TerrainTileGenerationStatus.COMPLETE, result.status());
        assertEquals(64, result.sampledChunks());
        assertEquals(63, result.unavailableChunks());
        assertEquals(256, result.knownPixels());
        assertEquals(TerrainTileGenerator.TILE_SIZE * TerrainTileGenerator.TILE_SIZE - 256, result.unknownPixels());
        assertEquals(RED, payload.colorAt(0, 0));
        assertEquals(TerrainTileGenerator.UNKNOWN_COLOR, payload.colorAt(16, 0));
    }

    @Test
    void generationStopsSamplingAtChunkBudgetAndMarksPartial() {
        FileMapTileCache cache = new FileMapTileCache(tempDir);
        MapTileKey key = new MapTileKey("minecraft:overworld", 0, 0, 0);
        List<String> sampled = new ArrayList<>();
        TerrainChunkSampler sampler = (dimension, chunkX, chunkZ) -> {
            sampled.add(chunkX + "," + chunkZ);
            return Optional.of(solidSnapshot(dimension, chunkX, chunkZ, RED));
        };

        TerrainTileGenerationResult result = generator.generate(key, sampler, cache, 1, Instant.EPOCH);
        TerrainTilePayload payload = TerrainTilePayload.decode(cache.read(key).payloadBytes());

        assertEquals(TerrainTileGenerationStatus.PARTIAL_BUDGET_EXHAUSTED, result.status());
        assertEquals(List.of("0,0"), sampled);
        assertEquals(1, result.sampledChunks());
        assertTrue(result.deferredChunks() > 0);
        assertEquals(RED, payload.colorAt(0, 0));
        assertEquals(TerrainTileGenerator.UNKNOWN_COLOR, payload.colorAt(16, 0));
    }

    @Test
    void negativeTileCoordinatesUseFloorChunkMath() {
        FileMapTileCache cache = new FileMapTileCache(tempDir);
        MapTileKey key = new MapTileKey("minecraft:overworld", 0, -1, -1);
        List<String> sampled = new ArrayList<>();
        TerrainChunkSampler sampler = (dimension, chunkX, chunkZ) -> {
            sampled.add(chunkX + "," + chunkZ);
            return Optional.of(solidSnapshot(dimension, chunkX, chunkZ, RED));
        };

        generator.generate(key, sampler, cache, 1, Instant.EPOCH);

        assertEquals(List.of("-8,-8"), sampled);
    }

    private static TerrainChunkSnapshot solidSnapshot(String dimension, int chunkX, int chunkZ, int color) {
        int[] colors = new int[TerrainChunkSnapshot.SAMPLE_COUNT];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = color;
        }
        return new TerrainChunkSnapshot(dimension, chunkX, chunkZ, colors);
    }
}
