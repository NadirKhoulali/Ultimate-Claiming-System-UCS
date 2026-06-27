package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.api.UcsClaimService;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.AuditAction;
import com.nadirkhoulali.ucs.core.model.AuditEntry;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimId;
import com.nadirkhoulali.ucs.core.model.ClaimOwnership;
import com.nadirkhoulali.ucs.core.model.PlayerOwner;
import com.nadirkhoulali.ucs.core.model.RoleId;
import com.nadirkhoulali.ucs.storage.ClaimRepository;
import com.nadirkhoulali.ucs.storage.ClaimRepositoryException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ClaimRoleService {
    public ClaimRoleResult trustPlayer(
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            ClaimRoleRequest request,
            ClaimRoleTarget target
    ) {
        return assignRole(repository, claimService, config, request, target, new RoleId(config.roles().defaultTrustRoleId()), ClaimRoleAction.TRUST);
    }

    public ClaimRoleResult assignRole(
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            ClaimRoleRequest request,
            ClaimRoleTarget target,
            RoleId role
    ) {
        return assignRole(repository, claimService, config, request, target, role, ClaimRoleAction.ASSIGN_ROLE);
    }

    public ClaimRoleResult removePlayer(
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            ClaimRoleRequest request,
            ClaimRoleTarget target
    ) {
        Objects.requireNonNull(target, "target");
        RoleId visitor = new RoleId("visitor");
        return updateOwnedClaim(repository, claimService, request, ClaimRoleAction.UNTRUST, target, visitor, (claim, owner) -> {
            if (request.playerId().equals(target.playerId())) {
                return failure(ClaimRoleAction.UNTRUST, ClaimRoleFailureReason.TARGET_IS_SELF, target.playerName());
            }
            if (isClaimOwner(claim, target)) {
                return failure(ClaimRoleAction.UNTRUST, ClaimRoleFailureReason.TARGET_IS_OWNER, target.playerName());
            }

            Map<RoleId, Set<UUID>> assignments = mutableRoleMap(claim.roleAssignments());
            Map<RoleId, Set<UUID>> invites = mutableRoleMap(claim.pendingRoleInvites());
            removeFromAllNonOwnerRoles(assignments, target.playerId());
            removeFromAllRoles(invites, target.playerId());
            return save(
                    claimService,
                    request,
                    owner,
                    target,
                    visitor,
                    ClaimRoleAction.UNTRUST,
                    claim,
                    assignments,
                    invites,
                    false,
                    "removed " + target.playerName() + " from claim roles"
            );
        });
    }

    public ClaimRoleResult acceptInvite(
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            ClaimRoleRequest request
    ) {
        return resolveInvite(repository, claimService, config, request, ClaimRoleAction.ACCEPT_INVITE, true);
    }

    public ClaimRoleResult declineInvite(
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            ClaimRoleRequest request
    ) {
        return resolveInvite(repository, claimService, config, request, ClaimRoleAction.DECLINE_INVITE, false);
    }

    private ClaimRoleResult assignRole(
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            ClaimRoleRequest request,
            ClaimRoleTarget target,
            RoleId role,
            ClaimRoleAction action
    ) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(role, "role");
        if (!config.roles().defaultRoleIds().contains(role.value()) || role.value().equals("owner")) {
            return failure(action, ClaimRoleFailureReason.ROLE_NOT_CONFIGURED, role.value());
        }

        return updateOwnedClaim(repository, claimService, request, action, target, role, (claim, owner) -> {
            if (request.playerId().equals(target.playerId())) {
                return failure(action, ClaimRoleFailureReason.TARGET_IS_SELF, target.playerName());
            }
            if (isClaimOwner(claim, target)) {
                return failure(action, ClaimRoleFailureReason.TARGET_IS_OWNER, target.playerName());
            }

            RoleId banned = new RoleId(config.roles().bannedRoleId());
            if (!role.equals(banned) && ClaimRoleResolver.isAssigned(claim, banned, target.playerId())) {
                return failure(action, ClaimRoleFailureReason.TARGET_BANNED, target.playerName());
            }

            Map<RoleId, Set<UUID>> assignments = mutableRoleMap(claim.roleAssignments());
            Map<RoleId, Set<UUID>> invites = mutableRoleMap(claim.pendingRoleInvites());
            removeFromAllRoles(invites, target.playerId());
            boolean pending = config.roles().requireInviteAcceptance() && !role.equals(banned);
            if (pending) {
                addToRole(invites, role, target.playerId());
            } else {
                removeFromAllNonOwnerRoles(assignments, target.playerId());
                addToRole(assignments, role, target.playerId());
            }

            return save(
                    claimService,
                    request,
                    owner,
                    target,
                    role,
                    action,
                    claim,
                    assignments,
                    invites,
                    pending,
                    pending ? "invited " + target.playerName() + " as " + role.value() : "assigned " + target.playerName() + " to " + role.value()
            );
        });
    }

    private ClaimRoleResult resolveInvite(
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            ClaimRoleRequest request,
            ClaimRoleAction action,
            boolean accept
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(claimService, "claimService");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(request, "request");

        Optional<Claim> existing = repository.findByChunk(request.chunk());
        if (existing.isEmpty()) {
            return failure(action, ClaimRoleFailureReason.NO_CLAIM_AT_CHUNK, request.chunk().storageKey());
        }

        Claim claim = existing.orElseThrow();
        ClaimRoleTarget target = new ClaimRoleTarget(request.playerId(), request.playerName());
        RoleId banned = new RoleId(config.roles().bannedRoleId());
        if (ClaimRoleResolver.isAssigned(claim, banned, request.playerId())) {
            return failure(action, ClaimRoleFailureReason.TARGET_BANNED, request.playerName());
        }

        Optional<RoleId> pendingRole = firstPendingRole(claim, request.playerId());
        if (pendingRole.isEmpty()) {
            return failure(action, ClaimRoleFailureReason.NO_PENDING_INVITE, request.chunk().storageKey());
        }

        Map<RoleId, Set<UUID>> assignments = mutableRoleMap(claim.roleAssignments());
        Map<RoleId, Set<UUID>> invites = mutableRoleMap(claim.pendingRoleInvites());
        removeFromAllRoles(invites, request.playerId());
        if (accept) {
            removeFromAllNonOwnerRoles(assignments, request.playerId());
            addToRole(assignments, pendingRole.orElseThrow(), request.playerId());
        }

        PlayerOwner actor = ClaimOwnership.player(request.playerId(), request.playerName());
        return save(
                claimService,
                request,
                actor,
                target,
                pendingRole.orElseThrow(),
                action,
                claim,
                assignments,
                invites,
                false,
                (accept ? "accepted" : "declined") + " invite for " + pendingRole.orElseThrow().value()
        );
    }

    private ClaimRoleResult updateOwnedClaim(
            ClaimRepository repository,
            UcsClaimService claimService,
            ClaimRoleRequest request,
            ClaimRoleAction action,
            ClaimRoleTarget target,
            RoleId role,
            OwnedClaimUpdater updater
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(claimService, "claimService");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(updater, "updater");

        Optional<Claim> existing = repository.findByChunk(request.chunk());
        if (existing.isEmpty()) {
            return failure(action, ClaimRoleFailureReason.NO_CLAIM_AT_CHUNK, request.chunk().storageKey());
        }

        PlayerOwner owner = ClaimOwnership.player(request.playerId(), request.playerName());
        Claim claim = existing.orElseThrow();
        if (!ClaimOwnership.isOwnedBy(claim, owner)) {
            return failure(action, ClaimRoleFailureReason.NOT_OWNER, request.chunk().storageKey());
        }
        return updater.update(claim, owner);
    }

    private static ClaimRoleResult save(
            UcsClaimService claimService,
            ClaimRoleRequest request,
            PlayerOwner actor,
            ClaimRoleTarget target,
            RoleId role,
            ClaimRoleAction action,
            Claim claim,
            Map<RoleId, Set<UUID>> assignments,
            Map<RoleId, Set<UUID>> invites,
            boolean pending,
            String auditDetail
    ) {
        Claim updated = new Claim(
                claim.id(),
                claim.owner(),
                claim.chunks(),
                claim.metadata(),
                assignments,
                invites,
                claim.flagOverrides()
        );

        try {
            ClaimView saved = claimService.saveClaim(updated);
            return ClaimRoleResult.success(action, saved, audit(actor, request.requestedAt(), saved.id(), auditDetail), target, role, pending);
        } catch (ClaimRepositoryException exception) {
            return failure(action, ClaimRoleFailureReason.SAVE_FAILED, exceptionDetail(exception));
        }
    }

    private static boolean isClaimOwner(Claim claim, ClaimRoleTarget target) {
        return ClaimOwnership.isOwnedBy(claim, ClaimOwnership.player(target.playerId(), target.playerName()))
                || claim.roleAssignments().getOrDefault(new RoleId("owner"), Set.of()).contains(target.playerId());
    }

    private static Optional<RoleId> firstPendingRole(Claim claim, UUID playerId) {
        return claim.pendingRoleInvites().entrySet().stream()
                .filter(entry -> entry.getValue().contains(playerId))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private static Map<RoleId, Set<UUID>> mutableRoleMap(Map<RoleId, Set<UUID>> source) {
        Map<RoleId, Set<UUID>> copy = new LinkedHashMap<>();
        source.forEach((role, players) -> copy.put(role, new LinkedHashSet<>(players)));
        return copy;
    }

    private static void addToRole(Map<RoleId, Set<UUID>> roles, RoleId role, UUID playerId) {
        roles.computeIfAbsent(role, ignored -> new LinkedHashSet<>()).add(playerId);
    }

    private static void removeFromAllNonOwnerRoles(Map<RoleId, Set<UUID>> roles, UUID playerId) {
        roles.forEach((role, players) -> {
            if (!role.value().equals("owner")) {
                players.remove(playerId);
            }
        });
        roles.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private static void removeFromAllRoles(Map<RoleId, Set<UUID>> roles, UUID playerId) {
        roles.values().forEach(players -> players.remove(playerId));
        roles.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private static AuditEntry audit(PlayerOwner actor, Instant requestedAt, ClaimId claimId, String detail) {
        return new AuditEntry(
                UUID.randomUUID(),
                requestedAt,
                actor.stableKey(),
                AuditAction.CLAIM_UPDATED,
                Optional.of(claimId),
                detail
        );
    }

    private static ClaimRoleResult failure(ClaimRoleAction action, ClaimRoleFailureReason reason, String detail) {
        return ClaimRoleResult.failure(action, new ClaimRoleFailure(reason, detail));
    }

    private static String exceptionDetail(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    @FunctionalInterface
    private interface OwnedClaimUpdater {
        ClaimRoleResult update(Claim claim, PlayerOwner owner);
    }
}
