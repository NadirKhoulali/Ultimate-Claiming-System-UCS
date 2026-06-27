package com.nadirkhoulali.ucs.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UcsTerrainTileTextureCacheTest {
    @Test
    void argbColorsAreConvertedToNativeImageAbgrOrder() {
        assertEquals(0xDDCCBBAA, UcsTerrainTileTextureCache.argbToAbgr(0xDDAABBCC));
    }
}
