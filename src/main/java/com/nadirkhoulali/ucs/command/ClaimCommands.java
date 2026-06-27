package com.nadirkhoulali.ucs.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.nadirkhoulali.ucs.claim.ClaimChunkEditAction;
import com.nadirkhoulali.ucs.claim.ClaimChunkEditFailure;
import com.nadirkhoulali.ucs.claim.ClaimChunkEditRequest;
import com.nadirkhoulali.ucs.claim.ClaimChunkEditResult;
import com.nadirkhoulali.ucs.claim.ClaimCreationFailure;
import com.nadirkhoulali.ucs.claim.ClaimCreationRequest;
import com.nadirkhoulali.ucs.claim.ClaimCreationResult;
import com.nadirkhoulali.ucs.config.UcsCommonConfig;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.service.UcsServices;
import com.nadirkhoulali.ucs.storage.ClaimRepository;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.time.Instant;
import java.util.Optional;

public final class ClaimCommands {
    private ClaimCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, UcsServices services) {
        dispatcher.register(Commands.literal("claim")
                .executes(context -> claim(context, services, 0))
                .then(Commands.literal("add").executes(context -> edit(context, services, ClaimChunkEditAction.ADD)))
                .then(Commands.literal("remove").executes(context -> edit(context, services, ClaimChunkEditAction.REMOVE)))
                .then(Commands.literal("split").executes(context -> edit(context, services, ClaimChunkEditAction.SPLIT)))
                .then(Commands.literal("merge").executes(context -> edit(context, services, ClaimChunkEditAction.MERGE)))
                .then(Commands.argument("radius", IntegerArgumentType.integer(0))
                        .executes(context -> claim(context, services, IntegerArgumentType.getInteger(context, "radius")))));
    }

    private static int claim(CommandContext<CommandSourceStack> context, UcsServices services, int radius) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.ucs.claim.player_only"));
            return 0;
        }

        Optional<ClaimRepository> repository = services.claimRepository();
        if (repository.isEmpty() || services.claimService().isEmpty()) {
            source.sendFailure(Component.translatable("command.ucs.claim.service_unavailable"));
            return 0;
        }

        ChunkPos chunkPos = new ChunkPos(player.blockPosition());
        ChunkKey center = new ChunkKey(player.serverLevel().dimension().location().toString(), chunkPos.x, chunkPos.z);
        ClaimCreationRequest request = new ClaimCreationRequest(
                player.getUUID(),
                player.getGameProfile().getName(),
                center,
                radius,
                Instant.now()
        );

        ClaimCreationResult result = services.claimCreation().createPlayerClaim(
                repository.orElseThrow(),
                services.claimService().orElseThrow(),
                UcsCommonConfig.snapshot(),
                request
        );

        if (result.claim().isPresent()) {
            source.sendSuccess(
                    () -> successMessage(result, center, radius),
                    false
            );
            return Command.SINGLE_SUCCESS;
        }

        source.sendFailure(failureMessage(result.failure().orElseThrow()));
        return 0;
    }

    private static int edit(CommandContext<CommandSourceStack> context, UcsServices services, ClaimChunkEditAction action) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.ucs.claim.player_only"));
            return 0;
        }

        Optional<ClaimRepository> repository = services.claimRepository();
        if (repository.isEmpty() || services.claimService().isEmpty()) {
            source.sendFailure(Component.translatable("command.ucs.claim.service_unavailable"));
            return 0;
        }

        ClaimChunkEditRequest request = editRequest(player);
        ClaimChunkEditResult result = switch (action) {
            case ADD -> services.claimChunkEdit().addChunk(
                    repository.orElseThrow(),
                    services.claimService().orElseThrow(),
                    UcsCommonConfig.snapshot(),
                    request
            );
            case REMOVE -> services.claimChunkEdit().removeChunk(
                    repository.orElseThrow(),
                    services.claimService().orElseThrow(),
                    request
            );
            case SPLIT -> services.claimChunkEdit().splitClaim(
                    repository.orElseThrow(),
                    services.claimService().orElseThrow(),
                    request
            );
            case MERGE -> services.claimChunkEdit().mergeAdjacentClaims(
                    repository.orElseThrow(),
                    services.claimService().orElseThrow(),
                    UcsCommonConfig.snapshot(),
                    request
            );
        };

        if (result.failure().isPresent()) {
            source.sendFailure(editFailureMessage(result.failure().orElseThrow()));
            return 0;
        }

        source.sendSuccess(() -> editSuccessMessage(result, request.chunk()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static Component successMessage(ClaimCreationResult result, ChunkKey center, int radius) {
        if (radius == 0) {
            return Component.translatable(
                    "command.ucs.claim.success.single",
                    center.x(),
                    center.z(),
                    center.dimension(),
                    result.claim().orElseThrow().displayName()
            );
        }
        return Component.translatable(
                "command.ucs.claim.success.radius",
                result.selectedChunkCount(),
                center.x(),
                center.z(),
                center.dimension(),
                result.claim().orElseThrow().displayName()
        );
    }

    private static Component failureMessage(ClaimCreationFailure failure) {
        return switch (failure.reason()) {
            case DIMENSION_DISABLED -> Component.translatable("command.ucs.claim.denied.dimension_disabled", failure.detail());
            case RADIUS_TOO_LARGE -> Component.translatable("command.ucs.claim.denied.radius_too_large", failure.detail());
            case CLAIM_TOO_LARGE -> Component.translatable("command.ucs.claim.denied.claim_too_large", failure.detail());
            case TOO_MANY_CLAIMS -> Component.translatable("command.ucs.claim.denied.too_many_claims", failure.detail());
            case TOO_MANY_CHUNKS -> Component.translatable("command.ucs.claim.denied.too_many_chunks", failure.detail());
            case OVERLAP -> Component.translatable("command.ucs.claim.denied.overlap", failure.detail());
            case SAVE_FAILED -> Component.translatable("command.ucs.claim.denied.save_failed", failure.detail());
        };
    }

    private static ClaimChunkEditRequest editRequest(ServerPlayer player) {
        ChunkPos chunkPos = new ChunkPos(player.blockPosition());
        return new ClaimChunkEditRequest(
                player.getUUID(),
                player.getGameProfile().getName(),
                new ChunkKey(player.serverLevel().dimension().location().toString(), chunkPos.x, chunkPos.z),
                Instant.now()
        );
    }

    private static Component editSuccessMessage(ClaimChunkEditResult result, ChunkKey chunk) {
        return switch (result.action()) {
            case ADD -> Component.translatable(
                    "command.ucs.claim.edit.added",
                    chunk.x(),
                    chunk.z(),
                    result.claims().getFirst().displayName()
            );
            case REMOVE -> Component.translatable(
                    "command.ucs.claim.edit.removed",
                    chunk.x(),
                    chunk.z(),
                    result.claims().getFirst().displayName()
            );
            case SPLIT -> Component.translatable(
                    "command.ucs.claim.edit.split",
                    result.claims().size(),
                    chunk.x(),
                    chunk.z()
            );
            case MERGE -> Component.translatable(
                    "command.ucs.claim.edit.merged",
                    result.claims().getFirst().displayName(),
                    result.claims().getFirst().chunks().size()
            );
        };
    }

    private static Component editFailureMessage(ClaimChunkEditFailure failure) {
        return switch (failure.reason()) {
            case NO_CLAIM_AT_CHUNK -> Component.translatable("command.ucs.claim.edit.denied.no_claim", failure.detail());
            case NOT_OWNER -> Component.translatable("command.ucs.claim.edit.denied.not_owner", failure.detail());
            case CHUNK_ALREADY_CLAIMED -> Component.translatable("command.ucs.claim.edit.denied.already_claimed", failure.detail());
            case NOT_ADJACENT -> Component.translatable("command.ucs.claim.edit.denied.not_adjacent", failure.detail());
            case AMBIGUOUS_ADJACENT_CLAIMS -> Component.translatable("command.ucs.claim.edit.denied.ambiguous", failure.detail());
            case CLAIM_TOO_LARGE -> Component.translatable("command.ucs.claim.denied.claim_too_large", failure.detail());
            case TOO_MANY_CHUNKS -> Component.translatable("command.ucs.claim.denied.too_many_chunks", failure.detail());
            case CANNOT_REMOVE_ONLY_CHUNK -> Component.translatable("command.ucs.claim.edit.denied.only_chunk", failure.detail());
            case WOULD_SPLIT -> Component.translatable("command.ucs.claim.edit.denied.would_split", failure.detail());
            case NO_MERGE_TARGETS -> Component.translatable("command.ucs.claim.edit.denied.no_merge_targets", failure.detail());
            case SAVE_FAILED -> Component.translatable("command.ucs.claim.denied.save_failed", failure.detail());
        };
    }
}
