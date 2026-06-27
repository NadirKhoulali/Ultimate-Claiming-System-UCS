package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.UcsClaimService;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyAccountRef;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyProvider;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyResult;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimId;
import com.nadirkhoulali.ucs.core.model.ClaimMetadata;
import com.nadirkhoulali.ucs.core.model.ClaimTaxLedgerEntry;
import com.nadirkhoulali.ucs.core.model.ClaimTaxLedgerStatus;
import com.nadirkhoulali.ucs.core.model.ClaimTaxState;
import com.nadirkhoulali.ucs.core.model.EconomyAuditAction;
import com.nadirkhoulali.ucs.core.model.EconomyAuditEntry;
import com.nadirkhoulali.ucs.core.model.EconomyAuditStatus;
import com.nadirkhoulali.ucs.core.model.LeaseContract;
import com.nadirkhoulali.ucs.core.model.LeaseId;
import com.nadirkhoulali.ucs.core.model.LeaseStatus;
import com.nadirkhoulali.ucs.core.model.PlayerOwner;
import com.nadirkhoulali.ucs.core.model.RoleId;
import com.nadirkhoulali.ucs.storage.ClaimRepository;
import com.nadirkhoulali.ucs.storage.ClaimRepositoryException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ClaimEconomyAdminService {
    public static final String REF_ADMIN_REFUND = "UCS_ADMIN_REFUND";
    public static final String REF_ADMIN_TAX_RETRY = "UCS_ADMIN_TAX_RETRY";
    public static final String REF_ADMIN_SALE_CANCEL = "UCS_ADMIN_SALE_CANCEL";
    public static final String REF_ADMIN_LEASE_CANCEL = "UCS_ADMIN_LEASE_CANCEL";
    public static final String REF_ADMIN_DEBT_CLEAR = "UCS_ADMIN_DEBT_CLEAR";

    private final ClaimPricingService pricing = new ClaimPricingService();

    public ClaimEconomyPreview preview(
            ClaimRepository repository,
            UcsConfigSnapshot config,
            ClaimTaxService taxService,
            ClaimEconomyProvider provider,
            Instant now,
            int taxLimit
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(taxService, "taxService");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(now, "now");
        if (taxLimit <= 0) {
            throw new IllegalArgumentException("taxLimit must be greater than zero");
        }

        int offeredLeases = 0;
        int activeLeases = 0;
        int saleListings = 0;
        for (Claim claim : repository.claims()) {
            if (claim.saleListing().isPresent()) {
                saleListings++;
            }
            for (LeaseContract lease : claim.leases().values()) {
                if (lease.status() == LeaseStatus.OFFERED) {
                    offeredLeases++;
                } else if (lease.status() == LeaseStatus.ACTIVE) {
                    activeLeases++;
                }
            }
        }

        int delinquentClaims = (int) repository.taxStates().stream()
                .filter(state -> state.outstandingDebt().signum() > 0)
                .count();
        return new ClaimEconomyPreview(
                provider.id(),
                provider.displayName(),
                provider.isAvailable(),
                BigDecimal.valueOf(config.economy().starterClaimPrice()),
                BigDecimal.valueOf(config.economy().pricePerExtraChunk()),
                config.economy().unclaimRefundRatio(),
                BigDecimal.valueOf(config.economy().maxClaimSalePrice()),
                config.claimTax().enabled(),
                BigDecimal.valueOf(config.claimTax().baseAmount()),
                BigDecimal.valueOf(config.claimTax().perChunkAmount()),
                saleListings,
                offeredLeases,
                activeLeases,
                delinquentClaims,
                taxService.previewUpcomingTaxes(repository, config, now, taxLimit)
        );
    }

    public List<EconomyAuditEntry> auditEntries(ClaimRepository repository, int limit) {
        Objects.requireNonNull(repository, "repository");
        validateLimit(limit);
        return sortedAudit(repository).stream()
                .limit(limit)
                .toList();
    }

    public List<EconomyAuditEntry> auditEntriesForClaim(ClaimRepository repository, ClaimId claimId, int limit) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(claimId, "claimId");
        validateLimit(limit);
        return sortedAudit(repository).stream()
                .filter(entry -> entry.claimId().filter(claimId::equals).isPresent())
                .limit(limit)
                .toList();
    }

    public List<EconomyAuditEntry> auditEntriesForOwner(ClaimRepository repository, String ownerKey, int limit) {
        Objects.requireNonNull(repository, "repository");
        ownerKey = Objects.requireNonNull(ownerKey, "ownerKey").trim();
        if (ownerKey.isEmpty()) {
            throw new IllegalArgumentException("ownerKey cannot be blank");
        }
        String stableOwnerKey = ownerKey;
        validateLimit(limit);
        return sortedAudit(repository).stream()
                .filter(entry -> entry.ownerKey().equals(stableOwnerKey))
                .limit(limit)
                .toList();
    }

    public ClaimEconomyAdminResult refundPlayer(
            ClaimRepository repository,
            ClaimEconomyProvider provider,
            String actorKey,
            UUID playerId,
            BigDecimal amount,
            String reason,
            Instant now
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(now, "now");
        reason = nonBlank(reason, "reason");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ClaimEconomyAdminResult.failure("amount must be greater than zero");
        }
        String reference = reference(REF_ADMIN_REFUND, playerId.toString(), now);
        if (!provider.isAvailable()) {
            EconomyAuditEntry audit = appendAudit(
                    repository,
                    actorKey,
                    EconomyAuditAction.ADMIN_REFUND,
                    EconomyAuditStatus.FAILED,
                    Optional.empty(),
                    "player:" + playerId,
                    amount,
                    reference,
                    provider,
                    "",
                    reason,
                    "refund failed: economy provider unavailable",
                    now
            );
            return ClaimEconomyAdminResult.failure("economy provider unavailable", audit);
        }

        ClaimEconomyResult refund = provider.refund(ClaimEconomyAccountRef.playerPrimary(playerId), amount, reference);
        EconomyAuditEntry audit = appendAudit(
                repository,
                actorKey,
                EconomyAuditAction.ADMIN_REFUND,
                refund.success() ? EconomyAuditStatus.SUCCESS : EconomyAuditStatus.FAILED,
                Optional.empty(),
                "player:" + playerId,
                amount,
                reference,
                provider,
                refund.providerReference(),
                reason,
                refund.success() ? "refunded " + refund.formattedAmount() : "refund failed: " + refund.userMessage(),
                now
        );
        if (refund.success()) {
            return ClaimEconomyAdminResult.success("refunded " + refund.formattedAmount(), audit);
        }
        return ClaimEconomyAdminResult.failure(refund.userMessage(), audit);
    }

    public ClaimEconomyAdminResult retryTax(
            ClaimRepository repository,
            UcsConfigSnapshot config,
            ClaimEconomyProvider provider,
            ClaimId claimId,
            String actorKey,
            Instant now
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(now, "now");

        Optional<Claim> claim = repository.findById(claimId);
        Optional<ClaimTaxState> state = repository.findTaxState(claimId);
        if (claim.isEmpty() || state.isEmpty()) {
            return ClaimEconomyAdminResult.failure("claim tax state not found");
        }
        if (!(claim.orElseThrow().owner() instanceof PlayerOwner owner)) {
            EconomyAuditEntry audit = appendAudit(
                    repository,
                    actorKey,
                    EconomyAuditAction.TAX_RETRY,
                    EconomyAuditStatus.FAILED,
                    Optional.of(claimId),
                    claim.orElseThrow().owner().stableKey(),
                    state.orElseThrow().outstandingDebt(),
                    reference(REF_ADMIN_TAX_RETRY, claimId.value().toString(), now),
                    provider,
                    "",
                    "retry tax",
                    "claim owner has no player payment source",
                    now
            );
            return ClaimEconomyAdminResult.failure("claim owner has no player payment source", audit);
        }
        BigDecimal amount = state.orElseThrow().outstandingDebt().max(BigDecimal.ZERO);
        if (!pricing.shouldTransact(amount)) {
            return ClaimEconomyAdminResult.failure("claim has no outstanding tax debt");
        }

        String reference = reference(REF_ADMIN_TAX_RETRY, claimId.value().toString(), now);
        ClaimEconomyResult charge = provider.isAvailable()
                ? provider.charge(ClaimEconomyAccountRef.playerPrimary(owner.playerId()), amount, reference)
                : ClaimEconomyResult.fail(
                        com.nadirkhoulali.ucs.api.economy.ClaimEconomyFailureReason.PROVIDER_UNAVAILABLE,
                        "No compatible economy provider is available.",
                        amount,
                        provider.format(amount)
                );
        ClaimTaxState updatedState = charge.success()
                ? state.orElseThrow().clearDebt(now.plus(Duration.ofHours(config.claimTax().intervalHours())), now)
                : state.orElseThrow().recordMissed(amount, now, now.plus(Duration.ofHours(config.nonpayment().retryIntervalHours())));
        repository.saveTaxState(updatedState);
        repository.appendTaxLedgerEntry(new ClaimTaxLedgerEntry(
                UUID.randomUUID(),
                claimId,
                claim.orElseThrow().owner().stableKey(),
                amount,
                state.orElseThrow().nextDueAt(),
                now,
                reference,
                charge.success() ? ClaimTaxLedgerStatus.PAID : ClaimTaxLedgerStatus.FAILED,
                charge.providerReference(),
                charge.success() ? "admin retry charged " + charge.formattedAmount() : "admin retry failed: " + charge.userMessage()
        ));
        EconomyAuditEntry audit = appendAudit(
                repository,
                actorKey,
                EconomyAuditAction.TAX_RETRY,
                charge.success() ? EconomyAuditStatus.SUCCESS : EconomyAuditStatus.FAILED,
                Optional.of(claimId),
                claim.orElseThrow().owner().stableKey(),
                amount,
                reference,
                provider,
                charge.providerReference(),
                "retry tax",
                charge.success() ? "admin retry charged " + charge.formattedAmount() : "admin retry failed: " + charge.userMessage(),
                now
        );
        if (charge.success()) {
            return ClaimEconomyAdminResult.success("tax retry charged " + charge.formattedAmount(), audit);
        }
        return ClaimEconomyAdminResult.failure(charge.userMessage(), audit);
    }

    public ClaimEconomyAdminResult cancelSale(
            ClaimRepository repository,
            UcsClaimService claimService,
            ClaimId claimId,
            String actorKey,
            String reason,
            Instant now
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(claimService, "claimService");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(now, "now");
        reason = nonBlank(reason, "reason");

        Optional<Claim> claim = repository.findById(claimId);
        if (claim.isEmpty()) {
            return ClaimEconomyAdminResult.failure("claim not found");
        }
        if (claim.orElseThrow().saleListing().isEmpty()) {
            return ClaimEconomyAdminResult.failure("claim is not listed for sale");
        }
        BigDecimal amount = claim.orElseThrow().saleListing().orElseThrow().price();
        Claim updated = withSaleListing(claim.orElseThrow(), now);
        try {
            claimService.saveClaim(updated);
        } catch (ClaimRepositoryException exception) {
            return ClaimEconomyAdminResult.failure(exceptionDetail(exception));
        }

        EconomyAuditEntry audit = appendAudit(
                repository,
                actorKey,
                EconomyAuditAction.SALE_CANCEL,
                EconomyAuditStatus.CANCELLED,
                Optional.of(claimId),
                claim.orElseThrow().owner().stableKey(),
                amount,
                reference(REF_ADMIN_SALE_CANCEL, claimId.value().toString(), now),
                null,
                "",
                reason,
                "admin cancelled sale listing",
                now
        );
        return ClaimEconomyAdminResult.success("sale listing cancelled", audit);
    }

    public ClaimEconomyAdminResult cancelLease(
            ClaimRepository repository,
            UcsClaimService claimService,
            LeaseId leaseId,
            String actorKey,
            String reason,
            Instant now
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(claimService, "claimService");
        Objects.requireNonNull(leaseId, "leaseId");
        Objects.requireNonNull(now, "now");
        reason = nonBlank(reason, "reason");

        Optional<Claim> claim = repository.claims().stream()
                .filter(candidate -> candidate.leases().containsKey(leaseId))
                .findFirst();
        if (claim.isEmpty()) {
            return ClaimEconomyAdminResult.failure("lease not found");
        }
        LeaseContract lease = claim.orElseThrow().leases().get(leaseId);
        if (lease.status() == LeaseStatus.CANCELLED || lease.status() == LeaseStatus.EXPIRED) {
            return ClaimEconomyAdminResult.failure("lease is already closed");
        }

        Map<LeaseId, LeaseContract> leases = new LinkedHashMap<>(claim.orElseThrow().leases());
        LeaseContract cancelled = lease.cancel();
        leases.put(cancelled.id(), cancelled);
        Map<RoleId, Set<UUID>> roles = mutableRoles(claim.orElseThrow().roleAssignments());
        revokeLeaseRole(roles, cancelled);
        Claim updated = withLeasesAndRoles(claim.orElseThrow(), leases, roles, now);
        try {
            claimService.saveClaim(updated);
        } catch (ClaimRepositoryException exception) {
            return ClaimEconomyAdminResult.failure(exceptionDetail(exception));
        }

        EconomyAuditEntry audit = appendAudit(
                repository,
                actorKey,
                EconomyAuditAction.LEASE_CANCEL,
                EconomyAuditStatus.CANCELLED,
                Optional.of(claim.orElseThrow().id()),
                claim.orElseThrow().owner().stableKey(),
                lease.price(),
                reference(REF_ADMIN_LEASE_CANCEL, leaseId.value().toString(), now),
                null,
                "",
                reason,
                "admin cancelled lease " + leaseId.value(),
                now
        );
        return ClaimEconomyAdminResult.success("lease cancelled", audit);
    }

    public EconomyAuditEntry recordDebtClear(
            ClaimRepository repository,
            ClaimId claimId,
            String ownerKey,
            BigDecimal amount,
            String actorKey,
            String reason,
            Instant now
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(now, "now");
        return appendAudit(
                repository,
                actorKey,
                EconomyAuditAction.DEBT_CLEAR,
                EconomyAuditStatus.OVERRIDDEN,
                Optional.of(claimId),
                ownerKey,
                amount.max(BigDecimal.ZERO),
                reference(REF_ADMIN_DEBT_CLEAR, claimId.value().toString(), now),
                null,
                "",
                nonBlank(reason, "reason"),
                "admin cleared recorded debt",
                now
        );
    }

    private List<EconomyAuditEntry> sortedAudit(ClaimRepository repository) {
        return repository.economyAuditEntries().stream()
                .sorted(Comparator.comparing(EconomyAuditEntry::occurredAt).reversed()
                        .thenComparing(entry -> entry.id().toString()))
                .toList();
    }

    private static void validateLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
    }

    private static EconomyAuditEntry appendAudit(
            ClaimRepository repository,
            String actorKey,
            EconomyAuditAction action,
            EconomyAuditStatus status,
            Optional<ClaimId> claimId,
            String ownerKey,
            BigDecimal amount,
            String reference,
            ClaimEconomyProvider provider,
            String providerReference,
            String reason,
            String detail,
            Instant now
    ) {
        String providerId = provider == null ? "" : provider.id();
        EconomyAuditEntry entry = new EconomyAuditEntry(
                UUID.randomUUID(),
                now,
                nonBlank(actorKey, "actorKey"),
                action,
                status,
                claimId,
                nonBlank(ownerKey, "ownerKey"),
                amount,
                reference,
                providerId,
                providerReference,
                reason,
                detail
        );
        return repository.appendEconomyAuditEntry(entry);
    }

    private static String reference(String prefix, String subject, Instant now) {
        return prefix + ":" + subject + ":" + now.toEpochMilli();
    }

    private static Claim withSaleListing(Claim claim, Instant updatedAt) {
        return new Claim(
                claim.id(),
                claim.owner(),
                claim.chunks(),
                metadataUpdatedAt(claim, updatedAt),
                claim.roleAssignments(),
                claim.pendingRoleInvites(),
                claim.flagOverrides(),
                Optional.empty(),
                claim.leases()
        );
    }

    private static Claim withLeasesAndRoles(
            Claim claim,
            Map<LeaseId, LeaseContract> leases,
            Map<RoleId, Set<UUID>> roleAssignments,
            Instant updatedAt
    ) {
        return new Claim(
                claim.id(),
                claim.owner(),
                claim.chunks(),
                metadataUpdatedAt(claim, updatedAt),
                roleAssignments,
                claim.pendingRoleInvites(),
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

    private static Map<RoleId, Set<UUID>> mutableRoles(Map<RoleId, Set<UUID>> source) {
        Map<RoleId, Set<UUID>> roles = new LinkedHashMap<>();
        source.forEach((role, players) -> roles.put(role, new LinkedHashSet<>(players)));
        return roles;
    }

    private static void revokeLeaseRole(Map<RoleId, Set<UUID>> roles, LeaseContract lease) {
        if (!lease.roleGranted() || !(lease.tenant() instanceof PlayerOwner player)) {
            return;
        }
        Optional.ofNullable(roles.get(lease.roleId())).ifPresent(players -> players.remove(player.playerId()));
        roles.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private static String nonBlank(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return normalized;
    }

    private static String exceptionDetail(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
