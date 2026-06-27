package com.nadirkhoulali.ucs.core.model;

import java.util.Objects;
import java.util.UUID;

public final class ClaimOwnership {
    private ClaimOwnership() {
    }

    public static PlayerOwner player(UUID playerId, String lastKnownName) {
        return new PlayerOwner(playerId, lastKnownName);
    }

    public static boolean isOwnedBy(Claim claim, OwnerRef owner) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(owner, "owner");
        return claim.owner().stableKey().equals(owner.stableKey());
    }

    public static boolean isPlayerOwnedBy(Claim claim, UUID playerId) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(playerId, "playerId");
        return claim.owner() instanceof PlayerOwner playerOwner && playerOwner.playerId().equals(playerId);
    }
}
