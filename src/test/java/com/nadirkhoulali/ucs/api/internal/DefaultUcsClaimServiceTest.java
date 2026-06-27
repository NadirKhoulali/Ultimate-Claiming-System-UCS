package com.nadirkhoulali.ucs.api.internal;

import com.nadirkhoulali.ucs.api.ClaimArchiveView;
import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.config.UcsConfigDefaults;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.ArchiveId;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.storage.ClaimFixtures;
import com.nadirkhoulali.ucs.storage.ClaimRepositoryException;
import com.nadirkhoulali.ucs.storage.SavedDataClaimRepository;
import com.nadirkhoulali.ucs.storage.UcsClaimsSavedData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultUcsClaimServiceTest {
    @Test
    void savesFindsArchivesAndRestoresClaims() {
        SavedDataClaimRepository repository = new SavedDataClaimRepository(new UcsClaimsSavedData());
        DefaultUcsClaimService service = service(repository, defaultConfig(365));
        Claim claim = ClaimFixtures.claimAt(8, -3);

        ClaimView saved = service.saveClaim(claim);

        assertEquals(saved, service.findClaim(claim.id()).orElseThrow());
        assertEquals(saved, service.findClaim(new ChunkKey("minecraft:overworld", 8, -3)).orElseThrow());

        ArchiveId archiveId = ArchiveId.random();
        ClaimArchiveView archive = service.archiveClaim(claim.id(), archiveId, Instant.EPOCH, "api test", "player:admin").orElseThrow();

        assertEquals(saved, archive.claim());
        assertEquals("player:admin", archive.actor());
        assertEquals(UcsClaimsSavedData.STORAGE_VERSION, archive.dataVersion());
        assertEquals(archive, service.findArchive(archiveId).orElseThrow());
        assertTrue(service.archives().contains(archive));
        assertTrue(service.findClaim(claim.id()).isEmpty());

        assertEquals(saved, service.restoreClaim(archiveId).orElseThrow());
        assertEquals(saved, service.deleteClaim(claim.id()).orElseThrow());
        assertTrue(service.findClaim(claim.id()).isEmpty());
    }

    @Test
    void restoreRejectsOccupiedArchiveChunks() {
        SavedDataClaimRepository repository = new SavedDataClaimRepository(new UcsClaimsSavedData());
        DefaultUcsClaimService service = service(repository, defaultConfig(365));
        Claim archivedClaim = ClaimFixtures.claimAt(0, 0);
        Claim occupyingClaim = ClaimFixtures.claimAt(0, 0);
        service.saveClaim(archivedClaim);
        ArchiveId archiveId = ArchiveId.random();
        service.archiveClaim(archivedClaim.id(), archiveId, Instant.EPOCH, "occupied restore test", "admin");
        service.saveClaim(occupyingClaim);

        assertThrows(ClaimRepositoryException.class, () -> service.restoreClaim(archiveId));
        assertTrue(service.findArchive(archiveId).isPresent());
    }

    @Test
    void restoreRejectsNewerArchiveDataVersion() {
        SavedDataClaimRepository repository = new SavedDataClaimRepository(new UcsClaimsSavedData());
        DefaultUcsClaimService service = service(repository, defaultConfig(365));
        Claim claim = ClaimFixtures.claimAt(1, 1);
        repository.save(claim);
        ArchiveId archiveId = ArchiveId.random();
        repository.archive(
                claim.id(),
                archiveId,
                Instant.EPOCH,
                "future version",
                "admin",
                UcsClaimsSavedData.STORAGE_VERSION + 1
        );

        assertThrows(ClaimRepositoryException.class, () -> service.restoreClaim(archiveId));
        assertTrue(service.findArchive(archiveId).isPresent());
    }

    @Test
    void archiveCreationPrunesExpiredArchives() {
        SavedDataClaimRepository repository = new SavedDataClaimRepository(new UcsClaimsSavedData());
        DefaultUcsClaimService service = service(repository, defaultConfig(1));
        Claim oldClaim = ClaimFixtures.claimAt(2, 2);
        Claim newClaim = ClaimFixtures.claimAt(3, 3);
        repository.save(oldClaim);
        repository.save(newClaim);
        ArchiveId oldArchiveId = ArchiveId.random();
        repository.archive(oldClaim.id(), oldArchiveId, Instant.EPOCH, "old", "admin", UcsClaimsSavedData.STORAGE_VERSION);

        ArchiveId newArchiveId = ArchiveId.random();
        service.archiveClaim(newClaim.id(), newArchiveId, Instant.EPOCH.plusSeconds(172_800), "new", "admin");

        assertTrue(service.findArchive(oldArchiveId).isEmpty());
        assertTrue(service.findArchive(newArchiveId).isPresent());
    }

    private static DefaultUcsClaimService service(SavedDataClaimRepository repository, UcsConfigSnapshot config) {
        return new DefaultUcsClaimService(repository, () -> config);
    }

    private static UcsConfigSnapshot defaultConfig(int archiveRetentionDays) {
        return new UcsConfigSnapshot(
                UcsConfigDefaults.CURRENT_SCHEMA_VERSION,
                true,
                new UcsConfigSnapshot.DimensionPolicy(
                        UcsConfigDefaults.ENABLED_DIMENSIONS,
                        UcsConfigDefaults.DISABLED_DIMENSIONS,
                        true
                ),
                new UcsConfigSnapshot.ClaimLimitPolicy(16, 256, 128, 5, true),
                new UcsConfigSnapshot.ClaimMetadataPolicy(48, 240),
                new UcsConfigSnapshot.ClaimTeleportPolicy(3, true, true),
                new UcsConfigSnapshot.RoleDefaults(UcsConfigDefaults.DEFAULT_ROLE_IDS, "member", "banned", false),
                new UcsConfigSnapshot.BanPolicy(true, 48, 40),
                new UcsConfigSnapshot.FlagDefaults(UcsConfigDefaults.DEFAULT_PROTECTION_FLAG_IDS),
                new UcsConfigSnapshot.ProtectionPolicy(List.of(), List.of(), UcsConfigDefaults.DEFAULT_SPECIAL_BLOCK_IDS),
                new UcsConfigSnapshot.EconomyPolicy(true, 25.0D, 5.0D, 0.75D, true),
                new UcsConfigSnapshot.MapCachePolicy(1024, 30, 64, 512),
                new UcsConfigSnapshot.AuditPolicy(true, 250, 180),
                new UcsConfigSnapshot.ArchivePolicy(archiveRetentionDays),
                new UcsConfigSnapshot.InactivePurgePolicy(false, 90, true),
                new UcsConfigSnapshot.CommandPolicy(
                        UcsConfigDefaults.PERMISSION_NODE_PREFIX,
                        UcsConfigDefaults.OP_FALLBACK_ENABLED
                ),
                new UcsConfigSnapshot.MessagePolicy("en_us", true)
        );
    }
}
