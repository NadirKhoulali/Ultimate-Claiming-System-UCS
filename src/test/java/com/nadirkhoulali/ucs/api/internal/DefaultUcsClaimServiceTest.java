package com.nadirkhoulali.ucs.api.internal;

import com.nadirkhoulali.ucs.api.ClaimArchiveView;
import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.core.model.ArchiveId;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.storage.ClaimFixtures;
import com.nadirkhoulali.ucs.storage.SavedDataClaimRepository;
import com.nadirkhoulali.ucs.storage.UcsClaimsSavedData;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultUcsClaimServiceTest {
    @Test
    void savesFindsArchivesAndRestoresClaims() {
        DefaultUcsClaimService service = new DefaultUcsClaimService(
                new SavedDataClaimRepository(new UcsClaimsSavedData())
        );
        Claim claim = ClaimFixtures.claimAt(8, -3);

        ClaimView saved = service.saveClaim(claim);

        assertEquals(saved, service.findClaim(claim.id()).orElseThrow());
        assertEquals(saved, service.findClaim(new ChunkKey("minecraft:overworld", 8, -3)).orElseThrow());

        ArchiveId archiveId = ArchiveId.random();
        ClaimArchiveView archive = service.archiveClaim(claim.id(), archiveId, Instant.EPOCH, "api test").orElseThrow();

        assertEquals(saved, archive.claim());
        assertTrue(service.findClaim(claim.id()).isEmpty());

        assertEquals(saved, service.restoreClaim(archiveId).orElseThrow());
        assertEquals(saved, service.deleteClaim(claim.id()).orElseThrow());
        assertTrue(service.findClaim(claim.id()).isEmpty());
    }
}
