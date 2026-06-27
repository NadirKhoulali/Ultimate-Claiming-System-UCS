package com.nadirkhoulali.ucs.map;

import com.nadirkhoulali.ucs.core.model.OwnerType;

import java.util.List;
import java.util.Objects;

public record ClaimMapOverlayEntry(
        String claimId,
        String displayName,
        String ownerKey,
        OwnerType ownerType,
        ClaimMapOverlayRelation relation,
        List<ClaimMapOverlayChunk> chunks,
        boolean forSale,
        boolean leased,
        int fillColor,
        int borderColor,
        int saleAccentColor,
        int leaseAccentColor
) {
    public ClaimMapOverlayEntry {
        claimId = requireText(claimId, "claimId");
        displayName = requireText(displayName, "displayName");
        ownerKey = requireText(ownerKey, "ownerKey");
        Objects.requireNonNull(ownerType, "ownerType");
        Objects.requireNonNull(relation, "relation");
        chunks = List.copyOf(Objects.requireNonNull(chunks, "chunks"));
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be nonblank");
        }
        return value.trim();
    }
}
