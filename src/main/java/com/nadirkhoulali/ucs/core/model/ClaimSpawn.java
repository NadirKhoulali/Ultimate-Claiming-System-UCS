package com.nadirkhoulali.ucs.core.model;

import java.util.Objects;

public record ClaimSpawn(
        ChunkKey chunk,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
    public ClaimSpawn {
        Objects.requireNonNull(chunk, "chunk");
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
        requireFinite(yaw, "yaw");
        requireFinite(pitch, "pitch");

        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        if (Math.floorDiv(blockX, 16) != chunk.x() || Math.floorDiv(blockZ, 16) != chunk.z()) {
            throw new IllegalArgumentException("spawn position must be inside its chunk");
        }
    }

    private static void requireFinite(double value, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
    }
}
