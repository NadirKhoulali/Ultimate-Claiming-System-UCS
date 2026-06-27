package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.internal.DefaultUcsClaimService;
import com.nadirkhoulali.ucs.config.UcsConfigDefaults;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimTaxState;
import com.nadirkhoulali.ucs.storage.SavedDataClaimRepository;
import com.nadirkhoulali.ucs.storage.UcsClaimsSavedData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimNonpaymentServiceTest {
    private final ClaimCreationService creationService = new ClaimCreationService();
    private final ClaimNonpaymentService nonpaymentService = new ClaimNonpaymentService();

    @Test
    void delinquentClaimGetsWarningTimestampBeforeGraceExpires() {
        ClaimHarness harness = new ClaimHarness(config(72, 24, 24, true, false));
        Claim claim = harness.create(UUID.randomUUID(), chunk(0, 0));
        Instant now = Instant.EPOCH.plus(Duration.ofHours(2));
        harness.saveDebt(claim, now.minus(Duration.ofHours(1)), BigDecimal.valueOf(20));

        ClaimNonpaymentResult result = nonpaymentService.processNonpayment(
                null,
                harness.repository,
                harness.claimService,
                harness.config,
                now,
                64
        );

        assertEquals(0, result.archivedClaims());
        assertTrue(harness.repository.findTaxState(claim.id()).orElseThrow().lastWarningAt().isPresent());
        assertTrue(harness.repository.findById(claim.id()).isPresent());
    }

    @Test
    void delinquentClaimArchivesAfterGraceAndPreservesDebtState() {
        ClaimHarness harness = new ClaimHarness(config(1, 24, 24, true, false));
        Claim claim = harness.create(UUID.randomUUID(), chunk(0, 0));
        Instant now = Instant.EPOCH.plus(Duration.ofHours(3));
        harness.saveDebt(claim, now.minus(Duration.ofHours(2)), BigDecimal.valueOf(20));

        ClaimNonpaymentResult result = nonpaymentService.processNonpayment(
                null,
                harness.repository,
                harness.claimService,
                harness.config,
                now,
                64
        );

        assertEquals(1, result.archivedClaims());
        assertTrue(harness.repository.findById(claim.id()).isEmpty());
        assertEquals(1, harness.repository.archives().size());
        assertEquals(BigDecimal.valueOf(20), harness.repository.findTaxState(claim.id()).orElseThrow().outstandingDebt());
    }

    @Test
    void restoreCanBeBlockedUntilDebtIsCleared() {
        ClaimHarness harness = new ClaimHarness(config(1, 24, 24, true, true));
        Claim claim = harness.create(UUID.randomUUID(), chunk(0, 0));
        harness.saveDebt(claim, Instant.EPOCH, BigDecimal.valueOf(20));

        assertTrue(nonpaymentService.hasBlockingDebt(harness.repository, claim.id(), harness.config));

        nonpaymentService.clearDebt(harness.repository, claim.id(), harness.config, Instant.EPOCH.plusSeconds(10));

        assertFalse(nonpaymentService.hasBlockingDebt(harness.repository, claim.id(), harness.config));
        assertEquals(BigDecimal.ZERO, harness.repository.findTaxState(claim.id()).orElseThrow().outstandingDebt());
    }

    @Test
    void restoreDefersDebtRetryFromRestoreTime() {
        ClaimHarness harness = new ClaimHarness(config(1, 6, 24, true, false));
        Claim claim = harness.create(UUID.randomUUID(), chunk(0, 0));
        harness.saveDebt(claim, Instant.EPOCH, BigDecimal.valueOf(20));
        Instant restoredAt = Instant.EPOCH.plus(Duration.ofHours(5));

        Optional<ClaimTaxState> updated = nonpaymentService.deferAfterRestore(
                harness.repository,
                claim.id(),
                harness.config,
                restoredAt
        );

        assertTrue(updated.isPresent());
        assertEquals(restoredAt, updated.orElseThrow().delinquentSince().orElseThrow());
        assertEquals(restoredAt.plus(Duration.ofHours(6)), updated.orElseThrow().nextDueAt());
        assertTrue(updated.orElseThrow().lastWarningAt().isEmpty());
    }

    private static ChunkKey chunk(int x, int z) {
        return new ChunkKey("minecraft:overworld", x, z);
    }

    private static UcsConfigSnapshot config(
            int graceHours,
            int retryIntervalHours,
            int warningIntervalHours,
            boolean archiveAfterGrace,
            boolean requireDebtPaidBeforeRestore
    ) {
        return new UcsConfigSnapshot(
                UcsConfigDefaults.CURRENT_SCHEMA_VERSION,
                true,
                new UcsConfigSnapshot.DimensionPolicy(List.of("minecraft:overworld"), List.of(), true),
                new UcsConfigSnapshot.ClaimLimitPolicy(16, 256, 128, 1, true),
                new UcsConfigSnapshot.ClaimMetadataPolicy(48, 240),
                new UcsConfigSnapshot.ClaimTeleportPolicy(3, true, true),
                new UcsConfigSnapshot.RoleDefaults(UcsConfigDefaults.DEFAULT_ROLE_IDS, "member", "banned", false),
                new UcsConfigSnapshot.BanPolicy(true, 48, 40),
                new UcsConfigSnapshot.FlagDefaults(UcsConfigDefaults.DEFAULT_PROTECTION_FLAG_IDS),
                new UcsConfigSnapshot.ProtectionPolicy(List.of(), List.of(), UcsConfigDefaults.DEFAULT_SPECIAL_BLOCK_IDS),
                new UcsConfigSnapshot.EconomyPolicy(true, 25.0D, 5.0D, 0.75D, 1_000_000.0D, true),
                new UcsConfigSnapshot.ClaimTaxPolicy(true, 24, 0, 10.0D, 1.0D, 64, 24),
                new UcsConfigSnapshot.NonpaymentPolicy(
                        graceHours,
                        retryIntervalHours,
                        warningIntervalHours,
                        archiveAfterGrace,
                        requireDebtPaidBeforeRestore,
                        64
                ),
                new UcsConfigSnapshot.MapCachePolicy(1024, 30, 64, 512),
                new UcsConfigSnapshot.AuditPolicy(true, 250, 180),
                new UcsConfigSnapshot.ArchivePolicy(365),
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
        private final UcsConfigSnapshot config;
        private final DefaultUcsClaimService claimService;

        private ClaimHarness(UcsConfigSnapshot config) {
            this.config = config;
            this.claimService = new DefaultUcsClaimService(repository, () -> this.config);
        }

        private Claim create(UUID owner, ChunkKey chunk) {
            var result = creationService.createPlayerClaim(
                    repository,
                    claimService,
                    config,
                    new ClaimCreationRequest(owner, "Owner", chunk, 0, Instant.EPOCH)
            );
            return repository.findById(result.claim().orElseThrow().id()).orElseThrow();
        }

        private void saveDebt(Claim claim, Instant delinquentSince, BigDecimal debt) {
            repository.saveTaxState(new ClaimTaxState(
                    claim.id(),
                    delinquentSince.plus(Duration.ofHours(config.nonpayment().retryIntervalHours())),
                    Optional.empty(),
                    1,
                    debt,
                    Optional.of(delinquentSince),
                    Optional.empty(),
                    delinquentSince
            ));
        }
    }
}
