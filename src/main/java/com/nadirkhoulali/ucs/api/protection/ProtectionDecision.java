package com.nadirkhoulali.ucs.api.protection;

import com.nadirkhoulali.ucs.core.model.FlagId;
import com.nadirkhoulali.ucs.core.model.RoleId;

import java.util.Objects;
import java.util.Set;

public record ProtectionDecision(
        ProtectionDecisionType type,
        FlagId flagId,
        String reason,
        Set<RoleId> effectiveRoles
) {
    public ProtectionDecision {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(flagId, "flagId");
        reason = requireNonBlank(reason, "reason");
        effectiveRoles = Set.copyOf(Objects.requireNonNull(effectiveRoles, "effectiveRoles"));
    }

    public boolean allowed() {
        return type == ProtectionDecisionType.ALLOW;
    }

    public boolean denied() {
        return type == ProtectionDecisionType.DENY;
    }

    public boolean abstained() {
        return type == ProtectionDecisionType.ABSTAIN;
    }

    public static ProtectionDecision allow(FlagId flagId, String reason, Set<RoleId> effectiveRoles) {
        return new ProtectionDecision(ProtectionDecisionType.ALLOW, flagId, reason, effectiveRoles);
    }

    public static ProtectionDecision deny(FlagId flagId, String reason, Set<RoleId> effectiveRoles) {
        return new ProtectionDecision(ProtectionDecisionType.DENY, flagId, reason, effectiveRoles);
    }

    public static ProtectionDecision abstain(FlagId flagId, String reason, Set<RoleId> effectiveRoles) {
        return new ProtectionDecision(ProtectionDecisionType.ABSTAIN, flagId, reason, effectiveRoles);
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return trimmed;
    }
}
