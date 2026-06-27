package com.nadirkhoulali.ucs.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
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
}
