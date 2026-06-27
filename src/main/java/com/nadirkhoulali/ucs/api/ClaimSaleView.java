package com.nadirkhoulali.ucs.api;

import com.nadirkhoulali.ucs.core.model.ClaimSaleListing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ClaimSaleView(
        UUID listingId,
        UUID sellerPlayerId,
        String sellerName,
        BigDecimal price,
        Instant listedAt
) {
    public ClaimSaleView {
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(sellerPlayerId, "sellerPlayerId");
        Objects.requireNonNull(sellerName, "sellerName");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(listedAt, "listedAt");
    }

    static ClaimSaleView from(ClaimSaleListing listing) {
        return new ClaimSaleView(
                listing.listingId(),
                listing.sellerPlayerId(),
                listing.sellerName(),
                listing.price(),
                listing.listedAt()
        );
    }
}
