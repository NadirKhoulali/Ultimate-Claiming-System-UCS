package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.internal.DefaultUcsClaimService;
import com.nadirkhoulali.ucs.config.UcsConfigDefaults;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimOwnership;
import com.nadirkhoulali.ucs.core.model.ClaimSpawn;
import com.nadirkhoulali.ucs.storage.ClaimFixtures;
import com.nadirkhoulali.ucs.storage.SavedDataClaimRepository;
import com.nadirkhoulali.ucs.storage.UcsClaimsSavedData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimMetadataServiceTest {
    private final ClaimMetadataService service = new ClaimMetadataService();

    @Test
    void ownerCanRenameClaimWithinConfiguredLimit() {
        UUID owner = UUID.randomUUID();
        ClaimHarness harness = new ClaimHarness(defaultConfig());
        harness.savePlayerClaim(owner, "Nadir", 0, 0);

        ClaimMetadataResult result = service.renameClaim(
                harness.repository,
                harness.claimService,
                harness.config,
                request(owner, "Nadir", chunk(0, 0)),
                "  Workshop  "
        );

        assertTrue(result.claim().isPresent());
        assertTrue(result.auditEntry().isPresent());
        assertEquals("Workshop", result.claim().orElseThrow().displayName());
        assertEquals("Workshop", harness.repository.findByChunk(chunk(0, 0)).orElseThrow().metadata().displayName());
    }

    @Test
    void rejectsOverlongName() {
        UUID owner = UUID.randomUUID();
        ClaimHarness harness = new ClaimHarness(configWithMetadataLimits(6, 32));
        harness.savePlayerClaim(owner, "Nadir", 0, 0);

        ClaimMetadataResult result = service.renameClaim(
                harness.repository,
                harness.claimService,
                harness.config,
                request(owner, "Nadir", chunk(0, 0)),
                "Too Long"
        );

        assertEquals(ClaimMetadataFailureReason.INVALID_NAME, result.failure().orElseThrow().reason());
        assertEquals("6", result.failure().orElseThrow().detail());
    }

    @Test
    void ownerCanDescribeClaimWithinConfiguredLimit() {
        UUID owner = UUID.randomUUID();
        ClaimHarness harness = new ClaimHarness(defaultConfig());
        harness.savePlayerClaim(owner, "Nadir", 0, 0);

        ClaimMetadataResult result = service.describeClaim(
                harness.repository,
                harness.claimService,
                harness.config,
                request(owner, "Nadir", chunk(0, 0)),
                "  Shared machines and storage.  "
        );

        assertEquals("Shared machines and storage.", result.claim().orElseThrow().description());
        assertEquals("Shared machines and storage.", harness.repository.findByChunk(chunk(0, 0)).orElseThrow().metadata().description());
    }

    @Test
    void ownerCanSetSpawnInsideCurrentClaimChunk() {
        UUID owner = UUID.randomUUID();
        ClaimHarness harness = new ClaimHarness(defaultConfig());
        harness.savePlayerClaim(owner, "Nadir", 0, 0);
        ClaimSpawn spawn = new ClaimSpawn(chunk(0, 0), 8.5D, 70.0D, 8.5D, 180.0F, 0.0F);

        ClaimMetadataResult result = service.setSpawn(
                harness.repository,
                harness.claimService,
                request(owner, "Nadir", chunk(0, 0)),
                spawn
        );

        assertEquals(spawn, result.claim().orElseThrow().spawn().orElseThrow());
        assertEquals(spawn, harness.repository.findByChunk(chunk(0, 0)).orElseThrow().metadata().spawn().orElseThrow());
    }

    @Test
    void rejectsMetadataUpdateFromNonOwner() {
        UUID owner = UUID.randomUUID();
        ClaimHarness harness = new ClaimHarness(defaultConfig());
        harness.savePlayerClaim(owner, "Nadir", 0, 0);

        ClaimMetadataResult result = service.renameClaim(
                harness.repository,
                harness.claimService,
                harness.config,
                request(UUID.randomUUID(), "Visitor", chunk(0, 0)),
                "Visitor Base"
        );

        assertEquals(ClaimMetadataFailureReason.NOT_OWNER, result.failure().orElseThrow().reason());
    }

    private static ClaimMetadataRequest request(UUID playerId, String playerName, ChunkKey chunk) {
        return new ClaimMetadataRequest(playerId, playerName, chunk, Instant.EPOCH.plusSeconds(5));
    }

    private static ChunkKey chunk(int x, int z) {
        return new ChunkKey("minecraft:overworld", x, z);
    }

    private static UcsConfigSnapshot defaultConfig() {
        return configWithMetadataLimits(48, 240);
    }

    private static UcsConfigSnapshot configWithMetadataLimits(int maxNameLength, int maxDescriptionLength) {
        return new UcsConfigSnapshot(
                UcsConfigDefaults.CURRENT_SCHEMA_VERSION,
                true,
                new UcsConfigSnapshot.DimensionPolicy(List.of("minecraft:overworld"), List.of(), true),
                new UcsConfigSnapshot.ClaimLimitPolicy(16, 256, 128, 5, true),
                new UcsConfigSnapshot.ClaimMetadataPolicy(maxNameLength, maxDescriptionLength),
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

    private static final class ClaimHarness {
        private final SavedDataClaimRepository repository = new SavedDataClaimRepository(new UcsClaimsSavedData());
        private final DefaultUcsClaimService claimService = new DefaultUcsClaimService(repository);
        private final UcsConfigSnapshot config;

        private ClaimHarness(UcsConfigSnapshot config) {
            this.config = config;
        }

        private void savePlayerClaim(UUID owner, String name, int chunkX, int chunkZ) {
            Claim claim = ClaimFixtures.claimAt(chunkX, chunkZ, ClaimOwnership.player(owner, name));
            claimService.saveClaim(claim);
        }
    }
}
