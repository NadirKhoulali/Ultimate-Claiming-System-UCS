package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.api.UcsClaimService;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.AuditAction;
import com.nadirkhoulali.ucs.core.model.AuditEntry;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimId;
import com.nadirkhoulali.ucs.core.model.ClaimMetadata;
import com.nadirkhoulali.ucs.core.model.ClaimOwnership;
import com.nadirkhoulali.ucs.core.model.ClaimSpawn;
import com.nadirkhoulali.ucs.core.model.PlayerOwner;
import com.nadirkhoulali.ucs.storage.ClaimRepository;
import com.nadirkhoulali.ucs.storage.ClaimRepositoryException;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ClaimMetadataService {
    public ClaimMetadataResult renameClaim(
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            ClaimMetadataRequest request,
            String requestedName
    ) {
        Objects.requireNonNull(requestedName, "requestedName");
        String name = requestedName.trim();
        UcsConfigSnapshot.ClaimMetadataPolicy policy = config.claimMetadata();
        if (name.isEmpty()) {
            return failure(ClaimMetadataAction.RENAME, ClaimMetadataFailureReason.INVALID_NAME, "blank");
        }
        if (name.length() > policy.maxNameLength()) {
            return failure(ClaimMetadataAction.RENAME, ClaimMetadataFailureReason.INVALID_NAME, Integer.toString(policy.maxNameLength()));
        }
        return updateMetadata(
                repository,
                claimService,
                request,
                ClaimMetadataAction.RENAME,
                metadata -> new ClaimMetadata(name, metadata.description(), metadata.spawn(), metadata.createdAt(), request.requestedAt()),
                "renamed claim to " + name
        );
    }

    public ClaimMetadataResult describeClaim(
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            ClaimMetadataRequest request,
            String requestedDescription
    ) {
        Objects.requireNonNull(requestedDescription, "requestedDescription");
        String description = requestedDescription.trim();
        UcsConfigSnapshot.ClaimMetadataPolicy policy = config.claimMetadata();
        if (description.length() > policy.maxDescriptionLength()) {
            return failure(
                    ClaimMetadataAction.DESCRIBE,
                    ClaimMetadataFailureReason.INVALID_DESCRIPTION,
                    Integer.toString(policy.maxDescriptionLength())
            );
        }
        return updateMetadata(
                repository,
                claimService,
                request,
                ClaimMetadataAction.DESCRIBE,
                metadata -> new ClaimMetadata(metadata.displayName(), description, metadata.spawn(), metadata.createdAt(), request.requestedAt()),
                "updated claim description"
        );
    }

    public ClaimMetadataResult setSpawn(
            ClaimRepository repository,
            UcsClaimService claimService,
            ClaimMetadataRequest request,
            ClaimSpawn spawn
    ) {
        Objects.requireNonNull(spawn, "spawn");
        if (!spawn.chunk().equals(request.chunk())) {
            return failure(ClaimMetadataAction.SET_SPAWN, ClaimMetadataFailureReason.NO_CLAIM_AT_CHUNK, spawn.chunk().storageKey());
        }
        return updateMetadata(
                repository,
                claimService,
                request,
                ClaimMetadataAction.SET_SPAWN,
                metadata -> new ClaimMetadata(metadata.displayName(), metadata.description(), Optional.of(spawn), metadata.createdAt(), request.requestedAt()),
                "set claim spawn at " + spawn.chunk().storageKey()
        );
    }

    private ClaimMetadataResult updateMetadata(
            ClaimRepository repository,
            UcsClaimService claimService,
            ClaimMetadataRequest request,
            ClaimMetadataAction action,
            MetadataUpdater updater,
            String auditDetail
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(claimService, "claimService");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(updater, "updater");

        Optional<Claim> existing = repository.findByChunk(request.chunk());
        if (existing.isEmpty()) {
            return failure(action, ClaimMetadataFailureReason.NO_CLAIM_AT_CHUNK, request.chunk().storageKey());
        }

        PlayerOwner owner = ClaimOwnership.player(request.playerId(), request.playerName());
        Claim claim = existing.orElseThrow();
        if (!ClaimOwnership.isOwnedBy(claim, owner)) {
            return failure(action, ClaimMetadataFailureReason.NOT_OWNER, request.chunk().storageKey());
        }

        Claim updated = new Claim(
                claim.id(),
                claim.owner(),
                claim.chunks(),
                updater.apply(claim.metadata()),
                claim.roleAssignments(),
                claim.pendingRoleInvites(),
                claim.flagOverrides(),
                claim.saleListing()
        );

        try {
            ClaimView saved = claimService.saveClaim(updated);
            return ClaimMetadataResult.success(action, saved, audit(owner, request.requestedAt(), saved.id(), auditDetail));
        } catch (ClaimRepositoryException exception) {
            return failure(action, ClaimMetadataFailureReason.SAVE_FAILED, exceptionDetail(exception));
        }
    }

    private static AuditEntry audit(PlayerOwner owner, Instant requestedAt, ClaimId claimId, String detail) {
        return new AuditEntry(
                UUID.randomUUID(),
                requestedAt,
                owner.stableKey(),
                AuditAction.CLAIM_UPDATED,
                Optional.of(claimId),
                detail
        );
    }

    private static ClaimMetadataResult failure(
            ClaimMetadataAction action,
            ClaimMetadataFailureReason reason,
            String detail
    ) {
        return ClaimMetadataResult.failure(action, new ClaimMetadataFailure(reason, detail));
    }

    private static String exceptionDetail(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    @FunctionalInterface
    private interface MetadataUpdater {
        ClaimMetadata apply(ClaimMetadata metadata);
    }
}
