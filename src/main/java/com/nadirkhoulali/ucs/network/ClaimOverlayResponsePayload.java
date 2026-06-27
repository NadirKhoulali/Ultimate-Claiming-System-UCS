package com.nadirkhoulali.ucs.network;

import com.nadirkhoulali.ucs.UcsMod;
import com.nadirkhoulali.ucs.core.model.OwnerType;
import com.nadirkhoulali.ucs.map.ClaimMapOverlayChunk;
import com.nadirkhoulali.ucs.map.ClaimMapOverlayEntry;
import com.nadirkhoulali.ucs.map.ClaimMapOverlayRelation;
import com.nadirkhoulali.ucs.map.ClaimMapOverlayService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record ClaimOverlayResponsePayload(
        int requestId,
        String dimension,
        List<ClaimMapOverlayEntry> entries
) implements CustomPacketPayload {
    private static final int MAX_TEXT_LENGTH = 160;
    public static final int MAX_DIMENSION_LENGTH = 128;
    public static final Type<ClaimOverlayResponsePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UcsMod.MOD_ID, "claim_overlay_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimOverlayResponsePayload> STREAM_CODEC = CustomPacketPayload.codec(
            ClaimOverlayResponsePayload::write,
            ClaimOverlayResponsePayload::read
    );

    public ClaimOverlayResponsePayload {
        dimension = Objects.requireNonNull(dimension, "dimension").trim();
        if (dimension.isBlank() || dimension.length() > MAX_DIMENSION_LENGTH) {
            throw new IllegalArgumentException("invalid overlay response dimension");
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (entries.size() > ClaimMapOverlayService.MAX_OVERLAY_CLAIMS) {
            throw new IllegalArgumentException("too many claim overlay entries");
        }
        int chunks = entries.stream().mapToInt(entry -> entry.chunks().size()).sum();
        if (chunks > ClaimMapOverlayService.MAX_OVERLAY_CHUNKS) {
            throw new IllegalArgumentException("too many claim overlay chunks");
        }
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(requestId);
        buffer.writeUtf(dimension, MAX_DIMENSION_LENGTH);
        buffer.writeVarInt(entries.size());
        for (ClaimMapOverlayEntry entry : entries) {
            writeEntry(buffer, entry);
        }
    }

    private static ClaimOverlayResponsePayload read(RegistryFriendlyByteBuf buffer) {
        int requestId = buffer.readVarInt();
        String dimension = buffer.readUtf(MAX_DIMENSION_LENGTH);
        int size = buffer.readVarInt();
        if (size < 0 || size > ClaimMapOverlayService.MAX_OVERLAY_CLAIMS) {
            throw new IllegalArgumentException("invalid claim overlay entry count " + size);
        }
        List<ClaimMapOverlayEntry> entries = new ArrayList<>(size);
        int chunks = 0;
        for (int i = 0; i < size; i++) {
            ClaimMapOverlayEntry entry = readEntry(buffer);
            chunks += entry.chunks().size();
            if (chunks > ClaimMapOverlayService.MAX_OVERLAY_CHUNKS) {
                throw new IllegalArgumentException("claim overlay chunk count exceeds limit");
            }
            entries.add(entry);
        }
        return new ClaimOverlayResponsePayload(requestId, dimension, entries);
    }

    private static void writeEntry(RegistryFriendlyByteBuf buffer, ClaimMapOverlayEntry entry) {
        buffer.writeUtf(entry.claimId(), MAX_TEXT_LENGTH);
        buffer.writeUtf(entry.displayName(), MAX_TEXT_LENGTH);
        buffer.writeUtf(entry.ownerKey(), MAX_TEXT_LENGTH);
        buffer.writeVarInt(entry.ownerType().ordinal());
        buffer.writeVarInt(entry.relation().ordinal());
        buffer.writeBoolean(entry.forSale());
        buffer.writeBoolean(entry.leased());
        buffer.writeInt(entry.fillColor());
        buffer.writeInt(entry.borderColor());
        buffer.writeInt(entry.saleAccentColor());
        buffer.writeInt(entry.leaseAccentColor());
        buffer.writeVarInt(entry.chunks().size());
        for (ClaimMapOverlayChunk chunk : entry.chunks()) {
            buffer.writeVarInt(chunk.x());
            buffer.writeVarInt(chunk.z());
        }
    }

    private static ClaimMapOverlayEntry readEntry(RegistryFriendlyByteBuf buffer) {
        String claimId = buffer.readUtf(MAX_TEXT_LENGTH);
        String displayName = buffer.readUtf(MAX_TEXT_LENGTH);
        String ownerKey = buffer.readUtf(MAX_TEXT_LENGTH);
        OwnerType ownerType = readEnum(buffer, OwnerType.values(), "owner type");
        ClaimMapOverlayRelation relation = readEnum(buffer, ClaimMapOverlayRelation.values(), "overlay relation");
        boolean forSale = buffer.readBoolean();
        boolean leased = buffer.readBoolean();
        int fillColor = buffer.readInt();
        int borderColor = buffer.readInt();
        int saleAccentColor = buffer.readInt();
        int leaseAccentColor = buffer.readInt();
        int chunkCount = buffer.readVarInt();
        if (chunkCount < 0 || chunkCount > ClaimMapOverlayService.MAX_OVERLAY_CHUNKS) {
            throw new IllegalArgumentException("invalid claim overlay chunk count " + chunkCount);
        }
        List<ClaimMapOverlayChunk> chunks = new ArrayList<>(chunkCount);
        for (int i = 0; i < chunkCount; i++) {
            chunks.add(new ClaimMapOverlayChunk(buffer.readVarInt(), buffer.readVarInt()));
        }
        return new ClaimMapOverlayEntry(
                claimId,
                displayName,
                ownerKey,
                ownerType,
                relation,
                chunks,
                forSale,
                leased,
                fillColor,
                borderColor,
                saleAccentColor,
                leaseAccentColor
        );
    }

    private static <T> T readEnum(RegistryFriendlyByteBuf buffer, T[] values, String fieldName) {
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("invalid claim overlay " + fieldName + " " + ordinal);
        }
        return values[ordinal];
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
