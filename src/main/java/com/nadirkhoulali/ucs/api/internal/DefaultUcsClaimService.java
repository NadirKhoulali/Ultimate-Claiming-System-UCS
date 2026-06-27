package com.nadirkhoulali.ucs.api.internal;

import com.nadirkhoulali.ucs.api.ClaimArchiveView;
import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.api.UcsClaimService;
import com.nadirkhoulali.ucs.api.event.UcsClaimEvent;
import com.nadirkhoulali.ucs.config.UcsCommonConfig;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.ArchiveId;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimArchive;
import com.nadirkhoulali.ucs.core.model.ClaimChunk;
import com.nadirkhoulali.ucs.core.model.ClaimId;
import com.nadirkhoulali.ucs.storage.ClaimRepository;
import com.nadirkhoulali.ucs.storage.ClaimRepositoryException;
import com.nadirkhoulali.ucs.storage.UcsClaimsSavedData;
import net.neoforged.neoforge.common.NeoForge;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class DefaultUcsClaimService implements UcsClaimService {
    private final ClaimRepository repository;
    private final Supplier<UcsConfigSnapshot> configSupplier;

    public DefaultUcsClaimService(ClaimRepository repository) {
        this(repository, UcsCommonConfig::snapshot);
    }

    public DefaultUcsClaimService(ClaimRepository repository, Supplier<UcsConfigSnapshot> configSupplier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
    }

    @Override
    public Collection<ClaimView> claims() {
        return repository.claims().stream().map(ClaimView::from).toList();
    }

    @Override
    public Collection<ClaimArchiveView> archives() {
        return repository.archives().stream().map(ClaimArchiveView::from).toList();
    }

    @Override
    public Optional<ClaimView> findClaim(ClaimId claimId) {
        return repository.findById(claimId).map(ClaimView::from);
    }

    @Override
    public Optional<ClaimView> findClaim(ChunkKey chunkKey) {
        return repository.findByChunk(chunkKey).map(ClaimView::from);
    }

    @Override
    public Optional<ClaimArchiveView> findArchive(ArchiveId archiveId) {
        return repository.findArchive(archiveId).map(ClaimArchiveView::from);
    }

    @Override
    public ClaimView saveClaim(Claim claim) {
        boolean existing = repository.findById(claim.id()).isPresent();
        ClaimView view = ClaimView.from(repository.save(claim));
        if (existing) {
            NeoForge.EVENT_BUS.post(new UcsClaimEvent.Updated(view, Instant.now()));
        } else {
            NeoForge.EVENT_BUS.post(new UcsClaimEvent.Created(view, Instant.now()));
        }
        return view;
    }

    @Override
    public Optional<ClaimArchiveView> archiveClaim(ClaimId claimId, ArchiveId archiveId, Instant archivedAt, String reason) {
        return archiveClaim(claimId, archiveId, archivedAt, reason, ClaimArchive.UNKNOWN_ACTOR);
    }

    @Override
    public Optional<ClaimArchiveView> archiveClaim(ClaimId claimId, ArchiveId archiveId, Instant archivedAt, String reason, String actor) {
        Objects.requireNonNull(archivedAt, "archivedAt");
        pruneExpiredArchives(archivedAt, configSupplier.get());
        Optional<ClaimArchiveView> archive = repository.archive(
                        claimId,
                        archiveId,
                        archivedAt,
                        reason,
                        actor,
                        UcsClaimsSavedData.STORAGE_VERSION
                )
                .map(ClaimArchiveView::from);
        archive.ifPresent(view -> NeoForge.EVENT_BUS.post(new UcsClaimEvent.Archived(view, Instant.now())));
        return archive;
    }

    @Override
    public Optional<ClaimView> restoreClaim(ArchiveId archiveId) {
        repository.findArchive(archiveId).ifPresent(archive -> validateRestorable(archive, configSupplier.get()));
        Optional<ClaimView> claim = repository.restore(archiveId).map(ClaimView::from);
        claim.ifPresent(view -> NeoForge.EVENT_BUS.post(new UcsClaimEvent.Restored(view, Instant.now())));
        return claim;
    }

    @Override
    public Optional<ClaimView> deleteClaim(ClaimId claimId) {
        Optional<ClaimView> claim = repository.delete(claimId).map(ClaimView::from);
        claim.ifPresent(view -> NeoForge.EVENT_BUS.post(new UcsClaimEvent.Deleted(view, Instant.now())));
        return claim;
    }

    private void pruneExpiredArchives(Instant now, UcsConfigSnapshot config) {
        Instant cutoff = now.minus(config.archive().retentionDays(), ChronoUnit.DAYS);
        repository.pruneArchivesBefore(cutoff);
    }

    private void validateRestorable(ClaimArchive archive, UcsConfigSnapshot config) {
        if (archive.dataVersion() > UcsClaimsSavedData.STORAGE_VERSION) {
            throw new ClaimRepositoryException(
                    "Archive data version " + archive.dataVersion()
                            + " is newer than supported version " + UcsClaimsSavedData.STORAGE_VERSION
            );
        }
        if (archive.claim().owner().stableKey().isBlank()) {
            throw new ClaimRepositoryException("Archive claim owner is invalid");
        }
        if (repository.findById(archive.claim().id()).isPresent()) {
            throw new ClaimRepositoryException("Archive claim id " + archive.claim().id().value() + " is already active");
        }

        for (ClaimChunk chunk : archive.claim().chunks()) {
            ChunkKey key = chunk.key();
            if (!dimensionAllowsClaims(config.dimensions(), key.dimension())) {
                throw new ClaimRepositoryException("Archive dimension " + key.dimension() + " is not enabled for claims");
            }
            Optional<Claim> occupyingClaim = repository.findByChunk(key);
            if (occupyingClaim.isPresent()) {
                throw new ClaimRepositoryException(
                        "Archive chunk " + key.storageKey() + " is already claimed by " + occupyingClaim.orElseThrow().id().value()
                );
            }
        }
    }

    private static boolean dimensionAllowsClaims(UcsConfigSnapshot.DimensionPolicy dimensions, String dimension) {
        return !dimensions.disabledDimensions().contains(dimension) && dimensions.enabledDimensions().contains(dimension);
    }
}
