package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.internal.DefaultUcsClaimService;
import com.nadirkhoulali.ucs.config.UcsConfigDefaults;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.ClaimTaxLedgerStatus;
import com.nadirkhoulali.ucs.core.model.ClaimTaxState;
import com.nadirkhoulali.ucs.storage.SavedDataClaimRepository;
import com.nadirkhoulali.ucs.storage.UcsClaimsSavedData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimTaxServiceTest {
    private final ClaimCreationService creationService = new ClaimCreationService();
    private final ClaimTaxService taxService = new ClaimTaxService();

    @Test
    void calculatorUsesBaseAndPerChunkFormula() {
        ClaimHarness harness = new ClaimHarness(taxConfig(true, 24, 0, 10.0D, 2.5D, 64));
        UUID owner = UUID.randomUUID();
        harness.create(owner, chunk(0, 0), 0);

        BigDecimal tax = taxService.calculateTax(harness.config, harness.repository.findByChunk(chunk(0, 0)).orElseThrow());

        assertEquals(BigDecimal.valueOf(12.5D), tax);
    }

    @Test
    void duePlayerClaimChargesOwnerAndWritesPaidSinkLedgerEntry() {
        ClaimHarness harness = new ClaimHarness(taxConfig(true, 24, 0, 10.0D, 2.0D, 64));
        FakeClaimEconomyProvider economy = new FakeClaimEconomyProvider();
        UUID owner = UUID.randomUUID();
        Instant now = Instant.EPOCH.plusSeconds(100);
        harness.create(owner, chunk(0, 0), 0);

        ClaimTaxBatchResult result = taxService.processDueTaxes(harness.repository, harness.config, economy, now, 64);

        assertEquals(1, result.billedClaims());
        assertEquals(1, result.paidClaims());
        assertEquals(BigDecimal.valueOf(12.0D), economy.charged());
        assertEquals(ClaimTaxLedgerStatus.PAID, result.ledgerEntries().getFirst().status());
        assertTrue(result.ledgerEntries().getFirst().reference().startsWith(ClaimPricingService.REF_CLAIM_TAX + ":"));
        ClaimTaxState state = harness.repository.taxStates().iterator().next();
        assertEquals(now.plus(Duration.ofHours(24)), state.nextDueAt());
        assertEquals(BigDecimal.ZERO, state.outstandingDebt());
    }

    @Test
    void failedPaymentStartsNonpaymentStateWithoutDeletingClaim() {
        ClaimHarness harness = new ClaimHarness(taxConfig(true, 24, 0, 10.0D, 2.0D, 64));
        FakeClaimEconomyProvider economy = new FakeClaimEconomyProvider();
        economy.failCharge();
        UUID owner = UUID.randomUUID();
        Instant now = Instant.EPOCH.plusSeconds(100);
        harness.create(owner, chunk(0, 0), 0);

        ClaimTaxBatchResult result = taxService.processDueTaxes(harness.repository, harness.config, economy, now, 64);

        assertEquals(1, result.failedClaims());
        assertEquals(ClaimTaxLedgerStatus.FAILED, result.ledgerEntries().getFirst().status());
        ClaimTaxState state = harness.repository.taxStates().iterator().next();
        assertEquals(1, state.missedPayments());
        assertEquals(BigDecimal.valueOf(12.0D), state.outstandingDebt());
        assertTrue(state.delinquentSince().isPresent());
        assertTrue(harness.repository.findByChunk(chunk(0, 0)).isPresent());
    }

    @Test
    void processingIsBoundedByMaxClaims() {
        ClaimHarness harness = new ClaimHarness(taxConfig(true, 24, 0, 1.0D, 0.0D, 2));
        FakeClaimEconomyProvider economy = new FakeClaimEconomyProvider();
        harness.create(UUID.randomUUID(), chunk(0, 0), 0);
        harness.create(UUID.randomUUID(), chunk(2, 0), 0);
        harness.create(UUID.randomUUID(), chunk(4, 0), 0);

        ClaimTaxBatchResult result = taxService.processDueTaxes(
                harness.repository,
                harness.config,
                economy,
                Instant.EPOCH.plusSeconds(100),
                2
        );

        assertEquals(2, result.scannedClaims());
        assertEquals(2, result.billedClaims());
        assertEquals(2, harness.repository.taxLedgerEntries().size());
    }

    @Test
    void previewInitializesUpcomingScheduleWithoutSavingState() {
        ClaimHarness harness = new ClaimHarness(taxConfig(true, 24, 12, 5.0D, 1.0D, 64));
        harness.create(UUID.randomUUID(), chunk(0, 0), 0);
        Instant now = Instant.EPOCH.plusSeconds(100);

        List<ClaimTaxPreview> previews = taxService.previewUpcomingTaxes(harness.repository, harness.config, now, 5);

        assertEquals(1, previews.size());
        assertEquals(now.plus(Duration.ofHours(12)), previews.getFirst().dueAt());
        assertEquals(BigDecimal.valueOf(6.0D), previews.getFirst().amount());
        assertTrue(harness.repository.taxStates().isEmpty());
    }

    private static ChunkKey chunk(int x, int z) {
        return new ChunkKey("minecraft:overworld", x, z);
    }

    private static UcsConfigSnapshot taxConfig(
            boolean enabled,
            int intervalHours,
            int initialDelayHours,
            double base,
            double perChunk,
            int maxClaimsPerTick
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
                new UcsConfigSnapshot.ClaimTaxPolicy(enabled, intervalHours, initialDelayHours, base, perChunk, maxClaimsPerTick, 24),
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

        private void create(UUID owner, ChunkKey chunk, int radius) {
            creationService.createPlayerClaim(
                    repository,
                    claimService,
                    config,
                    new ClaimCreationRequest(owner, "Owner", chunk, radius, Instant.EPOCH)
            );
        }
    }
}
