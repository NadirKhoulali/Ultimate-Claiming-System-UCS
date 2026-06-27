package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.ClaimLeaseView;
import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.api.UcsClaimService;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyAccountRef;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyProvider;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyResult;
import com.nadirkhoulali.ucs.api.event.UcsClaimLeaseEvent;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.AuditAction;
import com.nadirkhoulali.ucs.core.model.AuditEntry;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimId;
import com.nadirkhoulali.ucs.core.model.ClaimMetadata;
import com.nadirkhoulali.ucs.core.model.ClaimOwnership;
import com.nadirkhoulali.ucs.core.model.LeaseContract;
import com.nadirkhoulali.ucs.core.model.LeaseId;
import com.nadirkhoulali.ucs.core.model.LeaseStatus;
import com.nadirkhoulali.ucs.core.model.PlayerOwner;
import com.nadirkhoulali.ucs.core.model.RoleId;
import com.nadirkhoulali.ucs.storage.ClaimRepository;
import com.nadirkhoulali.ucs.storage.ClaimRepositoryException;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ClaimLeaseService {
    private static final int EXPIRATION_SCAN_INTERVAL_TICKS = 200;
    private static final int MAX_CLAIMS_PER_EXPIRATION_SCAN = 64;
    private final ClaimPricingService pricing = new ClaimPricingService();
    private long nextExpirationScanTick;

    public ClaimLeaseResult offerLease(
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            ClaimLeaseRequest request
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(claimService, "claimService");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(request, "request");

        Optional<Claim> claim = repository.findByChunk(request.chunk());
        if (claim.isEmpty()) {
            return failure(ClaimLeaseAction.OFFER, ClaimLeaseFailureReason.NO_CLAIM_AT_CHUNK, request.chunk().storageKey());
        }
        Optional<PlayerOwner> owner = playerOwner(claim.orElseThrow());
        if (owner.isEmpty()) {
            return failure(ClaimLeaseAction.OFFER, ClaimLeaseFailureReason.NOT_PLAYER_OWNED, claim.orElseThrow().owner().stableKey());
        }
        if (!owner.orElseThrow().playerId().equals(request.playerId())) {
            return failure(ClaimLeaseAction.OFFER, ClaimLeaseFailureReason.NOT_OWNER, request.chunk().storageKey());
        }

        ClaimRoleTarget tenant = request.tenant().orElseThrow();
        if (tenant.playerId().equals(owner.orElseThrow().playerId())) {
            return failure(ClaimLeaseAction.OFFER, ClaimLeaseFailureReason.TENANT_IS_OWNER, tenant.playerName());
        }
        RoleId role = request.role().orElseThrow();
        Optional<ClaimLeaseFailure> roleFailure = validateLeaseRole(config, role);
        if (roleFailure.isPresent()) {
            return ClaimLeaseResult.failure(ClaimLeaseAction.OFFER, roleFailure.orElseThrow());
        }
        if (ClaimRoleResolver.isAssigned(claim.orElseThrow(), new RoleId(config.roles().bannedRoleId()), tenant.playerId())) {
            return failure(ClaimLeaseAction.OFFER, ClaimLeaseFailureReason.TENANT_BANNED, tenant.playerName());
        }
        if (hasOpenLeaseForTenant(claim.orElseThrow(), tenant.playerId())) {
            return failure(ClaimLeaseAction.OFFER, ClaimLeaseFailureReason.ALREADY_HAS_LEASE, tenant.playerName());
        }

        BigDecimal price = request.price().orElseThrow();
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            return failure(ClaimLeaseAction.OFFER, ClaimLeaseFailureReason.PRICE_TOO_LOW, price.toPlainString());
        }
        if (request.duration().orElseThrow().isZero() || request.duration().orElseThrow().isNegative()) {
            return failure(ClaimLeaseAction.OFFER, ClaimLeaseFailureReason.DURATION_TOO_SHORT, request.duration().orElseThrow().toString());
        }

        LeaseContract lease = LeaseContract.offer(
                LeaseId.random(),
                claim.orElseThrow().id(),
                new PlayerOwner(tenant.playerId(), tenant.playerName()),
                role,
                price,
                request.duration().orElseThrow(),
                request.requestedAt()
        );
        Claim updated = withLease(claim.orElseThrow(), lease, request.requestedAt());
        return saveSuccess(
                claimService,
                ClaimLeaseAction.OFFER,
                updated,
                lease,
                request.requestedAt(),
                request.actorKey(),
                "offered lease " + lease.id().value() + " to " + tenant.playerName() + " as " + role.value()
        );
    }

    public ClaimLeaseResult acceptLease(
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            ClaimLeaseRequest request,
            ClaimEconomyProvider economyProvider
    ) {
        return payAndActivate(repository, claimService, config, request, economyProvider, ClaimLeaseAction.ACCEPT);
    }

    public ClaimLeaseResult renewLease(
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            ClaimLeaseRequest request,
            ClaimEconomyProvider economyProvider
    ) {
        return payAndActivate(repository, claimService, config, request, economyProvider, ClaimLeaseAction.RENEW);
    }

    public ClaimLeaseResult cancelLease(
            ClaimRepository repository,
            UcsClaimService claimService,
            ClaimLeaseRequest request
    ) {
        return terminateLease(repository, claimService, request, ClaimLeaseAction.CANCEL, false);
    }

    public ClaimLeaseResult evictTenant(
            ClaimRepository repository,
            UcsClaimService claimService,
            ClaimLeaseRequest request
    ) {
        return terminateLease(repository, claimService, request, ClaimLeaseAction.EVICT, true);
    }

    public void tick(MinecraftServer server, ClaimRepository repository, UcsClaimService claimService) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(claimService, "claimService");
        if (server.getTickCount() < nextExpirationScanTick) {
            return;
        }
        nextExpirationScanTick = server.getTickCount() + EXPIRATION_SCAN_INTERVAL_TICKS;
        expireLeases(repository, claimService, Instant.now(), MAX_CLAIMS_PER_EXPIRATION_SCAN);
    }

    public void clear() {
        nextExpirationScanTick = 0L;
    }

    public ClaimLeaseExpirationResult expireLeases(
            ClaimRepository repository,
            UcsClaimService claimService,
            Instant now,
            int maxClaims
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(claimService, "claimService");
        Objects.requireNonNull(now, "now");
        if (maxClaims <= 0) {
            throw new IllegalArgumentException("maxClaims must be greater than zero");
        }

        int scanned = 0;
        int expired = 0;
        List<AuditEntry> audits = new ArrayList<>();
        for (Claim claim : new ArrayList<>(repository.claims())) {
            if (scanned >= maxClaims) {
                break;
            }
            scanned++;
            Map<LeaseId, LeaseContract> leases = mutableLeases(claim);
            Map<RoleId, Set<UUID>> roles = mutableRoles(claim.roleAssignments());
            boolean changed = false;
            List<LeaseContract> expiredInClaim = new ArrayList<>();
            for (LeaseContract lease : claim.leases().values()) {
                if (lease.status() == LeaseStatus.ACTIVE
                        && lease.expiresAt().isPresent()
                        && !lease.expiresAt().orElseThrow().isAfter(now)) {
                    LeaseContract expiredLease = lease.expire();
                    leases.put(expiredLease.id(), expiredLease);
                    revokeLeaseRole(roles, expiredLease);
                    expiredInClaim.add(expiredLease);
                    changed = true;
                }
            }
            if (!changed) {
                continue;
            }

            Claim updated = withLeasesAndRoles(claim, leases, roles, claim.pendingRoleInvites(), now);
            try {
                ClaimView saved = claimService.saveClaim(updated);
                for (LeaseContract lease : expiredInClaim) {
                    ClaimLeaseView leaseView = saved.leases().get(lease.id());
                    AuditEntry audit = audit(
                            "system:lease-expiration",
                            now,
                            saved.id(),
                            ClaimLeaseAction.EXPIRE,
                            "expired lease " + lease.id().value() + " for " + lease.roleId().value()
                    );
                    audits.add(audit);
                    postEvent(ClaimLeaseAction.EXPIRE, saved, leaseView, now);
                    expired++;
                }
            } catch (ClaimRepositoryException ignored) {
                // Expiration is retried on the next scan; command paths surface save failures synchronously.
            }
        }
        return new ClaimLeaseExpirationResult(scanned, expired, audits);
    }

    private ClaimLeaseResult payAndActivate(
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            ClaimLeaseRequest request,
            ClaimEconomyProvider economyProvider,
            ClaimLeaseAction action
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(claimService, "claimService");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(request, "request");

        Optional<Claim> claim = repository.findByChunk(request.chunk());
        if (claim.isEmpty()) {
            return failure(action, ClaimLeaseFailureReason.NO_CLAIM_AT_CHUNK, request.chunk().storageKey());
        }
        Optional<PlayerOwner> owner = playerOwner(claim.orElseThrow());
        if (owner.isEmpty()) {
            return failure(action, ClaimLeaseFailureReason.NOT_PLAYER_OWNED, claim.orElseThrow().owner().stableKey());
        }
        Optional<LeaseContract> lease = leaseById(claim.orElseThrow(), request.leaseId().orElseThrow());
        if (lease.isEmpty()) {
            return failure(action, ClaimLeaseFailureReason.NO_LEASE, request.leaseId().orElseThrow().value().toString());
        }
        Optional<UUID> tenantId = playerId(lease.orElseThrow().tenant());
        if (tenantId.isEmpty() || !tenantId.orElseThrow().equals(request.playerId())) {
            return failure(action, ClaimLeaseFailureReason.NOT_TENANT, request.playerName());
        }
        if (action == ClaimLeaseAction.ACCEPT && lease.orElseThrow().status() != LeaseStatus.OFFERED) {
            return failure(action, ClaimLeaseFailureReason.NOT_OFFERED, lease.orElseThrow().status().name());
        }
        if (action == ClaimLeaseAction.RENEW && lease.orElseThrow().status() != LeaseStatus.ACTIVE) {
            return failure(action, ClaimLeaseFailureReason.NOT_ACTIVE, lease.orElseThrow().status().name());
        }
        if (ClaimRoleResolver.isAssigned(claim.orElseThrow(), new RoleId(config.roles().bannedRoleId()), request.playerId())) {
            return failure(action, ClaimLeaseFailureReason.TENANT_BANNED, request.playerName());
        }
        if (!pricing.economyActive(config, economyProvider)) {
            return failure(action, ClaimLeaseFailureReason.PAYMENT_FAILED, "No economy provider is available.");
        }

        String reference = action == ClaimLeaseAction.ACCEPT
                ? ClaimPricingService.REF_LEASE_ACCEPT
                : ClaimPricingService.REF_LEASE_RENEW;
        ClaimEconomyResult payment = economyProvider.transfer(
                ClaimEconomyAccountRef.playerPrimary(request.playerId()),
                ClaimEconomyAccountRef.playerPrimary(owner.orElseThrow().playerId()),
                lease.orElseThrow().price(),
                reference
        );
        if (!payment.success()) {
            return ClaimLeaseResult.failure(action, new ClaimLeaseFailure(ClaimLeaseFailureReason.PAYMENT_FAILED, payment.userMessage()), payment);
        }

        Map<RoleId, Set<UUID>> roles = mutableRoles(claim.orElseThrow().roleAssignments());
        Map<RoleId, Set<UUID>> invites = mutableRoles(claim.orElseThrow().pendingRoleInvites());
        boolean roleAlreadyAssigned = ClaimRoleResolver.isAssigned(claim.orElseThrow(), lease.orElseThrow().roleId(), request.playerId());
        boolean roleGranted = lease.orElseThrow().roleGranted() || !roleAlreadyAssigned;
        addToRole(roles, lease.orElseThrow().roleId(), request.playerId());
        removeFromAllRoles(invites, request.playerId());

        LeaseContract updatedLease = action == ClaimLeaseAction.ACCEPT
                ? lease.orElseThrow().activate(request.requestedAt(), roleGranted)
                : renewLeaseWindow(lease.orElseThrow(), request.requestedAt(), roleGranted);
        Map<LeaseId, LeaseContract> leases = mutableLeases(claim.orElseThrow());
        leases.put(updatedLease.id(), updatedLease);
        Claim updated = withLeasesAndRoles(claim.orElseThrow(), leases, roles, invites, request.requestedAt());

        try {
            ClaimView saved = claimService.saveClaim(updated);
            ClaimLeaseView leaseView = saved.leases().get(updatedLease.id());
            AuditEntry audit = audit(
                    request.actorKey(),
                    request.requestedAt(),
                    saved.id(),
                    action,
                    (action == ClaimLeaseAction.ACCEPT ? "accepted" : "renewed")
                            + " lease " + updatedLease.id().value()
                            + " for " + payment.formattedAmount()
                            + economyReferenceSuffix(payment)
            );
            postEvent(action, saved, leaseView, request.requestedAt());
            return ClaimLeaseResult.success(action, saved, leaseView, audit, payment);
        } catch (ClaimRepositoryException exception) {
            String rollbackReference = action == ClaimLeaseAction.ACCEPT
                    ? ClaimPricingService.REF_LEASE_ACCEPT_ROLLBACK
                    : ClaimPricingService.REF_LEASE_RENEW_ROLLBACK;
            ClaimEconomyResult rollback = economyProvider.transfer(
                    ClaimEconomyAccountRef.playerPrimary(owner.orElseThrow().playerId()),
                    ClaimEconomyAccountRef.playerPrimary(request.playerId()),
                    lease.orElseThrow().price(),
                    rollbackReference
            );
            return ClaimLeaseResult.failure(
                    action,
                    new ClaimLeaseFailure(ClaimLeaseFailureReason.SAVE_FAILED, exceptionDetail(exception) + rollbackDetail(rollback)),
                    rollback
            );
        }
    }

    private ClaimLeaseResult terminateLease(
            ClaimRepository repository,
            UcsClaimService claimService,
            ClaimLeaseRequest request,
            ClaimLeaseAction action,
            boolean requireActive
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(claimService, "claimService");
        Objects.requireNonNull(request, "request");

        Optional<Claim> claim = repository.findByChunk(request.chunk());
        if (claim.isEmpty()) {
            return failure(action, ClaimLeaseFailureReason.NO_CLAIM_AT_CHUNK, request.chunk().storageKey());
        }
        Optional<PlayerOwner> owner = playerOwner(claim.orElseThrow());
        if (owner.isEmpty()) {
            return failure(action, ClaimLeaseFailureReason.NOT_PLAYER_OWNED, claim.orElseThrow().owner().stableKey());
        }
        if (!owner.orElseThrow().playerId().equals(request.playerId())) {
            return failure(action, ClaimLeaseFailureReason.NOT_OWNER, request.chunk().storageKey());
        }
        Optional<LeaseContract> lease = leaseById(claim.orElseThrow(), request.leaseId().orElseThrow());
        if (lease.isEmpty()) {
            return failure(action, ClaimLeaseFailureReason.NO_LEASE, request.leaseId().orElseThrow().value().toString());
        }
        if (requireActive && lease.orElseThrow().status() != LeaseStatus.ACTIVE) {
            return failure(action, ClaimLeaseFailureReason.NOT_ACTIVE, lease.orElseThrow().status().name());
        }
        if (lease.orElseThrow().status() == LeaseStatus.CANCELLED || lease.orElseThrow().status() == LeaseStatus.EXPIRED) {
            return failure(action, ClaimLeaseFailureReason.NO_LEASE, lease.orElseThrow().status().name());
        }

        Map<RoleId, Set<UUID>> roles = mutableRoles(claim.orElseThrow().roleAssignments());
        LeaseContract cancelled = lease.orElseThrow().cancel();
        revokeLeaseRole(roles, cancelled);
        Map<LeaseId, LeaseContract> leases = mutableLeases(claim.orElseThrow());
        leases.put(cancelled.id(), cancelled);
        Claim updated = withLeasesAndRoles(claim.orElseThrow(), leases, roles, claim.orElseThrow().pendingRoleInvites(), request.requestedAt());

        return saveSuccess(
                claimService,
                action,
                updated,
                cancelled,
                request.requestedAt(),
                request.actorKey(),
                (action == ClaimLeaseAction.EVICT ? "evicted tenant for lease " : "cancelled lease ") + cancelled.id().value()
        );
    }

    private static LeaseContract renewLeaseWindow(LeaseContract lease, Instant now, boolean roleGranted) {
        Instant startsAt = lease.startsAt().orElse(now);
        Instant base = lease.expiresAt().filter(expiresAt -> expiresAt.isAfter(now)).orElse(now);
        return lease.renew(startsAt, base.plusSeconds(lease.durationSeconds()), roleGranted);
    }

    private ClaimLeaseResult saveSuccess(
            UcsClaimService claimService,
            ClaimLeaseAction action,
            Claim updated,
            LeaseContract lease,
            Instant requestedAt,
            String actorKey,
            String detail
    ) {
        try {
            ClaimView saved = claimService.saveClaim(updated);
            ClaimLeaseView leaseView = saved.leases().get(lease.id());
            AuditEntry audit = audit(actorKey, requestedAt, saved.id(), action, detail);
            postEvent(action, saved, leaseView, requestedAt);
            return ClaimLeaseResult.success(action, saved, leaseView, audit);
        } catch (ClaimRepositoryException exception) {
            return failure(action, ClaimLeaseFailureReason.SAVE_FAILED, exceptionDetail(exception));
        }
    }

    private static Claim withLease(Claim claim, LeaseContract lease, Instant updatedAt) {
        Map<LeaseId, LeaseContract> leases = mutableLeases(claim);
        leases.put(lease.id(), lease);
        return withLeasesAndRoles(claim, leases, claim.roleAssignments(), claim.pendingRoleInvites(), updatedAt);
    }

    private static Claim withLeasesAndRoles(
            Claim claim,
            Map<LeaseId, LeaseContract> leases,
            Map<RoleId, Set<UUID>> roleAssignments,
            Map<RoleId, Set<UUID>> pendingInvites,
            Instant updatedAt
    ) {
        return new Claim(
                claim.id(),
                claim.owner(),
                claim.chunks(),
                metadataUpdatedAt(claim, updatedAt),
                roleAssignments,
                pendingInvites,
                claim.flagOverrides(),
                claim.saleListing(),
                leases
        );
    }

    private static ClaimMetadata metadataUpdatedAt(Claim claim, Instant updatedAt) {
        return new ClaimMetadata(
                claim.metadata().displayName(),
                claim.metadata().description(),
                claim.metadata().spawn(),
                claim.metadata().createdAt(),
                updatedAt
        );
    }

    private static Optional<ClaimLeaseFailure> validateLeaseRole(UcsConfigSnapshot config, RoleId role) {
        if (!config.roles().defaultRoleIds().contains(role.value())
                || role.value().equals("owner")
                || role.value().equals(config.roles().bannedRoleId())) {
            return Optional.of(new ClaimLeaseFailure(ClaimLeaseFailureReason.ROLE_NOT_CONFIGURED, role.value()));
        }
        return Optional.empty();
    }

    private static boolean hasOpenLeaseForTenant(Claim claim, UUID tenantId) {
        return claim.leases().values().stream()
                .filter(lease -> lease.status() == LeaseStatus.OFFERED || lease.status() == LeaseStatus.ACTIVE)
                .map(lease -> playerId(lease.tenant()))
                .flatMap(Optional::stream)
                .anyMatch(tenantId::equals);
    }

    private static Optional<LeaseContract> leaseById(Claim claim, LeaseId leaseId) {
        return Optional.ofNullable(claim.leases().get(leaseId));
    }

    private static Optional<PlayerOwner> playerOwner(Claim claim) {
        return claim.owner() instanceof PlayerOwner player ? Optional.of(player) : Optional.empty();
    }

    private static Optional<UUID> playerId(com.nadirkhoulali.ucs.core.model.OwnerRef owner) {
        return owner instanceof PlayerOwner player ? Optional.of(player.playerId()) : Optional.empty();
    }

    private static Map<LeaseId, LeaseContract> mutableLeases(Claim claim) {
        return new LinkedHashMap<>(claim.leases());
    }

    private static Map<RoleId, Set<UUID>> mutableRoles(Map<RoleId, Set<UUID>> source) {
        Map<RoleId, Set<UUID>> copy = new LinkedHashMap<>();
        source.forEach((role, players) -> copy.put(role, new LinkedHashSet<>(players)));
        return copy;
    }

    private static void addToRole(Map<RoleId, Set<UUID>> roles, RoleId role, UUID playerId) {
        roles.computeIfAbsent(role, ignored -> new LinkedHashSet<>()).add(playerId);
    }

    private static void revokeLeaseRole(Map<RoleId, Set<UUID>> roles, LeaseContract lease) {
        if (!lease.roleGranted()) {
            return;
        }
        playerId(lease.tenant()).ifPresent(playerId -> {
            Optional.ofNullable(roles.get(lease.roleId())).ifPresent(players -> players.remove(playerId));
            roles.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        });
    }

    private static void removeFromAllRoles(Map<RoleId, Set<UUID>> roles, UUID playerId) {
        roles.values().forEach(players -> players.remove(playerId));
        roles.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private static AuditEntry audit(
            String actorKey,
            Instant occurredAt,
            ClaimId claimId,
            ClaimLeaseAction action,
            String detail
    ) {
        AuditAction auditAction = switch (action) {
            case ACCEPT, RENEW -> AuditAction.ECONOMY_TRANSACTION;
            case OFFER, CANCEL, EVICT, EXPIRE -> AuditAction.CLAIM_UPDATED;
        };
        return new AuditEntry(
                UUID.randomUUID(),
                occurredAt,
                actorKey,
                auditAction,
                Optional.of(claimId),
                detail
        );
    }

    private static void postEvent(ClaimLeaseAction action, ClaimView claim, ClaimLeaseView lease, Instant occurredAt) {
        NeoForge.EVENT_BUS.post(new UcsClaimLeaseEvent(action, claim, lease, occurredAt));
    }

    private static ClaimLeaseResult failure(ClaimLeaseAction action, ClaimLeaseFailureReason reason, String detail) {
        return ClaimLeaseResult.failure(action, new ClaimLeaseFailure(reason, detail));
    }

    private static String economyReferenceSuffix(ClaimEconomyResult result) {
        return result.providerReference().isBlank() ? "" : " ref " + result.providerReference();
    }

    private static String rollbackDetail(ClaimEconomyResult rollback) {
        if (rollback.success()) {
            return "; lease payment rollback transferred " + rollback.formattedAmount();
        }
        return "; lease payment rollback failed: " + rollback.userMessage();
    }

    private static String exceptionDetail(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
