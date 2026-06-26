package com.nadirkhoulali.ucs.core.model;

import java.util.Objects;
import java.util.UUID;

public record ArchiveId(UUID value) {
    public ArchiveId {
        Objects.requireNonNull(value, "value");
    }

    public static ArchiveId random() {
        return new ArchiveId(UUID.randomUUID());
    }
}
