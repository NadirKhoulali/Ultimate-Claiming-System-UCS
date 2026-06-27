package com.nadirkhoulali.ucs.api.internal;

import com.nadirkhoulali.ucs.api.ClaimArchiveView;
import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.api.UcsClaimService;
import com.nadirkhoulali.ucs.api.event.UcsClaimEvent;
import com.nadirkhoulali.ucs.core.model.ArchiveId;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimId;
import com.nadirkhoulali.ucs.storage.ClaimRepository;
import net.neoforged.neoforge.common.NeoForge;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public final class DefaultUcsClaimService implements UcsClaimService {
    private final ClaimRepository repository;

    public DefaultUcsClaimService(ClaimRepository repository) {
        this.repository = repository;
    }

    @Override
    public Collection<ClaimView> claims() {
        return repository.claims().stream().map(ClaimView::from).toList();
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
        Optional<ClaimArchiveView> archive = repository.archive(claimId, archiveId, archivedAt, reason)
                .map(ClaimArchiveView::from);
        archive.ifPresent(view -> NeoForge.EVENT_BUS.post(new UcsClaimEvent.Archived(view, Instant.now())));
        return archive;
    }

    @Override
    public Optional<ClaimView> restoreClaim(ArchiveId archiveId) {
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
}
