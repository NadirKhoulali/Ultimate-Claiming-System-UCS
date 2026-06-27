package com.nadirkhoulali.ucs.storage;

import com.nadirkhoulali.ucs.core.model.ArchiveId;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimArchive;
import com.nadirkhoulali.ucs.core.model.ClaimId;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface ClaimRepository {
    Collection<Claim> claims();

    Collection<ClaimArchive> archives();

    Optional<Claim> findById(ClaimId id);

    Optional<Claim> findByChunk(ChunkKey chunkKey);

    Claim save(Claim claim);

    Optional<ClaimArchive> archive(ClaimId claimId, ArchiveId archiveId, Instant archivedAt, String reason);

    Optional<Claim> restore(ArchiveId archiveId);

    Optional<Claim> delete(ClaimId claimId);

    void flush();
}
