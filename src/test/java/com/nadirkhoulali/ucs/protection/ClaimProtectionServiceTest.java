package com.nadirkhoulali.ucs.protection;

import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.api.protection.ProtectionDecision;
import com.nadirkhoulali.ucs.api.protection.ProtectionDecisionType;
import com.nadirkhoulali.ucs.api.protection.UcsBuiltInProtectionFlags;
import com.nadirkhoulali.ucs.config.UcsConfigDefaults;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimOwnership;
import com.nadirkhoulali.ucs.core.model.FlagId;
import com.nadirkhoulali.ucs.core.model.RoleId;
import com.nadirkhoulali.ucs.storage.ClaimFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimProtectionServiceTest {
    private final ClaimProtectionService service = new ClaimProtectionService();

    @Test
    void ownerCanUseEnabledBreakFlag() {
        UUID owner = UUID.randomUUID();
        Claim claim = claimWithFlags(owner, Set.of(UcsBuiltInProtectionFlags.BLOCK_BREAK));

        ProtectionDecision decision = service.evaluateClaimAction(
                DefaultProtectionFlagRegistry.withBuiltIns(),
                config(List.of(), List.of(), UcsConfigDefaults.DEFAULT_SPECIAL_BLOCK_IDS),
                ClaimView.from(claim),
                UcsBuiltInProtectionFlags.BLOCK_BREAK,
                Optional.of(owner),
                true
        );

        assertEquals(ProtectionDecisionType.ALLOW, decision.type());
        assertTrue(decision.effectiveRoles().contains(new RoleId("owner")));
    }

    @Test
    void visitorCannotPlaceInEnabledClaim() {
        Claim claim = claimWithFlags(UUID.randomUUID(), Set.of(UcsBuiltInProtectionFlags.BLOCK_PLACE));

        ProtectionDecision decision = service.evaluateClaimAction(
                DefaultProtectionFlagRegistry.withBuiltIns(),
                config(List.of(), List.of(), UcsConfigDefaults.DEFAULT_SPECIAL_BLOCK_IDS),
                ClaimView.from(claim),
                UcsBuiltInProtectionFlags.BLOCK_PLACE,
                Optional.of(UUID.randomUUID()),
                true
        );

        assertEquals(ProtectionDecisionType.DENY, decision.type());
        assertEquals("role_not_allowed", decision.reason());
    }

    @Test
    void specialBlocksUseSpecialBlockFlag() {
        UcsConfigSnapshot config = config(List.of(), List.of(), List.of("minecraft:beacon"));

        assertEquals(UcsBuiltInProtectionFlags.SPECIAL_BLOCK_USE, service.breakFlagForBlock(config, "minecraft:beacon"));
        assertEquals(UcsBuiltInProtectionFlags.BLOCK_BREAK, service.breakFlagForBlock(config, "minecraft:dirt"));
    }

    @Test
    void ignoredAndAllowedBlockPoliciesAreExposed() {
        UcsConfigSnapshot config = config(List.of("minecraft:air"), List.of("minecraft:torch"), List.of("minecraft:beacon"));

        assertTrue(service.isIgnoredBlock(config, "minecraft:air"));
        assertTrue(service.isAllowedBlock(config, "minecraft:torch"));
        assertFalse(service.isIgnoredBlock(config, "minecraft:stone"));
    }

    private static Claim claimWithFlags(UUID owner, Set<FlagId> flags) {
        Claim claim = ClaimFixtures.claimAt(0, 0, ClaimOwnership.player(owner, "Owner"));
        return new Claim(
                claim.id(),
                claim.owner(),
                claim.chunks(),
                claim.metadata(),
                claim.roleAssignments(),
                claim.pendingRoleInvites(),
                flags
        );
    }

    private static UcsConfigSnapshot config(List<String> ignoredBlocks, List<String> allowedBlocks, List<String> specialBlocks) {
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
                new UcsConfigSnapshot.ProtectionPolicy(ignoredBlocks, allowedBlocks, specialBlocks),
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
}
