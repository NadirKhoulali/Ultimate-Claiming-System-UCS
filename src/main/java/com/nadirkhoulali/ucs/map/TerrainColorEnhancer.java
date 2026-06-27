package com.nadirkhoulali.ucs.map;

import java.util.Objects;

public final class TerrainColorEnhancer {
    private static final int DEFAULT_WATER_TINT = 0xFF2F66B5;

    private TerrainColorEnhancer() {
    }

    public static int waterSurfaceColor(int biomeWaterColor, int depth) {
        int water = opaque(biomeWaterColor);
        int corrected = blend(water, DEFAULT_WATER_TINT, 0.55D);
        int clampedDepth = Math.max(0, Math.min(depth, 24));
        double depthShade = 0.96D - clampedDepth * 0.010D;
        if (depth <= 2) {
            corrected = blend(corrected, 0xFF6FAED6, 0.14D);
        }
        return multiply(corrected, depthShade);
    }

    public static int grassSurfaceColor(int mapColor, int biomeGrassColor) {
        return multiply(blend(opaque(mapColor), opaque(biomeGrassColor), 0.58D), 0.97D);
    }

    public static int foliageSurfaceColor(int mapColor, int biomeFoliageColor) {
        return multiply(blend(opaque(mapColor), opaque(biomeFoliageColor), 0.68D), 0.82D);
    }

    public static void applyReliefShading(int[] colors, int[] heights, int size, int zoom) {
        Objects.requireNonNull(colors, "colors");
        Objects.requireNonNull(heights, "heights");
        if (size <= 0 || colors.length != size * size || heights.length != size * size) {
            throw new IllegalArgumentException("colors and heights must match size");
        }

        int[] shaded = colors.clone();
        double zoomFade = Math.max(0.45D, 1.0D - Math.max(0, zoom) * 0.07D);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int index = z * size + x;
                int centerHeight = heights[index];
                if (centerHeight == TerrainChunkSnapshot.UNKNOWN_HEIGHT) {
                    continue;
                }

                int northwest = heightAt(heights, size, x - 1, z - 1, centerHeight);
                int southeast = heightAt(heights, size, x + 1, z + 1, centerHeight);
                int north = heightAt(heights, size, x, z - 1, centerHeight);
                int south = heightAt(heights, size, x, z + 1, centerHeight);
                int west = heightAt(heights, size, x - 1, z, centerHeight);
                int east = heightAt(heights, size, x + 1, z, centerHeight);

                int diagonalSlope = clamp((northwest + north + west) - (southeast + south + east), -24, 24);
                int highNeighbor = Math.max(Math.max(north, south), Math.max(west, east));
                int lowNeighbor = Math.min(Math.min(north, south), Math.min(west, east));
                int localRelief = Math.min(18, highNeighbor - lowNeighbor);
                int averageNeighbor = (north + south + west + east) / 4;

                double light = 1.0D + diagonalSlope * 0.014D * zoomFade;
                if (centerHeight > averageNeighbor + 1) {
                    light += Math.min(0.12D, (centerHeight - averageNeighbor) * 0.010D) * zoomFade;
                } else if (centerHeight < averageNeighbor - 1) {
                    light -= Math.min(0.16D, (averageNeighbor - centerHeight) * 0.014D) * zoomFade;
                }
                light -= localRelief * 0.004D * zoomFade;
                shaded[index] = multiply(colors[index], clamp(light, 0.68D, 1.28D));
            }
        }

        System.arraycopy(shaded, 0, colors, 0, colors.length);
    }

    static int blend(int first, int second, double secondWeight) {
        double weight = clamp(secondWeight, 0.0D, 1.0D);
        double inverse = 1.0D - weight;
        int alpha = channel(first, 24);
        int red = (int) Math.round(channel(first, 16) * inverse + channel(second, 16) * weight);
        int green = (int) Math.round(channel(first, 8) * inverse + channel(second, 8) * weight);
        int blue = (int) Math.round(channel(first, 0) * inverse + channel(second, 0) * weight);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    static int multiply(int color, double factor) {
        double clamped = clamp(factor, 0.0D, 2.0D);
        int alpha = channel(color, 24);
        int red = clamp((int) Math.round(channel(color, 16) * clamped), 0, 255);
        int green = clamp((int) Math.round(channel(color, 8) * clamped), 0, 255);
        int blue = clamp((int) Math.round(channel(color, 0) * clamped), 0, 255);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int heightAt(int[] heights, int size, int x, int z, int fallback) {
        if (x < 0 || x >= size || z < 0 || z >= size) {
            return fallback;
        }
        int height = heights[z * size + x];
        return height == TerrainChunkSnapshot.UNKNOWN_HEIGHT ? fallback : height;
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    private static int channel(int color, int shift) {
        return (color >>> shift) & 0xFF;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
