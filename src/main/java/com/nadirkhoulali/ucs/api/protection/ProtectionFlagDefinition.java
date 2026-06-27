package com.nadirkhoulali.ucs.api.protection;

import com.nadirkhoulali.ucs.core.model.FlagId;
import com.nadirkhoulali.ucs.core.model.RoleId;

import java.util.Objects;
import java.util.Set;

public record ProtectionFlagDefinition(
        FlagId id,
        String displayName,
        ProtectionFlagCategory category,
        ProtectionDecisionType defaultDecision,
        Set<RoleId> allowedRoles,
        boolean actorRequired
) {
    public ProtectionFlagDefinition {
        Objects.requireNonNull(id, "id");
        displayName = requireNonBlank(displayName, "displayName");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(defaultDecision, "defaultDecision");
        if (defaultDecision == ProtectionDecisionType.ABSTAIN) {
            throw new IllegalArgumentException("defaultDecision cannot be ABSTAIN");
        }
        allowedRoles = Set.copyOf(Objects.requireNonNull(allowedRoles, "allowedRoles"));
    }

    public boolean allowsAnyRole(Set<RoleId> roles) {
        Objects.requireNonNull(roles, "roles");
        return roles.stream().anyMatch(allowedRoles::contains);
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
