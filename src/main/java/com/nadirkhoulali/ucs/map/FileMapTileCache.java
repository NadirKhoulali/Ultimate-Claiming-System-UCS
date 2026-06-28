package com.nadirkhoulali.ucs.map;

import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.MapTileKey;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class FileMapTileCache {
    public static final int CURRENT_FORMAT_VERSION = 6;
    public static final String TILE_EXTENSION = ".ucstile";
    private static final int MAGIC = 0x5543534D;
    private static final int TILE_SHARD_SIZE = 256;

    private final Path root;
    private final int formatVersion;

    public FileMapTileCache(Path root) {
        this(root, CURRENT_FORMAT_VERSION);
    }

    public FileMapTileCache(Path root, int formatVersion) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        if (formatVersion <= 0) {
            throw new IllegalArgumentException("formatVersion must be positive");
        }
        this.formatVersion = formatVersion;
    }

    public Path root() {
        return root;
    }

    public int formatVersion() {
        return formatVersion;
    }

    public Path pathFor(MapTileKey key) {
        Objects.requireNonNull(key, "key");
        String[] dimension = key.dimension().split(":", 2);
        Path path = root.resolve("v" + formatVersion).resolve(safeSegment(dimension[0]));
        for (String segment : dimension[1].split("/")) {
            path = path.resolve(safeSegment(segment));
        }
        int shardX = Math.floorDiv(key.tileX(), TILE_SHARD_SIZE);
        int shardZ = Math.floorDiv(key.tileZ(), TILE_SHARD_SIZE);
        return path
                .resolve("z" + key.zoom())
                .resolve("s_" + shardX + "_" + shardZ)
                .resolve(key.tileX() + "_" + key.tileZ() + TILE_EXTENSION)
                .normalize();
    }

    public MapTileCacheReadResult read(MapTileKey key) {
        Objects.requireNonNull(key, "key");
        Path path = pathFor(key);
        if (!Files.exists(path)) {
            return MapTileCacheReadResult.miss(key, path);
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                int magic = input.readInt();
                int version = input.readInt();
                String dimension = input.readUTF();
                int zoom = input.readInt();
                int tileX = input.readInt();
                int tileZ = input.readInt();
                int payloadLength = input.readInt();
                if (magic != MAGIC || payloadLength < 0 || payloadLength > input.available()) {
                    deleteQuietly(path);
                    return MapTileCacheReadResult.corrupt(key, path);
                }
                MapTileKey storedKey = new MapTileKey(dimension, zoom, tileX, tileZ);
                if (!storedKey.equals(key)) {
                    deleteQuietly(path);
                    return MapTileCacheReadResult.corrupt(key, path);
                }
                if (version != formatVersion) {
                    deleteQuietly(path);
                    return MapTileCacheReadResult.staleVersion(key, path, version);
                }
                byte[] payload = input.readNBytes(payloadLength);
                return MapTileCacheReadResult.hit(key, path, payload);
            }
        } catch (IOException | RuntimeException exception) {
            deleteQuietly(path);
            return MapTileCacheReadResult.corrupt(key, path);
        }
    }

    public Path write(MapTileKey key, byte[] payload, Instant now) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(now, "now");
        if (payload.length == 0) {
            throw new IllegalArgumentException("payload cannot be empty");
        }
        Path path = pathFor(key);
        try {
            Files.createDirectories(path.getParent());
            Path temp = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
            try {
                Files.write(temp, encode(key, payload));
                Files.setLastModifiedTime(temp, FileTime.from(now));
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                deleteQuietly(temp);
                throw exception;
            }
            Files.setLastModifiedTime(path, FileTime.from(now));
            return path;
        } catch (IOException exception) {
            throw new MapTileCacheException("Failed to write map tile " + key.storageKey(), exception);
        }
    }

    public boolean invalidate(MapTileKey key) {
        Objects.requireNonNull(key, "key");
        return deleteQuietly(pathFor(key));
    }

    public MapTileCachePruneResult prune(UcsConfigSnapshot.MapCachePolicy policy, Instant now) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(now, "now");
        if (!Files.exists(root)) {
            return new MapTileCachePruneResult(0, 0, 0L, 0L);
        }
        List<TileFile> files = scanTiles();
        long bytesBefore = files.stream().mapToLong(TileFile::sizeBytes).sum();
        long bytesDeleted = 0L;
        int deleted = 0;
        Instant ageCutoff = now.minus(Duration.ofDays(policy.maxTileAgeDays()));
        for (TileFile file : files) {
            if (file.lastModified().isBefore(ageCutoff) && deleteQuietly(file.path())) {
                bytesDeleted += file.sizeBytes();
                deleted++;
            }
        }

        long maxBytes = policy.maxSizeMiB() * 1024L * 1024L;
        long remainingBytes = bytesBefore - bytesDeleted;
        if (remainingBytes > maxBytes) {
            for (TileFile file : files.stream()
                    .filter(file -> Files.exists(file.path()))
                    .sorted(Comparator.comparing(TileFile::lastModified))
                    .toList()) {
                if (remainingBytes <= maxBytes) {
                    break;
                }
                if (deleteQuietly(file.path())) {
                    remainingBytes -= file.sizeBytes();
                    bytesDeleted += file.sizeBytes();
                    deleted++;
                }
            }
        }
        return new MapTileCachePruneResult(files.size(), deleted, bytesBefore, bytesDeleted);
    }

    public int invalidateAll() {
        if (!Files.exists(root)) {
            return 0;
        }
        List<Path> files = scanTiles().stream().map(TileFile::path).toList();
        int deleted = 0;
        for (Path file : files) {
            if (deleteQuietly(file)) {
                deleted++;
            }
        }
        return deleted;
    }

    private List<TileFile> scanTiles() {
        List<TileFile> files = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (file.getFileName().toString().endsWith(TILE_EXTENSION)) {
                        files.add(new TileFile(file, attributes.size(), attributes.lastModifiedTime().toInstant()));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new MapTileCacheException("Failed to scan map tile cache " + root, exception);
        }
        return files;
    }

    private byte[] encode(MapTileKey key, byte[] payload) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC);
                output.writeInt(formatVersion);
                output.writeUTF(key.dimension());
                output.writeInt(key.zoom());
                output.writeInt(key.tileX());
                output.writeInt(key.tileZ());
                output.writeInt(payload.length);
                output.write(payload);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new MapTileCacheException("Failed to encode map tile " + key.storageKey(), exception);
        }
    }

    private static boolean deleteQuietly(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String safeSegment(String segment) {
        String safe = segment.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank() || safe.equals(".") || safe.equals("..")) {
            throw new IllegalArgumentException("invalid map tile path segment " + segment);
        }
        return safe;
    }

    private record TileFile(Path path, long sizeBytes, Instant lastModified) {
    }
}
