package com.nadirkhoulali.ucs.permission;

import java.util.Objects;

public record UcsPermissionDecision(
        UcsPermission permission,
        String nodeName,
        boolean allowed,
        Source source
) {
    public UcsPermissionDecision {
        Objects.requireNonNull(permission, "permission");
        nodeName = Objects.requireNonNull(nodeName, "nodeName");
        Objects.requireNonNull(source, "source");
    }

    public boolean shouldAuditUse() {
        return allowed && permission.auditCandidate();
    }

    public enum Source {
        NEOFORGE_HANDLER,
        COMMAND_SOURCE,
        PLAYER_OP_FALLBACK,
        PUBLIC_DEFAULT,
        FALLBACK_DISABLED,
        UNREGISTERED_NODE
    }
}
