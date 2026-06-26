package com.nadirkhoulali.ucs.core.model;

import java.util.Objects;
import java.util.UUID;

public record PlayerOwner(UUID playerId, String lastKnownName) implements OwnerRef {
    public PlayerOwner {
        Objects.requireNonNull(playerId, "playerId");
        lastKnownName = IdentifierRules.requireNonBlank(lastKnownName, "lastKnownName");
    }

    @Override
    public OwnerType type() {
        return OwnerType.PLAYER;
    }

    @Override
    public String stableKey() {
        return "player:" + playerId;
    }
}
