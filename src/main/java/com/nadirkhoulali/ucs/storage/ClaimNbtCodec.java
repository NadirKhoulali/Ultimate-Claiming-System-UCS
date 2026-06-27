package com.nadirkhoulali.ucs.storage;

import com.nadirkhoulali.ucs.core.model.ArchiveId;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimArchive;
import com.nadirkhoulali.ucs.core.model.ClaimChunk;
import com.nadirkhoulali.ucs.core.model.ClaimId;
import com.nadirkhoulali.ucs.core.model.ClaimMetadata;
import com.nadirkhoulali.ucs.core.model.ClaimSaleListing;
import com.nadirkhoulali.ucs.core.model.ClaimSpawn;
import com.nadirkhoulali.ucs.core.model.ClaimTaxLedgerEntry;
import com.nadirkhoulali.ucs.core.model.ClaimTaxLedgerStatus;
import com.nadirkhoulali.ucs.core.model.ClaimTaxState;
import com.nadirkhoulali.ucs.core.model.EconomyAuditAction;
import com.nadirkhoulali.ucs.core.model.EconomyAuditEntry;
import com.nadirkhoulali.ucs.core.model.EconomyAuditStatus;
import com.nadirkhoulali.ucs.core.model.FlagId;
import com.nadirkhoulali.ucs.core.model.LeaseContract;
import com.nadirkhoulali.ucs.core.model.LeaseId;
import com.nadirkhoulali.ucs.core.model.LeaseStatus;
import com.nadirkhoulali.ucs.core.model.OwnerRef;
import com.nadirkhoulali.ucs.core.model.OwnerType;
import com.nadirkhoulali.ucs.core.model.PlayerOwner;
import com.nadirkhoulali.ucs.core.model.RoleId;
import com.nadirkhoulali.ucs.core.model.ServerOwner;
import com.nadirkhoulali.ucs.core.model.TeamOwner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class ClaimNbtCodec {
    private ClaimNbtCodec() {
    }

    static CompoundTag encodeClaim(Claim claim) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", claim.id().value().toString());
        tag.put("owner", encodeOwner(claim.owner()));
        tag.put("chunks", encodeChunks(claim.chunks()));
        tag.put("metadata", encodeMetadata(claim.metadata()));
        tag.put("roleAssignments", encodeRoleAssignments(claim.roleAssignments()));
        tag.put("pendingRoleInvites", encodeRoleAssignments(claim.pendingRoleInvites()));
        tag.put("flagOverrides", encodeFlags(claim.flagOverrides()));
        claim.saleListing().ifPresent(listing -> tag.put("saleListing", encodeSaleListing(listing)));
        tag.put("leases", encodeLeases(claim.leases()));
        return tag;
    }

    static Claim decodeClaim(CompoundTag tag) {
        return new Claim(
                new ClaimId(UUID.fromString(tag.getString("id"))),
                decodeOwner(tag.getCompound("owner")),
                decodeChunks(tag.getList("chunks", Tag.TAG_COMPOUND)),
                decodeMetadata(tag.getCompound("metadata")),
                decodeRoleAssignments(tag.getList("roleAssignments", Tag.TAG_COMPOUND)),
                decodeRoleAssignments(tag.getList("pendingRoleInvites", Tag.TAG_COMPOUND)),
                decodeFlags(tag.getList("flagOverrides", Tag.TAG_STRING)),
                decodeSaleListing(tag),
                decodeLeases(tag.getList("leases", Tag.TAG_COMPOUND))
        );
    }

    static CompoundTag encodeArchive(ClaimArchive archive) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", archive.id().value().toString());
        tag.put("claim", encodeClaim(archive.claim()));
        tag.putLong("archivedAt", archive.archivedAt().toEpochMilli());
        tag.putString("reason", archive.reason());
        tag.putString("actor", archive.actor());
        tag.putInt("dataVersion", archive.dataVersion());
        return tag;
    }

    static ClaimArchive decodeArchive(CompoundTag tag) {
        String actor = tag.contains("actor", Tag.TAG_STRING) ? tag.getString("actor") : ClaimArchive.UNKNOWN_ACTOR;
        int dataVersion = tag.contains("dataVersion", Tag.TAG_INT) ? tag.getInt("dataVersion") : 1;
        return new ClaimArchive(
                new ArchiveId(UUID.fromString(tag.getString("id"))),
                decodeClaim(tag.getCompound("claim")),
                Instant.ofEpochMilli(tag.getLong("archivedAt")),
                tag.getString("reason"),
                actor,
                dataVersion
        );
    }

    static CompoundTag encodeTaxState(ClaimTaxState taxState) {
        CompoundTag tag = new CompoundTag();
        tag.putString("claimId", taxState.claimId().value().toString());
        tag.putLong("nextDueAt", taxState.nextDueAt().toEpochMilli());
        taxState.lastPaidAt().ifPresent(lastPaidAt -> tag.putLong("lastPaidAt", lastPaidAt.toEpochMilli()));
        tag.putInt("missedPayments", taxState.missedPayments());
        tag.putString("outstandingDebt", taxState.outstandingDebt().toPlainString());
        taxState.delinquentSince().ifPresent(delinquentSince -> tag.putLong("delinquentSince", delinquentSince.toEpochMilli()));
        taxState.lastWarningAt().ifPresent(lastWarningAt -> tag.putLong("lastWarningAt", lastWarningAt.toEpochMilli()));
        tag.putLong("updatedAt", taxState.updatedAt().toEpochMilli());
        return tag;
    }

    static ClaimTaxState decodeTaxState(CompoundTag tag) {
        Optional<Instant> lastPaidAt = tag.contains("lastPaidAt", Tag.TAG_LONG)
                ? Optional.of(Instant.ofEpochMilli(tag.getLong("lastPaidAt")))
                : Optional.empty();
        Optional<Instant> delinquentSince = tag.contains("delinquentSince", Tag.TAG_LONG)
                ? Optional.of(Instant.ofEpochMilli(tag.getLong("delinquentSince")))
                : Optional.empty();
        Optional<Instant> lastWarningAt = tag.contains("lastWarningAt", Tag.TAG_LONG)
                ? Optional.of(Instant.ofEpochMilli(tag.getLong("lastWarningAt")))
                : Optional.empty();
        return new ClaimTaxState(
                new ClaimId(UUID.fromString(tag.getString("claimId"))),
                Instant.ofEpochMilli(tag.getLong("nextDueAt")),
                lastPaidAt,
                tag.getInt("missedPayments"),
                new BigDecimal(tag.getString("outstandingDebt")),
                delinquentSince,
                lastWarningAt,
                Instant.ofEpochMilli(tag.getLong("updatedAt"))
        );
    }

    static CompoundTag encodeTaxLedgerEntry(ClaimTaxLedgerEntry entry) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", entry.id().toString());
        tag.putString("claimId", entry.claimId().value().toString());
        tag.putString("ownerKey", entry.ownerKey());
        tag.putString("amount", entry.amount().toPlainString());
        tag.putLong("dueAt", entry.dueAt().toEpochMilli());
        tag.putLong("processedAt", entry.processedAt().toEpochMilli());
        tag.putString("reference", entry.reference());
        tag.putString("status", entry.status().name());
        tag.putString("providerReference", entry.providerReference());
        tag.putString("detail", entry.detail());
        return tag;
    }

    static ClaimTaxLedgerEntry decodeTaxLedgerEntry(CompoundTag tag) {
        return new ClaimTaxLedgerEntry(
                UUID.fromString(tag.getString("id")),
                new ClaimId(UUID.fromString(tag.getString("claimId"))),
                tag.getString("ownerKey"),
                new BigDecimal(tag.getString("amount")),
                Instant.ofEpochMilli(tag.getLong("dueAt")),
                Instant.ofEpochMilli(tag.getLong("processedAt")),
                tag.getString("reference"),
                ClaimTaxLedgerStatus.valueOf(tag.getString("status")),
                tag.getString("providerReference"),
                tag.getString("detail")
        );
    }

    static CompoundTag encodeEconomyAuditEntry(EconomyAuditEntry entry) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", entry.id().toString());
        tag.putLong("occurredAt", entry.occurredAt().toEpochMilli());
        tag.putString("actorKey", entry.actorKey());
        tag.putString("action", entry.action().name());
        tag.putString("status", entry.status().name());
        entry.claimId().ifPresent(claimId -> tag.putString("claimId", claimId.value().toString()));
        tag.putString("ownerKey", entry.ownerKey());
        tag.putString("amount", entry.amount().toPlainString());
        tag.putString("reference", entry.reference());
        tag.putString("providerId", entry.providerId());
        tag.putString("providerReference", entry.providerReference());
        tag.putString("reason", entry.reason());
        tag.putString("detail", entry.detail());
        return tag;
    }

    static EconomyAuditEntry decodeEconomyAuditEntry(CompoundTag tag) {
        Optional<ClaimId> claimId = tag.contains("claimId", Tag.TAG_STRING)
                ? Optional.of(new ClaimId(UUID.fromString(tag.getString("claimId"))))
                : Optional.empty();
        return new EconomyAuditEntry(
                UUID.fromString(tag.getString("id")),
                Instant.ofEpochMilli(tag.getLong("occurredAt")),
                tag.getString("actorKey"),
                EconomyAuditAction.valueOf(tag.getString("action")),
                EconomyAuditStatus.valueOf(tag.getString("status")),
                claimId,
                tag.getString("ownerKey"),
                new BigDecimal(tag.getString("amount")),
                tag.getString("reference"),
                tag.getString("providerId"),
                tag.getString("providerReference"),
                tag.getString("reason"),
                tag.getString("detail")
        );
    }

    private static CompoundTag encodeOwner(OwnerRef owner) {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", owner.type().name());
        switch (owner) {
            case PlayerOwner player -> {
                tag.putString("playerId", player.playerId().toString());
                tag.putString("lastKnownName", player.lastKnownName());
            }
            case TeamOwner team -> tag.putString("teamId", team.teamId());
            case ServerOwner server -> tag.putString("namespace", server.namespace());
        }
        return tag;
    }

    private static OwnerRef decodeOwner(CompoundTag tag) {
        OwnerType type = OwnerType.valueOf(tag.getString("type"));
        return switch (type) {
            case PLAYER -> new PlayerOwner(UUID.fromString(tag.getString("playerId")), tag.getString("lastKnownName"));
            case TEAM -> new TeamOwner(tag.getString("teamId"));
            case SERVER -> new ServerOwner(tag.getString("namespace"));
        };
    }

    private static ListTag encodeChunks(Set<ClaimChunk> chunks) {
        ListTag tags = new ListTag();
        chunks.stream()
                .map(ClaimChunk::key)
                .map(ClaimNbtCodec::encodeChunkKey)
                .forEach(tags::add);
        return tags;
    }

    private static Set<ClaimChunk> decodeChunks(ListTag tags) {
        Set<ClaimChunk> chunks = new LinkedHashSet<>();
        for (int i = 0; i < tags.size(); i++) {
            chunks.add(new ClaimChunk(decodeChunkKey(tags.getCompound(i))));
        }
        return chunks;
    }

    private static CompoundTag encodeChunkKey(ChunkKey key) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", key.dimension());
        tag.putInt("x", key.x());
        tag.putInt("z", key.z());
        return tag;
    }

    private static ChunkKey decodeChunkKey(CompoundTag tag) {
        return new ChunkKey(tag.getString("dimension"), tag.getInt("x"), tag.getInt("z"));
    }

    private static CompoundTag encodeMetadata(ClaimMetadata metadata) {
        CompoundTag tag = new CompoundTag();
        tag.putString("displayName", metadata.displayName());
        tag.putString("description", metadata.description());
        metadata.spawn().ifPresent(spawn -> tag.put("spawn", encodeSpawn(spawn)));
        tag.putLong("createdAt", metadata.createdAt().toEpochMilli());
        tag.putLong("updatedAt", metadata.updatedAt().toEpochMilli());
        return tag;
    }

    private static ClaimMetadata decodeMetadata(CompoundTag tag) {
        Optional<ClaimSpawn> spawn = tag.contains("spawn", Tag.TAG_COMPOUND)
                ? Optional.of(decodeSpawn(tag.getCompound("spawn")))
                : Optional.empty();
        return new ClaimMetadata(
                tag.getString("displayName"),
                tag.getString("description"),
                spawn,
                Instant.ofEpochMilli(tag.getLong("createdAt")),
                Instant.ofEpochMilli(tag.getLong("updatedAt"))
        );
    }

    private static CompoundTag encodeSpawn(ClaimSpawn spawn) {
        CompoundTag tag = new CompoundTag();
        tag.put("chunk", encodeChunkKey(spawn.chunk()));
        tag.putDouble("x", spawn.x());
        tag.putDouble("y", spawn.y());
        tag.putDouble("z", spawn.z());
        tag.putFloat("yaw", spawn.yaw());
        tag.putFloat("pitch", spawn.pitch());
        return tag;
    }

    private static ClaimSpawn decodeSpawn(CompoundTag tag) {
        return new ClaimSpawn(
                decodeChunkKey(tag.getCompound("chunk")),
                tag.getDouble("x"),
                tag.getDouble("y"),
                tag.getDouble("z"),
                tag.getFloat("yaw"),
                tag.getFloat("pitch")
        );
    }

    private static ListTag encodeRoleAssignments(Map<RoleId, Set<UUID>> roleAssignments) {
        ListTag roleTags = new ListTag();
        roleAssignments.forEach((roleId, players) -> {
            CompoundTag roleTag = new CompoundTag();
            roleTag.putString("roleId", roleId.value());
            ListTag playerTags = new ListTag();
            players.stream()
                    .map(UUID::toString)
                    .map(StringTag::valueOf)
                    .forEach(playerTags::add);
            roleTag.put("players", playerTags);
            roleTags.add(roleTag);
        });
        return roleTags;
    }

    private static Map<RoleId, Set<UUID>> decodeRoleAssignments(ListTag tags) {
        Map<RoleId, Set<UUID>> assignments = new LinkedHashMap<>();
        for (int i = 0; i < tags.size(); i++) {
            CompoundTag roleTag = tags.getCompound(i);
            ListTag playerTags = roleTag.getList("players", Tag.TAG_STRING);
            Set<UUID> players = new LinkedHashSet<>();
            for (int playerIndex = 0; playerIndex < playerTags.size(); playerIndex++) {
                players.add(UUID.fromString(playerTags.getString(playerIndex)));
            }
            assignments.put(new RoleId(roleTag.getString("roleId")), players);
        }
        return assignments;
    }

    private static ListTag encodeFlags(Set<FlagId> flags) {
        ListTag tags = new ListTag();
        flags.stream()
                .map(FlagId::value)
                .map(StringTag::valueOf)
                .forEach(tags::add);
        return tags;
    }

    private static Set<FlagId> decodeFlags(ListTag tags) {
        Set<FlagId> flags = new LinkedHashSet<>();
        for (int i = 0; i < tags.size(); i++) {
            flags.add(new FlagId(tags.getString(i)));
        }
        return flags;
    }

    private static CompoundTag encodeSaleListing(ClaimSaleListing listing) {
        CompoundTag tag = new CompoundTag();
        tag.putString("listingId", listing.listingId().toString());
        tag.putString("sellerPlayerId", listing.sellerPlayerId().toString());
        tag.putString("sellerName", listing.sellerName());
        tag.putString("price", listing.price().toPlainString());
        tag.putLong("listedAt", listing.listedAt().toEpochMilli());
        return tag;
    }

    private static Optional<ClaimSaleListing> decodeSaleListing(CompoundTag claimTag) {
        if (!claimTag.contains("saleListing", Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag tag = claimTag.getCompound("saleListing");
        return Optional.of(new ClaimSaleListing(
                UUID.fromString(tag.getString("listingId")),
                UUID.fromString(tag.getString("sellerPlayerId")),
                tag.getString("sellerName"),
                new BigDecimal(tag.getString("price")),
                Instant.ofEpochMilli(tag.getLong("listedAt"))
        ));
    }

    private static ListTag encodeLeases(Map<LeaseId, LeaseContract> leases) {
        ListTag tags = new ListTag();
        leases.values().stream()
                .map(ClaimNbtCodec::encodeLease)
                .forEach(tags::add);
        return tags;
    }

    private static CompoundTag encodeLease(LeaseContract lease) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", lease.id().value().toString());
        tag.putString("claimId", lease.claimId().value().toString());
        tag.put("tenant", encodeOwner(lease.tenant()));
        tag.putString("roleId", lease.roleId().value());
        tag.putString("price", lease.price().toPlainString());
        tag.putLong("durationSeconds", lease.durationSeconds());
        tag.putLong("offeredAt", lease.offeredAt().toEpochMilli());
        lease.startsAt().ifPresent(startsAt -> tag.putLong("startsAt", startsAt.toEpochMilli()));
        lease.expiresAt().ifPresent(expiresAt -> tag.putLong("expiresAt", expiresAt.toEpochMilli()));
        tag.putString("status", lease.status().name());
        tag.putBoolean("roleGranted", lease.roleGranted());
        return tag;
    }

    private static Map<LeaseId, LeaseContract> decodeLeases(ListTag tags) {
        Map<LeaseId, LeaseContract> leases = new LinkedHashMap<>();
        for (int i = 0; i < tags.size(); i++) {
            LeaseContract lease = decodeLease(tags.getCompound(i));
            leases.put(lease.id(), lease);
        }
        return Map.copyOf(leases);
    }

    private static LeaseContract decodeLease(CompoundTag tag) {
        Optional<Instant> startsAt = tag.contains("startsAt", Tag.TAG_LONG)
                ? Optional.of(Instant.ofEpochMilli(tag.getLong("startsAt")))
                : Optional.empty();
        Optional<Instant> expiresAt = tag.contains("expiresAt", Tag.TAG_LONG)
                ? Optional.of(Instant.ofEpochMilli(tag.getLong("expiresAt")))
                : Optional.empty();
        return new LeaseContract(
                new LeaseId(UUID.fromString(tag.getString("id"))),
                new ClaimId(UUID.fromString(tag.getString("claimId"))),
                decodeOwner(tag.getCompound("tenant")),
                new RoleId(tag.getString("roleId")),
                new BigDecimal(tag.getString("price")),
                tag.getLong("durationSeconds"),
                Instant.ofEpochMilli(tag.getLong("offeredAt")),
                startsAt,
                expiresAt,
                LeaseStatus.valueOf(tag.getString("status")),
                tag.getBoolean("roleGranted")
        );
    }
}
