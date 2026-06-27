package com.nadirkhoulali.ucs.storage;

import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimChunk;
import com.nadirkhoulali.ucs.core.model.ClaimId;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ClaimSpatialIndex {
    private final Map<ChunkKey, ClaimId> chunkToClaim = new HashMap<>();

    public static ClaimSpatialIndex rebuild(Collection<Claim> claims) {
        ClaimSpatialIndex index = new ClaimSpatialIndex();
        claims.forEach(index::add);
        return index;
    }

    public Optional<ClaimId> findClaimId(ChunkKey chunkKey) {
        return Optional.ofNullable(chunkToClaim.get(Objects.requireNonNull(chunkKey, "chunkKey")));
    }

    public void add(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        for (ClaimChunk chunk : claim.chunks()) {
            ClaimId existing = chunkToClaim.get(chunk.key());
            if (existing != null && !existing.equals(claim.id())) {
                throw new ClaimRepositoryException(
                        "Chunk " + chunk.key().storageKey() + " is already claimed by " + existing.value()
                );
            }
        }
        claim.chunks().forEach(chunk -> chunkToClaim.put(chunk.key(), claim.id()));
    }

    public void remove(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        claim.chunks().forEach(chunk -> chunkToClaim.remove(chunk.key(), claim.id()));
    }

    public int size() {
        return chunkToClaim.size();
    }
}
