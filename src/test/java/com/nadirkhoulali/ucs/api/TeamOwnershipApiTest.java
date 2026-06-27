package com.nadirkhoulali.ucs.api;

import com.nadirkhoulali.ucs.api.internal.DefaultUcsClaimService;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimChunk;
import com.nadirkhoulali.ucs.core.model.ClaimMetadata;
import com.nadirkhoulali.ucs.core.model.TeamOwner;
import com.nadirkhoulali.ucs.storage.ClaimFixtures;
import com.nadirkhoulali.ucs.storage.SavedDataClaimRepository;
import com.nadirkhoulali.ucs.storage.UcsClaimsSavedData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeamOwnershipApiTest {
    @Test
    void apiCanCreateReadAndUpdateTeamOwnedClaim() {
        DefaultUcsClaimService service = new DefaultUcsClaimService(
                new SavedDataClaimRepository(new UcsClaimsSavedData())
        );
        Claim claim = ClaimFixtures.claimAt(0, 0, new TeamOwner("builders"));

        ClaimView created = service.saveClaim(claim);

        assertEquals("team:builders", created.owner().stableKey());
        assertEquals("builders", created.owner().teamId().orElseThrow());

        Set<ClaimChunk> updatedChunks = new LinkedHashSet<>(claim.chunks());
        updatedChunks.add(new ClaimChunk(new ChunkKey("minecraft:overworld", 1, 0)));
        Claim updatedClaim = new Claim(
                claim.id(),
                claim.owner(),
                updatedChunks,
                new ClaimMetadata("Builders Base", claim.metadata().spawnChunk(), claim.metadata().createdAt(), Instant.EPOCH.plusSeconds(1)),
                claim.roleAssignments(),
                claim.flagOverrides()
        );

        ClaimView updated = service.saveClaim(updatedClaim);

        assertEquals("team:builders", service.findClaim(claim.id()).orElseThrow().owner().stableKey());
        assertEquals(2, updated.chunks().size());
    }
}
