package com.nadirkhoulali.ucs.client;

import com.nadirkhoulali.ucs.map.ClaimMapOverlayEntry;
import com.nadirkhoulali.ucs.network.ClaimOverlayResponsePayload;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class UcsClaimOverlayClientCache {
    private static final Map<OverlayRequestKey, List<ClaimMapOverlayEntry>> ENTRIES = new ConcurrentHashMap<>();

    private UcsClaimOverlayClientCache() {
    }

    public static void accept(ClaimOverlayResponsePayload payload) {
        ENTRIES.put(new OverlayRequestKey(payload.requestId(), payload.dimension()), List.copyOf(payload.entries()));
    }

    public static List<ClaimMapOverlayEntry> entries(int requestId, String dimension) {
        return ENTRIES.getOrDefault(new OverlayRequestKey(requestId, dimension), List.of());
    }

    public static void clear() {
        ENTRIES.clear();
    }

    private record OverlayRequestKey(int requestId, String dimension) {
    }
}
