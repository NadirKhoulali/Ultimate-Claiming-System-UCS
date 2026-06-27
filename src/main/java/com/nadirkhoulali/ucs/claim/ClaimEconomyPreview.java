package com.nadirkhoulali.ucs.claim;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record ClaimEconomyPreview(
        String providerId,
        String providerName,
        boolean providerAvailable,
        BigDecimal starterClaimPrice,
        BigDecimal pricePerExtraChunk,
        double refundRatio,
        BigDecimal maxClaimSalePrice,
        boolean taxEnabled,
        BigDecimal taxBaseAmount,
        BigDecimal taxPerChunkAmount,
        int openSaleListings,
        int offeredLeases,
        int activeLeases,
        int delinquentClaims,
        List<ClaimTaxPreview> upcomingTaxes
) {
    public ClaimEconomyPreview {
        providerId = Objects.requireNonNull(providerId, "providerId");
        providerName = Objects.requireNonNull(providerName, "providerName");
        Objects.requireNonNull(starterClaimPrice, "starterClaimPrice");
        Objects.requireNonNull(pricePerExtraChunk, "pricePerExtraChunk");
        Objects.requireNonNull(maxClaimSalePrice, "maxClaimSalePrice");
        Objects.requireNonNull(taxBaseAmount, "taxBaseAmount");
        Objects.requireNonNull(taxPerChunkAmount, "taxPerChunkAmount");
        upcomingTaxes = List.copyOf(Objects.requireNonNull(upcomingTaxes, "upcomingTaxes"));
        if (openSaleListings < 0 || offeredLeases < 0 || activeLeases < 0 || delinquentClaims < 0) {
            throw new IllegalArgumentException("preview counts must be nonnegative");
        }
    }
}
