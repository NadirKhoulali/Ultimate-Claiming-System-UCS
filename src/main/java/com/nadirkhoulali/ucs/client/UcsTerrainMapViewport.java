package com.nadirkhoulali.ucs.client;

import com.nadirkhoulali.ucs.core.model.MapTileKey;
import com.nadirkhoulali.ucs.map.TerrainTileGenerator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class UcsTerrainMapViewport {
    public static final int MIN_ZOOM = 0;
    public static final int MAX_ZOOM = 8;
    public static final int TILE_SCREEN_SIZE = TerrainTileGenerator.TILE_SIZE;
    public static final int DEFAULT_MAX_VISIBLE_TILES = 256;

    private UcsTerrainMapViewport() {
    }

    public static int clampZoom(int zoom) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    }

    public static int tileBlockSize(int zoom) {
        return TerrainTileGenerator.TILE_SIZE * blockScale(zoom);
    }

    public static int blockScale(int zoom) {
        return 1 << clampZoom(zoom);
    }

    public static List<MapTileKey> visibleTiles(
            String dimension,
            int zoom,
            double centerBlockX,
            double centerBlockZ,
            int viewportWidth,
            int viewportHeight,
            int maxTiles
    ) {
        Objects.requireNonNull(dimension, "dimension");
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return List.of();
        }
        int clampedZoom = clampZoom(zoom);
        int scale = blockScale(clampedZoom);
        int tileBlockSize = tileBlockSize(clampedZoom);
        double halfBlocksWide = viewportWidth * scale / 2.0D;
        double halfBlocksHigh = viewportHeight * scale / 2.0D;
        int minTileX = floorTile(centerBlockX - halfBlocksWide, tileBlockSize);
        int maxTileX = floorTile(centerBlockX + halfBlocksWide, tileBlockSize);
        int minTileZ = floorTile(centerBlockZ - halfBlocksHigh, tileBlockSize);
        int maxTileZ = floorTile(centerBlockZ + halfBlocksHigh, tileBlockSize);

        List<MapTileKey> keys = new ArrayList<>();
        for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                keys.add(new MapTileKey(dimension, clampedZoom, tileX, tileZ));
            }
        }

        keys.sort(Comparator.comparingDouble(key -> tileDistanceSquared(key, centerBlockX, centerBlockZ)));
        int limit = Math.max(1, Math.min(maxTiles, DEFAULT_MAX_VISIBLE_TILES));
        if (keys.size() > limit) {
            return List.copyOf(keys.subList(0, limit));
        }
        return List.copyOf(keys);
    }

    public static TileBounds tileBounds(
            MapTileKey key,
            double centerBlockX,
            double centerBlockZ,
            int viewportLeft,
            int viewportTop,
            int viewportWidth,
            int viewportHeight
    ) {
        Objects.requireNonNull(key, "key");
        int scale = blockScale(key.zoom());
        int tileBlockSize = tileBlockSize(key.zoom());
        double viewportCenterX = viewportLeft + viewportWidth / 2.0D;
        double viewportCenterY = viewportTop + viewportHeight / 2.0D;
        double tileOriginBlockX = (double) key.tileX() * tileBlockSize;
        double tileOriginBlockZ = (double) key.tileZ() * tileBlockSize;
        int left = (int) Math.round(viewportCenterX + (tileOriginBlockX - centerBlockX) / scale);
        int top = (int) Math.round(viewportCenterY + (tileOriginBlockZ - centerBlockZ) / scale);
        return new TileBounds(left, top, TILE_SCREEN_SIZE, TILE_SCREEN_SIZE);
    }

    private static int floorTile(double blockCoordinate, int tileBlockSize) {
        return (int) Math.floor(blockCoordinate / tileBlockSize);
    }

    private static double tileDistanceSquared(MapTileKey key, double centerBlockX, double centerBlockZ) {
        int tileBlockSize = tileBlockSize(key.zoom());
        double tileCenterX = ((double) key.tileX() * tileBlockSize) + tileBlockSize / 2.0D;
        double tileCenterZ = ((double) key.tileZ() * tileBlockSize) + tileBlockSize / 2.0D;
        double dx = tileCenterX - centerBlockX;
        double dz = tileCenterZ - centerBlockZ;
        return dx * dx + dz * dz;
    }

    public record TileBounds(int left, int top, int width, int height) {
        public int right() {
            return left + width;
        }

        public int bottom() {
            return top + height;
        }
    }
}
