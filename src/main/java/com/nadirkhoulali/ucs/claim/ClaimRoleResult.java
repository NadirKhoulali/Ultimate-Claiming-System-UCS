package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.core.model.AuditEntry;
import com.nadirkhoulali.ucs.core.model.RoleId;

import java.util.Objects;
import java.util.Optional;

public record ClaimRoleResult(
        ClaimRoleAction action,
        Optional<ClaimView> claim,
        Optional<ClaimRoleFailure> failure,
        Optional<AuditEntry> auditEntry,
        Optional<ClaimRoleTarget> target,
        Optional<RoleId> role,
        boolean pendingInvite
) {
    public ClaimRoleResult {
        Objects.requireNonNull(action, "action");
        claim = Objects.requireNonNull(claim, "claim");
        failure = Objects.requireNonNull(failure, "failure");
        auditEntry = Objects.requireNonNull(auditEntry, "auditEntry");
        target = Objects.requireNonNull(target, "target");
        role = Objects.requireNonNull(role, "role");
        if (claim.isPresent() == failure.isPresent()) {
            throw new IllegalArgumentException("claim role result must contain either a claim or a failure");
        }
        if (claim.isPresent() != auditEntry.isPresent()) {
            throw new IllegalArgumentException("successful claim role update must include an audit entry");
        }
        if (failure.isPresent() && pendingInvite) {
            throw new IllegalArgumentException("failed claim role update cannot be pending");
        }
    }

    public static ClaimRoleResult success(
            ClaimRoleAction action,
            ClaimView claim,
            AuditEntry auditEntry,
            ClaimRoleTarget target,
            RoleId role,
            boolean pendingInvite
    ) {
        return new ClaimRoleResult(
                action,
                Optional.of(claim),
                Optional.empty(),
                Optional.of(auditEntry),
                Optional.of(target),
                Optional.of(role),
                pendingInvite
        );
    }

    public static ClaimRoleResult failure(ClaimRoleAction action, ClaimRoleFailure failure) {
        return new ClaimRoleResult(
                action,
                Optional.empty(),
                Optional.of(failure),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false
        );
    }
}
