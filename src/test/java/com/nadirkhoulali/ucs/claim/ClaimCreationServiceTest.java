package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.internal.DefaultUcsClaimService;
import com.nadirkhoulali.ucs.config.UcsConfigDefaults;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.PlayerOwner;
import com.nadirkhoulali.ucs.storage.SavedDataClaimRepository;
import com.nadirkhoulali.ucs.storage.UcsClaimsSavedData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimCreationServiceTest {
    private final ClaimCreationService service = new ClaimCreationService();

    @Test
    void createsSingleChunkClaim() {
        ClaimHarness harness = new ClaimHarness(defaultConfig());
        ClaimCreationRequest request = request(new ChunkKey("minecraft:overworld", 3, -2), 0);

        ClaimCreationResult result = harness.create(request);

        assertTrue(result.claim().isPresent());
        assertEquals(1, result.selectedChunkCount());
        assertEquals(result.claim().orElseThrow(), harness.claimService.findClaim(request.center()).orElseThrow());
        assertEquals("player:" + request.playerId(), result.auditEntry().orElseThrow().actorKey());
    }

    @Test
    void radiusClaimSelectsSquareArea() {
        ClaimHarness harness = new ClaimHarness(defaultConfig());

        ClaimCreationResult result = harness.create(request(new ChunkKey("minecraft:overworld", 0, 0), 1));

        assertTrue(result.claim().isPresent());
        assertEquals(9, result.selectedChunkCount());
    }

    @Test
    void rejectsDisabledDimension() {
        ClaimHarness harness = new ClaimHarness(defaultConfig());

        ClaimCreationResult result = harness.create(request(new ChunkKey("minecraft:the_nether", 0, 0), 0));

        assertEquals(ClaimCreationFailureReason.DIMENSION_DISABLED, result.failure().orElseThrow().reason());
    }

    @Test
    void rejectsRadiusAboveConfigLimit() {
        ClaimHarness harness = new ClaimHarness(defaultConfig());

        ClaimCreationResult result = harness.create(request(new ChunkKey("minecraft:overworld", 0, 0), 2));

        assertEquals(ClaimCreationFailureReason.RADIUS_TOO_LARGE, result.failure().orElseThrow().reason());
    }

    @Test
    void rejectsSelectionAbovePerClaimChunkLimit() {
        ClaimHarness harness = new ClaimHarness(configWithLimits(16, 256, 4, 2));

        ClaimCreationResult result = harness.create(request(new ChunkKey("minecraft:overworld", 0, 0), 1));

        assertEquals(ClaimCreationFailureReason.CLAIM_TOO_LARGE, result.failure().orElseThrow().reason());
    }

    @Test
    void rejectsOverlappingClaim() {
        ClaimHarness harness = new ClaimHarness(defaultConfig());
        harness.create(request(new ChunkKey("minecraft:overworld", 0, 0), 0));

        ClaimCreationResult result = harness.create(request(new ChunkKey("minecraft:overworld", 0, 0), 0));

        assertEquals(ClaimCreationFailureReason.OVERLAP, result.failure().orElseThrow().reason());
    }

    @Test
    void rejectsPlayerClaimLimit() {
        UUID playerId = UUID.randomUUID();
        UcsConfigSnapshot config = configWithLimits(1, 10, 9, 1);
        ClaimHarness harness = new ClaimHarness(config);
        harness.create(request(playerId, "Nadir", new ChunkKey("minecraft:overworld", 0, 0), 0));

        ClaimCreationResult result = harness.create(request(playerId, "Nadir", new ChunkKey("minecraft:overworld", 3, 0), 0));

        assertEquals(ClaimCreationFailureReason.TOO_MANY_CLAIMS, result.failure().orElseThrow().reason());
    }

    @Test
    void rejectsPlayerChunkLimit() {
        UUID playerId = UUID.randomUUID();
        UcsConfigSnapshot config = configWithLimits(3, 1, 9, 1);
        ClaimHarness harness = new ClaimHarness(config);
        harness.create(request(playerId, "Nadir", new ChunkKey("minecraft:overworld", 0, 0), 0));

        ClaimCreationResult result = harness.create(request(playerId, "Nadir", new ChunkKey("minecraft:overworld", 3, 0), 0));

        assertEquals(ClaimCreationFailureReason.TOO_MANY_CHUNKS, result.failure().orElseThrow().reason());
    }

    @Test
    void createdClaimUsesPlayerOwnerAndDefaultFlags() {
        ClaimHarness harness = new ClaimHarness(defaultConfig());
        UUID playerId = UUID.randomUUID();
        ClaimCreationRequest request = request(playerId, "Nadir", new ChunkKey("minecraft:overworld", 0, 0), 0);

        ClaimCreationResult result = harness.create(request);

        PlayerOwner owner = (PlayerOwner) harness.repository.claims().iterator().next().owner();
        assertEquals(playerId, owner.playerId());
        assertEquals("Nadir", owner.lastKnownName());
        assertEquals(UcsConfigDefaults.DEFAULT_PROTECTION_FLAG_IDS.size(), result.claim().orElseThrow().flagOverrides().size());
    }

    private ClaimCreationRequest request(ChunkKey center, int radius) {
        return request(UUID.randomUUID(), "Nadir", center, radius);
    }

    private ClaimCreationRequest request(UUID playerId, String playerName, ChunkKey center, int radius) {
        return new ClaimCreationRequest(playerId, playerName, center, radius, Instant.EPOCH);
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
                new UcsConfigSnapshot.BanPolicy(true, 48, 40),
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

        private ClaimCreationResult create(ClaimCreationRequest request) {
            return service.createPlayerClaim(repository, claimService, config, request);
        }
    }
}
