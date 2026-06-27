package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimOwnership;
import com.nadirkhoulali.ucs.core.model.PlayerOwner;
import com.nadirkhoulali.ucs.core.model.RoleId;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ClaimRoleResolver {
    private ClaimRoleResolver() {
    }

    public static Set<RoleId> effectiveRoles(Claim claim, UUID playerId, UcsConfigSnapshot config) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(config, "config");

        RoleId banned = new RoleId(config.roles().bannedRoleId());
        if (isAssigned(claim, banned, playerId)) {
            return Set.of(banned);
        }

        Set<RoleId> roles = new LinkedHashSet<>();
        PlayerOwner owner = ClaimOwnership.player(playerId, "unknown");
        if (ClaimOwnership.isOwnedBy(claim, owner) || isAssigned(claim, new RoleId("owner"), playerId)) {
            roles.add(new RoleId("owner"));
        }

        claim.roleAssignments().forEach((role, players) -> {
            if (players.contains(playerId)) {
                roles.add(role);
            }
        });

        if (roles.isEmpty()) {
            roles.add(new RoleId("visitor"));
        }
        return Set.copyOf(roles);
    }

    public static boolean isAssigned(Claim claim, RoleId role, UUID playerId) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(playerId, "playerId");
        return claim.roleAssignments().getOrDefault(role, Set.of()).contains(playerId);
    }
}
