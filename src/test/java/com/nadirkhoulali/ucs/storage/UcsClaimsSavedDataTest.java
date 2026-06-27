package com.nadirkhoulali.ucs.storage;

import com.nadirkhoulali.ucs.core.model.ArchiveId;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimArchive;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UcsClaimsSavedDataTest {
    @Test
    void savedDataRoundTripsClaimsAndArchives() {
        Claim active = ClaimFixtures.claimAt(0, 0);
        Claim archivedClaim = ClaimFixtures.claimAt(1, 0);
        ArchiveId archiveId = ArchiveId.random();

        UcsClaimsSavedData data = new UcsClaimsSavedData();
        data.putClaim(active);
        data.putClaim(archivedClaim);
        ClaimArchive archive = data.archive(archivedClaim.id(), archiveId, Instant.EPOCH, "test archive").orElseThrow();

        CompoundTag saved = data.save(new CompoundTag(), null);
        UcsClaimsSavedData loaded = UcsClaimsSavedData.load(saved, null);

        assertEquals(active, loaded.findById(active.id()).orElseThrow());
        assertEquals(active, loaded.findByChunk(new ChunkKey("minecraft:overworld", 0, 0)).orElseThrow());
        assertEquals(archive, loaded.archives().stream().findFirst().orElseThrow());
        assertEquals(1, loaded.indexedChunkCount());
    }

    @Test
    void skipsDuplicateChunkClaimsWhenLoadingCorruptData() {
        Claim first = ClaimFixtures.claimAt(0, 0);
        Claim duplicate = ClaimFixtures.claimAt(0, 0);

        CompoundTag root = new CompoundTag();
        root.putInt("storageVersion", UcsClaimsSavedData.STORAGE_VERSION);
        ListTag claims = new ListTag();
        claims.add(ClaimNbtCodec.encodeClaim(first));
        claims.add(ClaimNbtCodec.encodeClaim(duplicate));
        root.put("claims", claims);
        root.put("archives", new ListTag());

        UcsClaimsSavedData loaded = UcsClaimsSavedData.load(root, null);

        assertEquals(1, loaded.claims().size());
        assertTrue(loaded.findById(first.id()).isPresent());
        assertFalse(loaded.findById(duplicate.id()).isPresent());
        assertTrue(loaded.isDirty());
    }

    @Test
    void repositoryArchiveAndRestoreUpdatesIndexes() {
        Claim claim = ClaimFixtures.claimAt(2, 2);
        SavedDataClaimRepository repository = new SavedDataClaimRepository(new UcsClaimsSavedData());
        repository.save(claim);

        ArchiveId archiveId = ArchiveId.random();
        assertTrue(repository.archive(claim.id(), archiveId, Instant.EPOCH, "restore test").isPresent());
        assertTrue(repository.findByChunk(new ChunkKey("minecraft:overworld", 2, 2)).isEmpty());

        assertEquals(claim, repository.restore(archiveId).orElseThrow());
        assertEquals(claim, repository.findByChunk(new ChunkKey("minecraft:overworld", 2, 2)).orElseThrow());
    }
}
