package com.nadirkhoulali.ucs.api.protection;

import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.FlagId;
import com.nadirkhoulali.ucs.core.model.RoleId;

import java.util.Objects;
import java.util.Set;

public final class ProtectionFlagEvaluator {
    private ProtectionFlagEvaluator() {
    }

    public static ProtectionDecision evaluate(
            ProtectionFlagRegistry registry,
            UcsConfigSnapshot config,
            ClaimView claim,
            FlagId flagId,
            Set<RoleId> effectiveRoles,
            boolean actorPresent
    ) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(flagId, "flagId");
        Set<RoleId> roles = Set.copyOf(Objects.requireNonNull(effectiveRoles, "effectiveRoles"));

        return registry.find(flagId)
                .map(definition -> evaluateDefinition(config, claim, definition, roles, actorPresent))
                .orElseGet(() -> ProtectionDecision.abstain(flagId, "unknown_flag", roles));
    }

    private static ProtectionDecision evaluateDefinition(
            UcsConfigSnapshot config,
            ClaimView claim,
            ProtectionFlagDefinition definition,
            Set<RoleId> effectiveRoles,
            boolean actorPresent
    ) {
        boolean configuredByDefault = config.flags().defaultProtectionFlagIds().contains(definition.id().value());
        boolean enabledForClaim = claim.flagOverrides().contains(definition.id());
        if (!configuredByDefault && !enabledForClaim) {
            return ProtectionDecision.abstain(definition.id(), "flag_not_enabled", effectiveRoles);
        }
        if (!enabledForClaim) {
            return ProtectionDecision.abstain(definition.id(), "flag_disabled_for_claim", effectiveRoles);
        }
        if (definition.actorRequired() && !actorPresent) {
            return ProtectionDecision.deny(definition.id(), "actor_required", effectiveRoles);
        }
        if (definition.allowsAnyRole(effectiveRoles)) {
            return ProtectionDecision.allow(definition.id(), "role_allowed", effectiveRoles);
        }
        if (definition.defaultDecision() == ProtectionDecisionType.ALLOW) {
            return ProtectionDecision.allow(definition.id(), "default_allow", effectiveRoles);
        }
        return ProtectionDecision.deny(definition.id(), "role_not_allowed", effectiveRoles);
    }
}
