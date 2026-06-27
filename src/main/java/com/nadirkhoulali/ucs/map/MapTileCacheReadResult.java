package com.nadirkhoulali.ucs.map;

import com.nadirkhoulali.ucs.core.model.MapTileKey;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public record MapTileCacheReadResult(
        MapTileCacheReadStatus status,
        MapTileKey key,
        Path path,
        Optional<byte[]> payload,
        Optional<Integer> storedFormatVersion
) {
    public MapTileCacheReadResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(key, "key");
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        payload = payload.map(bytes -> Arrays.copyOf(bytes, bytes.length));
        storedFormatVersion = Objects.requireNonNull(storedFormatVersion, "storedFormatVersion");
        if (status == MapTileCacheReadStatus.HIT && payload.isEmpty()) {
            throw new IllegalArgumentException("hit results must include payload");
        }
        if (status != MapTileCacheReadStatus.HIT && payload.isPresent()) {
            throw new IllegalArgumentException("non-hit results cannot include payload");
        }
    }

    public byte[] payloadBytes() {
        return payload.map(bytes -> Arrays.copyOf(bytes, bytes.length)).orElse(new byte[0]);
    }

    public static MapTileCacheReadResult hit(MapTileKey key, Path path, byte[] payload) {
        return new MapTileCacheReadResult(
                MapTileCacheReadStatus.HIT,
                key,
                path,
                Optional.of(Arrays.copyOf(payload, payload.length)),
                Optional.empty()
        );
    }

    public static MapTileCacheReadResult miss(MapTileKey key, Path path) {
        return new MapTileCacheReadResult(MapTileCacheReadStatus.MISS, key, path, Optional.empty(), Optional.empty());
    }

    public static MapTileCacheReadResult staleVersion(MapTileKey key, Path path, int storedFormatVersion) {
        return new MapTileCacheReadResult(
                MapTileCacheReadStatus.STALE_VERSION,
                key,
                path,
                Optional.empty(),
                Optional.of(storedFormatVersion)
        );
    }

    public static MapTileCacheReadResult corrupt(MapTileKey key, Path path) {
        return new MapTileCacheReadResult(MapTileCacheReadStatus.CORRUPT, key, path, Optional.empty(), Optional.empty());
    }
}
