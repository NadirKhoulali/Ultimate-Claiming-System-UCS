package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyResult;
import com.nadirkhoulali.ucs.core.model.AuditEntry;
import com.nadirkhoulali.ucs.core.model.ChunkKey;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ClaimChunkEditResult(
        ClaimChunkEditAction action,
        List<ClaimView> claims,
        Optional<ClaimChunkEditFailure> failure,
        Optional<AuditEntry> auditEntry,
        Optional<ClaimEconomyResult> economyResult,
        Set<ChunkKey> affectedChunks
) {
    public ClaimChunkEditResult {
        Objects.requireNonNull(action, "action");
        claims = List.copyOf(claims);
        failure = Objects.requireNonNull(failure, "failure");
        auditEntry = Objects.requireNonNull(auditEntry, "auditEntry");
        economyResult = Objects.requireNonNull(economyResult, "economyResult");
        affectedChunks = Set.copyOf(Objects.requireNonNull(affectedChunks, "affectedChunks"));
        if (claims.isEmpty() == failure.isEmpty()) {
            throw new IllegalArgumentException("claim chunk edit result must contain claims or a failure");
        }
        if (claims.isEmpty() != auditEntry.isEmpty()) {
            throw new IllegalArgumentException("successful claim chunk edit must include an audit entry");
        }
    }

    public static ClaimChunkEditResult success(
            ClaimChunkEditAction action,
            List<ClaimView> claims,
            AuditEntry auditEntry,
            Set<ChunkKey> affectedChunks
    ) {
        return new ClaimChunkEditResult(action, claims, Optional.empty(), Optional.of(auditEntry), Optional.empty(), affectedChunks);
    }

    public static ClaimChunkEditResult success(
            ClaimChunkEditAction action,
            List<ClaimView> claims,
            AuditEntry auditEntry,
            ClaimEconomyResult economyResult,
            Set<ChunkKey> affectedChunks
    ) {
        return new ClaimChunkEditResult(
                action,
                claims,
                Optional.empty(),
                Optional.of(auditEntry),
                Optional.of(economyResult),
                affectedChunks
        );
    }

    public static ClaimChunkEditResult failure(
            ClaimChunkEditAction action,
            ClaimChunkEditFailure failure,
            Set<ChunkKey> affectedChunks
    ) {
        return new ClaimChunkEditResult(action, List.of(), Optional.of(failure), Optional.empty(), Optional.empty(), affectedChunks);
    }

    public static ClaimChunkEditResult failure(
            ClaimChunkEditAction action,
            ClaimChunkEditFailure failure,
            ClaimEconomyResult economyResult,
            Set<ChunkKey> affectedChunks
    ) {
        return new ClaimChunkEditResult(
                action,
                List.of(),
                Optional.of(failure),
                Optional.empty(),
                Optional.of(economyResult),
                affectedChunks
        );
    }

}
