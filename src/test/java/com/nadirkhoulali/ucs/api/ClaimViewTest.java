package com.nadirkhoulali.ucs.api;

import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.storage.ClaimFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimViewTest {
    @Test
    void exposesImmutableCollections() {
        Claim claim = ClaimFixtures.claimAt(4, 4);
        ClaimView view = ClaimView.from(claim);

        assertTrue(view.chunks().contains(new ChunkKey("minecraft:overworld", 4, 4)));
        assertThrows(UnsupportedOperationException.class, () -> view.chunks().add(new ChunkKey("minecraft:overworld", 5, 5)));
        assertThrows(UnsupportedOperationException.class, () -> view.flagOverrides().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.roleAssignments().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.pendingRoleInvites().clear());
    }
}
