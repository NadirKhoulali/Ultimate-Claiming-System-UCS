package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.internal.DefaultUcsClaimService;
import com.nadirkhoulali.ucs.config.UcsConfigDefaults;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimOwnership;
import com.nadirkhoulali.ucs.core.model.RoleId;
import com.nadirkhoulali.ucs.storage.ClaimFixtures;
import com.nadirkhoulali.ucs.storage.SavedDataClaimRepository;
import com.nadirkhoulali.ucs.storage.UcsClaimsSavedData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimRoleServiceTest {
    private final ClaimRoleService service = new ClaimRoleService();

    @Test
    void trustDirectlyAssignsDefaultRoleByDefault() {
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        ClaimHarness harness = new ClaimHarness(defaultConfig(false));
        harness.savePlayerClaim(owner, "Owner", 0, 0);

        ClaimRoleResult result = service.trustPlayer(
                harness.repository,
                harness.claimService,
                harness.config,
                request(owner, "Owner", chunk(0, 0)),
                new ClaimRoleTarget(target, "Friend")
        );

        assertFalse(result.pendingInvite());
        assertTrue(result.claim().orElseThrow().roleAssignments().get(new RoleId("member")).contains(target));
        assertTrue(result.claim().orElseThrow().pendingRoleInvites().isEmpty());
    }

    @Test
    void trustCreatesPendingInviteWhenConfigured() {
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        ClaimHarness harness = new ClaimHarness(defaultConfig(true));
        harness.savePlayerClaim(owner, "Owner", 0, 0);

        ClaimRoleResult result = service.trustPlayer(
                harness.repository,
                harness.claimService,
                harness.config,
                request(owner, "Owner", chunk(0, 0)),
                new ClaimRoleTarget(target, "Friend")
        );

        assertTrue(result.pendingInvite());
        assertTrue(result.claim().orElseThrow().pendingRoleInvites().get(new RoleId("member")).contains(target));
        assertFalse(result.claim().orElseThrow().roleAssignments().getOrDefault(new RoleId("member"), Set.of()).contains(target));
    }

    @Test
    void invitedPlayerCanAcceptRole() {
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        ClaimHarness harness = new ClaimHarness(defaultConfig(true));
        harness.savePlayerClaim(owner, "Owner", 0, 0);
        service.trustPlayer(
                harness.repository,
                harness.claimService,
                harness.config,
                request(owner, "Owner", chunk(0, 0)),
                new ClaimRoleTarget(target, "Friend")
        );

        ClaimRoleResult result = service.acceptInvite(
                harness.repository,
                harness.claimService,
                harness.config,
                request(target, "Friend", chunk(0, 0))
        );

        assertEquals(ClaimRoleAction.ACCEPT_INVITE, result.action());
        assertTrue(result.claim().orElseThrow().roleAssignments().get(new RoleId("member")).contains(target));
        assertTrue(result.claim().orElseThrow().pendingRoleInvites().isEmpty());
    }

    @Test
    void roleAssignmentRejectsBannedTarget() {
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        ClaimHarness harness = new ClaimHarness(defaultConfig(false));
        harness.savePlayerClaim(owner, "Owner", 0, 0);
        service.assignRole(
                harness.repository,
                harness.claimService,
                harness.config,
                request(owner, "Owner", chunk(0, 0)),
                new ClaimRoleTarget(target, "Trouble"),
                new RoleId("banned")
        );

        ClaimRoleResult result = service.trustPlayer(
                harness.repository,
                harness.claimService,
                harness.config,
                request(owner, "Owner", chunk(0, 0)),
                new ClaimRoleTarget(target, "Trouble")
        );

        assertEquals(ClaimRoleFailureReason.TARGET_BANNED, result.failure().orElseThrow().reason());
    }

    @Test
    void banRemovesConflictingRoleGrants() {
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        ClaimHarness harness = new ClaimHarness(defaultConfig(false));
        harness.savePlayerClaim(owner, "Owner", 0, 0);
        service.trustPlayer(
                harness.repository,
                harness.claimService,
                harness.config,
                request(owner, "Owner", chunk(0, 0)),
                new ClaimRoleTarget(target, "Trouble")
        );

        ClaimRoleResult result = service.banPlayer(
                harness.repository,
                harness.claimService,
                harness.config,
                request(owner, "Owner", chunk(0, 0)),
                new ClaimRoleTarget(target, "Trouble")
        );

        assertEquals(ClaimRoleAction.BAN, result.action());
        assertFalse(result.claim().orElseThrow().roleAssignments().getOrDefault(new RoleId("member"), Set.of()).contains(target));
        assertTrue(result.claim().orElseThrow().roleAssignments().get(new RoleId("banned")).contains(target));
    }

    @Test
    void adminOverrideCanBanClaimWithoutOwningIt() {
        UUID owner = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        ClaimHarness harness = new ClaimHarness(defaultConfig(false));
        harness.savePlayerClaim(owner, "Owner", 0, 0);

        ClaimRoleResult result = service.banPlayer(
                harness.repository,
                harness.claimService,
                harness.config,
                new ClaimRoleRequest(admin, "Admin", chunk(0, 0), Instant.EPOCH.plusSeconds(10), true),
                new ClaimRoleTarget(target, "Trouble")
        );

        assertEquals(ClaimRoleAction.BAN, result.action());
        assertTrue(result.claim().orElseThrow().roleAssignments().get(new RoleId("banned")).contains(target));
    }

    @Test
    void unbanOnlyClearsBannedRole() {
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        ClaimHarness harness = new ClaimHarness(defaultConfig(false));
        harness.savePlayerClaim(owner, "Owner", 0, 0);
        service.banPlayer(
                harness.repository,
                harness.claimService,
                harness.config,
                request(owner, "Owner", chunk(0, 0)),
                new ClaimRoleTarget(target, "Trouble")
        );

        ClaimRoleResult result = service.unbanPlayer(
                harness.repository,
                harness.claimService,
                harness.config,
                request(owner, "Owner", chunk(0, 0)),
                new ClaimRoleTarget(target, "Trouble")
        );

        assertEquals(ClaimRoleAction.UNBAN, result.action());
        assertFalse(result.claim().orElseThrow().roleAssignments().containsKey(new RoleId("banned")));
    }

    @Test
    void resolverReturnsBannedBeforeOtherRoles() {
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        ClaimHarness harness = new ClaimHarness(defaultConfig(false));
        harness.savePlayerClaim(owner, "Owner", 0, 0);
        service.assignRole(
                harness.repository,
                harness.claimService,
                harness.config,
                request(owner, "Owner", chunk(0, 0)),
                new ClaimRoleTarget(target, "Trouble"),
                new RoleId("banned")
        );

        Claim claim = harness.repository.findByChunk(chunk(0, 0)).orElseThrow();

        assertEquals(Set.of(new RoleId("banned")), ClaimRoleResolver.effectiveRoles(claim, target, harness.config));
    }

    private static ClaimRoleRequest request(UUID playerId, String playerName, ChunkKey chunk) {
        return new ClaimRoleRequest(playerId, playerName, chunk, Instant.EPOCH.plusSeconds(10));
    }

    private static ChunkKey chunk(int x, int z) {
        return new ChunkKey("minecraft:overworld", x, z);
    }

    private static UcsConfigSnapshot defaultConfig(boolean requireInviteAcceptance) {
        return new UcsConfigSnapshot(
                UcsConfigDefaults.CURRENT_SCHEMA_VERSION,
                true,
                new UcsConfigSnapshot.DimensionPolicy(List.of("minecraft:overworld"), List.of(), true),
                new UcsConfigSnapshot.ClaimLimitPolicy(16, 256, 128, 5, true),
                new UcsConfigSnapshot.ClaimMetadataPolicy(48, 240),
                new UcsConfigSnapshot.ClaimTeleportPolicy(3, true, true),
                new UcsConfigSnapshot.RoleDefaults(UcsConfigDefaults.DEFAULT_ROLE_IDS, "member", "banned", requireInviteAcceptance),
                new UcsConfigSnapshot.BanPolicy(true, 48, 40),
                new UcsConfigSnapshot.FlagDefaults(UcsConfigDefaults.DEFAULT_PROTECTION_FLAG_IDS),
                new UcsConfigSnapshot.ProtectionPolicy(List.of(), List.of(), UcsConfigDefaults.DEFAULT_SPECIAL_BLOCK_IDS),
                new UcsConfigSnapshot.EconomyPolicy(true, 25.0D, 5.0D, 0.75D, true),
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
