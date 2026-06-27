package com.nadirkhoulali.ucs.core.model;

import java.time.Instant;
import java.util.Objects;

public record ClaimArchive(
        ArchiveId id,
        Claim claim,
        Instant archivedAt,
        String reason,
        String actor,
        int dataVersion
) {
    public static final String UNKNOWN_ACTOR = "unknown";

    public ClaimArchive(ArchiveId id, Claim claim, Instant archivedAt, String reason) {
        this(id, claim, archivedAt, reason, UNKNOWN_ACTOR, 1);
    }

    public ClaimArchive {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(archivedAt, "archivedAt");
        reason = IdentifierRules.requireNonBlank(reason, "reason");
        actor = IdentifierRules.requireNonBlank(actor, "actor");
        if (dataVersion < 1) {
            throw new IllegalArgumentException("dataVersion must be positive");
        }
    }
}
