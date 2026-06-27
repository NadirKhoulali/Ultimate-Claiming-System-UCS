package com.nadirkhoulali.ucs.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.nadirkhoulali.ucs.UcsMod;
import com.nadirkhoulali.ucs.core.model.MapTileKey;
import com.nadirkhoulali.ucs.map.TerrainTilePayload;
import com.nadirkhoulali.ucs.map.TerrainTileResponseStatus;
import com.nadirkhoulali.ucs.map.TerrainTileStreamResponse;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class UcsTerrainTileTextureCache implements AutoCloseable {
    private static final int DEFAULT_MAX_ENTRIES = 384;

    private final TextureManager textureManager;
    private final int maxEntries;
    private final LinkedHashMap<MapTileKey, CachedTexture> textures = new LinkedHashMap<>(64, 0.75F, true);
    private int nextTextureId = 1;
    private boolean closed;

    UcsTerrainTileTextureCache(TextureManager textureManager) {
        this(textureManager, DEFAULT_MAX_ENTRIES);
    }

    UcsTerrainTileTextureCache(TextureManager textureManager, int maxEntries) {
        this.textureManager = Objects.requireNonNull(textureManager, "textureManager");
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
    }

    TileTexture textureFor(TerrainTileStreamResponse response) {
        Objects.requireNonNull(response, "response");
        if (closed || (response.status() != TerrainTileResponseStatus.HIT && response.status() != TerrainTileResponseStatus.GENERATED)) {
            return null;
        }

        CachedTexture cached = textures.get(response.key());
        if (cached != null && cached.requestId() == response.requestId() && cached.status() == response.status()) {
            return cached.texture();
        }

        byte[] payloadBytes = response.payload();
        if (payloadBytes.length == 0) {
            return null;
        }

        TerrainTilePayload payload = TerrainTilePayload.decode(payloadBytes);
        if (!payload.key().equals(response.key())) {
            return null;
        }

        TileTexture texture = upload(payload);
        CachedTexture previous = textures.put(response.key(), new CachedTexture(response.requestId(), response.status(), texture));
        if (previous != null) {
            release(previous.texture().location());
        }
        trimToLimit();
        return texture;
    }

    private TileTexture upload(TerrainTilePayload payload) {
        NativeImage image = new NativeImage(payload.size(), payload.size(), false);
        try {
            int[] colors = payload.argb();
            for (int z = 0; z < payload.size(); z++) {
                for (int x = 0; x < payload.size(); x++) {
                    image.setPixelRGBA(x, z, argbToAbgr(colors[z * payload.size() + x]));
                }
            }

            DynamicTexture texture = new DynamicTexture(image);
            image = null;
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                    UcsMod.MOD_ID,
                    "terrain_map/" + nextTextureId++
            );
            try {
                textureManager.register(location, texture);
            } catch (RuntimeException exception) {
                texture.close();
                throw exception;
            }
            return new TileTexture(location, payload.size());
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private void trimToLimit() {
        Iterator<Map.Entry<MapTileKey, CachedTexture>> iterator = textures.entrySet().iterator();
        while (textures.size() > maxEntries && iterator.hasNext()) {
            CachedTexture eldest = iterator.next().getValue();
            iterator.remove();
            release(eldest.texture().location());
        }
    }

    private void release(ResourceLocation location) {
        textureManager.release(location);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        for (CachedTexture texture : textures.values()) {
            release(texture.texture().location());
        }
        textures.clear();
        closed = true;
    }

    static int argbToAbgr(int argb) {
        int alpha = argb & 0xFF000000;
        int red = (argb >>> 16) & 0xFF;
        int green = argb & 0x0000FF00;
        int blue = (argb & 0xFF) << 16;
        return alpha | blue | green | red;
    }

    record TileTexture(ResourceLocation location, int size) {
    }

    private record CachedTexture(int requestId, TerrainTileResponseStatus status, TileTexture texture) {
    }
}
