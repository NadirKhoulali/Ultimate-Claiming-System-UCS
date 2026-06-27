package com.nadirkhoulali.ucs.api;

import com.nadirkhoulali.ucs.core.model.ArchiveId;
import com.nadirkhoulali.ucs.core.model.ClaimArchive;

import java.time.Instant;

public record ClaimArchiveView(
        ArchiveId id,
        ClaimView claim,
        Instant archivedAt,
        String reason,
        String actor,
        int dataVersion
) {
    public static ClaimArchiveView from(ClaimArchive archive) {
        return new ClaimArchiveView(
                archive.id(),
                ClaimView.from(archive.claim()),
                archive.archivedAt(),
                archive.reason(),
                archive.actor(),
                archive.dataVersion()
        );
    }
}
