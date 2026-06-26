package com.nadirkhoulali.ucs.core.model;

import java.util.Objects;
import java.util.UUID;

public record ClaimId(UUID value) {
    public ClaimId {
        Objects.requireNonNull(value, "value");
    }

    public static ClaimId random() {
        return new ClaimId(UUID.randomUUID());
    }
}
