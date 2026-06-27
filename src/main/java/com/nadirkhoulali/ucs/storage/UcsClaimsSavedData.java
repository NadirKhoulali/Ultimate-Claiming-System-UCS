package com.nadirkhoulali.ucs.storage;

import com.nadirkhoulali.ucs.UcsMod;
import com.nadirkhoulali.ucs.core.model.ArchiveId;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimArchive;
import com.nadirkhoulali.ucs.core.model.ClaimId;
import com.nadirkhoulali.ucs.core.model.ClaimTaxLedgerEntry;
import com.nadirkhoulali.ucs.core.model.ClaimTaxState;
import com.nadirkhoulali.ucs.core.model.EconomyAuditEntry;
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
    private static final String KEY_TAX_STATES = "taxStates";
    private static final String KEY_TAX_LEDGER = "taxLedger";
    private static final String KEY_ECONOMY_AUDIT = "economyAudit";

    private final Map<ClaimId, Claim> claims = new LinkedHashMap<>();
    private final Map<ArchiveId, ClaimArchive> archives = new LinkedHashMap<>();
    private final Map<ClaimId, ClaimTaxState> taxStates = new LinkedHashMap<>();
    private final Map<java.util.UUID, ClaimTaxLedgerEntry> taxLedger = new LinkedHashMap<>();
    private final Map<java.util.UUID, EconomyAuditEntry> economyAudit = new LinkedHashMap<>();
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

        ListTag taxStateTags = tag.getList(KEY_TAX_STATES, Tag.TAG_COMPOUND);
        for (int i = 0; i < taxStateTags.size(); i++) {
            try {
                ClaimTaxState taxState = ClaimNbtCodec.decodeTaxState(taxStateTags.getCompound(i));
                data.taxStates.put(taxState.claimId(), taxState);
            } catch (RuntimeException exception) {
                sanitized = true;
                UcsMod.LOGGER.error("Skipping corrupted UCS tax state at index {}", i, exception);
            }
        }

        ListTag taxLedgerTags = tag.getList(KEY_TAX_LEDGER, Tag.TAG_COMPOUND);
        for (int i = 0; i < taxLedgerTags.size(); i++) {
            try {
                ClaimTaxLedgerEntry entry = ClaimNbtCodec.decodeTaxLedgerEntry(taxLedgerTags.getCompound(i));
                if (data.taxLedger.containsKey(entry.id())) {
                    sanitized = true;
                    UcsMod.LOGGER.error("Skipping duplicate UCS tax ledger entry id {}", entry.id());
                } else {
                    data.taxLedger.put(entry.id(), entry);
                }
            } catch (RuntimeException exception) {
                sanitized = true;
                UcsMod.LOGGER.error("Skipping corrupted UCS tax ledger entry at index {}", i, exception);
            }
        }

        ListTag economyAuditTags = tag.getList(KEY_ECONOMY_AUDIT, Tag.TAG_COMPOUND);
        for (int i = 0; i < economyAuditTags.size(); i++) {
            try {
                EconomyAuditEntry entry = ClaimNbtCodec.decodeEconomyAuditEntry(economyAuditTags.getCompound(i));
                if (data.economyAudit.containsKey(entry.id())) {
                    sanitized = true;
                    UcsMod.LOGGER.error("Skipping duplicate UCS economy audit entry id {}", entry.id());
                } else {
                    data.economyAudit.put(entry.id(), entry);
                }
            } catch (RuntimeException exception) {
                sanitized = true;
                UcsMod.LOGGER.error("Skipping corrupted UCS economy audit entry at index {}", i, exception);
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

        ListTag taxStateTags = new ListTag();
        taxStates.values().stream()
                .map(ClaimNbtCodec::encodeTaxState)
                .forEach(taxStateTags::add);
        tag.put(KEY_TAX_STATES, taxStateTags);

        ListTag taxLedgerTags = new ListTag();
        taxLedger.values().stream()
                .map(ClaimNbtCodec::encodeTaxLedgerEntry)
                .forEach(taxLedgerTags::add);
        tag.put(KEY_TAX_LEDGER, taxLedgerTags);

        ListTag economyAuditTags = new ListTag();
        economyAudit.values().stream()
                .map(ClaimNbtCodec::encodeEconomyAuditEntry)
                .forEach(economyAuditTags::add);
        tag.put(KEY_ECONOMY_AUDIT, economyAuditTags);

        return tag;
    }

    public Collection<Claim> claims() {
        return List.copyOf(claims.values());
    }

    public Collection<ClaimArchive> archives() {
        return List.copyOf(archives.values());
    }

    public Collection<ClaimTaxState> taxStates() {
        return List.copyOf(taxStates.values());
    }

    public Collection<ClaimTaxLedgerEntry> taxLedgerEntries() {
        return List.copyOf(taxLedger.values());
    }

    public Collection<EconomyAuditEntry> economyAuditEntries() {
        return List.copyOf(economyAudit.values());
    }

    public Optional<ClaimArchive> findArchive(ArchiveId archiveId) {
        return Optional.ofNullable(archives.get(archiveId));
    }

    public Optional<Claim> findById(ClaimId id) {
        return Optional.ofNullable(claims.get(id));
    }

    public Optional<Claim> findByChunk(ChunkKey chunkKey) {
        return index.findClaimId(chunkKey).flatMap(this::findById);
    }

    public Optional<ClaimTaxState> findTaxState(ClaimId claimId) {
        return Optional.ofNullable(taxStates.get(claimId));
    }

    public void putClaim(Claim claim) {
        putClaimInternal(claim);
        setDirty();
    }

    public void putTaxState(ClaimTaxState taxState) {
        taxStates.put(taxState.claimId(), taxState);
        setDirty();
    }

    public ClaimTaxLedgerEntry appendTaxLedgerEntry(ClaimTaxLedgerEntry entry) {
        if (taxLedger.containsKey(entry.id())) {
            throw new ClaimRepositoryException("Duplicate tax ledger entry id " + entry.id());
        }
        taxLedger.put(entry.id(), entry);
        setDirty();
        return entry;
    }

    public EconomyAuditEntry appendEconomyAuditEntry(EconomyAuditEntry entry) {
        if (economyAudit.containsKey(entry.id())) {
            throw new ClaimRepositoryException("Duplicate economy audit entry id " + entry.id());
        }
        economyAudit.put(entry.id(), entry);
        setDirty();
        return entry;
    }

    public Optional<ClaimTaxState> removeTaxState(ClaimId claimId) {
        ClaimTaxState removed = taxStates.remove(claimId);
        if (removed == null) {
            return Optional.empty();
        }
        setDirty();
        return Optional.of(removed);
    }

    public Optional<Claim> removeClaim(ClaimId claimId) {
        Claim removed = claims.remove(claimId);
        if (removed == null) {
            return Optional.empty();
        }
        index.remove(removed);
        taxStates.remove(claimId);
        setDirty();
        return Optional.of(removed);
    }

    public Optional<ClaimArchive> archive(
            ClaimId claimId,
            ArchiveId archiveId,
            Instant archivedAt,
            String reason,
            String actor,
            int dataVersion
    ) {
        if (archives.containsKey(archiveId)) {
            throw new ClaimRepositoryException("Duplicate archive id " + archiveId.value());
        }
        Claim removed = claims.remove(claimId);
        if (removed == null) {
            return Optional.empty();
        }
        index.remove(removed);
        taxStates.remove(claimId);
        ClaimArchive archive = new ClaimArchive(archiveId, removed, archivedAt, reason, actor, dataVersion);
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

    public Optional<ClaimArchive> deleteArchive(ArchiveId archiveId) {
        ClaimArchive removed = archives.remove(archiveId);
        if (removed == null) {
            return Optional.empty();
        }
        setDirty();
        return Optional.of(removed);
    }

    public int pruneArchivesBefore(Instant cutoff) {
        List<ArchiveId> expired = archives.values().stream()
                .filter(archive -> archive.archivedAt().isBefore(cutoff))
                .map(ClaimArchive::id)
                .toList();
        expired.forEach(archives::remove);
        if (!expired.isEmpty()) {
            setDirty();
        }
        return expired.size();
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
