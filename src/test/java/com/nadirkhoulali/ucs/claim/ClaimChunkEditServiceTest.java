package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.internal.DefaultUcsClaimService;
import com.nadirkhoulali.ucs.config.UcsConfigDefaults;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.storage.SavedDataClaimRepository;
import com.nadirkhoulali.ucs.storage.UcsClaimsSavedData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimChunkEditServiceTest {
    private final ClaimCreationService creationService = new ClaimCreationService();
    private final ClaimChunkEditService editService = new ClaimChunkEditService();

    @Test
    void addsAdjacentChunkToOwnedClaim() {
        ClaimHarness harness = new ClaimHarness(defaultConfig());
        UUID owner = UUID.randomUUID();
        harness.create(owner, chunk(0, 0), 0);

        ClaimChunkEditResult result = harness.add(owner, chunk(1, 0));

        assertTrue(result.failure().isEmpty());
        assertEquals(2, result.claims().getFirst().chunks().size());
        assertTrue(harness.claimService.findClaim(chunk(1, 0)).isPresent());
    }

    @Test
    void rejectsNonAdjacentAdd() {
        ClaimHarness harness = new ClaimHarness(defaultConfig());
        UUID owner = UUID.randomUUID();
        harness.create(owner, chunk(0, 0), 0);

        ClaimChunkEditResult result = harness.add(owner, chunk(3, 0));

        assertEquals(ClaimChunkEditFailureReason.NOT_ADJACENT, result.failure().orElseThrow().reason());
    }

    @Test
    void removeRejectsDisconnectedRemainder() {
        ClaimHarness harness = new ClaimHarness(defaultConfig());
        UUID owner = UUID.randomUUID();
        harness.create(owner, chunk(0, 0), 0);
        harness.add(owner, chunk(1, 0));
        harness.add(owner, chunk(2, 0));

        ClaimChunkEditResult result = harness.remove(owner, chunk(1, 0));

        assertEquals(ClaimChunkEditFailureReason.WOULD_SPLIT, result.failure().orElseThrow().reason());
    }

    @Test
    void splitCreatesConnectedClaimRecords() {
        ClaimHarness harness = new ClaimHarness(defaultConfig());
        UUID owner = UUID.randomUUID();
        harness.create(owner, chunk(0, 0), 0);
        harness.add(owner, chunk(1, 0));
        harness.add(owner, chunk(2, 0));

        ClaimChunkEditResult result = harness.split(owner, chunk(1, 0));

        assertTrue(result.failure().isEmpty());
        assertEquals(2, result.claims().size());
        assertEquals(2, harness.repository.claims().size());
    }

    @Test
    void mergesAdjacentClaimsWithSameOwner() {
        ClaimHarness harness = new ClaimHarness(defaultConfig());
        UUID owner = UUID.randomUUID();
        harness.create(owner, chunk(0, 0), 0);
        harness.create(owner, chunk(1, 0), 0);

        ClaimChunkEditResult result = harness.merge(owner, chunk(0, 0));

        assertTrue(result.failure().isEmpty());
        assertEquals(1, harness.repository.claims().size());
        assertEquals(2, result.claims().getFirst().chunks().size());
    }

    @Test
    void mergeIgnoresDifferentOwners() {
        ClaimHarness harness = new ClaimHarness(defaultConfig());
        harness.create(UUID.randomUUID(), chunk(0, 0), 0);
        harness.create(UUID.randomUUID(), chunk(1, 0), 0);
        UUID owner = harness.ownerAt(chunk(0, 0));

        ClaimChunkEditResult result = harness.merge(owner, chunk(0, 0));

        assertEquals(ClaimChunkEditFailureReason.NO_MERGE_TARGETS, result.failure().orElseThrow().reason());
    }

    @Test
    void addHonorsPerClaimChunkLimit() {
        ClaimHarness harness = new ClaimHarness(configWithLimits(16, 256, 1, 1));
        UUID owner = UUID.randomUUID();
        harness.create(owner, chunk(0, 0), 0);

        ClaimChunkEditResult result = harness.add(owner, chunk(1, 0));

        assertEquals(ClaimChunkEditFailureReason.CLAIM_TOO_LARGE, result.failure().orElseThrow().reason());
    }

    private static ChunkKey chunk(int x, int z) {
        return new ChunkKey("minecraft:overworld", x, z);
    }

    private static UcsConfigSnapshot defaultConfig() {
        return configWithLimits(16, 256, 128, 1);
    }

    private static UcsConfigSnapshot configWithLimits(
            int maxClaimsPerPlayer,
            int maxChunksPerPlayer,
            int maxChunksPerClaim,
            int maxRadiusClaim
    ) {
        return new UcsConfigSnapshot(
                UcsConfigDefaults.CURRENT_SCHEMA_VERSION,
                true,
                new UcsConfigSnapshot.DimensionPolicy(List.of("minecraft:overworld"), List.of(), true),
                new UcsConfigSnapshot.ClaimLimitPolicy(
                        maxClaimsPerPlayer,
                        maxChunksPerPlayer,
                        maxChunksPerClaim,
                        maxRadiusClaim,
                        true
                ),
                new UcsConfigSnapshot.ClaimMetadataPolicy(48, 240),
                new UcsConfigSnapshot.ClaimTeleportPolicy(3, true, true),
                new UcsConfigSnapshot.RoleDefaults(UcsConfigDefaults.DEFAULT_ROLE_IDS, "member", "banned", false),
                new UcsConfigSnapshot.FlagDefaults(UcsConfigDefaults.DEFAULT_PROTECTION_FLAG_IDS),
                new UcsConfigSnapshot.EconomyPolicy(true, 25.0D, 5.0D, 0.75D, true),
                new UcsConfigSnapshot.MapCachePolicy(1024, 30, 64, 512),
                new UcsConfigSnapshot.AuditPolicy(true, 250, 180),
                new UcsConfigSnapshot.InactivePurgePolicy(false, 90, true),
                new UcsConfigSnapshot.CommandPolicy(
                        UcsConfigDefaults.PERMISSION_NODE_PREFIX,
                        UcsConfigDefaults.OP_FALLBACK_ENABLED
                ),
                new UcsConfigSnapshot.MessagePolicy("en_us", true)
        );
    }

    private final class ClaimHarness {
        private final SavedDataClaimRepository repository = new SavedDataClaimRepository(new UcsClaimsSavedData());
        private final DefaultUcsClaimService claimService = new DefaultUcsClaimService(repository);
        private final UcsConfigSnapshot config;

        private ClaimHarness(UcsConfigSnapshot config) {
            this.config = config;
        }

        private void create(UUID owner, ChunkKey chunk, int radius) {
            creationService.createPlayerClaim(
                    repository,
                    claimService,
                    config,
                    new ClaimCreationRequest(owner, "Nadir", chunk, radius, Instant.EPOCH)
            );
        }

        private ClaimChunkEditResult add(UUID owner, ChunkKey chunk) {
            return editService.addChunk(repository, claimService, config, request(owner, chunk));
        }

        private ClaimChunkEditResult remove(UUID owner, ChunkKey chunk) {
            return editService.removeChunk(repository, claimService, request(owner, chunk));
        }

        private ClaimChunkEditResult split(UUID owner, ChunkKey chunk) {
            return editService.splitClaim(repository, claimService, request(owner, chunk));
        }

        private ClaimChunkEditResult merge(UUID owner, ChunkKey chunk) {
            return editService.mergeAdjacentClaims(repository, claimService, config, request(owner, chunk));
        }

        private UUID ownerAt(ChunkKey chunk) {
            return repository.findByChunk(chunk)
                    .map(claim -> claim.owner().stableKey().replace("player:", ""))
                    .map(UUID::fromString)
                    .orElseThrow();
        }

        private ClaimChunkEditRequest request(UUID owner, ChunkKey chunk) {
            return new ClaimChunkEditRequest(owner, "Nadir", chunk, Instant.EPOCH);
        }
    }
}
