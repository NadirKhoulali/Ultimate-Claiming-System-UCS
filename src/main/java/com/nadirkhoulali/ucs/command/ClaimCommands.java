package com.nadirkhoulali.ucs.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.nadirkhoulali.ucs.claim.ClaimChunkEditAction;
import com.nadirkhoulali.ucs.claim.ClaimChunkEditFailure;
import com.nadirkhoulali.ucs.claim.ClaimChunkEditRequest;
import com.nadirkhoulali.ucs.claim.ClaimChunkEditResult;
import com.nadirkhoulali.ucs.claim.ClaimCreationFailure;
import com.nadirkhoulali.ucs.claim.ClaimCreationRequest;
import com.nadirkhoulali.ucs.claim.ClaimCreationResult;
import com.nadirkhoulali.ucs.claim.ClaimMetadataFailure;
import com.nadirkhoulali.ucs.claim.ClaimMetadataRequest;
import com.nadirkhoulali.ucs.claim.ClaimMetadataResult;
import com.nadirkhoulali.ucs.claim.ClaimTeleportService;
import com.nadirkhoulali.ucs.claim.ClaimTeleportStartResult;
import com.nadirkhoulali.ucs.config.UcsCommonConfig;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.ClaimSpawn;
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
                .then(Commands.literal("name")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> rename(context, services, StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("description")
                        .then(Commands.argument("description", StringArgumentType.greedyString())
                                .executes(context -> describe(context, services, StringArgumentType.getString(context, "description")))))
                .then(Commands.literal("setspawn").executes(context -> setSpawn(context, services)))
                .then(Commands.literal("home").executes(context -> home(context, services)))
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

    private static int rename(CommandContext<CommandSourceStack> context, UcsServices services, String name) {
        return updateMetadata(context, services, metadataRequest -> services.claimMetadata().renameClaim(
                services.claimRepository().orElseThrow(),
                services.claimService().orElseThrow(),
                UcsCommonConfig.snapshot(),
                metadataRequest,
                name
        ));
    }

    private static int describe(CommandContext<CommandSourceStack> context, UcsServices services, String description) {
        return updateMetadata(context, services, metadataRequest -> services.claimMetadata().describeClaim(
                services.claimRepository().orElseThrow(),
                services.claimService().orElseThrow(),
                UcsCommonConfig.snapshot(),
                metadataRequest,
                description
        ));
    }

    private static int setSpawn(CommandContext<CommandSourceStack> context, UcsServices services) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.ucs.claim.player_only"));
            return 0;
        }
        return updateMetadata(context, services, metadataRequest -> services.claimMetadata().setSpawn(
                services.claimRepository().orElseThrow(),
                services.claimService().orElseThrow(),
                metadataRequest,
                new ClaimSpawn(
                        metadataRequest.chunk(),
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        player.getYRot(),
                        player.getXRot()
                )
        ));
    }

    private static int updateMetadata(
            CommandContext<CommandSourceStack> context,
            UcsServices services,
            MetadataCommand command
    ) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.ucs.claim.player_only"));
            return 0;
        }
        if (services.claimRepository().isEmpty() || services.claimService().isEmpty()) {
            source.sendFailure(Component.translatable("command.ucs.claim.service_unavailable"));
            return 0;
        }

        ClaimMetadataResult result = command.execute(metadataRequest(player));
        if (result.failure().isPresent()) {
            source.sendFailure(metadataFailureMessage(result.failure().orElseThrow()));
            return 0;
        }

        source.sendSuccess(() -> metadataSuccessMessage(result), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int home(CommandContext<CommandSourceStack> context, UcsServices services) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.ucs.claim.player_only"));
            return 0;
        }

        Optional<ClaimRepository> repository = services.claimRepository();
        if (repository.isEmpty()) {
            source.sendFailure(Component.translatable("command.ucs.claim.service_unavailable"));
            return 0;
        }

        ClaimTeleportStartResult result = services.claimTeleport().requestHomeTeleport(
                repository.orElseThrow(),
                UcsCommonConfig.snapshot(),
                player,
                Instant.now()
        );
        if (result.failure().isPresent()) {
            source.sendFailure(ClaimTeleportService.teleportFailureMessage(result.failure().orElseThrow()));
            return 0;
        }

        if (result.immediate()) {
            source.sendSuccess(
                    () -> Component.translatable("command.ucs.claim.teleport.completed.named", result.claim().orElseThrow().displayName()),
                    false
            );
        } else {
            source.sendSuccess(
                    () -> Component.translatable(
                            "command.ucs.claim.teleport.queued",
                            result.claim().orElseThrow().displayName(),
                            result.delaySeconds()
                    ),
                    false
            );
        }
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

    private static ClaimMetadataRequest metadataRequest(ServerPlayer player) {
        ChunkPos chunkPos = new ChunkPos(player.blockPosition());
        return new ClaimMetadataRequest(
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

    private static Component metadataSuccessMessage(ClaimMetadataResult result) {
        return switch (result.action()) {
            case RENAME -> Component.translatable(
                    "command.ucs.claim.metadata.renamed",
                    result.claim().orElseThrow().displayName()
            );
            case DESCRIBE -> Component.translatable(
                    "command.ucs.claim.metadata.described",
                    result.claim().orElseThrow().displayName()
            );
            case SET_SPAWN -> Component.translatable(
                    "command.ucs.claim.metadata.spawn_set",
                    result.claim().orElseThrow().displayName()
            );
        };
    }

    private static Component metadataFailureMessage(ClaimMetadataFailure failure) {
        return switch (failure.reason()) {
            case NO_CLAIM_AT_CHUNK -> Component.translatable("command.ucs.claim.edit.denied.no_claim", failure.detail());
            case NOT_OWNER -> Component.translatable("command.ucs.claim.edit.denied.not_owner", failure.detail());
            case INVALID_NAME -> Component.translatable("command.ucs.claim.metadata.denied.invalid_name", failure.detail());
            case INVALID_DESCRIPTION -> Component.translatable("command.ucs.claim.metadata.denied.invalid_description", failure.detail());
            case SAVE_FAILED -> Component.translatable("command.ucs.claim.denied.save_failed", failure.detail());
        };
    }

    @FunctionalInterface
    private interface MetadataCommand {
        ClaimMetadataResult execute(ClaimMetadataRequest request);
    }
}
