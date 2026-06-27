package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.internal.DefaultUcsClaimService;
import com.nadirkhoulali.ucs.config.UcsConfigDefaults;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.OwnerType;
import com.nadirkhoulali.ucs.storage.SavedDataClaimRepository;
import com.nadirkhoulali.ucs.storage.UcsClaimsSavedData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimSaleServiceTest {
    private final ClaimCreationService creationService = new ClaimCreationService();
    private final ClaimSaleService saleService = new ClaimSaleService();

    @Test
    void ownerCanListAndCancelClaimSale() {
        ClaimHarness harness = new ClaimHarness(defaultConfig());
        UUID owner = UUID.randomUUID();
        harness.create(owner, chunk(0, 0));

        ClaimSaleResult listed = harness.list(owner, chunk(0, 0), BigDecimal.valueOf(250));

        assertTrue(listed.failure().isEmpty());
        assertEquals(BigDecimal.valueOf(250), listed.claim().orElseThrow().saleListing().orElseThrow().price());

        ClaimSaleResult cancelled = harness.cancel(owner, chunk(0, 0));

        assertTrue(cancelled.failure().isEmpty());
        assertTrue(cancelled.claim().orElseThrow().saleListing().isEmpty());
    }

    @Test
    void listingAboveConfiguredCapIsRejected() {
        ClaimHarness harness = new ClaimHarness(configWithMaxSale(100.0D));
        UUID owner = UUID.randomUUID();
        harness.create(owner, chunk(0, 0));

        ClaimSaleResult result = harness.list(owner, chunk(0, 0), BigDecimal.valueOf(101));

        assertEquals(ClaimSaleFailureReason.PRICE_TOO_HIGH, result.failure().orElseThrow().reason());
        assertTrue(harness.repository.findByChunk(chunk(0, 0)).orElseThrow().saleListing().isEmpty());
    }

    @Test
    void buyerCanPurchaseListedClaim() {
        ClaimHarness harness = new ClaimHarness(defaultConfig());
        FakeClaimEconomyProvider economy = new FakeClaimEconomyProvider();
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        harness.create(seller, chunk(0, 0));
        ClaimSaleResult listed = harness.list(seller, chunk(0, 0), BigDecimal.valueOf(250));

        ClaimSaleResult result = harness.buy(buyer, chunk(0, 0), listed.claim().orElseThrow().saleListing().orElseThrow().listingId(), economy);

        assertTrue(result.failure().isEmpty());
        assertEquals(OwnerType.PLAYER, result.claim().orElseThrow().owner().type());
        assertEquals(buyer, result.claim().orElseThrow().owner().playerId().orElseThrow());
        assertTrue(result.claim().orElseThrow().saleListing().isEmpty());
        assertEquals(List.of(ClaimPricingService.REF_CLAIM_SALE_PURCHASE), economy.transferReferences());
        assertEquals("transfer:" + ClaimPricingService.REF_CLAIM_SALE_PURCHASE, result.economyResult().orElseThrow().providerReference());
        assertTrue(result.auditEntry().orElseThrow().detail().contains("purchased claim"));
    }

    @Test
    void selfPurchaseIsDenied() {
        ClaimHarness harness = new ClaimHarness(defaultConfig());
        FakeClaimEconomyProvider economy = new FakeClaimEconomyProvider();
        UUID owner = UUID.randomUUID();
        harness.create(owner, chunk(0, 0));
        ClaimSaleResult listed = harness.list(owner, chunk(0, 0), BigDecimal.valueOf(250));

        ClaimSaleResult result = harness.buy(owner, chunk(0, 0), listed.claim().orElseThrow().saleListing().orElseThrow().listingId(), economy);

        assertEquals(ClaimSaleFailureReason.SELF_PURCHASE, result.failure().orElseThrow().reason());
        assertEquals(BigDecimal.ZERO, economy.transferred());
    }

    @Test
    void staleListingIdIsDeniedBeforePayment() {
        ClaimHarness harness = new ClaimHarness(defaultConfig());
        FakeClaimEconomyProvider economy = new FakeClaimEconomyProvider();
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        harness.create(seller, chunk(0, 0));
        harness.list(seller, chunk(0, 0), BigDecimal.valueOf(250));

        ClaimSaleResult result = harness.buy(buyer, chunk(0, 0), UUID.randomUUID(), economy);

        assertEquals(ClaimSaleFailureReason.STALE_LISTING, result.failure().orElseThrow().reason());
        assertEquals(BigDecimal.ZERO, economy.transferred());
    }

    private static ChunkKey chunk(int x, int z) {
        return new ChunkKey("minecraft:overworld", x, z);
    }

    private static UcsConfigSnapshot defaultConfig() {
        return configWithMaxSale(1_000_000.0D);
    }

    private static UcsConfigSnapshot configWithMaxSale(double maxSale) {
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
                new UcsConfigSnapshot.EconomyPolicy(true, 25.0D, 5.0D, 0.75D, maxSale, true),
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

        private void create(UUID owner, ChunkKey chunk) {
            creationService.createPlayerClaim(
                    repository,
                    claimService,
                    config,
                    new ClaimCreationRequest(owner, "Player", chunk, 0, Instant.EPOCH)
            );
        }

        private ClaimSaleResult list(UUID owner, ChunkKey chunk, BigDecimal price) {
            return saleService.listClaimForSale(
                    repository,
                    claimService,
                    config,
                    ClaimSaleRequest.list(owner, "Player", chunk, price, Instant.EPOCH)
            );
        }

        private ClaimSaleResult cancel(UUID owner, ChunkKey chunk) {
            return saleService.cancelSale(
                    repository,
                    claimService,
                    ClaimSaleRequest.simple(owner, "Player", chunk, Instant.EPOCH)
            );
        }

        private ClaimSaleResult buy(UUID buyer, ChunkKey chunk, UUID expectedListingId, FakeClaimEconomyProvider economy) {
            return saleService.purchaseClaim(
                    repository,
                    claimService,
                    config,
                    ClaimSaleRequest.simple(buyer, "Buyer", chunk, Instant.EPOCH).withExpectedListingId(expectedListingId),
                    economy
            );
        }
    }
}
