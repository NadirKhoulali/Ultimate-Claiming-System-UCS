package com.nadirkhoulali.ucs.storage;

import com.nadirkhoulali.ucs.UcsMod;
import com.nadirkhoulali.ucs.core.model.ArchiveId;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimArchive;
import com.nadirkhoulali.ucs.core.model.ClaimId;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class UcsClaimsSavedData extends SavedData {
    public static final String DATA_NAME = UcsMod.MOD_ID + "_claims";
    public static final int STORAGE_VERSION = 1;

    private static final String KEY_STORAGE_VERSION = "storageVersion";
    private static final String KEY_CLAIMS = "claims";
    private static final String KEY_ARCHIVES = "archives";

    private final Map<ClaimId, Claim> claims = new LinkedHashMap<>();
    private final Map<ArchiveId, ClaimArchive> archives = new LinkedHashMap<>();
    private ClaimSpatialIndex index = new ClaimSpatialIndex();

    public static SavedData.Factory<UcsClaimsSavedData> factory() {
        return new SavedData.Factory<>(UcsClaimsSavedData::new, UcsClaimsSavedData::load);
    }

    public static UcsClaimsSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        UcsClaimsSavedData data = new UcsClaimsSavedData();
        int loadedVersion = tag.getInt(KEY_STORAGE_VERSION);
        if (loadedVersion > STORAGE_VERSION) {
            UcsMod.LOGGER.warn(
                    "UCS claim data storage version {} is newer than supported version {}; attempting best-effort load.",
                    loadedVersion,
                    STORAGE_VERSION
            );
        }

        boolean sanitized = false;
        ListTag claimTags = tag.getList(KEY_CLAIMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < claimTags.size(); i++) {
            try {
                Claim claim = ClaimNbtCodec.decodeClaim(claimTags.getCompound(i));
                data.putLoadedClaim(claim);
            } catch (RuntimeException exception) {
                sanitized = true;
                UcsMod.LOGGER.error("Skipping corrupted UCS claim at index {}", i, exception);
            }
        }

        ListTag archiveTags = tag.getList(KEY_ARCHIVES, Tag.TAG_COMPOUND);
        for (int i = 0; i < archiveTags.size(); i++) {
            try {
                ClaimArchive archive = ClaimNbtCodec.decodeArchive(archiveTags.getCompound(i));
                if (data.archives.containsKey(archive.id())) {
                    sanitized = true;
                    UcsMod.LOGGER.error("Skipping duplicate UCS archive id {}", archive.id().value());
                } else {
                    data.archives.put(archive.id(), archive);
                }
            } catch (RuntimeException exception) {
                sanitized = true;
                UcsMod.LOGGER.error("Skipping corrupted UCS claim archive at index {}", i, exception);
            }
        }

        if (sanitized) {
            data.setDirty();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(KEY_STORAGE_VERSION, STORAGE_VERSION);

        ListTag claimTags = new ListTag();
        claims.values().stream()
                .map(ClaimNbtCodec::encodeClaim)
                .forEach(claimTags::add);
        tag.put(KEY_CLAIMS, claimTags);

        ListTag archiveTags = new ListTag();
        archives.values().stream()
                .map(ClaimNbtCodec::encodeArchive)
                .forEach(archiveTags::add);
        tag.put(KEY_ARCHIVES, archiveTags);

        return tag;
    }

    public Collection<Claim> claims() {
        return List.copyOf(claims.values());
    }

    public Collection<ClaimArchive> archives() {
        return List.copyOf(archives.values());
    }

    public Optional<Claim> findById(ClaimId id) {
        return Optional.ofNullable(claims.get(id));
    }

    public Optional<Claim> findByChunk(ChunkKey chunkKey) {
        return index.findClaimId(chunkKey).flatMap(this::findById);
    }

    public void putClaim(Claim claim) {
        putClaimInternal(claim);
        setDirty();
    }

    public Optional<Claim> removeClaim(ClaimId claimId) {
        Claim removed = claims.remove(claimId);
        if (removed == null) {
            return Optional.empty();
        }
        index.remove(removed);
        setDirty();
        return Optional.of(removed);
    }

    public Optional<ClaimArchive> archive(ClaimId claimId, ArchiveId archiveId, Instant archivedAt, String reason) {
        Claim removed = claims.remove(claimId);
        if (removed == null) {
            return Optional.empty();
        }
        index.remove(removed);
        ClaimArchive archive = new ClaimArchive(archiveId, removed, archivedAt, reason);
        archives.put(archive.id(), archive);
        setDirty();
        return Optional.of(archive);
    }

    public Optional<Claim> restore(ArchiveId archiveId) {
        ClaimArchive archive = archives.remove(archiveId);
        if (archive == null) {
            return Optional.empty();
        }
        try {
            putClaimInternal(archive.claim());
            setDirty();
            return Optional.of(archive.claim());
        } catch (RuntimeException exception) {
            archives.put(archive.id(), archive);
            throw exception;
        }
    }

    public int indexedChunkCount() {
        return index.size();
    }

    private void putLoadedClaim(Claim claim) {
        if (claims.containsKey(claim.id())) {
            throw new ClaimRepositoryException("Duplicate claim id " + claim.id().value());
        }
        putClaimInternal(claim);
    }

    private void putClaimInternal(Claim claim) {
        Claim existing = claims.get(claim.id());
        if (existing != null) {
            index.remove(existing);
        }
        try {
            index.add(claim);
            claims.put(claim.id(), claim);
        } catch (RuntimeException exception) {
            if (existing != null) {
                index.add(existing);
            }
            throw exception;
        }
    }
}
