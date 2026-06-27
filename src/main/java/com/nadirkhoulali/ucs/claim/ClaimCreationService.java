package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.api.UcsClaimService;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.AuditAction;
import com.nadirkhoulali.ucs.core.model.AuditEntry;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimChunk;
import com.nadirkhoulali.ucs.core.model.ClaimId;
import com.nadirkhoulali.ucs.core.model.ClaimMetadata;
import com.nadirkhoulali.ucs.core.model.ClaimOwnership;
import com.nadirkhoulali.ucs.core.model.FlagId;
import com.nadirkhoulali.ucs.core.model.PlayerOwner;
import com.nadirkhoulali.ucs.core.model.RoleId;
import com.nadirkhoulali.ucs.storage.ClaimRepository;
import com.nadirkhoulali.ucs.storage.ClaimRepositoryException;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ClaimCreationService {
    public ClaimCreationResult createPlayerClaim(
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            ClaimCreationRequest request
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(claimService, "claimService");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(request, "request");

        Set<ClaimChunk> selectedChunks = selectSquare(request.center(), request.radius());
        int selectedChunkCount = selectedChunks.size();

        Optional<ClaimCreationFailure> validationFailure = validate(repository, config, request, selectedChunks);
        if (validationFailure.isPresent()) {
            return ClaimCreationResult.failure(validationFailure.orElseThrow(), selectedChunkCount);
        }

        PlayerOwner owner = ClaimOwnership.player(request.playerId(), request.playerName());
        Claim claim = new Claim(
                ClaimId.random(),
                owner,
                selectedChunks,
                ClaimMetadata.create(request.playerName() + "'s Claim", request.requestedAt()),
                ownerRoleAssignment(request.playerId()),
                defaultFlags(config)
        );

        try {
            ClaimView saved = claimService.saveClaim(claim);
            AuditEntry auditEntry = new AuditEntry(
                    UUID.randomUUID(),
                    request.requestedAt(),
                    owner.stableKey(),
                    AuditAction.CLAIM_CREATED,
                    Optional.of(saved.id()),
                    "created " + selectedChunkCount + " chunks centered at " + request.center().storageKey()
            );
            return ClaimCreationResult.success(saved, auditEntry, selectedChunkCount);
        } catch (ClaimRepositoryException exception) {
            return ClaimCreationResult.failure(
                    ClaimCreationFailure.simple(ClaimCreationFailureReason.SAVE_FAILED, exceptionDetail(exception)),
                    selectedChunkCount
            );
        }
    }

    public Set<ClaimChunk> selectSquare(ChunkKey center, int radius) {
        Objects.requireNonNull(center, "center");
        if (radius < 0) {
            throw new IllegalArgumentException("radius must not be negative");
        }

        Set<ClaimChunk> chunks = new LinkedHashSet<>();
        for (int x = center.x() - radius; x <= center.x() + radius; x++) {
            for (int z = center.z() - radius; z <= center.z() + radius; z++) {
                chunks.add(new ClaimChunk(new ChunkKey(center.dimension(), x, z)));
            }
        }
        return Set.copyOf(chunks);
    }

    private Optional<ClaimCreationFailure> validate(
            ClaimRepository repository,
            UcsConfigSnapshot config,
            ClaimCreationRequest request,
            Set<ClaimChunk> selectedChunks
    ) {
        UcsConfigSnapshot.DimensionPolicy dimensions = config.dimensions();
        String dimension = request.center().dimension();
        if (dimensions.disabledDimensions().contains(dimension) || !dimensions.enabledDimensions().contains(dimension)) {
            return Optional.of(ClaimCreationFailure.simple(ClaimCreationFailureReason.DIMENSION_DISABLED, dimension));
        }

        UcsConfigSnapshot.ClaimLimitPolicy limits = config.claimLimits();
        if (request.radius() > limits.maxRadiusClaim()) {
            return Optional.of(ClaimCreationFailure.simple(
                    ClaimCreationFailureReason.RADIUS_TOO_LARGE,
                    Integer.toString(limits.maxRadiusClaim())
            ));
        }
        if (selectedChunks.size() > limits.maxChunksPerClaim()) {
            return Optional.of(ClaimCreationFailure.simple(
                    ClaimCreationFailureReason.CLAIM_TOO_LARGE,
                    Integer.toString(limits.maxChunksPerClaim())
            ));
        }

        PlayerOwner owner = ClaimOwnership.player(request.playerId(), request.playerName());
        long ownedClaimCount = repository.claims().stream()
                .filter(claim -> ClaimOwnership.isOwnedBy(claim, owner))
                .count();
        if (ownedClaimCount >= limits.maxClaimsPerPlayer()) {
            return Optional.of(ClaimCreationFailure.simple(
                    ClaimCreationFailureReason.TOO_MANY_CLAIMS,
                    Integer.toString(limits.maxClaimsPerPlayer())
            ));
        }

        int ownedChunkCount = repository.claims().stream()
                .filter(claim -> ClaimOwnership.isOwnedBy(claim, owner))
                .mapToInt(claim -> claim.chunks().size())
                .sum();
        if (ownedChunkCount + selectedChunks.size() > limits.maxChunksPerPlayer()) {
            return Optional.of(ClaimCreationFailure.simple(
                    ClaimCreationFailureReason.TOO_MANY_CHUNKS,
                    Integer.toString(limits.maxChunksPerPlayer())
            ));
        }

        for (ClaimChunk chunk : selectedChunks) {
            if (repository.findByChunk(chunk.key()).isPresent()) {
                return Optional.of(ClaimCreationFailure.conflict(chunk.key()));
            }
        }

        return Optional.empty();
    }

    private static Map<RoleId, Set<UUID>> ownerRoleAssignment(UUID playerId) {
        Map<RoleId, Set<UUID>> assignments = new LinkedHashMap<>();
        assignments.put(new RoleId("owner"), Set.of(playerId));
        return Map.copyOf(assignments);
    }

    private static Set<FlagId> defaultFlags(UcsConfigSnapshot config) {
        return config.flags().defaultProtectionFlagIds().stream()
                .map(FlagId::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String exceptionDetail(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
