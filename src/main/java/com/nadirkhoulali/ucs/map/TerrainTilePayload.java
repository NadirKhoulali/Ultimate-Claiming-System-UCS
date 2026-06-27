package com.nadirkhoulali.ucs.map;

import com.nadirkhoulali.ucs.core.model.MapTileKey;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public record TerrainTilePayload(
        MapTileKey key,
        int size,
        int[] argb
) {
    public static final int MAGIC = 0x55435452;

    public TerrainTilePayload {
        Objects.requireNonNull(key, "key");
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        Objects.requireNonNull(argb, "argb");
        if (argb.length != size * size) {
            throw new IllegalArgumentException("argb length must equal size * size");
        }
        argb = Arrays.copyOf(argb, argb.length);
    }

    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC);
                output.writeUTF(key.dimension());
                output.writeInt(key.zoom());
                output.writeInt(key.tileX());
                output.writeInt(key.tileZ());
                output.writeInt(size);
                for (int color : argb) {
                    output.writeInt(color);
                }
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new MapTileCacheException("Failed to encode terrain tile " + key.storageKey(), exception);
        }
    }

    public static TerrainTilePayload decode(byte[] payload) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            int magic = input.readInt();
            if (magic != MAGIC) {
                throw new IllegalArgumentException("payload magic mismatch");
            }
            MapTileKey key = new MapTileKey(input.readUTF(), input.readInt(), input.readInt(), input.readInt());
            int size = input.readInt();
            int[] argb = new int[size * size];
            for (int i = 0; i < argb.length; i++) {
                argb[i] = input.readInt();
            }
            return new TerrainTilePayload(key, size, argb);
        } catch (IOException exception) {
            throw new MapTileCacheException("Failed to decode terrain tile payload", exception);
        }
    }

    public int colorAt(int x, int z) {
        if (x < 0 || x >= size || z < 0 || z >= size) {
            throw new IllegalArgumentException("pixel coordinates must be inside the tile");
        }
        return argb[z * size + x];
    }

    @Override
    public int[] argb() {
        return Arrays.copyOf(argb, argb.length);
    }
}
