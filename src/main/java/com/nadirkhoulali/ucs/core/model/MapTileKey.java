package com.nadirkhoulali.ucs.core.model;

public record MapTileKey(String dimension, int zoom, int tileX, int tileZ) {
    public MapTileKey {
        dimension = IdentifierRules.requireResourceId(dimension, "dimension");
        if (zoom < 0 || zoom > 30) {
            throw new IllegalArgumentException("zoom must be between 0 and 30");
        }
    }

    public String storageKey() {
        return dimension + "/" + zoom + "/" + tileX + "/" + tileZ;
    }
}
