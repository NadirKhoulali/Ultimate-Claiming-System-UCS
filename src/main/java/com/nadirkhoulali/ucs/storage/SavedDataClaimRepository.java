package com.nadirkhoulali.ucs.storage;

import com.nadirkhoulali.ucs.core.model.ArchiveId;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimArchive;
import com.nadirkhoulali.ucs.core.model.ClaimId;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public final class SavedDataClaimRepository implements ClaimRepository {
    private final UcsClaimsSavedData savedData;

    public SavedDataClaimRepository(UcsClaimsSavedData savedData) {
        this.savedData = Objects.requireNonNull(savedData, "savedData");
    }

    @Override
    public synchronized Collection<Claim> claims() {
        return savedData.claims();
    }

    @Override
    public synchronized Collection<ClaimArchive> archives() {
        return savedData.archives();
    }

    @Override
    public synchronized Optional<Claim> findById(ClaimId id) {
        return savedData.findById(id);
    }

    @Override
    public synchronized Optional<Claim> findByChunk(ChunkKey chunkKey) {
        return savedData.findByChunk(chunkKey);
    }

    @Override
    public synchronized Claim save(Claim claim) {
        savedData.putClaim(claim);
        return claim;
    }

    @Override
    public synchronized Optional<ClaimArchive> archive(ClaimId claimId, ArchiveId archiveId, Instant archivedAt, String reason) {
        return savedData.archive(claimId, archiveId, archivedAt, reason);
    }

    @Override
    public synchronized Optional<Claim> restore(ArchiveId archiveId) {
        return savedData.restore(archiveId);
    }

    @Override
    public synchronized Optional<Claim> delete(ClaimId claimId) {
        return savedData.removeClaim(claimId);
    }

    @Override
    public synchronized void flush() {
        savedData.setDirty();
    }
}
