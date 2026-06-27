package com.nadirkhoulali.ucs.map;

import com.nadirkhoulali.ucs.core.model.MapTileKey;

@FunctionalInterface
public interface TerrainTileResolver {
    TerrainTileStreamResponse resolve(int requestId, MapTileKey key);
}
