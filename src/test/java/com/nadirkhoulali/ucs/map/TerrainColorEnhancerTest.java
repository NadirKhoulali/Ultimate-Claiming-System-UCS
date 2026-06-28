package com.nadirkhoulali.ucs.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainColorEnhancerTest {
    @Test
    void waterColorUsesBiomeTintAndDepth() {
        int shallow = TerrainColorEnhancer.waterSurfaceColor(0x3F76E4, 1);
        int deep = TerrainColorEnhancer.waterSurfaceColor(0x3F76E4, 20);

        assertNotEquals(0xFF3F76E4, shallow);
        assertTrue(luminance(shallow) > luminance(deep));
    }

    @Test
    void foliageColorUsesTopDownTintDirectly() {
        int foliage = TerrainColorEnhancer.foliageSurfaceColor(0xFF00FF00, 0x33691E);

        assertEquals(0xFF33691E, foliage);
    }

    @Test
    void grassColorUsesBiomeTopDownTintDirectly() {
        int plains = TerrainColorEnhancer.grassSurfaceColor(0xFF7FB238, 0x91BD59);
        int savanna = TerrainColorEnhancer.grassSurfaceColor(0xFF7FB238, 0xBFB755);

        assertEquals(0xFF91BD59, plains);
        assertEquals(0xFFBFB755, savanna);
        assertNotEquals(plains, savanna);
        assertNotEquals(0xFF7FB238, plains);
    }

    @Test
    void plainsGrassColorStaysGreenInsteadOfDryBrown() {
        int plains = TerrainColorEnhancer.grassSurfaceColor(0xFF7FB238, 0x91BD59);

        assertTrue(green(plains) - red(plains) >= 30);
        assertTrue(green(plains) - blue(plains) >= 90);
    }

    @Test
    void localColorBlendingDoesNotTurnShallowWaterIntoSand() {
        int water = TerrainColorEnhancer.waterSurfaceColor(0x3F76E4, 1);
        int sand = 0xFFE0D6A0;
        int[] colors = new int[]{
                sand, sand, sand,
                sand, water, sand,
                sand, sand, sand
        };
        int[] heights = new int[]{
                63, 63, 63,
                63, 64, 63,
                63, 63, 63
        };

        TerrainColorEnhancer.applyLocalColorBlending(colors, heights, 3);

        assertEquals(water, colors[4]);
    }

    @Test
    void reliefShadingBrightensSlopesFacingNorthwestLight() {
        int[] colors = new int[9];
        int[] heights = new int[]{
                70, 72, 74,
                72, 74, 76,
                74, 76, 78
        };
        for (int i = 0; i < colors.length; i++) {
            colors[i] = 0xFF777777;
        }

        TerrainColorEnhancer.applyReliefShading(colors, heights, 3, 0);

        assertTrue(luminance(colors[4]) > luminance(0xFF777777));
    }

    @Test
    void reliefShadingDarkensSlopesFacingAwayFromNorthwestLight() {
        int[] colors = new int[9];
        int[] heights = new int[]{
                78, 76, 74,
                76, 74, 72,
                74, 72, 70
        };
        for (int i = 0; i < colors.length; i++) {
            colors[i] = 0xFF777777;
        }

        TerrainColorEnhancer.applyReliefShading(colors, heights, 3, 0);

        assertTrue(luminance(colors[4]) < luminance(0xFF777777));
    }

    @Test
    void localColorBlendingSmoothsSimilarTerrainButKeepsSharpEdges() {
        int[] colors = new int[]{
                0xFF607A38, 0xFF637C3A, 0xFF233388,
                0xFF617B39, 0xFF66803D, 0xFF233388,
                0xFF5F7938, 0xFF637D3B, 0xFF233388
        };
        int[] heights = new int[]{
                64, 64, 64,
                64, 64, 64,
                64, 64, 64
        };

        TerrainColorEnhancer.applyLocalColorBlending(colors, heights, 3);

        assertNotEquals(0xFF66803D, colors[4]);
        assertEquals(0xFF233388, colors[5]);
    }

    private static int luminance(int color) {
        return (red(color) * 3 + green(color) * 4 + blue(color)) / 8;
    }

    private static int red(int color) {
        return (color >>> 16) & 0xFF;
    }

    private static int green(int color) {
        return (color >>> 8) & 0xFF;
    }

    private static int blue(int color) {
        return color & 0xFF;
    }
}
