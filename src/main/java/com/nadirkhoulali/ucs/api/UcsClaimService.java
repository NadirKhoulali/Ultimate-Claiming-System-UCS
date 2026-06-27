package com.nadirkhoulali.ucs.api;

import com.nadirkhoulali.ucs.core.model.ArchiveId;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimId;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface UcsClaimService {
    /**
     * Threading: server-thread only. Returns immutable claim views.
     */
    Collection<ClaimView> claims();

    /**
     * Threading: server-thread only.
     */
    Optional<ClaimView> findClaim(ClaimId claimId);

    /**
     * Threading: server-thread only. Uses the repository spatial index.
     */
    Optional<ClaimView> findClaim(ChunkKey chunkKey);

    /**
     * Threading: server-thread only. Saves an immutable domain claim and emits create/update events after commit.
     */
    ClaimView saveClaim(Claim claim);

    /**
     * Threading: server-thread only. Archives a claim and emits an archive event after commit.
     */
    Optional<ClaimArchiveView> archiveClaim(ClaimId claimId, ArchiveId archiveId, Instant archivedAt, String reason);

    /**
     * Threading: server-thread only. Restores an archived claim and emits a restore event after commit.
     */
    Optional<ClaimView> restoreClaim(ArchiveId archiveId);

    /**
     * Threading: server-thread only. Deletes a claim and emits a delete event after commit.
     */
    Optional<ClaimView> deleteClaim(ClaimId claimId);
}
