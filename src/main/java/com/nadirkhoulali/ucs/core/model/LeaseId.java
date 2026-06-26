package com.nadirkhoulali.ucs.core.model;

import java.util.Objects;
import java.util.UUID;

public record LeaseId(UUID value) {
    public LeaseId {
        Objects.requireNonNull(value, "value");
    }

    public static LeaseId random() {
        return new LeaseId(UUID.randomUUID());
    }
}
