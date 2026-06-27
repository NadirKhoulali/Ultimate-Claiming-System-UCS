package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.internal.DefaultUcsClaimService;
import com.nadirkhoulali.ucs.config.UcsConfigDefaults;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimMetadata;
import com.nadirkhoulali.ucs.core.model.ClaimSaleListing;
import com.nadirkhoulali.ucs.core.model.ClaimTaxLedgerStatus;
import com.nadirkhoulali.ucs.core.model.ClaimTaxState;
import com.nadirkhoulali.ucs.core.model.EconomyAuditAction;
import com.nadirkhoulali.ucs.core.model.EconomyAuditEntry;
import com.nadirkhoulali.ucs.core.model.EconomyAuditStatus;
import com.nadirkhoulali.ucs.core.model.LeaseContract;
import com.nadirkhoulali.ucs.core.model.LeaseId;
import com.nadirkhoulali.ucs.core.model.LeaseStatus;
import com.nadirkhoulali.ucs.core.model.PlayerOwner;
import com.nadirkhoulali.ucs.core.model.RoleId;
import com.nadirkhoulali.ucs.storage.ClaimFixtures;
import com.nadirkhoulali.ucs.storage.SavedDataClaimRepository;
import com.nadirkhoulali.ucs.storage.UcsClaimsSavedData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimEconomyAdminServiceTest {
    private static final String ACTOR = "player:admin";
    private static final RoleId TENANT_ROLE = new RoleId("tenant");
    private final ClaimEconomyAdminService service = new ClaimEconomyAdminService();

    @Test
    void refundWritesSuccessfulAuditEntry() {
        ClaimHarness harness = new ClaimHarness();
        FakeClaimEconomyProvider economy = new FakeClaimEconomyProvider();
        UUID playerId = UUID.randomUUID();

        ClaimEconomyAdminResult result = service.refundPlayer(
                harness.repository,
                economy,
                ACTOR,
                playerId,
                BigDecimal.valueOf(30),
                "manual correction",
                Instant.EPOCH
        );

        assertTrue(result.success());
        assertEquals(BigDecimal.valueOf(30), economy.refunded());
        EconomyAuditEntry audit = harness.repository.economyAuditEntries().iterator().next();
        assertEquals(EconomyAuditAction.ADMIN_REFUND, audit.action());
        assertEquals(EconomyAuditStatus.SUCCESS, audit.status());
        assertTrue(audit.reference().startsWith(ClaimEconomyAdminService.REF_ADMIN_REFUND + ":"));
        assertEquals("refund:" + audit.reference(), audit.providerReference());
    }

    @Test
    void retryTaxChargesDebtClearsStateAndWritesLedgerAndAudit() {
        ClaimHarness harness = new ClaimHarness();
        FakeClaimEconomyProvider economy = new FakeClaimEconomyProvider();
        UUID owner = UUID.randomUUID();
        Claim claim = harness.savePlayerClaim(owner);
        harness.repository.saveTaxState(new ClaimTaxState(
                claim.id(),
                Instant.EPOCH.minusSeconds(60),
                Optional.empty(),
                2,
                BigDecimal.valueOf(18),
                Optional.of(Instant.EPOCH.minusSeconds(120)),
                Optional.empty(),
                Instant.EPOCH.minusSeconds(30)
        ));

        ClaimEconomyAdminResult result = service.retryTax(
                harness.repository,
                harness.config,
                economy,
                claim.id(),
                ACTOR,
                Instant.EPOCH
        );

        assertTrue(result.success());
        assertEquals(BigDecimal.valueOf(18), economy.charged());
        assertEquals(BigDecimal.ZERO, harness.repository.findTaxState(claim.id()).orElseThrow().outstandingDebt());
        assertEquals(0, harness.repository.findTaxState(claim.id()).orElseThrow().missedPayments());
        assertEquals(ClaimTaxLedgerStatus.PAID, harness.repository.taxLedgerEntries().iterator().next().status());
        EconomyAuditEntry audit = harness.repository.economyAuditEntries().iterator().next();
        assertEquals(EconomyAuditAction.TAX_RETRY, audit.action());
        assertEquals(claim.id(), audit.claimId().orElseThrow());
        assertTrue(audit.reference().startsWith(ClaimEconomyAdminService.REF_ADMIN_TAX_RETRY + ":"));
    }

    @Test
    void cancelSaleClearsListingAndAudits() {
        ClaimHarness harness = new ClaimHarness();
        Claim claim = harness.saveSaleClaim(UUID.randomUUID(), BigDecimal.valueOf(125));

        ClaimEconomyAdminResult result = service.cancelSale(
                harness.repository,
                harness.claimService,
                claim.id(),
                ACTOR,
                "stale listing",
                Instant.EPOCH.plusSeconds(10)
        );

        assertTrue(result.success());
        assertTrue(harness.repository.findById(claim.id()).orElseThrow().saleListing().isEmpty());
        EconomyAuditEntry audit = harness.repository.economyAuditEntries().iterator().next();
        assertEquals(EconomyAuditAction.SALE_CANCEL, audit.action());
        assertEquals(EconomyAuditStatus.CANCELLED, audit.status());
        assertEquals(BigDecimal.valueOf(125), audit.amount());
    }

    @Test
    void cancelLeaseRevokesGrantedRoleAndAudits() {
        ClaimHarness harness = new ClaimHarness();
        UUID tenant = UUID.randomUUID();
        Claim claim = harness.saveActiveLeaseClaim(UUID.randomUUID(), tenant, BigDecimal.valueOf(45));
        LeaseId leaseId = claim.leases().keySet().iterator().next();

        ClaimEconomyAdminResult result = service.cancelLease(
                harness.repository,
                harness.claimService,
                leaseId,
                ACTOR,
                "staff correction",
                Instant.EPOCH.plusSeconds(20)
        );

        assertTrue(result.success());
        Claim stored = harness.repository.findById(claim.id()).orElseThrow();
        assertEquals(LeaseStatus.CANCELLED, stored.leases().get(leaseId).status());
        assertFalse(stored.roleAssignments().getOrDefault(TENANT_ROLE, Set.of()).contains(tenant));
        EconomyAuditEntry audit = harness.repository.economyAuditEntries().iterator().next();
        assertEquals(EconomyAuditAction.LEASE_CANCEL, audit.action());
        assertEquals(claim.id(), audit.claimId().orElseThrow());
    }

    @Test
    void auditCanFilterByClaimAndOwner() {
        ClaimHarness harness = new ClaimHarness();
        Claim first = harness.saveSaleClaim(UUID.randomUUID(), BigDecimal.valueOf(50));
        Claim second = harness.saveSaleClaim(UUID.randomUUID(), BigDecimal.valueOf(75));
        service.cancelSale(harness.repository, harness.claimService, first.id(), ACTOR, "first", Instant.EPOCH);
        service.cancelSale(harness.repository, harness.claimService, second.id(), ACTOR, "second", Instant.EPOCH.plusSeconds(1));

        List<EconomyAuditEntry> claimEntries = service.auditEntriesForClaim(harness.repository, first.id(), 10);
        List<EconomyAuditEntry> ownerEntries = service.auditEntriesForOwner(harness.repository, second.owner().stableKey(), 10);

        assertEquals(1, claimEntries.size());
        assertEquals(first.id(), claimEntries.getFirst().claimId().orElseThrow());
        assertEquals(1, ownerEntries.size());
        assertEquals(second.owner().stableKey(), ownerEntries.getFirst().ownerKey());
    }

    private static UcsConfigSnapshot defaultConfig() {
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
                new UcsConfigSnapshot.ClaimTaxPolicy(true, 24, 0, 10.0D, 2.0D, 64, 24),
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

    private static final class ClaimHarness {
        private final SavedDataClaimRepository repository = new SavedDataClaimRepository(new UcsClaimsSavedData());
        private final UcsConfigSnapshot config = defaultConfig();
        private final DefaultUcsClaimService claimService = new DefaultUcsClaimService(repository, () -> config);

        private Claim savePlayerClaim(UUID owner) {
            Claim claim = ClaimFixtures.claimAt(0, repository.claims().size(), new PlayerOwner(owner, "Owner"));
            claimService.saveClaim(claim);
            return claim;
        }

        private Claim saveSaleClaim(UUID owner, BigDecimal price) {
            Claim claim = savePlayerClaim(owner);
            ClaimSaleListing listing = new ClaimSaleListing(UUID.randomUUID(), owner, "Owner", price, Instant.EPOCH);
            Claim updated = new Claim(
                    claim.id(),
                    claim.owner(),
                    claim.chunks(),
                    claim.metadata(),
                    claim.roleAssignments(),
                    claim.pendingRoleInvites(),
                    claim.flagOverrides(),
                    Optional.of(listing),
                    claim.leases()
            );
            claimService.saveClaim(updated);
            return updated;
        }

        private Claim saveActiveLeaseClaim(UUID owner, UUID tenant, BigDecimal price) {
            Claim claim = savePlayerClaim(owner);
            LeaseContract lease = LeaseContract.offer(
                    LeaseId.random(),
                    claim.id(),
                    new PlayerOwner(tenant, "Tenant"),
                    TENANT_ROLE,
                    price,
                    Duration.ofDays(3),
                    Instant.EPOCH
            ).activate(Instant.EPOCH.plusSeconds(10), true);
            Claim updated = new Claim(
                    claim.id(),
                    claim.owner(),
                    claim.chunks(),
                    new ClaimMetadata(
                            claim.metadata().displayName(),
                            claim.metadata().description(),
                            claim.metadata().spawn(),
                            claim.metadata().createdAt(),
                            Instant.EPOCH.plusSeconds(10)
                    ),
                    Map.of(TENANT_ROLE, Set.of(tenant)),
                    claim.pendingRoleInvites(),
                    claim.flagOverrides(),
                    claim.saleListing(),
                    Map.of(lease.id(), lease)
            );
            claimService.saveClaim(updated);
            return updated;
        }
    }
}
