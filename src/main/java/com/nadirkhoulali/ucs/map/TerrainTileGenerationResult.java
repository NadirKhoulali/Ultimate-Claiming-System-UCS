package com.nadirkhoulali.ucs.map;

import com.nadirkhoulali.ucs.core.model.MapTileKey;

import java.nio.file.Path;
import java.util.Objects;

public record TerrainTileGenerationResult(
        MapTileKey key,
        TerrainTileGenerationStatus status,
        int sampledChunks,
        int unavailableChunks,
        int deferredChunks,
        int knownPixels,
        int unknownPixels,
        Path cachePath
) {
    public TerrainTileGenerationResult {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(status, "status");
        if (sampledChunks < 0 || unavailableChunks < 0 || deferredChunks < 0 || knownPixels < 0 || unknownPixels < 0) {
            throw new IllegalArgumentException("generation counters must be nonnegative");
        }
        cachePath = Objects.requireNonNull(cachePath, "cachePath").toAbsolutePath().normalize();
    }
}
