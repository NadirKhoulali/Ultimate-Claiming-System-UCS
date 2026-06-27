package com.nadirkhoulali.ucs.storage;

import com.nadirkhoulali.ucs.core.model.ArchiveId;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimArchive;
import com.nadirkhoulali.ucs.core.model.ClaimChunk;
import com.nadirkhoulali.ucs.core.model.ClaimId;
import com.nadirkhoulali.ucs.core.model.ClaimMetadata;
import com.nadirkhoulali.ucs.core.model.ClaimSpawn;
import com.nadirkhoulali.ucs.core.model.FlagId;
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
                decodeFlags(tag.getList("flagOverrides", Tag.TAG_STRING))
        );
    }

    static CompoundTag encodeArchive(ClaimArchive archive) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", archive.id().value().toString());
        tag.put("claim", encodeClaim(archive.claim()));
        tag.putLong("archivedAt", archive.archivedAt().toEpochMilli());
        tag.putString("reason", archive.reason());
        return tag;
    }

    static ClaimArchive decodeArchive(CompoundTag tag) {
        return new ClaimArchive(
                new ArchiveId(UUID.fromString(tag.getString("id"))),
                decodeClaim(tag.getCompound("claim")),
                Instant.ofEpochMilli(tag.getLong("archivedAt")),
                tag.getString("reason")
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
}
