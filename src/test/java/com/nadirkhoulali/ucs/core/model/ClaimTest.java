package com.nadirkhoulali.ucs.core.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimTest {
    @Test
    void claimRequiresAtLeastOneChunk() {
        assertThrows(IllegalArgumentException.class, () -> new Claim(
                ClaimId.random(),
                new ServerOwner("spawn"),
                Set.of(),
                ClaimMetadata.create("Spawn", Instant.EPOCH),
                Map.of(),
                Set.of()
        ));
    }

    @Test
    void claimDefensivelyCopiesCollections() {
        ClaimChunk chunk = new ClaimChunk(new ChunkKey("minecraft:overworld", 0, 0));
        Set<ClaimChunk> chunks = new HashSet<>();
        chunks.add(chunk);

        RoleId member = new RoleId("member");
        UUID playerId = UUID.randomUUID();
        Set<UUID> members = new HashSet<>();
        members.add(playerId);
        Map<RoleId, Set<UUID>> assignments = new HashMap<>();
        assignments.put(member, members);

        Claim claim = new Claim(
                ClaimId.random(),
                new ServerOwner("spawn"),
                chunks,
                ClaimMetadata.create("Spawn", Instant.EPOCH),
                assignments,
                Set.of(new FlagId("ucs:block_break"))
        );

        chunks.clear();
        members.clear();
        assignments.clear();

        assertEquals(Set.of(chunk), claim.chunks());
        assertEquals(Set.of(playerId), claim.roleAssignments().get(member));
        assertTrue(claim.contains(chunk.key()));
        assertThrows(UnsupportedOperationException.class, () -> claim.chunks().add(chunk));
    }
}
