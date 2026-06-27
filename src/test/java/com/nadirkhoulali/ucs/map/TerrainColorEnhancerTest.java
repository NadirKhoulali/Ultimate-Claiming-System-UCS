package com.nadirkhoulali.ucs.map;

import org.junit.jupiter.api.Test;

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
    void foliageColorUsesBiomeTintAndDarkensCanopy() {
        int foliage = TerrainColorEnhancer.foliageSurfaceColor(0xFF00FF00, 0x33691E);

        assertNotEquals(0xFF00FF00, foliage);
        assertTrue(luminance(foliage) < luminance(0xFF00FF00));
    }

    @Test
    void reliefShadingChangesFlatMapColorsWhenHeightChanges() {
        int[] colors = new int[9];
        int[] heights = new int[]{
                72, 72, 71,
                72, 76, 70,
                71, 70, 69
        };
        for (int i = 0; i < colors.length; i++) {
            colors[i] = 0xFF777777;
        }

        TerrainColorEnhancer.applyReliefShading(colors, heights, 3, 0);

        assertNotEquals(0xFF777777, colors[4]);
    }

    private static int luminance(int color) {
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return (red * 3 + green * 4 + blue) / 8;
    }
}
