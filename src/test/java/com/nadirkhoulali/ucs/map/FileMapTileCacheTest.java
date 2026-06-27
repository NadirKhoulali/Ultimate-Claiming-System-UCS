package com.nadirkhoulali.ucs.map;

import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.MapTileKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileMapTileCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void pathMappingUsesVersionDimensionZoomShardAndCoordinates() {
        FileMapTileCache cache = new FileMapTileCache(tempDir);
        MapTileKey key = new MapTileKey("minecraft:overworld", 3, -1, 512);

        Path path = tempDir.relativize(cache.pathFor(key));

        assertEquals(
                Path.of("v1", "minecraft", "overworld", "z3", "s_-1_2", "-1_512.ucstile"),
                path
        );
    }

    @Test
    void writeThenReadReturnsPayload() {
        FileMapTileCache cache = new FileMapTileCache(tempDir);
        MapTileKey key = new MapTileKey("minecraft:the_nether", 2, 8, 9);
        byte[] payload = new byte[]{1, 2, 3, 4};

        cache.write(key, payload, Instant.EPOCH);
        MapTileCacheReadResult result = cache.read(key);

        assertEquals(MapTileCacheReadStatus.HIT, result.status());
        assertArrayEquals(payload, result.payloadBytes());
    }

    @Test
    void corruptTileReturnsCorruptAndDeletesFile() throws Exception {
        FileMapTileCache cache = new FileMapTileCache(tempDir);
        MapTileKey key = new MapTileKey("minecraft:overworld", 0, 0, 0);
        Path path = cache.pathFor(key);
        Files.createDirectories(path.getParent());
        Files.writeString(path, "not a ucs tile");

        MapTileCacheReadResult result = cache.read(key);

        assertEquals(MapTileCacheReadStatus.CORRUPT, result.status());
        assertFalse(Files.exists(path));
    }

    @Test
    void staleFormatVersionReturnsStaleAndDeletesFile() throws Exception {
        FileMapTileCache writer = new FileMapTileCache(tempDir, 2);
        FileMapTileCache reader = new FileMapTileCache(tempDir, 1);
        MapTileKey key = new MapTileKey("minecraft:overworld", 0, 1, 1);
        Path stalePath = writer.write(key, new byte[]{9}, Instant.EPOCH);
        Path path = reader.pathFor(key);
        Files.createDirectories(path.getParent());
        Files.move(stalePath, path, StandardCopyOption.REPLACE_EXISTING);

        MapTileCacheReadResult result = reader.read(key);

        assertEquals(MapTileCacheReadStatus.STALE_VERSION, result.status());
        assertEquals(2, result.storedFormatVersion().orElseThrow());
        assertFalse(Files.exists(path));
    }

    @Test
    void pruneDeletesOldTilesAndThenOldestUntilSizeLimit() throws Exception {
        FileMapTileCache cache = new FileMapTileCache(tempDir);
        Instant now = Instant.EPOCH.plusSeconds(60L * 60L * 24L * 10L);
        Path old = cache.write(new MapTileKey("minecraft:overworld", 0, 0, 0), new byte[512], now.minusSeconds(60L * 60L * 24L * 40L));
        Path newest = cache.write(new MapTileKey("minecraft:overworld", 0, 1, 0), new byte[700 * 1024], now);
        Path middle = cache.write(new MapTileKey("minecraft:overworld", 0, 2, 0), new byte[700 * 1024], now.minusSeconds(60));
        Files.setLastModifiedTime(old, FileTime.from(now.minusSeconds(60L * 60L * 24L * 40L)));
        Files.setLastModifiedTime(middle, FileTime.from(now.minusSeconds(60)));
        Files.setLastModifiedTime(newest, FileTime.from(now));

        MapTileCachePruneResult result = cache.prune(new UcsConfigSnapshot.MapCachePolicy(1, 30, 64, 512), now);

        assertEquals(3, result.scannedFiles());
        assertEquals(2, result.deletedFiles());
        assertFalse(Files.exists(old));
        assertFalse(Files.exists(middle));
        assertTrue(Files.exists(newest));
    }
}
