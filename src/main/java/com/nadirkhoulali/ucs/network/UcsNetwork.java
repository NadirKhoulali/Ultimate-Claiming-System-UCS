package com.nadirkhoulali.ucs.network;

import com.nadirkhoulali.ucs.UcsMod;
import com.nadirkhoulali.ucs.client.UcsTerrainTileClientCache;
import com.nadirkhoulali.ucs.config.UcsCommonConfig;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.MapTileKey;
import com.nadirkhoulali.ucs.map.FileMapTileCache;
import com.nadirkhoulali.ucs.map.LoadedServerTerrainChunkSampler;
import com.nadirkhoulali.ucs.map.MapTileCacheReadResult;
import com.nadirkhoulali.ucs.map.MapTileCacheReadStatus;
import com.nadirkhoulali.ucs.map.TerrainTileGenerationResult;
import com.nadirkhoulali.ucs.map.TerrainTileResponseStatus;
import com.nadirkhoulali.ucs.map.TerrainTileStreamResponse;
import com.nadirkhoulali.ucs.service.UcsServices;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class UcsNetwork {
    private static final String NETWORK_VERSION = "1";
    private static final String MAP_CACHE_DIR = "map-cache";

    private UcsNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event, UcsServices services) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToServer(TerrainTileRequestPayload.TYPE, TerrainTileRequestPayload.STREAM_CODEC, (payload, context) -> handleTileRequest(payload, context, services));
        registrar.playToServer(TerrainTileCancelPayload.TYPE, TerrainTileCancelPayload.STREAM_CODEC, (payload, context) -> handleTileCancel(payload, context, services));
        registrar.playToClient(TerrainTileResponsePayload.TYPE, TerrainTileResponsePayload.STREAM_CODEC, (payload, context) -> UcsTerrainTileClientCache.accept(payload));
    }

    private static void handleTileCancel(TerrainTileCancelPayload payload, IPayloadContext context, UcsServices services) {
        if (context.player() instanceof ServerPlayer player) {
            services.terrainTileStreams().cancel(player.getUUID(), payload.requestId());
        }
    }

    private static void handleTileRequest(TerrainTileRequestPayload payload, IPayloadContext context, UcsServices services) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        UcsConfigSnapshot config = UcsCommonConfig.snapshot();
        FileMapTileCache cache = new FileMapTileCache(mapCacheRoot(player));
        List<TerrainTileStreamResponse> responses = services.terrainTileStreams().stream(
                player.getUUID(),
                payload.requestId(),
                payload.keys(),
                config.mapCache(),
                (requestId, key) -> resolveTile(player, services, cache, config, requestId, key)
        );
        responses.stream()
                .map(TerrainTileResponsePayload::from)
                .forEach(response -> PacketDistributor.sendToPlayer(player, response));
    }

    private static TerrainTileStreamResponse resolveTile(
            ServerPlayer player,
            UcsServices services,
            FileMapTileCache cache,
            UcsConfigSnapshot config,
            int requestId,
            MapTileKey key
    ) {
        try {
            MapTileCacheReadResult cached = cache.read(key);
            if (cached.status() == MapTileCacheReadStatus.HIT) {
                return TerrainTileStreamResponse.payload(requestId, key, TerrainTileResponseStatus.HIT, cached.payloadBytes(), "cache hit");
            }

            Optional<ServerLevel> level = levelFor(player.server, key.dimension());
            if (level.isEmpty()) {
                return TerrainTileStreamResponse.payload(requestId, key, TerrainTileResponseStatus.PLACEHOLDER, new byte[0], "dimension unavailable");
            }

            TerrainTileGenerationResult generated = services.terrainTiles().generate(
                    key,
                    new LoadedServerTerrainChunkSampler(level.orElseThrow()),
                    cache,
                    Math.max(1, Math.min(64, config.mapCache().maxGlobalTileJobs())),
                    Instant.now()
            );
            MapTileCacheReadResult generatedTile = cache.read(key);
            if (generatedTile.status() != MapTileCacheReadStatus.HIT) {
                return TerrainTileStreamResponse.payload(requestId, key, TerrainTileResponseStatus.PLACEHOLDER, new byte[0], "generated tile unavailable");
            }
            TerrainTileResponseStatus status = generated.knownPixels() == 0
                    ? TerrainTileResponseStatus.PLACEHOLDER
                    : TerrainTileResponseStatus.GENERATED;
            return TerrainTileStreamResponse.payload(requestId, key, status, generatedTile.payloadBytes(), generated.status().name());
        } catch (RuntimeException exception) {
            UcsMod.LOGGER.warn("Failed to resolve UCS terrain tile {}", key.storageKey(), exception);
            return TerrainTileStreamResponse.payload(requestId, key, TerrainTileResponseStatus.ERROR, new byte[0], exception.getClass().getSimpleName());
        }
    }

    private static Path mapCacheRoot(ServerPlayer player) {
        return player.server.getWorldPath(LevelResource.ROOT).resolve(UcsMod.MOD_ID).resolve(MAP_CACHE_DIR);
    }

    private static Optional<ServerLevel> levelFor(net.minecraft.server.MinecraftServer server, String dimension) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension));
        return Optional.ofNullable(server.getLevel(key));
    }
}
