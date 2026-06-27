package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.ClaimArchiveView;
import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.api.UcsClaimService;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyAccountRef;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyProvider;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyResult;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.ArchiveId;
import com.nadirkhoulali.ucs.core.model.AuditAction;
import com.nadirkhoulali.ucs.core.model.AuditEntry;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimChunk;
import com.nadirkhoulali.ucs.core.model.ClaimId;
import com.nadirkhoulali.ucs.core.model.ClaimMetadata;
import com.nadirkhoulali.ucs.core.model.ClaimOwnership;
import com.nadirkhoulali.ucs.core.model.PlayerOwner;
import com.nadirkhoulali.ucs.storage.ClaimRepository;
import com.nadirkhoulali.ucs.storage.ClaimRepositoryException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ClaimChunkEditService {
    private final ClaimPricingService pricing = new ClaimPricingService();

    public ClaimChunkEditResult addChunk(
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            ClaimChunkEditRequest request
    ) {
        return addChunk(repository, claimService, config, request, null);
    }

    public ClaimChunkEditResult addChunk(
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            ClaimChunkEditRequest request,
            ClaimEconomyProvider economyProvider
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(claimService, "claimService");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(request, "request");

        if (repository.findByChunk(request.chunk()).isPresent()) {
            return failure(ClaimChunkEditAction.ADD, ClaimChunkEditFailureReason.CHUNK_ALREADY_CLAIMED, request.chunk().storageKey(), request.chunk());
        }

        PlayerOwner owner = owner(request);
        List<Claim> adjacentClaims = repository.claims().stream()
                .filter(claim -> sameOwner(claim, owner))
                .filter(claim -> ClaimShape.touches(claim, request.chunk()))
                .toList();
        if (adjacentClaims.isEmpty()) {
            return failure(ClaimChunkEditAction.ADD, ClaimChunkEditFailureReason.NOT_ADJACENT, request.chunk().storageKey(), request.chunk());
        }
        if (adjacentClaims.size() > 1) {
            return failure(ClaimChunkEditAction.ADD, ClaimChunkEditFailureReason.AMBIGUOUS_ADJACENT_CLAIMS, request.chunk().storageKey(), request.chunk());
        }

        Claim target = adjacentClaims.getFirst();
        Set<ClaimChunk> updatedChunks = mutableChunks(target);
        updatedChunks.add(new ClaimChunk(request.chunk()));
        Optional<ClaimChunkEditFailure> limitFailure = validateChunkLimits(repository, config, owner, target, updatedChunks.size());
        if (limitFailure.isPresent()) {
            return ClaimChunkEditResult.failure(ClaimChunkEditAction.ADD, limitFailure.orElseThrow(), Set.of(request.chunk()));
        }

        ClaimEconomyResult economyCharge = chargeChunkAddIfNeeded(config, request, economyProvider);
        if (economyCharge != null && !economyCharge.success()) {
            return failure(
                    ClaimChunkEditAction.ADD,
                    ClaimChunkEditFailureReason.PAYMENT_FAILED,
                    economyCharge.userMessage(),
                    request.chunk(),
                    economyCharge
            );
        }

        ClaimChunkEditResult result = saveSingle(
                ClaimChunkEditAction.ADD,
                claimService,
                withChunks(target, updatedChunks, request.requestedAt(), target.metadata().displayName()),
                owner,
                request.requestedAt(),
                Set.of(request.chunk()),
                "added chunk " + request.chunk().storageKey() + economyAuditSuffix("charged", economyCharge),
                economyCharge
        );
        return rollbackChunkAddOnSaveFailure(result, config, request, economyProvider, economyCharge);
    }

    public ClaimChunkEditResult removeChunk(
            ClaimRepository repository,
            UcsClaimService claimService,
            ClaimChunkEditRequest request
    ) {
        return removeChunk(repository, claimService, null, request, null);
    }

    public ClaimChunkEditResult removeChunk(
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            ClaimChunkEditRequest request,
            ClaimEconomyProvider economyProvider
    ) {
        Optional<Claim> claim = repository.findByChunk(request.chunk());
        if (claim.isEmpty()) {
            return failure(ClaimChunkEditAction.REMOVE, ClaimChunkEditFailureReason.NO_CLAIM_AT_CHUNK, request.chunk().storageKey(), request.chunk());
        }
        PlayerOwner owner = owner(request);
        if (!sameOwner(claim.orElseThrow(), owner)) {
            return failure(ClaimChunkEditAction.REMOVE, ClaimChunkEditFailureReason.NOT_OWNER, request.chunk().storageKey(), request.chunk());
        }

        Set<ClaimChunk> remaining = mutableChunks(claim.orElseThrow());
        remaining.remove(new ClaimChunk(request.chunk()));
        if (remaining.isEmpty()) {
            return failure(ClaimChunkEditAction.REMOVE, ClaimChunkEditFailureReason.CANNOT_REMOVE_ONLY_CHUNK, request.chunk().storageKey(), request.chunk());
        }
        if (!ClaimShape.isConnected(remaining)) {
            return failure(ClaimChunkEditAction.REMOVE, ClaimChunkEditFailureReason.WOULD_SPLIT, request.chunk().storageKey(), request.chunk());
        }

        ClaimChunkEditResult result = saveSingle(
                ClaimChunkEditAction.REMOVE,
                claimService,
                withChunks(claim.orElseThrow(), remaining, request.requestedAt(), claim.orElseThrow().metadata().displayName()),
                owner,
                request.requestedAt(),
                Set.of(request.chunk()),
                "removed chunk " + request.chunk().storageKey()
        );
        return refundRemovedChunkIfNeeded(result, config, request, economyProvider, ClaimPricingService.REF_CHUNK_REMOVE_REFUND);
    }

    public ClaimChunkEditResult splitClaim(
            ClaimRepository repository,
            UcsClaimService claimService,
            ClaimChunkEditRequest request
    ) {
        return splitClaim(repository, claimService, null, request, null);
    }

    public ClaimChunkEditResult splitClaim(
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            ClaimChunkEditRequest request,
            ClaimEconomyProvider economyProvider
    ) {
        Optional<Claim> original = repository.findByChunk(request.chunk());
        if (original.isEmpty()) {
            return failure(ClaimChunkEditAction.SPLIT, ClaimChunkEditFailureReason.NO_CLAIM_AT_CHUNK, request.chunk().storageKey(), request.chunk());
        }
        PlayerOwner owner = owner(request);
        if (!sameOwner(original.orElseThrow(), owner)) {
            return failure(ClaimChunkEditAction.SPLIT, ClaimChunkEditFailureReason.NOT_OWNER, request.chunk().storageKey(), request.chunk());
        }

        Set<ClaimChunk> remaining = mutableChunks(original.orElseThrow());
        remaining.remove(new ClaimChunk(request.chunk()));
        if (remaining.isEmpty()) {
            return failure(ClaimChunkEditAction.SPLIT, ClaimChunkEditFailureReason.CANNOT_REMOVE_ONLY_CHUNK, request.chunk().storageKey(), request.chunk());
        }

        List<Set<ClaimChunk>> components = ClaimShape.connectedComponents(remaining);
        if (components.size() == 1) {
            ClaimChunkEditResult result = saveSingle(
                    ClaimChunkEditAction.SPLIT,
                    claimService,
                    withChunks(original.orElseThrow(), remaining, request.requestedAt(), original.orElseThrow().metadata().displayName()),
                    owner,
                    request.requestedAt(),
                    Set.of(request.chunk()),
                    "removed chunk " + request.chunk().storageKey()
            );
            return refundRemovedChunkIfNeeded(result, config, request, economyProvider, ClaimPricingService.REF_CHUNK_SPLIT_REFUND);
        }

        List<Claim> splitClaims = splitClaims(original.orElseThrow(), components, request.requestedAt());
        Set<ChunkKey> affectedChunks = new LinkedHashSet<>(ClaimShape.keys(original.orElseThrow().chunks()));
        ClaimArchiveView archivedOriginal = null;
        try {
            archivedOriginal = archiveClaim(
                    claimService,
                    original.orElseThrow(),
                    owner,
                    request.requestedAt(),
                    "split claim after removing " + request.chunk().storageKey()
            );
            List<ClaimView> saved = new ArrayList<>();
            for (Claim splitClaim : splitClaims) {
                saved.add(claimService.saveClaim(splitClaim));
            }
            ClaimChunkEditResult result = ClaimChunkEditResult.success(
                    ClaimChunkEditAction.SPLIT,
                    saved,
                    audit(owner, request.requestedAt(), saved.getFirst().id(), "split claim after removing " + request.chunk().storageKey()),
                    affectedChunks
            );
            return refundRemovedChunkIfNeeded(result, config, request, economyProvider, ClaimPricingService.REF_CHUNK_SPLIT_REFUND);
        } catch (RuntimeException exception) {
            rollbackSplit(claimService, splitClaims);
            if (archivedOriginal != null) {
                claimService.restoreClaim(archivedOriginal.id());
            }
            return failure(ClaimChunkEditAction.SPLIT, ClaimChunkEditFailureReason.SAVE_FAILED, exceptionDetail(exception), request.chunk());
        }
    }

    public ClaimChunkEditResult mergeAdjacentClaims(
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            ClaimChunkEditRequest request
    ) {
        Optional<Claim> base = repository.findByChunk(request.chunk());
        if (base.isEmpty()) {
            return failure(ClaimChunkEditAction.MERGE, ClaimChunkEditFailureReason.NO_CLAIM_AT_CHUNK, request.chunk().storageKey(), request.chunk());
        }
        PlayerOwner owner = owner(request);
        if (!sameOwner(base.orElseThrow(), owner)) {
            return failure(ClaimChunkEditAction.MERGE, ClaimChunkEditFailureReason.NOT_OWNER, request.chunk().storageKey(), request.chunk());
        }

        List<Claim> mergeGroup = connectedOwnedClaimGroup(repository, base.orElseThrow(), owner);
        if (mergeGroup.size() < 2) {
            return failure(ClaimChunkEditAction.MERGE, ClaimChunkEditFailureReason.NO_MERGE_TARGETS, request.chunk().storageKey(), request.chunk());
        }

        Set<ClaimChunk> mergedChunks = mergeGroup.stream()
                .flatMap(claim -> claim.chunks().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (mergedChunks.size() > config.claimLimits().maxChunksPerClaim()) {
            return failure(
                    ClaimChunkEditAction.MERGE,
                    ClaimChunkEditFailureReason.CLAIM_TOO_LARGE,
                    Integer.toString(config.claimLimits().maxChunksPerClaim()),
                    request.chunk()
            );
        }

        Set<ChunkKey> affectedChunks = ClaimShape.keys(mergedChunks);
        List<ClaimArchiveView> archivedTargets = new ArrayList<>();
        try {
            for (Claim target : mergeGroup) {
                if (!target.id().equals(base.orElseThrow().id())) {
                    archivedTargets.add(archiveClaim(
                            claimService,
                            target,
                            owner,
                            request.requestedAt(),
                            "merged into " + base.orElseThrow().id().value()
                    ));
                }
            }
            Claim merged = withChunks(base.orElseThrow(), mergedChunks, request.requestedAt(), base.orElseThrow().metadata().displayName());
            ClaimView saved = claimService.saveClaim(merged);
            return ClaimChunkEditResult.success(
                    ClaimChunkEditAction.MERGE,
                    List.of(saved),
                    audit(owner, request.requestedAt(), saved.id(), "merged " + mergeGroup.size() + " claims"),
                    affectedChunks
            );
        } catch (RuntimeException exception) {
            claimService.saveClaim(base.orElseThrow());
            restoreArchives(claimService, archivedTargets);
            return failure(ClaimChunkEditAction.MERGE, ClaimChunkEditFailureReason.SAVE_FAILED, exceptionDetail(exception), request.chunk());
        }
    }

    private ClaimChunkEditResult saveSingle(
            ClaimChunkEditAction action,
            UcsClaimService claimService,
            Claim claim,
            PlayerOwner owner,
            Instant requestedAt,
            Set<ChunkKey> affectedChunks,
            String auditDetail
    ) {
        return saveSingle(action, claimService, claim, owner, requestedAt, affectedChunks, auditDetail, null);
    }

    private ClaimChunkEditResult saveSingle(
            ClaimChunkEditAction action,
            UcsClaimService claimService,
            Claim claim,
            PlayerOwner owner,
            Instant requestedAt,
            Set<ChunkKey> affectedChunks,
            String auditDetail,
            ClaimEconomyResult economyResult
    ) {
        try {
            ClaimView saved = claimService.saveClaim(claim);
            return economyResult == null
                    ? ClaimChunkEditResult.success(
                            action,
                            List.of(saved),
                            audit(owner, requestedAt, saved.id(), auditDetail),
                            affectedChunks
                    )
                    : ClaimChunkEditResult.success(
                            action,
                            List.of(saved),
                            audit(owner, requestedAt, saved.id(), auditDetail),
                            economyResult,
                            affectedChunks
                    );
        } catch (ClaimRepositoryException exception) {
            return ClaimChunkEditResult.failure(
                    action,
                    new ClaimChunkEditFailure(ClaimChunkEditFailureReason.SAVE_FAILED, exceptionDetail(exception)),
                    affectedChunks
            );
        }
    }

    private Optional<ClaimChunkEditFailure> validateChunkLimits(
            ClaimRepository repository,
            UcsConfigSnapshot config,
            PlayerOwner owner,
            Claim editedClaim,
            int editedClaimChunkCount
    ) {
        if (editedClaimChunkCount > config.claimLimits().maxChunksPerClaim()) {
            return Optional.of(new ClaimChunkEditFailure(
                    ClaimChunkEditFailureReason.CLAIM_TOO_LARGE,
                    Integer.toString(config.claimLimits().maxChunksPerClaim())
            ));
        }

        int ownedChunkCount = repository.claims().stream()
                .filter(claim -> sameOwner(claim, owner))
                .mapToInt(claim -> claim.id().equals(editedClaim.id()) ? editedClaimChunkCount : claim.chunks().size())
                .sum();
        if (ownedChunkCount > config.claimLimits().maxChunksPerPlayer()) {
            return Optional.of(new ClaimChunkEditFailure(
                    ClaimChunkEditFailureReason.TOO_MANY_CHUNKS,
                    Integer.toString(config.claimLimits().maxChunksPerPlayer())
            ));
        }
        return Optional.empty();
    }

    private ClaimEconomyResult chargeChunkAddIfNeeded(
            UcsConfigSnapshot config,
            ClaimChunkEditRequest request,
            ClaimEconomyProvider economyProvider
    ) {
        if (config == null || !pricing.economyActive(config, economyProvider)) {
            return null;
        }
        BigDecimal price = pricing.chunkAddPrice(config, 1);
        if (!pricing.shouldTransact(price)) {
            return null;
        }
        ClaimEconomyAccountRef payer = ClaimEconomyAccountRef.playerPrimary(request.playerId());
        ClaimEconomyResult validation = economyProvider.validateCanCharge(payer, price);
        if (!validation.success()) {
            return validation;
        }
        return economyProvider.charge(payer, price, ClaimPricingService.REF_CHUNK_ADD);
    }

    private ClaimChunkEditResult rollbackChunkAddOnSaveFailure(
            ClaimChunkEditResult result,
            UcsConfigSnapshot config,
            ClaimChunkEditRequest request,
            ClaimEconomyProvider economyProvider,
            ClaimEconomyResult economyCharge
    ) {
        if (result.failure().isEmpty()
                || economyCharge == null
                || !economyCharge.success()
                || config == null
                || !pricing.economyActive(config, economyProvider)) {
            return result;
        }
        BigDecimal price = pricing.chunkAddPrice(config, 1);
        ClaimEconomyResult rollback = economyProvider.refund(
                ClaimEconomyAccountRef.playerPrimary(request.playerId()),
                price,
                ClaimPricingService.REF_CHUNK_ADD_ROLLBACK
        );
        ClaimChunkEditFailure failure = result.failure().orElseThrow();
        String detail = failure.detail() + rollbackDetail(rollback);
        return ClaimChunkEditResult.failure(
                result.action(),
                new ClaimChunkEditFailure(failure.reason(), detail),
                rollback,
                result.affectedChunks()
        );
    }

    private ClaimChunkEditResult refundRemovedChunkIfNeeded(
            ClaimChunkEditResult result,
            UcsConfigSnapshot config,
            ClaimChunkEditRequest request,
            ClaimEconomyProvider economyProvider,
            String reference
    ) {
        if (result.failure().isPresent()
                || config == null
                || !pricing.economyActive(config, economyProvider)) {
            return result;
        }
        BigDecimal refund = pricing.removedChunkRefund(config, 1);
        if (!pricing.shouldTransact(refund)) {
            return result;
        }
        ClaimEconomyResult economyResult = economyProvider.refund(
                ClaimEconomyAccountRef.playerPrimary(request.playerId()),
                refund,
                reference
        );
        return withEconomyAudit(result, economyResult);
    }

    private static String economyAuditSuffix(String verb, ClaimEconomyResult economyResult) {
        if (economyResult == null || !economyResult.success()) {
            return "";
        }
        String reference = economyResult.providerReference().isBlank()
                ? ""
                : " ref " + economyResult.providerReference();
        return "; " + verb + " " + economyResult.formattedAmount() + reference;
    }

    private static ClaimChunkEditResult withEconomyAudit(
            ClaimChunkEditResult result,
            ClaimEconomyResult economyResult
    ) {
        AuditEntry auditEntry = result.auditEntry().orElseThrow();
        String detail = auditEntry.detail() + refundAuditSuffix(economyResult);
        AuditEntry updatedAudit = new AuditEntry(
                auditEntry.id(),
                auditEntry.occurredAt(),
                auditEntry.actorKey(),
                auditEntry.action(),
                auditEntry.claimId(),
                detail
        );
        return ClaimChunkEditResult.success(
                result.action(),
                result.claims(),
                updatedAudit,
                economyResult,
                result.affectedChunks()
        );
    }

    private static String refundAuditSuffix(ClaimEconomyResult economyResult) {
        if (economyResult.success()) {
            return economyAuditSuffix("refunded", economyResult);
        }
        return "; refund failed " + economyResult.userMessage();
    }

    private static String rollbackDetail(ClaimEconomyResult rollback) {
        if (rollback.success()) {
            return "; payment rollback refunded " + rollback.formattedAmount();
        }
        return "; payment rollback failed: " + rollback.userMessage();
    }

    private static List<Claim> connectedOwnedClaimGroup(ClaimRepository repository, Claim base, PlayerOwner owner) {
        Map<ClaimId, Claim> candidates = repository.claims().stream()
                .filter(claim -> sameOwner(claim, owner))
                .filter(claim -> sameDimension(claim, base))
                .collect(Collectors.toMap(Claim::id, claim -> claim));
        List<Claim> group = new ArrayList<>();
        Queue<Claim> queue = new ArrayDeque<>();
        queue.add(base);
        candidates.remove(base.id());

        while (!queue.isEmpty()) {
            Claim current = queue.remove();
            group.add(current);
            List<Claim> touching = candidates.values().stream()
                    .filter(candidate -> ClaimShape.touches(current, candidate) || touchesAny(group, candidate))
                    .toList();
            for (Claim claim : touching) {
                candidates.remove(claim.id());
                queue.add(claim);
            }
        }

        return List.copyOf(group);
    }

    private static boolean touchesAny(List<Claim> claims, Claim candidate) {
        return claims.stream().anyMatch(claim -> ClaimShape.touches(claim, candidate));
    }

    private static Claim withChunks(Claim claim, Set<ClaimChunk> chunks, Instant updatedAt, String displayName) {
        Optional<ChunkKey> spawnChunk = claim.metadata().spawnChunk()
                .filter(spawn -> chunks.stream().map(ClaimChunk::key).anyMatch(spawn::equals));
        return new Claim(
                claim.id(),
                claim.owner(),
                chunks,
                new ClaimMetadata(
                        displayName,
                        claim.metadata().description(),
                        claim.metadata().spawn().filter(spawn -> spawnChunk.isPresent()),
                        claim.metadata().createdAt(),
                        updatedAt
                ),
                claim.roleAssignments(),
                claim.pendingRoleInvites(),
                claim.flagOverrides(),
                claim.saleListing(),
                claim.leases()
        );
    }

    private static List<Claim> splitClaims(Claim original, List<Set<ClaimChunk>> components, Instant updatedAt) {
        List<Set<ClaimChunk>> ordered = components.stream()
                .sorted(Comparator.comparingInt(ClaimChunkEditService::minX).thenComparingInt(ClaimChunkEditService::minZ))
                .toList();
        List<Claim> claims = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            ClaimId id = index == 0 ? original.id() : ClaimId.random();
            String displayName = index == 0
                    ? original.metadata().displayName()
                    : original.metadata().displayName() + " Split " + (index + 1);
            claims.add(new Claim(
                    id,
                    original.owner(),
                    ordered.get(index),
                    new ClaimMetadata(displayName, original.metadata().description(), Optional.empty(), original.metadata().createdAt(), updatedAt),
                    original.roleAssignments(),
                    original.pendingRoleInvites(),
                    original.flagOverrides(),
                    index == 0 ? original.saleListing() : Optional.empty(),
                    index == 0 ? original.leases() : Map.of()
            ));
        }
        return List.copyOf(claims);
    }

    private static int minX(Set<ClaimChunk> chunks) {
        return chunks.stream().map(ClaimChunk::key).mapToInt(ChunkKey::x).min().orElse(0);
    }

    private static int minZ(Set<ClaimChunk> chunks) {
        return chunks.stream().map(ClaimChunk::key).mapToInt(ChunkKey::z).min().orElse(0);
    }

    private static ClaimArchiveView archiveClaim(
            UcsClaimService claimService,
            Claim claim,
            PlayerOwner actor,
            Instant archivedAt,
            String reason
    ) {
        return claimService.archiveClaim(claim.id(), ArchiveId.random(), archivedAt, reason, actor.stableKey())
                .orElseThrow(() -> new ClaimRepositoryException("Claim " + claim.id().value() + " could not be archived"));
    }

    private static void rollbackSplit(UcsClaimService claimService, List<Claim> splitClaims) {
        for (Claim splitClaim : splitClaims) {
            claimService.deleteClaim(splitClaim.id());
        }
    }

    private static void restoreArchives(UcsClaimService claimService, List<ClaimArchiveView> archives) {
        for (int index = archives.size() - 1; index >= 0; index--) {
            claimService.restoreClaim(archives.get(index).id());
        }
    }

    private static PlayerOwner owner(ClaimChunkEditRequest request) {
        return ClaimOwnership.player(request.playerId(), request.playerName());
    }

    private static boolean sameOwner(Claim claim, PlayerOwner owner) {
        return ClaimOwnership.isOwnedBy(claim, owner);
    }

    private static boolean sameDimension(Claim first, Claim second) {
        return first.chunks().iterator().next().key().dimension().equals(second.chunks().iterator().next().key().dimension());
    }

    private static Set<ClaimChunk> mutableChunks(Claim claim) {
        return new LinkedHashSet<>(claim.chunks());
    }

    private static AuditEntry audit(PlayerOwner owner, Instant requestedAt, ClaimId claimId, String detail) {
        return new AuditEntry(
                UUID.randomUUID(),
                requestedAt,
                owner.stableKey(),
                AuditAction.CLAIM_UPDATED,
                Optional.of(claimId),
                detail
        );
    }

    private static ClaimChunkEditResult failure(
            ClaimChunkEditAction action,
            ClaimChunkEditFailureReason reason,
            String detail,
            ChunkKey affectedChunk
    ) {
        return ClaimChunkEditResult.failure(action, new ClaimChunkEditFailure(reason, detail), Set.of(affectedChunk));
    }

    private static ClaimChunkEditResult failure(
            ClaimChunkEditAction action,
            ClaimChunkEditFailureReason reason,
            String detail,
            ChunkKey affectedChunk,
            ClaimEconomyResult economyResult
    ) {
        return ClaimChunkEditResult.failure(
                action,
                new ClaimChunkEditFailure(reason, detail),
                economyResult,
                Set.of(affectedChunk)
        );
    }

    private static String exceptionDetail(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
