package com.nadirkhoulali.ucs.map;

import java.util.Objects;

public final class TerrainColorEnhancer {
    private static final int DEFAULT_WATER_TINT = 0xFF2B5D9D;
    private static final double LIGHT_X = -0.55D;
    private static final double LIGHT_Z = -0.62D;
    private static final double LIGHT_Y = 0.56D;

    private TerrainColorEnhancer() {
    }

    public static int waterSurfaceColor(int biomeWaterColor, int depth) {
        int water = opaque(biomeWaterColor);
        int corrected = blend(water, DEFAULT_WATER_TINT, 0.30D);
        int clampedDepth = Math.max(0, Math.min(depth, 24));
        double depthShade = 0.98D - clampedDepth * 0.012D;
        if (depth <= 2) {
            corrected = blend(corrected, 0xFF6FAED6, 0.10D);
        }
        return multiply(corrected, depthShade);
    }

    public static int grassSurfaceColor(int mapColor, int biomeGrassColor) {
        return opaque(biomeGrassColor);
    }

    public static int foliageSurfaceColor(int mapColor, int biomeFoliageColor) {
        return opaque(biomeFoliageColor);
    }

    public static void applyLocalColorBlending(int[] colors, int[] heights, int size) {
        Objects.requireNonNull(colors, "colors");
        Objects.requireNonNull(heights, "heights");
        if (size <= 0 || colors.length != size * size || heights.length != size * size) {
            throw new IllegalArgumentException("colors and heights must match size");
        }

        int[] blended = colors.clone();
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int index = z * size + x;
                int centerHeight = heights[index];
                if (centerHeight == TerrainChunkSnapshot.UNKNOWN_HEIGHT) {
                    continue;
                }

                int self = colors[index];
                int red = channel(self, 16) * 4;
                int green = channel(self, 8) * 4;
                int blue = channel(self, 0) * 4;
                int weight = 4;
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        int neighborX = x + dx;
                        int neighborZ = z + dz;
                        if (!canBlendNeighbor(colors, heights, size, self, centerHeight, neighborX, neighborZ)) {
                            continue;
                        }
                        int neighbor = colors[neighborZ * size + neighborX];
                        red += channel(neighbor, 16);
                        green += channel(neighbor, 8);
                        blue += channel(neighbor, 0);
                        weight++;
                    }
                }
                blended[index] = (channel(self, 24) << 24)
                        | ((red / weight) << 16)
                        | ((green / weight) << 8)
                        | (blue / weight);
            }
        }

        System.arraycopy(blended, 0, colors, 0, colors.length);
    }

    public static void applyReliefShading(int[] colors, int[] heights, int size, int zoom) {
        Objects.requireNonNull(colors, "colors");
        Objects.requireNonNull(heights, "heights");
        if (size <= 0 || colors.length != size * size || heights.length != size * size) {
            throw new IllegalArgumentException("colors and heights must match size");
        }

        int[] shaded = colors.clone();
        double zoomFade = Math.max(0.35D, 1.0D - Math.max(0, zoom) * 0.08D);
        double lightLength = Math.sqrt(LIGHT_X * LIGHT_X + LIGHT_Z * LIGHT_Z + LIGHT_Y * LIGHT_Y);
        double normalizedLightX = LIGHT_X / lightLength;
        double normalizedLightZ = LIGHT_Z / lightLength;
        double normalizedLightY = LIGHT_Y / lightLength;
        double flatLight = normalizedLightY;
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int index = z * size + x;
                int centerHeight = heights[index];
                if (centerHeight == TerrainChunkSnapshot.UNKNOWN_HEIGHT) {
                    continue;
                }

                int north = heightAt(heights, size, x, z - 1, centerHeight);
                int south = heightAt(heights, size, x, z + 1, centerHeight);
                int west = heightAt(heights, size, x - 1, z, centerHeight);
                int east = heightAt(heights, size, x + 1, z, centerHeight);

                int dx = clamp(east - west, -32, 32);
                int dz = clamp(south - north, -32, 32);
                double normalX = -dx * 0.085D;
                double normalZ = -dz * 0.085D;
                double normalY = 1.0D;
                double normalLength = Math.sqrt(normalX * normalX + normalZ * normalZ + normalY * normalY);
                double directionalLight = (normalX * normalizedLightX + normalZ * normalizedLightZ + normalY * normalizedLightY) / normalLength;
                int averageNeighbor = (north + south + west + east) / 4;

                double light = 1.0D + (directionalLight - flatLight) * 0.62D * zoomFade;
                light += clamp((centerHeight - averageNeighbor) * 0.006D, -0.08D, 0.08D) * zoomFade;
                shaded[index] = multiply(colors[index], clamp(light, 0.70D, 1.24D));
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

    private static boolean canBlendNeighbor(int[] colors, int[] heights, int size, int centerColor, int centerHeight, int x, int z) {
        if (x < 0 || x >= size || z < 0 || z >= size) {
            return false;
        }
        int index = z * size + x;
        int neighborHeight = heights[index];
        if (neighborHeight == TerrainChunkSnapshot.UNKNOWN_HEIGHT || Math.abs(neighborHeight - centerHeight) > 3) {
            return false;
        }
        return colorDistanceSquared(centerColor, colors[index]) <= 3600;
    }

    private static int colorDistanceSquared(int first, int second) {
        int red = channel(first, 16) - channel(second, 16);
        int green = channel(first, 8) - channel(second, 8);
        int blue = channel(first, 0) - channel(second, 0);
        return red * red + green * green + blue * blue;
    }

    private static int adjustSaturation(int color, double saturationFactor) {
        int alpha = channel(color, 24);
        int red = channel(color, 16);
        int green = channel(color, 8);
        int blue = channel(color, 0);
        int gray = (red * 30 + green * 59 + blue * 11) / 100;
        double clampedFactor = clamp(saturationFactor, 0.0D, 2.0D);
        red = clamp((int) Math.round(gray + (red - gray) * clampedFactor), 0, 255);
        green = clamp((int) Math.round(gray + (green - gray) * clampedFactor), 0, 255);
        blue = clamp((int) Math.round(gray + (blue - gray) * clampedFactor), 0, 255);
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
