package com.nadirkhoulali.ucs.api.protection;

import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.config.UcsConfigDefaults;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.FlagId;
import com.nadirkhoulali.ucs.core.model.RoleId;
import com.nadirkhoulali.ucs.protection.DefaultProtectionFlagRegistry;
import com.nadirkhoulali.ucs.storage.ClaimFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtectionFlagEvaluatorTest {
    @Test
    void allowsConfiguredRoleForEnabledClaimFlag() {
        ProtectionDecision decision = evaluate(
                claimWithFlags(Set.of(UcsBuiltInProtectionFlags.BLOCK_BREAK)),
                UcsBuiltInProtectionFlags.BLOCK_BREAK,
                Set.of(new RoleId("member")),
                true
        );

        assertEquals(ProtectionDecisionType.ALLOW, decision.type());
        assertEquals("role_allowed", decision.reason());
    }

    @Test
    void deniesVisitorForProtectedClaimFlag() {
        ProtectionDecision decision = evaluate(
                claimWithFlags(Set.of(UcsBuiltInProtectionFlags.BLOCK_BREAK)),
                UcsBuiltInProtectionFlags.BLOCK_BREAK,
                Set.of(new RoleId("visitor")),
                true
        );

        assertEquals(ProtectionDecisionType.DENY, decision.type());
        assertEquals("role_not_allowed", decision.reason());
    }

    @Test
    void abstainsWhenFlagIsConfiguredButDisabledForClaim() {
        ProtectionDecision decision = evaluate(
                claimWithFlags(Set.of(UcsBuiltInProtectionFlags.BLOCK_BREAK)),
                UcsBuiltInProtectionFlags.BLOCK_PLACE,
                Set.of(new RoleId("member")),
                true
        );

        assertEquals(ProtectionDecisionType.ABSTAIN, decision.type());
        assertEquals("flag_disabled_for_claim", decision.reason());
    }

    @Test
    void abstainsUnknownFlags() {
        FlagId flag = new FlagId("addon:missing");
        ProtectionDecision decision = evaluate(
                claimWithFlags(Set.of(flag)),
                flag,
                Set.of(new RoleId("member")),
                true
        );

        assertEquals(ProtectionDecisionType.ABSTAIN, decision.type());
        assertEquals("unknown_flag", decision.reason());
    }

    @Test
    void deniesNaturalEventWithoutPlayerActorWhenFlagIsEnabled() {
        ProtectionDecision decision = evaluate(
                claimWithFlags(Set.of(UcsBuiltInProtectionFlags.EXPLOSION)),
                UcsBuiltInProtectionFlags.EXPLOSION,
                Set.of(),
                false
        );

        assertEquals(ProtectionDecisionType.DENY, decision.type());
        assertEquals("role_not_allowed", decision.reason());
    }

    @Test
    void evaluatesRegisteredAddonFlag() {
        DefaultProtectionFlagRegistry registry = DefaultProtectionFlagRegistry.withBuiltIns();
        ProtectionFlagDefinition definition = registry.register(new ProtectionFlagDefinition(
                new FlagId("addon:custom_machine_use"),
                "Custom Machine Use",
                ProtectionFlagCategory.INTERACTIONS,
                ProtectionDecisionType.DENY,
                Set.of(new RoleId("member")),
                true
        ));

        ProtectionDecision decision = ProtectionFlagEvaluator.evaluate(
                registry,
                defaultConfig(List.of()),
                ClaimView.from(claimWithFlags(Set.of(definition.id()))),
                definition.id(),
                Set.of(new RoleId("member")),
                true
        );

        assertEquals(ProtectionDecisionType.ALLOW, decision.type());
    }

    private static ProtectionDecision evaluate(Claim claim, FlagId flagId, Set<RoleId> roles, boolean actorPresent) {
        return ProtectionFlagEvaluator.evaluate(
                DefaultProtectionFlagRegistry.withBuiltIns(),
                defaultConfig(UcsConfigDefaults.DEFAULT_PROTECTION_FLAG_IDS),
                ClaimView.from(claim),
                flagId,
                roles,
                actorPresent
        );
    }

    private static Claim claimWithFlags(Set<FlagId> flags) {
        Claim claim = ClaimFixtures.claimAt(0, 0);
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

    private static UcsConfigSnapshot defaultConfig(List<String> enabledFlags) {
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
                new UcsConfigSnapshot.FlagDefaults(enabledFlags),
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
}
