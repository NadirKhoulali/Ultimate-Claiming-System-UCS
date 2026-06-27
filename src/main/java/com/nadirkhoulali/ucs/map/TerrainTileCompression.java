package com.nadirkhoulali.ucs.map;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class TerrainTileCompression {
    private TerrainTileCompression() {
    }

    public static CompressedPayload compressIfUseful(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        if (payload.length < 256) {
            return new CompressedPayload(false, payload);
        }
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(payload);
        deflater.finish();
        byte[] buffer = new byte[4096];
        ByteArrayOutputStream output = new ByteArrayOutputStream(payload.length);
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            output.write(buffer, 0, count);
        }
        deflater.end();
        byte[] compressed = output.toByteArray();
        return compressed.length + 16 < payload.length
                ? new CompressedPayload(true, compressed)
                : new CompressedPayload(false, payload);
    }

    public static byte[] decompress(byte[] payload, boolean compressed, int maxBytes) {
        Objects.requireNonNull(payload, "payload");
        if (!compressed) {
            if (payload.length > maxBytes) {
                throw new IllegalArgumentException("payload exceeds maxBytes");
            }
            return payload.clone();
        }
        Inflater inflater = new Inflater();
        inflater.setInput(payload);
        byte[] buffer = new byte[4096];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && inflater.needsInput()) {
                    break;
                }
                output.write(buffer, 0, count);
                if (output.size() > maxBytes) {
                    throw new IllegalArgumentException("decompressed payload exceeds maxBytes");
                }
            }
            return output.toByteArray();
        } catch (DataFormatException exception) {
            throw new IllegalArgumentException("invalid compressed terrain tile payload", exception);
        } finally {
            inflater.end();
        }
    }

    public record CompressedPayload(boolean compressed, byte[] bytes) {
        public CompressedPayload {
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
