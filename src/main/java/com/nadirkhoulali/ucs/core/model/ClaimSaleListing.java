package com.nadirkhoulali.ucs.core.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ClaimSaleListing(
        UUID listingId,
        UUID sellerPlayerId,
        String sellerName,
        BigDecimal price,
        Instant listedAt
) {
    public ClaimSaleListing {
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(sellerPlayerId, "sellerPlayerId");
        sellerName = IdentifierRules.requireNonBlank(sellerName, "sellerName");
        price = Objects.requireNonNull(price, "price");
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Sale price must be greater than zero");
        }
        Objects.requireNonNull(listedAt, "listedAt");
    }
}
