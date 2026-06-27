package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.api.UcsClaimService;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyAccountRef;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyProvider;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyResult;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.AuditAction;
import com.nadirkhoulali.ucs.core.model.AuditEntry;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimMetadata;
import com.nadirkhoulali.ucs.core.model.ClaimOwnership;
import com.nadirkhoulali.ucs.core.model.ClaimSaleListing;
import com.nadirkhoulali.ucs.core.model.PlayerOwner;
import com.nadirkhoulali.ucs.core.model.RoleId;
import com.nadirkhoulali.ucs.storage.ClaimRepository;
import com.nadirkhoulali.ucs.storage.ClaimRepositoryException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ClaimSaleService {
    private static final RoleId OWNER_ROLE = new RoleId("owner");
    private final ClaimPricingService pricing = new ClaimPricingService();

    public ClaimSaleResult listClaimForSale(
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            ClaimSaleRequest request
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(claimService, "claimService");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(request, "request");

        Optional<Claim> claim = repository.findByChunk(request.chunk());
        if (claim.isEmpty()) {
            return failure(ClaimSaleAction.LIST, ClaimSaleFailureReason.NO_CLAIM_AT_CHUNK, request.chunk().storageKey());
        }
        Optional<PlayerOwner> owner = playerOwner(claim.orElseThrow());
        if (owner.isEmpty()) {
            return failure(ClaimSaleAction.LIST, ClaimSaleFailureReason.NOT_PLAYER_OWNED, claim.orElseThrow().owner().stableKey());
        }
        if (!owner.orElseThrow().playerId().equals(request.playerId())) {
            return failure(ClaimSaleAction.LIST, ClaimSaleFailureReason.NOT_OWNER, request.chunk().storageKey());
        }
        if (claim.orElseThrow().saleListing().isPresent()) {
            return failure(ClaimSaleAction.LIST, ClaimSaleFailureReason.ALREADY_LISTED, claim.orElseThrow().id().value().toString());
        }

        BigDecimal price = request.price().orElse(BigDecimal.ZERO);
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            return failure(ClaimSaleAction.LIST, ClaimSaleFailureReason.PRICE_TOO_LOW, price.toPlainString());
        }
        BigDecimal max = BigDecimal.valueOf(config.economy().maxClaimSalePrice());
        if (price.compareTo(max) > 0) {
            return failure(ClaimSaleAction.LIST, ClaimSaleFailureReason.PRICE_TOO_HIGH, max.toPlainString());
        }

        ClaimSaleListing listing = new ClaimSaleListing(
                UUID.randomUUID(),
                request.playerId(),
                request.playerName(),
                price,
                request.requestedAt()
        );
        Claim updated = withSaleListing(claim.orElseThrow(), Optional.of(listing), request.requestedAt());
        try {
            ClaimView saved = claimService.saveClaim(updated);
            return ClaimSaleResult.success(
                    ClaimSaleAction.LIST,
                    saved,
                    audit(request, saved.id(), "listed claim for " + price.toPlainString() + " listing " + listing.listingId())
            );
        } catch (ClaimRepositoryException exception) {
            return failure(ClaimSaleAction.LIST, ClaimSaleFailureReason.SAVE_FAILED, exceptionDetail(exception));
        }
    }

    public ClaimSaleResult cancelSale(
            ClaimRepository repository,
            UcsClaimService claimService,
            ClaimSaleRequest request
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(claimService, "claimService");
        Objects.requireNonNull(request, "request");

        Optional<Claim> claim = repository.findByChunk(request.chunk());
        if (claim.isEmpty()) {
            return failure(ClaimSaleAction.CANCEL, ClaimSaleFailureReason.NO_CLAIM_AT_CHUNK, request.chunk().storageKey());
        }
        Optional<PlayerOwner> owner = playerOwner(claim.orElseThrow());
        if (owner.isEmpty()) {
            return failure(ClaimSaleAction.CANCEL, ClaimSaleFailureReason.NOT_PLAYER_OWNED, claim.orElseThrow().owner().stableKey());
        }
        if (!owner.orElseThrow().playerId().equals(request.playerId())) {
            return failure(ClaimSaleAction.CANCEL, ClaimSaleFailureReason.NOT_OWNER, request.chunk().storageKey());
        }
        if (claim.orElseThrow().saleListing().isEmpty()) {
            return failure(ClaimSaleAction.CANCEL, ClaimSaleFailureReason.NOT_LISTED, claim.orElseThrow().id().value().toString());
        }

        Claim updated = withSaleListing(claim.orElseThrow(), Optional.empty(), request.requestedAt());
        try {
            ClaimView saved = claimService.saveClaim(updated);
            return ClaimSaleResult.success(
                    ClaimSaleAction.CANCEL,
                    saved,
                    audit(request, saved.id(), "cancelled claim sale")
            );
        } catch (ClaimRepositoryException exception) {
            return failure(ClaimSaleAction.CANCEL, ClaimSaleFailureReason.SAVE_FAILED, exceptionDetail(exception));
        }
    }

    public ClaimSaleResult purchaseClaim(
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            ClaimSaleRequest request,
            ClaimEconomyProvider economyProvider
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(claimService, "claimService");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(request, "request");

        Optional<Claim> claim = repository.findByChunk(request.chunk());
        if (claim.isEmpty()) {
            return failure(ClaimSaleAction.PURCHASE, ClaimSaleFailureReason.NO_CLAIM_AT_CHUNK, request.chunk().storageKey());
        }
        Optional<PlayerOwner> owner = playerOwner(claim.orElseThrow());
        if (owner.isEmpty()) {
            return failure(ClaimSaleAction.PURCHASE, ClaimSaleFailureReason.NOT_PLAYER_OWNED, claim.orElseThrow().owner().stableKey());
        }
        Optional<ClaimSaleListing> listing = claim.orElseThrow().saleListing();
        if (listing.isEmpty()) {
            return failure(ClaimSaleAction.PURCHASE, ClaimSaleFailureReason.NOT_LISTED, claim.orElseThrow().id().value().toString());
        }
        if (request.expectedListingId().isPresent()
                && !request.expectedListingId().orElseThrow().equals(listing.orElseThrow().listingId())) {
            return failure(ClaimSaleAction.PURCHASE, ClaimSaleFailureReason.STALE_LISTING, listing.orElseThrow().listingId().toString());
        }
        if (!owner.orElseThrow().playerId().equals(listing.orElseThrow().sellerPlayerId())) {
            return failure(ClaimSaleAction.PURCHASE, ClaimSaleFailureReason.STALE_LISTING, owner.orElseThrow().playerId().toString());
        }
        if (request.playerId().equals(owner.orElseThrow().playerId())) {
            return failure(ClaimSaleAction.PURCHASE, ClaimSaleFailureReason.SELF_PURCHASE, claim.orElseThrow().id().value().toString());
        }
        Optional<ClaimSaleResult> buyerLimitFailure = buyerLimitFailure(repository, config, claim.orElseThrow(), request);
        if (buyerLimitFailure.isPresent()) {
            return buyerLimitFailure.orElseThrow();
        }
        if (!pricing.economyActive(config, economyProvider)) {
            return failure(ClaimSaleAction.PURCHASE, ClaimSaleFailureReason.PAYMENT_FAILED, "No economy provider is available.");
        }

        ClaimEconomyResult payment = economyProvider.transfer(
                ClaimEconomyAccountRef.playerPrimary(request.playerId()),
                ClaimEconomyAccountRef.playerPrimary(listing.orElseThrow().sellerPlayerId()),
                listing.orElseThrow().price(),
                ClaimPricingService.REF_CLAIM_SALE_PURCHASE
        );
        if (!payment.success()) {
            return ClaimSaleResult.failure(
                    ClaimSaleAction.PURCHASE,
                    new ClaimSaleFailure(ClaimSaleFailureReason.PAYMENT_FAILED, payment.userMessage()),
                    payment
            );
        }

        Claim updated = transferToBuyer(claim.orElseThrow(), request, request.requestedAt());
        try {
            ClaimView saved = claimService.saveClaim(updated);
            return ClaimSaleResult.success(
                    ClaimSaleAction.PURCHASE,
                    saved,
                    audit(
                            request,
                            saved.id(),
                            "purchased claim for " + payment.formattedAmount() + economyReferenceSuffix(payment)
                    ),
                    payment
            );
        } catch (ClaimRepositoryException exception) {
            ClaimEconomyResult rollback = economyProvider.transfer(
                    ClaimEconomyAccountRef.playerPrimary(listing.orElseThrow().sellerPlayerId()),
                    ClaimEconomyAccountRef.playerPrimary(request.playerId()),
                    listing.orElseThrow().price(),
                    ClaimPricingService.REF_CLAIM_SALE_ROLLBACK
            );
            return ClaimSaleResult.failure(
                    ClaimSaleAction.PURCHASE,
                    new ClaimSaleFailure(
                            ClaimSaleFailureReason.SAVE_FAILED,
                            exceptionDetail(exception) + rollbackDetail(rollback)
                    ),
                    rollback
            );
        }
    }

    private Optional<ClaimSaleResult> buyerLimitFailure(
            ClaimRepository repository,
            UcsConfigSnapshot config,
            Claim claim,
            ClaimSaleRequest request
    ) {
        PlayerOwner buyer = ClaimOwnership.player(request.playerId(), request.playerName());
        long buyerClaimCount = repository.claims().stream()
                .filter(candidate -> ClaimOwnership.isOwnedBy(candidate, buyer))
                .count();
        if (buyerClaimCount >= config.claimLimits().maxClaimsPerPlayer()) {
            return Optional.of(failure(
                    ClaimSaleAction.PURCHASE,
                    ClaimSaleFailureReason.BUYER_LIMIT_EXCEEDED,
                    Integer.toString(config.claimLimits().maxClaimsPerPlayer())
            ));
        }
        int buyerChunks = repository.claims().stream()
                .filter(candidate -> ClaimOwnership.isOwnedBy(candidate, buyer))
                .mapToInt(candidate -> candidate.chunks().size())
                .sum();
        if (buyerChunks + claim.chunks().size() > config.claimLimits().maxChunksPerPlayer()) {
            return Optional.of(failure(
                    ClaimSaleAction.PURCHASE,
                    ClaimSaleFailureReason.BUYER_LIMIT_EXCEEDED,
                    Integer.toString(config.claimLimits().maxChunksPerPlayer())
            ));
        }
        return Optional.empty();
    }

    private static Claim withSaleListing(Claim claim, Optional<ClaimSaleListing> listing, Instant updatedAt) {
        return new Claim(
                claim.id(),
                claim.owner(),
                claim.chunks(),
                metadataUpdatedAt(claim, updatedAt),
                claim.roleAssignments(),
                claim.pendingRoleInvites(),
                claim.flagOverrides(),
                listing
        );
    }

    private static Claim transferToBuyer(Claim claim, ClaimSaleRequest request, Instant updatedAt) {
        PlayerOwner buyer = ClaimOwnership.player(request.playerId(), request.playerName());
        return new Claim(
                claim.id(),
                buyer,
                claim.chunks(),
                metadataUpdatedAt(claim, updatedAt),
                ownershipRoles(claim, request.playerId()),
                pendingInvitesWithout(claim, request.playerId()),
                claim.flagOverrides(),
                Optional.empty()
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

    private static Map<RoleId, Set<UUID>> ownershipRoles(Claim claim, UUID buyerId) {
        Map<RoleId, Set<UUID>> roles = mutableRoles(claim.roleAssignments());
        roles.values().forEach(players -> players.remove(buyerId));
        roles.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        roles.put(OWNER_ROLE, new LinkedHashSet<>(Set.of(buyerId)));
        return immutableRoles(roles);
    }

    private static Map<RoleId, Set<UUID>> pendingInvitesWithout(Claim claim, UUID buyerId) {
        Map<RoleId, Set<UUID>> invites = mutableRoles(claim.pendingRoleInvites());
        invites.values().forEach(players -> players.remove(buyerId));
        invites.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        return immutableRoles(invites);
    }

    private static Map<RoleId, Set<UUID>> mutableRoles(Map<RoleId, Set<UUID>> source) {
        Map<RoleId, Set<UUID>> roles = new LinkedHashMap<>();
        source.forEach((role, players) -> roles.put(role, new LinkedHashSet<>(players)));
        return roles;
    }

    private static Map<RoleId, Set<UUID>> immutableRoles(Map<RoleId, Set<UUID>> source) {
        Map<RoleId, Set<UUID>> roles = new LinkedHashMap<>();
        source.forEach((role, players) -> roles.put(role, Set.copyOf(players)));
        return Map.copyOf(roles);
    }

    private static Optional<PlayerOwner> playerOwner(Claim claim) {
        return claim.owner() instanceof PlayerOwner player ? Optional.of(player) : Optional.empty();
    }

    private static ClaimSaleResult failure(ClaimSaleAction action, ClaimSaleFailureReason reason, String detail) {
        return ClaimSaleResult.failure(action, new ClaimSaleFailure(reason, detail));
    }

    private static AuditEntry audit(ClaimSaleRequest request, com.nadirkhoulali.ucs.core.model.ClaimId claimId, String detail) {
        return new AuditEntry(
                UUID.randomUUID(),
                request.requestedAt(),
                "player:" + request.playerId(),
                AuditAction.ECONOMY_TRANSACTION,
                Optional.of(claimId),
                detail
        );
    }

    private static String economyReferenceSuffix(ClaimEconomyResult result) {
        return result.providerReference().isBlank() ? "" : " ref " + result.providerReference();
    }

    private static String rollbackDetail(ClaimEconomyResult rollback) {
        if (rollback.success()) {
            return "; purchase rollback transferred " + rollback.formattedAmount();
        }
        return "; purchase rollback failed: " + rollback.userMessage();
    }

    private static String exceptionDetail(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
