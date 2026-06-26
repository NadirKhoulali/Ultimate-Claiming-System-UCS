package com.nadirkhoulali.ucs.core.model;

public record ChunkKey(String dimension, int x, int z) {
    public ChunkKey {
        dimension = IdentifierRules.requireResourceId(dimension, "dimension");
    }

    public String storageKey() {
        return dimension + ":" + x + "," + z;
    }
}
