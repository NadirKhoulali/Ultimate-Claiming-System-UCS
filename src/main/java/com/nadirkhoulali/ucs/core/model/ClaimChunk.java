package com.nadirkhoulali.ucs.core.model;

import java.util.Objects;

public record ClaimChunk(ChunkKey key) {
    public ClaimChunk {
        Objects.requireNonNull(key, "key");
    }
}
