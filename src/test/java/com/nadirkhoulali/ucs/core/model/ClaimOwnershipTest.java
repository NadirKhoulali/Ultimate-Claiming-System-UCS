package com.nadirkhoulali.ucs.core.model;

import com.nadirkhoulali.ucs.storage.ClaimFixtures;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimOwnershipTest {
    @Test
    void comparesOwnerRefsByStableKey() {
        Claim claim = ClaimFixtures.claimAt(0, 0, new TeamOwner("builders"));

        assertTrue(ClaimOwnership.isOwnedBy(claim, new TeamOwner("builders")));
        assertFalse(ClaimOwnership.isOwnedBy(claim, new ServerOwner("builders")));
    }

    @Test
    void playerOwnershipRequiresPlayerOwnerType() {
        UUID playerId = UUID.randomUUID();
        Claim playerClaim = ClaimFixtures.claimAt(0, 0, ClaimOwnership.player(playerId, "Nadir"));
        Claim teamClaim = ClaimFixtures.claimAt(1, 0, new TeamOwner("builders"));

        assertTrue(ClaimOwnership.isPlayerOwnedBy(playerClaim, playerId));
        assertFalse(ClaimOwnership.isPlayerOwnedBy(teamClaim, playerId));
    }
}
