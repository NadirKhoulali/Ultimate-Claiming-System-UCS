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

    @Test
    void classifiesInteractionTargetsByRegistryId() {
        UcsConfigSnapshot config = config(
                List.of(),
                List.of(),
                List.of("minecraft:beacon"),
                List.of("modded:machine"),
                List.of("modded:door"),
                List.of("modded:button"),
                List.of("modded:lever"),
                List.of("modded:redstone_bus"),
                UcsConfigDefaults.DEFAULT_ENTITY_TARGET_IDS,
                UcsConfigDefaults.DEFAULT_VEHICLE_TARGET_IDS
        );

        assertEquals(UcsBuiltInProtectionFlags.CONTAINER_OPEN, service.interactionFlagForBlockId(config, "modded:machine").orElseThrow());
        assertEquals(UcsBuiltInProtectionFlags.DOOR_USE, service.interactionFlagForBlockId(config, "modded:door").orElseThrow());
        assertEquals(UcsBuiltInProtectionFlags.BUTTON_USE, service.interactionFlagForBlockId(config, "modded:button").orElseThrow());
        assertEquals(UcsBuiltInProtectionFlags.LEVER_USE, service.interactionFlagForBlockId(config, "modded:lever").orElseThrow());
        assertEquals(UcsBuiltInProtectionFlags.REDSTONE_USE, service.interactionFlagForBlockId(config, "modded:redstone_bus").orElseThrow());
        assertTrue(service.interactionFlagForBlockId(config, "minecraft:dirt").isEmpty());
    }

    @Test
    void classifiesEntityTargetsByRegistryId() {
        UcsConfigSnapshot config = config(
                List.of(),
                List.of(),
                List.of("minecraft:beacon"),
                UcsConfigDefaults.DEFAULT_CONTAINER_TARGET_IDS,
                UcsConfigDefaults.DEFAULT_DOOR_TARGET_IDS,
                UcsConfigDefaults.DEFAULT_BUTTON_TARGET_IDS,
                UcsConfigDefaults.DEFAULT_LEVER_TARGET_IDS,
                UcsConfigDefaults.DEFAULT_REDSTONE_TARGET_IDS,
                List.of("modded:animal"),
                List.of("modded:cart")
        );

        assertEquals(UcsBuiltInProtectionFlags.ENTITY_INTERACT, service.entityInteractionFlagForEntityTypeId(config, "modded:animal").orElseThrow());
        assertEquals(UcsBuiltInProtectionFlags.VEHICLE_USE, service.entityInteractionFlagForEntityTypeId(config, "modded:cart").orElseThrow());
        assertTrue(service.isProtectedEntityTypeId(config, "modded:animal"));
        assertTrue(service.isProtectedEntityTypeId(config, "modded:cart"));
        assertTrue(service.isVehicleEntityTypeId(config, "modded:cart"));
        assertTrue(service.entityInteractionFlagForEntityTypeId(config, "minecraft:zombie").isEmpty());
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
        return config(
                ignoredBlocks,
                allowedBlocks,
                specialBlocks,
                UcsConfigDefaults.DEFAULT_CONTAINER_TARGET_IDS,
                UcsConfigDefaults.DEFAULT_DOOR_TARGET_IDS,
                UcsConfigDefaults.DEFAULT_BUTTON_TARGET_IDS,
                UcsConfigDefaults.DEFAULT_LEVER_TARGET_IDS,
                UcsConfigDefaults.DEFAULT_REDSTONE_TARGET_IDS,
                UcsConfigDefaults.DEFAULT_ENTITY_TARGET_IDS,
                UcsConfigDefaults.DEFAULT_VEHICLE_TARGET_IDS
        );
    }

    private static UcsConfigSnapshot config(
            List<String> ignoredBlocks,
            List<String> allowedBlocks,
            List<String> specialBlocks,
            List<String> containerTargets,
            List<String> doorTargets,
            List<String> buttonTargets,
            List<String> leverTargets,
            List<String> redstoneTargets,
            List<String> entityTargets,
            List<String> vehicleTargets
    ) {
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
                new UcsConfigSnapshot.ProtectionPolicy(
                        ignoredBlocks,
                        allowedBlocks,
                        specialBlocks,
                        containerTargets,
                        doorTargets,
                        buttonTargets,
                        leverTargets,
                        redstoneTargets,
                        entityTargets,
                        vehicleTargets
                ),
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
