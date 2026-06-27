package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.core.model.ChunkKey;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ClaimSaleRequest(
        UUID playerId,
        String playerName,
        ChunkKey chunk,
        Optional<BigDecimal> price,
        Optional<UUID> expectedListingId,
        Instant requestedAt
) {
    public ClaimSaleRequest {
        Objects.requireNonNull(playerId, "playerId");
        playerName = Objects.requireNonNull(playerName, "playerName").trim();
        Objects.requireNonNull(chunk, "chunk");
        price = Objects.requireNonNull(price, "price");
        expectedListingId = Objects.requireNonNull(expectedListingId, "expectedListingId");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }

    public static ClaimSaleRequest list(UUID playerId, String playerName, ChunkKey chunk, BigDecimal price, Instant requestedAt) {
        return new ClaimSaleRequest(playerId, playerName, chunk, Optional.of(price), Optional.empty(), requestedAt);
    }

    public static ClaimSaleRequest simple(UUID playerId, String playerName, ChunkKey chunk, Instant requestedAt) {
        return new ClaimSaleRequest(playerId, playerName, chunk, Optional.empty(), Optional.empty(), requestedAt);
    }

    public ClaimSaleRequest withExpectedListingId(UUID listingId) {
        return new ClaimSaleRequest(playerId, playerName, chunk, price, Optional.of(listingId), requestedAt);
    }
}
