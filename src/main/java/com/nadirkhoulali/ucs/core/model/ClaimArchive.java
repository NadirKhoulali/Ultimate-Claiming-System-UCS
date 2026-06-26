package com.nadirkhoulali.ucs.core.model;

import java.time.Instant;
import java.util.Objects;

public record ClaimArchive(
        ArchiveId id,
        Claim claim,
        Instant archivedAt,
        String reason
) {
    public ClaimArchive {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(archivedAt, "archivedAt");
        reason = IdentifierRules.requireNonBlank(reason, "reason");
    }
}
