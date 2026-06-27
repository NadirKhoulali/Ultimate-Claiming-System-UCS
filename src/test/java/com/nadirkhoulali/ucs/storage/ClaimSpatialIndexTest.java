package com.nadirkhoulali.ucs.storage;

import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClaimSpatialIndexTest {
    @Test
    void rebuildIndexesClaimChunks() {
        Claim claim = ClaimFixtures.claimAt(3, -8);

        ClaimSpatialIndex index = ClaimSpatialIndex.rebuild(List.of(claim));

        assertEquals(claim.id(), index.findClaimId(new ChunkKey("minecraft:overworld", 3, -8)).orElseThrow());
    }

    @Test
    void rejectsOverlappingChunks() {
        Claim first = ClaimFixtures.claimAt(0, 0);
        Claim second = ClaimFixtures.claimAt(0, 0);

        assertThrows(ClaimRepositoryException.class, () -> ClaimSpatialIndex.rebuild(List.of(first, second)));
    }
}
