package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.internal.DefaultUcsClaimService;
import com.nadirkhoulali.ucs.config.UcsConfigDefaults;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.LeaseId;
import com.nadirkhoulali.ucs.core.model.LeaseStatus;
import com.nadirkhoulali.ucs.core.model.RoleId;
import com.nadirkhoulali.ucs.storage.SavedDataClaimRepository;
import com.nadirkhoulali.ucs.storage.UcsClaimsSavedData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimLeaseServiceTest {
    private static final RoleId TENANT_ROLE = new RoleId("tenant");
    private final ClaimCreationService creationService = new ClaimCreationService();
    private final ClaimLeaseService leaseService = new ClaimLeaseService();

    @Test
    void ownerCanOfferLeaseWithoutGrantingRoleYet() {
        ClaimHarness harness = new ClaimHarness();
        UUID owner = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        harness.create(owner);

        ClaimLeaseResult result = harness.offer(owner, tenant, BigDecimal.valueOf(75), 7, Instant.EPOCH);

        assertTrue(result.failure().isEmpty());
        assertEquals(ClaimLeaseAction.OFFER, result.action());
        assertEquals(LeaseStatus.OFFERED, result.lease().orElseThrow().status());
        assertFalse(result.claim().orElseThrow().roleAssignments().getOrDefault(TENANT_ROLE, Set.of()).contains(tenant));
    }

    @Test
    void tenantAcceptsLeasePaysOwnerAndReceivesRole() {
        ClaimHarness harness = new ClaimHarness();
        FakeClaimEconomyProvider economy = new FakeClaimEconomyProvider();
        UUID owner = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        harness.create(owner);
        LeaseId leaseId = harness.offer(owner, tenant, BigDecimal.valueOf(75), 7, Instant.EPOCH).lease().orElseThrow().id();

        ClaimLeaseResult result = harness.accept(tenant, leaseId, economy, Instant.EPOCH.plusSeconds(10));

        assertTrue(result.failure().isEmpty());
        assertEquals(LeaseStatus.ACTIVE, result.lease().orElseThrow().status());
        assertTrue(result.lease().orElseThrow().roleGranted());
        assertTrue(result.claim().orElseThrow().roleAssignments().get(TENANT_ROLE).contains(tenant));
        assertEquals(List.of(ClaimPricingService.REF_LEASE_ACCEPT), economy.transferReferences());
        assertEquals("transfer:" + ClaimPricingService.REF_LEASE_ACCEPT, result.economyResult().orElseThrow().providerReference());
    }

    @Test
    void acceptWithoutEconomyProviderLeavesOfferAndRolesUnchanged() {
        ClaimHarness harness = new ClaimHarness();
        UUID owner = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        harness.create(owner);
        LeaseId leaseId = harness.offer(owner, tenant, BigDecimal.valueOf(75), 7, Instant.EPOCH).lease().orElseThrow().id();

        ClaimLeaseResult result = harness.accept(tenant, leaseId, null, Instant.EPOCH.plusSeconds(10));

        assertEquals(ClaimLeaseFailureReason.PAYMENT_FAILED, result.failure().orElseThrow().reason());
        Claim stored = harness.repository.findByChunk(chunk()).orElseThrow();
        assertEquals(LeaseStatus.OFFERED, stored.leases().get(leaseId).status());
        assertFalse(stored.roleAssignments().getOrDefault(TENANT_ROLE, Set.of()).contains(tenant));
    }

    @Test
    void tenantCanRenewActiveLeaseByPayingAgain() {
        ClaimHarness harness = new ClaimHarness();
        FakeClaimEconomyProvider economy = new FakeClaimEconomyProvider();
        UUID owner = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        harness.create(owner);
        LeaseId leaseId = harness.offer(owner, tenant, BigDecimal.valueOf(75), 7, Instant.EPOCH).lease().orElseThrow().id();
        ClaimLeaseResult accepted = harness.accept(tenant, leaseId, economy, Instant.EPOCH.plusSeconds(10));

        ClaimLeaseResult renewed = harness.renew(tenant, leaseId, economy, Instant.EPOCH.plusSeconds(20));

        assertTrue(renewed.failure().isEmpty());
        assertEquals(List.of(ClaimPricingService.REF_LEASE_ACCEPT, ClaimPricingService.REF_LEASE_RENEW), economy.transferReferences());
        assertEquals(
                accepted.lease().orElseThrow().expiresAt().orElseThrow().plusSeconds(Duration.ofDays(7).toSeconds()),
                renewed.lease().orElseThrow().expiresAt().orElseThrow()
        );
    }

    @Test
    void cancelActiveLeaseRevokesLeaseGrantedRole() {
        ClaimHarness harness = new ClaimHarness();
        FakeClaimEconomyProvider economy = new FakeClaimEconomyProvider();
        UUID owner = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        harness.create(owner);
        LeaseId leaseId = harness.offer(owner, tenant, BigDecimal.valueOf(75), 7, Instant.EPOCH).lease().orElseThrow().id();
        harness.accept(tenant, leaseId, economy, Instant.EPOCH.plusSeconds(10));

        ClaimLeaseResult cancelled = harness.cancel(owner, leaseId, Instant.EPOCH.plusSeconds(20));

        assertEquals(LeaseStatus.CANCELLED, cancelled.lease().orElseThrow().status());
        assertFalse(cancelled.claim().orElseThrow().roleAssignments().getOrDefault(TENANT_ROLE, Set.of()).contains(tenant));
    }

    @Test
    void expirationRevokesLeaseGrantedRole() {
        ClaimHarness harness = new ClaimHarness();
        FakeClaimEconomyProvider economy = new FakeClaimEconomyProvider();
        UUID owner = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        harness.create(owner);
        LeaseId leaseId = harness.offer(owner, tenant, BigDecimal.valueOf(75), 1, Instant.EPOCH).lease().orElseThrow().id();
        ClaimLeaseResult accepted = harness.accept(tenant, leaseId, economy, Instant.EPOCH.plusSeconds(10));

        ClaimLeaseExpirationResult result = leaseService.expireLeases(
                harness.repository,
                harness.claimService,
                accepted.lease().orElseThrow().expiresAt().orElseThrow(),
                64
        );

        assertEquals(1, result.expiredLeases());
        Claim stored = harness.repository.findByChunk(chunk()).orElseThrow();
        assertEquals(LeaseStatus.EXPIRED, stored.leases().get(leaseId).status());
        assertFalse(stored.roleAssignments().getOrDefault(TENANT_ROLE, Set.of()).contains(tenant));
    }

    @Test
    void expirationKeepsPreExistingTenantRoleWhenLeaseDidNotGrantIt() {
        ClaimHarness harness = new ClaimHarness();
        FakeClaimEconomyProvider economy = new FakeClaimEconomyProvider();
        UUID owner = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        harness.create(owner);
        harness.assignTenantRole(tenant);
        LeaseId leaseId = harness.offer(owner, tenant, BigDecimal.valueOf(75), 1, Instant.EPOCH).lease().orElseThrow().id();
        ClaimLeaseResult accepted = harness.accept(tenant, leaseId, economy, Instant.EPOCH.plusSeconds(10));

        leaseService.expireLeases(
                harness.repository,
                harness.claimService,
                accepted.lease().orElseThrow().expiresAt().orElseThrow(),
                64
        );

        Claim stored = harness.repository.findByChunk(chunk()).orElseThrow();
        assertFalse(stored.leases().get(leaseId).roleGranted());
        assertTrue(stored.roleAssignments().getOrDefault(TENANT_ROLE, Set.of()).contains(tenant));
    }

    private static ChunkKey chunk() {
        return new ChunkKey("minecraft:overworld", 0, 0);
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
        private final UcsConfigSnapshot config = defaultConfig();
        private final DefaultUcsClaimService claimService = new DefaultUcsClaimService(repository, () -> config);

        private void create(UUID owner) {
            creationService.createPlayerClaim(
                    repository,
                    claimService,
                    config,
                    new ClaimCreationRequest(owner, "Owner", chunk(), 0, Instant.EPOCH)
            );
        }

        private ClaimLeaseResult offer(UUID owner, UUID tenant, BigDecimal price, int days, Instant at) {
            return leaseService.offerLease(
                    repository,
                    claimService,
                    config,
                    ClaimLeaseRequest.offer(
                            owner,
                            "Owner",
                            chunk(),
                            at,
                            new ClaimRoleTarget(tenant, "Tenant"),
                            price,
                            Duration.ofDays(days),
                            TENANT_ROLE
                    )
            );
        }

        private ClaimLeaseResult accept(UUID tenant, LeaseId leaseId, FakeClaimEconomyProvider economy, Instant at) {
            return leaseService.acceptLease(
                    repository,
                    claimService,
                    config,
                    ClaimLeaseRequest.byLease(tenant, "Tenant", chunk(), at, leaseId),
                    economy
            );
        }

        private ClaimLeaseResult renew(UUID tenant, LeaseId leaseId, FakeClaimEconomyProvider economy, Instant at) {
            return leaseService.renewLease(
                    repository,
                    claimService,
                    config,
                    ClaimLeaseRequest.byLease(tenant, "Tenant", chunk(), at, leaseId),
                    economy
            );
        }

        private ClaimLeaseResult cancel(UUID owner, LeaseId leaseId, Instant at) {
            return leaseService.cancelLease(
                    repository,
                    claimService,
                    ClaimLeaseRequest.byLease(owner, "Owner", chunk(), at, leaseId)
            );
        }

        private void assignTenantRole(UUID tenant) {
            Claim claim = repository.findByChunk(chunk()).orElseThrow();
            Map<RoleId, Set<UUID>> roles = new LinkedHashMap<>(claim.roleAssignments());
            roles.put(TENANT_ROLE, Set.of(tenant));
            claimService.saveClaim(new Claim(
                    claim.id(),
                    claim.owner(),
                    claim.chunks(),
                    claim.metadata(),
                    roles,
                    claim.pendingRoleInvites(),
                    claim.flagOverrides(),
                    claim.saleListing(),
                    claim.leases()
            ));
        }
    }
}
