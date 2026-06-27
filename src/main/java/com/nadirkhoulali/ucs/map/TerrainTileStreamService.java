package com.nadirkhoulali.ucs.map;

import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.MapTileKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TerrainTileStreamService {
    private final Map<UUID, Set<Integer>> cancelledRequests = new ConcurrentHashMap<>();

    public void cancel(UUID playerId, int requestId) {
        Objects.requireNonNull(playerId, "playerId");
        cancelledRequests.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet()).add(requestId);
    }

    public List<TerrainTileStreamResponse> stream(
            UUID playerId,
            int requestId,
            Collection<MapTileKey> keys,
            UcsConfigSnapshot.MapCachePolicy policy,
            TerrainTileResolver resolver
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(keys, "keys");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(resolver, "resolver");
        List<MapTileKey> requested = List.copyOf(keys);
        int acceptedLimit = Math.min(policy.maxTileRequestsPerPlayer(), policy.maxGlobalTileJobs());
        if (acceptedLimit <= 0) {
            acceptedLimit = 1;
        }

        List<TerrainTileStreamResponse> responses = new ArrayList<>(requested.size());
        int accepted = 0;
        for (MapTileKey key : requested) {
            if (isCancelled(playerId, requestId)) {
                responses.add(TerrainTileStreamResponse.control(
                        requestId,
                        key,
                        TerrainTileResponseStatus.CANCELLED,
                        "request cancelled"
                ));
                continue;
            }
            if (accepted >= acceptedLimit) {
                responses.add(TerrainTileStreamResponse.control(
                        requestId,
                        key,
                        TerrainTileResponseStatus.RATE_LIMITED,
                        "tile request limit exceeded"
                ));
                continue;
            }
            responses.add(resolver.resolve(requestId, key));
            accepted++;
        }
        Set<Integer> cancelled = cancelledRequests.get(playerId);
        if (cancelled != null) {
            cancelled.remove(requestId);
        }
        return List.copyOf(responses);
    }

    public boolean isCancelled(UUID playerId, int requestId) {
        return cancelledRequests.getOrDefault(playerId, Set.of()).contains(requestId);
    }

    public void clearPlayer(UUID playerId) {
        cancelledRequests.remove(playerId);
    }

    public void clear() {
        cancelledRequests.clear();
    }
}
