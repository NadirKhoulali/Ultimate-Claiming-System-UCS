package com.nadirkhoulali.ucs.storage;

import com.nadirkhoulali.ucs.core.model.ArchiveId;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimArchive;
import com.nadirkhoulali.ucs.core.model.ClaimId;
import com.nadirkhoulali.ucs.core.model.ClaimTaxLedgerEntry;
import com.nadirkhoulali.ucs.core.model.ClaimTaxState;
import com.nadirkhoulali.ucs.core.model.EconomyAuditEntry;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface ClaimRepository {
    Collection<Claim> claims();

    Collection<ClaimArchive> archives();

    Collection<ClaimTaxState> taxStates();

    Collection<ClaimTaxLedgerEntry> taxLedgerEntries();

    Collection<EconomyAuditEntry> economyAuditEntries();

    Optional<ClaimArchive> findArchive(ArchiveId archiveId);

    Optional<Claim> findById(ClaimId id);

    Optional<Claim> findByChunk(ChunkKey chunkKey);

    Optional<ClaimTaxState> findTaxState(ClaimId claimId);

    Claim save(Claim claim);

    ClaimTaxState saveTaxState(ClaimTaxState taxState);

    ClaimTaxLedgerEntry appendTaxLedgerEntry(ClaimTaxLedgerEntry entry);

    EconomyAuditEntry appendEconomyAuditEntry(EconomyAuditEntry entry);

    Optional<ClaimTaxState> deleteTaxState(ClaimId claimId);

    Optional<ClaimArchive> archive(ClaimId claimId, ArchiveId archiveId, Instant archivedAt, String reason, String actor, int dataVersion);

    Optional<Claim> restore(ArchiveId archiveId);

    Optional<ClaimArchive> deleteArchive(ArchiveId archiveId);

    int pruneArchivesBefore(Instant cutoff);

    Optional<Claim> delete(ClaimId claimId);

    void flush();
}
