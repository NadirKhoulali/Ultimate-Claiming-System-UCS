package com.nadirkhoulali.ucs.client;

import com.nadirkhoulali.ucs.core.model.MapTileKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UcsTerrainMapViewportTest {
    @Test
    void visibleTilesIncludeCenterTileAndRespectLimit() {
        List<MapTileKey> visible = UcsTerrainMapViewport.visibleTiles(
                "minecraft:overworld",
                0,
                64.0D,
                64.0D,
                512,
                512,
                4
        );

        assertEquals(4, visible.size());
        assertTrue(visible.contains(new MapTileKey("minecraft:overworld", 0, 0, 0)));
    }

    @Test
    void negativeCoordinatesUseFloorTileMath() {
        List<MapTileKey> visible = UcsTerrainMapViewport.visibleTiles(
                "minecraft:overworld",
                0,
                -1.0D,
                -1.0D,
                1,
                1,
                8
        );

        assertTrue(visible.contains(new MapTileKey("minecraft:overworld", 0, -1, -1)));
    }

    @Test
    void tileBoundsScaleByZoom() {
        UcsTerrainMapViewport.TileBounds bounds = UcsTerrainMapViewport.tileBounds(
                new MapTileKey("minecraft:overworld", 2, 1, 1),
                512.0D,
                512.0D,
                10,
                20,
                256,
                256
        );

        assertEquals(138, bounds.left());
        assertEquals(148, bounds.top());
        assertEquals(UcsTerrainMapViewport.TILE_SCREEN_SIZE, bounds.width());
    }
}
