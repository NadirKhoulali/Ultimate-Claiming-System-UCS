package com.nadirkhoulali.ucs.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.nadirkhoulali.ucs.api.ClaimLeaseView;
import com.nadirkhoulali.ucs.api.UcsClaimService;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyProvider;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyResult;
import com.nadirkhoulali.ucs.claim.ClaimChunkEditAction;
import com.nadirkhoulali.ucs.claim.ClaimChunkEditFailure;
import com.nadirkhoulali.ucs.claim.ClaimChunkEditRequest;
import com.nadirkhoulali.ucs.claim.ClaimChunkEditResult;
import com.nadirkhoulali.ucs.claim.ClaimCreationFailure;
import com.nadirkhoulali.ucs.claim.ClaimCreationRequest;
import com.nadirkhoulali.ucs.claim.ClaimCreationResult;
import com.nadirkhoulali.ucs.claim.ClaimExpulsionResult;
import com.nadirkhoulali.ucs.claim.ClaimExpulsionService;
import com.nadirkhoulali.ucs.claim.ClaimExpulsionStatus;
import com.nadirkhoulali.ucs.claim.ClaimLeaseAction;
import com.nadirkhoulali.ucs.claim.ClaimLeaseFailure;
import com.nadirkhoulali.ucs.claim.ClaimLeaseRequest;
import com.nadirkhoulali.ucs.claim.ClaimLeaseResult;
import com.nadirkhoulali.ucs.claim.ClaimMetadataFailure;
import com.nadirkhoulali.ucs.claim.ClaimMetadataRequest;
import com.nadirkhoulali.ucs.claim.ClaimMetadataResult;
import com.nadirkhoulali.ucs.claim.ClaimRoleFailure;
import com.nadirkhoulali.ucs.claim.ClaimRoleAction;
import com.nadirkhoulali.ucs.claim.ClaimRoleRequest;
import com.nadirkhoulali.ucs.claim.ClaimRoleResult;
import com.nadirkhoulali.ucs.claim.ClaimRoleTarget;
import com.nadirkhoulali.ucs.claim.ClaimSaleFailure;
import com.nadirkhoulali.ucs.claim.ClaimSaleRequest;
import com.nadirkhoulali.ucs.claim.ClaimSaleResult;
import com.nadirkhoulali.ucs.claim.ClaimTeleportService;
import com.nadirkhoulali.ucs.claim.ClaimTeleportStartResult;
import com.nadirkhoulali.ucs.config.UcsCommonConfig;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimOwnership;
import com.nadirkhoulali.ucs.core.model.ClaimSpawn;
import com.nadirkhoulali.ucs.core.model.FlagId;
import com.nadirkhoulali.ucs.core.model.LeaseId;
import com.nadirkhoulali.ucs.core.model.RoleId;
import com.nadirkhoulali.ucs.permission.UcsPermission;
import com.nadirkhoulali.ucs.service.UcsServices;
import com.nadirkhoulali.ucs.storage.ClaimRepository;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import java.util.UUID;

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
                .then(Commands.literal("trust")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> trust(context, services, EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("untrust")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> untrust(context, services, EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("role")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                UcsCommonConfig.snapshot().roles().defaultRoleIds(),
                                                builder
                                        ))
                                        .executes(context -> assignRole(
                                                context,
                                                services,
                                                EntityArgument.getPlayer(context, "player"),
                                                StringArgumentType.getString(context, "role")
                                        )))))
                .then(Commands.literal("ban")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> ban(context, services, EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("unban")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> unban(context, services, EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> kick(context, services, EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("sale")
                        .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01D))
                                .executes(context -> listSale(
                                        context,
                                        services,
                                        BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "price"))
                                )))
                        .then(Commands.literal("cancel").executes(context -> cancelSale(context, services)))
                        .then(Commands.literal("buy")
                                .executes(context -> buySale(context, services, Optional.empty()))
                                .then(Commands.argument("listingId", StringArgumentType.word())
                                        .executes(context -> buySaleWithListingId(
                                                context,
                                                services,
                                                StringArgumentType.getString(context, "listingId")
                                        )))))
                .then(Commands.literal("lease")
                        .then(Commands.literal("offer")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01D))
                                                .then(Commands.argument("days", IntegerArgumentType.integer(1))
                                                        .executes(context -> offerLease(
                                                                context,
                                                                services,
                                                                EntityArgument.getPlayer(context, "player"),
                                                                BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "price")),
                                                                IntegerArgumentType.getInteger(context, "days"),
                                                                "tenant"
                                                        ))
                                                        .then(Commands.argument("role", StringArgumentType.word())
                                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                                        UcsCommonConfig.snapshot().roles().defaultRoleIds(),
                                                                        builder
                                                                ))
                                                                .executes(context -> offerLease(
                                                                        context,
                                                                        services,
                                                                        EntityArgument.getPlayer(context, "player"),
                                                                        BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "price")),
                                                                        IntegerArgumentType.getInteger(context, "days"),
                                                                        StringArgumentType.getString(context, "role")
                                                                )))))))
                        .then(Commands.literal("accept")
                                .then(Commands.argument("leaseId", StringArgumentType.word())
                                        .suggests((context, builder) -> leaseIdSuggestions(context, services, builder))
                                        .executes(context -> acceptLease(
                                                context,
                                                services,
                                                StringArgumentType.getString(context, "leaseId")
                                        ))))
                        .then(Commands.literal("renew")
                                .then(Commands.argument("leaseId", StringArgumentType.word())
                                        .suggests((context, builder) -> leaseIdSuggestions(context, services, builder))
                                        .executes(context -> renewLease(
                                                context,
                                                services,
                                                StringArgumentType.getString(context, "leaseId")
                                        ))))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("leaseId", StringArgumentType.word())
                                        .suggests((context, builder) -> leaseIdSuggestions(context, services, builder))
                                        .executes(context -> cancelLease(
                                                context,
                                                services,
                                                StringArgumentType.getString(context, "leaseId")
                                        ))))
                        .then(Commands.literal("evict")
                                .then(Commands.argument("leaseId", StringArgumentType.word())
                                        .suggests((context, builder) -> leaseIdSuggestions(context, services, builder))
                                        .executes(context -> evictLease(
                                                context,
                                                services,
                                                StringArgumentType.getString(context, "leaseId")
                                        )))))
                .then(Commands.literal("invite")
                        .then(Commands.literal("accept").executes(context -> acceptInvite(context, services)))
                        .then(Commands.literal("decline").executes(context -> declineInvite(context, services))))
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
                request,
                activeEconomyProvider(services)
        );

        if (result.claim().isPresent()) {
            source.sendSuccess(
                    () -> claimSuccessMessage(result, center, radius),
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
        ClaimEconomyProvider economyProvider = activeEconomyProvider(services);
        ClaimChunkEditResult result = switch (action) {
            case ADD -> services.claimChunkEdit().addChunk(
                    repository.orElseThrow(),
                    services.claimService().orElseThrow(),
                    UcsCommonConfig.snapshot(),
                    request,
                    economyProvider
            );
            case REMOVE -> services.claimChunkEdit().removeChunk(
                    repository.orElseThrow(),
                    services.claimService().orElseThrow(),
                    UcsCommonConfig.snapshot(),
                    request,
                    economyProvider
            );
            case SPLIT -> services.claimChunkEdit().splitClaim(
                    repository.orElseThrow(),
                    services.claimService().orElseThrow(),
                    UcsCommonConfig.snapshot(),
                    request,
                    economyProvider
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

    private static int trust(CommandContext<CommandSourceStack> context, UcsServices services, ServerPlayer target) {
        return updateRole(context, services, roleRequest -> services.claimRoles().trustPlayer(
                services.claimRepository().orElseThrow(),
                services.claimService().orElseThrow(),
                UcsCommonConfig.snapshot(),
                roleRequest,
                roleTarget(target)
        ));
    }

    private static int untrust(CommandContext<CommandSourceStack> context, UcsServices services, ServerPlayer target) {
        return updateRole(context, services, roleRequest -> services.claimRoles().removePlayer(
                services.claimRepository().orElseThrow(),
                services.claimService().orElseThrow(),
                UcsCommonConfig.snapshot(),
                roleRequest,
                roleTarget(target)
        ));
    }

    private static int assignRole(
            CommandContext<CommandSourceStack> context,
            UcsServices services,
            ServerPlayer target,
            String roleValue
    ) {
        RoleId role;
        try {
            role = new RoleId(roleValue);
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.translatable("command.ucs.claim.roles.denied.role_not_configured", roleValue));
            return 0;
        }

        return updateRole(context, services, roleRequest -> services.claimRoles().assignRole(
                services.claimRepository().orElseThrow(),
                services.claimService().orElseThrow(),
                UcsCommonConfig.snapshot(),
                roleRequest,
                roleTarget(target),
                role
        ));
    }

    private static int ban(CommandContext<CommandSourceStack> context, UcsServices services, ServerPlayer target) {
        return updateRole(context, services, true, roleRequest -> services.claimRoles().banPlayer(
                services.claimRepository().orElseThrow(),
                services.claimService().orElseThrow(),
                UcsCommonConfig.snapshot(),
                roleRequest,
                roleTarget(target)
        ));
    }

    private static int unban(CommandContext<CommandSourceStack> context, UcsServices services, ServerPlayer target) {
        return updateRole(context, services, true, roleRequest -> services.claimRoles().unbanPlayer(
                services.claimRepository().orElseThrow(),
                services.claimService().orElseThrow(),
                UcsCommonConfig.snapshot(),
                roleRequest,
                roleTarget(target)
        ));
    }

    private static int kick(CommandContext<CommandSourceStack> context, UcsServices services, ServerPlayer target) {
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

        ClaimRoleRequest request = roleRequest(player, services.permissions().has(player, UcsPermission.ADMIN));
        Optional<Claim> claim = repository.orElseThrow().findByChunk(request.chunk());
        if (claim.isEmpty()) {
            source.sendFailure(Component.translatable("command.ucs.claim.edit.denied.no_claim", request.chunk().storageKey()));
            return 0;
        }
        boolean canManageClaim = request.adminOverride()
                || ClaimOwnership.isOwnedBy(claim.orElseThrow(), ClaimOwnership.player(request.playerId(), request.playerName()));
        if (!canManageClaim) {
            source.sendFailure(Component.translatable("command.ucs.claim.edit.denied.not_owner", request.chunk().storageKey()));
            return 0;
        }

        ClaimExpulsionResult result = services.claimExpulsion().expelWithEvent(
                target,
                claim.orElseThrow(),
                UcsCommonConfig.snapshot(),
                new FlagId("ucs:expel"),
                "manual_kick"
        );
        if (result.status() != ClaimExpulsionStatus.EXPELLED) {
            source.sendFailure(ClaimExpulsionService.expulsionFailureMessage(result));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("command.ucs.claim.bans.kicked", target.getGameProfile().getName()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int listSale(CommandContext<CommandSourceStack> context, UcsServices services, BigDecimal price) {
        Optional<ClaimRepository> repository = services.claimRepository();
        if (repository.isEmpty() || services.claimService().isEmpty()) {
            context.getSource().sendFailure(Component.translatable("command.ucs.claim.service_unavailable"));
            return 0;
        }
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.translatable("command.ucs.claim.player_only"));
            return 0;
        }
        ClaimSaleResult result = services.claimSales().listClaimForSale(
                repository.orElseThrow(),
                services.claimService().orElseThrow(),
                UcsCommonConfig.snapshot(),
                ClaimSaleRequest.list(player.getUUID(), player.getGameProfile().getName(), currentChunk(player), price, Instant.now())
        );
        if (result.failure().isPresent()) {
            context.getSource().sendFailure(saleFailureMessage(result.failure().orElseThrow()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.ucs.claim.sale.listed",
                result.claim().orElseThrow().displayName(),
                activeEconomyProvider(services).format(result.claim().orElseThrow().saleListing().orElseThrow().price()),
                result.claim().orElseThrow().saleListing().orElseThrow().listingId().toString()
        ), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int cancelSale(CommandContext<CommandSourceStack> context, UcsServices services) {
        Optional<ClaimRepository> repository = services.claimRepository();
        if (repository.isEmpty() || services.claimService().isEmpty()) {
            context.getSource().sendFailure(Component.translatable("command.ucs.claim.service_unavailable"));
            return 0;
        }
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.translatable("command.ucs.claim.player_only"));
            return 0;
        }
        ClaimSaleResult result = services.claimSales().cancelSale(
                repository.orElseThrow(),
                services.claimService().orElseThrow(),
                ClaimSaleRequest.simple(player.getUUID(), player.getGameProfile().getName(), currentChunk(player), Instant.now())
        );
        if (result.failure().isPresent()) {
            context.getSource().sendFailure(saleFailureMessage(result.failure().orElseThrow()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.ucs.claim.sale.cancelled",
                result.claim().orElseThrow().displayName()
        ), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int buySale(
            CommandContext<CommandSourceStack> context,
            UcsServices services,
            Optional<UUID> expectedListingId
    ) {
        Optional<ClaimRepository> repository = services.claimRepository();
        if (repository.isEmpty() || services.claimService().isEmpty()) {
            context.getSource().sendFailure(Component.translatable("command.ucs.claim.service_unavailable"));
            return 0;
        }
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.translatable("command.ucs.claim.player_only"));
            return 0;
        }
        ClaimSaleRequest request = ClaimSaleRequest.simple(
                player.getUUID(),
                player.getGameProfile().getName(),
                currentChunk(player),
                Instant.now()
        );
        if (expectedListingId.isPresent()) {
            request = request.withExpectedListingId(expectedListingId.orElseThrow());
        }
        ClaimSaleResult result = services.claimSales().purchaseClaim(
                repository.orElseThrow(),
                services.claimService().orElseThrow(),
                UcsCommonConfig.snapshot(),
                request,
                activeEconomyProvider(services)
        );
        if (result.failure().isPresent()) {
            context.getSource().sendFailure(saleFailureMessage(result.failure().orElseThrow()));
            return 0;
        }
        context.getSource().sendSuccess(() -> appendEconomyMessage(Component.translatable(
                "command.ucs.claim.sale.purchased",
                result.claim().orElseThrow().displayName()
        ), result.economyResult().orElse(null), true), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int buySaleWithListingId(
            CommandContext<CommandSourceStack> context,
            UcsServices services,
            String rawListingId
    ) {
        Optional<UUID> listingId = parseListingId(context, rawListingId);
        if (listingId.isEmpty()) {
            return 0;
        }
        return buySale(context, services, listingId);
    }

    private static int offerLease(
            CommandContext<CommandSourceStack> context,
            UcsServices services,
            ServerPlayer tenant,
            BigDecimal price,
            int days,
            String roleValue
    ) {
        Optional<CommandLeaseContext> leaseContext = commandLeaseContext(context, services);
        if (leaseContext.isEmpty()) {
            return 0;
        }

        RoleId role;
        try {
            role = new RoleId(roleValue);
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.translatable("command.ucs.claim.roles.denied.role_not_configured", roleValue));
            return 0;
        }

        ServerPlayer player = leaseContext.orElseThrow().player();
        ClaimLeaseResult result = services.claimLeases().offerLease(
                leaseContext.orElseThrow().repository(),
                leaseContext.orElseThrow().claimService(),
                UcsCommonConfig.snapshot(),
                ClaimLeaseRequest.offer(
                        player.getUUID(),
                        player.getGameProfile().getName(),
                        currentChunk(player),
                        Instant.now(),
                        roleTarget(tenant),
                        price,
                        Duration.ofDays(days),
                        role
                )
        );
        return sendLeaseResult(context, services, result);
    }

    private static int acceptLease(
            CommandContext<CommandSourceStack> context,
            UcsServices services,
            String rawLeaseId
    ) {
        return leaseById(context, services, rawLeaseId, (leaseContext, request) -> services.claimLeases().acceptLease(
                leaseContext.repository(),
                leaseContext.claimService(),
                UcsCommonConfig.snapshot(),
                request,
                activeEconomyProvider(services)
        ));
    }

    private static int renewLease(
            CommandContext<CommandSourceStack> context,
            UcsServices services,
            String rawLeaseId
    ) {
        return leaseById(context, services, rawLeaseId, (leaseContext, request) -> services.claimLeases().renewLease(
                leaseContext.repository(),
                leaseContext.claimService(),
                UcsCommonConfig.snapshot(),
                request,
                activeEconomyProvider(services)
        ));
    }

    private static int cancelLease(
            CommandContext<CommandSourceStack> context,
            UcsServices services,
            String rawLeaseId
    ) {
        return leaseById(context, services, rawLeaseId, (leaseContext, request) -> services.claimLeases().cancelLease(
                leaseContext.repository(),
                leaseContext.claimService(),
                request
        ));
    }

    private static int evictLease(
            CommandContext<CommandSourceStack> context,
            UcsServices services,
            String rawLeaseId
    ) {
        return leaseById(context, services, rawLeaseId, (leaseContext, request) -> services.claimLeases().evictTenant(
                leaseContext.repository(),
                leaseContext.claimService(),
                request
        ));
    }

    private static int leaseById(
            CommandContext<CommandSourceStack> context,
            UcsServices services,
            String rawLeaseId,
            LeaseCommand command
    ) {
        Optional<CommandLeaseContext> leaseContext = commandLeaseContext(context, services);
        if (leaseContext.isEmpty()) {
            return 0;
        }
        Optional<LeaseId> leaseId = parseLeaseId(context, rawLeaseId);
        if (leaseId.isEmpty()) {
            return 0;
        }
        ServerPlayer player = leaseContext.orElseThrow().player();
        ClaimLeaseRequest request = ClaimLeaseRequest.byLease(
                player.getUUID(),
                player.getGameProfile().getName(),
                currentChunk(player),
                Instant.now(),
                leaseId.orElseThrow()
        );
        return sendLeaseResult(context, services, command.execute(leaseContext.orElseThrow(), request));
    }

    private static Optional<CommandLeaseContext> commandLeaseContext(
            CommandContext<CommandSourceStack> context,
            UcsServices services
    ) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.ucs.claim.player_only"));
            return Optional.empty();
        }
        if (services.claimRepository().isEmpty() || services.claimService().isEmpty()) {
            source.sendFailure(Component.translatable("command.ucs.claim.service_unavailable"));
            return Optional.empty();
        }
        return Optional.of(new CommandLeaseContext(
                player,
                services.claimRepository().orElseThrow(),
                services.claimService().orElseThrow()
        ));
    }

    private static int sendLeaseResult(
            CommandContext<CommandSourceStack> context,
            UcsServices services,
            ClaimLeaseResult result
    ) {
        if (result.failure().isPresent()) {
            context.getSource().sendFailure(leaseFailureMessage(result.failure().orElseThrow()));
            return 0;
        }
        context.getSource().sendSuccess(() -> leaseSuccessMessage(services, result), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int acceptInvite(CommandContext<CommandSourceStack> context, UcsServices services) {
        return updateRole(context, services, roleRequest -> services.claimRoles().acceptInvite(
                services.claimRepository().orElseThrow(),
                services.claimService().orElseThrow(),
                UcsCommonConfig.snapshot(),
                roleRequest
        ));
    }

    private static int declineInvite(CommandContext<CommandSourceStack> context, UcsServices services) {
        return updateRole(context, services, roleRequest -> services.claimRoles().declineInvite(
                services.claimRepository().orElseThrow(),
                services.claimService().orElseThrow(),
                UcsCommonConfig.snapshot(),
                roleRequest
        ));
    }

    private static int updateRole(
            CommandContext<CommandSourceStack> context,
            UcsServices services,
            RoleCommand command
    ) {
        return updateRole(context, services, false, command);
    }

    private static int updateRole(
            CommandContext<CommandSourceStack> context,
            UcsServices services,
            boolean allowAdminOverride,
            RoleCommand command
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

        ClaimRoleResult result = command.execute(roleRequest(player, allowAdminOverride && services.permissions().has(player, UcsPermission.ADMIN)));
        if (result.failure().isPresent()) {
            source.sendFailure(roleFailureMessage(result.failure().orElseThrow()));
            return 0;
        }

        source.sendSuccess(() -> roleSuccessMessage(result), false);
        if (result.action() == ClaimRoleAction.BAN) {
            expelBannedTargetIfPresent(player, services, result);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void expelBannedTargetIfPresent(ServerPlayer actor, UcsServices services, ClaimRoleResult result) {
        result.target().ifPresent(target -> services.claimRepository().orElseThrow()
                .findById(result.claim().orElseThrow().id())
                .ifPresent(claim -> {
                    ServerPlayer targetPlayer = actor.server.getPlayerList().getPlayer(target.playerId());
                    if (targetPlayer != null) {
                        services.claimExpulsion().expelWithEvent(
                                targetPlayer,
                                claim,
                                UcsCommonConfig.snapshot(),
                                new FlagId("ucs:expel"),
                                "manual_ban"
                        );
                    }
                }));
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

    private static Component claimSuccessMessage(ClaimCreationResult result, ChunkKey center, int radius) {
        Component message = claimBaseSuccessMessage(result, center, radius);
        return appendEconomyMessage(message, result.economyResult().orElse(null), true);
    }

    private static Component claimBaseSuccessMessage(ClaimCreationResult result, ChunkKey center, int radius) {
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
            case PAYMENT_FAILED -> Component.translatable("command.ucs.claim.denied.payment_failed", failure.detail());
            case SAVE_FAILED -> Component.translatable("command.ucs.claim.denied.save_failed", failure.detail());
        };
    }

    private static ClaimChunkEditRequest editRequest(ServerPlayer player) {
        return new ClaimChunkEditRequest(
                player.getUUID(),
                player.getGameProfile().getName(),
                currentChunk(player),
                Instant.now()
        );
    }

    private static ChunkKey currentChunk(ServerPlayer player) {
        ChunkPos chunkPos = new ChunkPos(player.blockPosition());
        return new ChunkKey(player.serverLevel().dimension().location().toString(), chunkPos.x, chunkPos.z);
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

    private static ClaimRoleRequest roleRequest(ServerPlayer player) {
        return roleRequest(player, false);
    }

    private static ClaimRoleRequest roleRequest(ServerPlayer player, boolean adminOverride) {
        ChunkPos chunkPos = new ChunkPos(player.blockPosition());
        return new ClaimRoleRequest(
                player.getUUID(),
                player.getGameProfile().getName(),
                new ChunkKey(player.serverLevel().dimension().location().toString(), chunkPos.x, chunkPos.z),
                Instant.now(),
                adminOverride
        );
    }

    private static ClaimRoleTarget roleTarget(ServerPlayer player) {
        return new ClaimRoleTarget(player.getUUID(), player.getGameProfile().getName());
    }

    private static Component editSuccessMessage(ClaimChunkEditResult result, ChunkKey chunk) {
        return switch (result.action()) {
            case ADD -> appendEconomyMessage(Component.translatable(
                    "command.ucs.claim.edit.added",
                    chunk.x(),
                    chunk.z(),
                    result.claims().getFirst().displayName()
            ), result.economyResult().orElse(null), true);
            case REMOVE -> appendEconomyMessage(Component.translatable(
                    "command.ucs.claim.edit.removed",
                    chunk.x(),
                    chunk.z(),
                    result.claims().getFirst().displayName()
            ), result.economyResult().orElse(null), false);
            case SPLIT -> appendEconomyMessage(Component.translatable(
                    "command.ucs.claim.edit.split",
                    result.claims().size(),
                    chunk.x(),
                    chunk.z()
            ), result.economyResult().orElse(null), false);
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
            case PAYMENT_FAILED -> Component.translatable("command.ucs.claim.denied.payment_failed", failure.detail());
            case SAVE_FAILED -> Component.translatable("command.ucs.claim.denied.save_failed", failure.detail());
        };
    }

    private static Component saleFailureMessage(ClaimSaleFailure failure) {
        return switch (failure.reason()) {
            case NO_CLAIM_AT_CHUNK -> Component.translatable("command.ucs.claim.edit.denied.no_claim", failure.detail());
            case NOT_OWNER -> Component.translatable("command.ucs.claim.edit.denied.not_owner", failure.detail());
            case NOT_PLAYER_OWNED -> Component.translatable("command.ucs.claim.sale.denied.not_player_owned", failure.detail());
            case ALREADY_LISTED -> Component.translatable("command.ucs.claim.sale.denied.already_listed", failure.detail());
            case NOT_LISTED -> Component.translatable("command.ucs.claim.sale.denied.not_listed", failure.detail());
            case PRICE_TOO_LOW -> Component.translatable("command.ucs.claim.sale.denied.price_too_low");
            case PRICE_TOO_HIGH -> Component.translatable("command.ucs.claim.sale.denied.price_too_high", failure.detail());
            case SELF_PURCHASE -> Component.translatable("command.ucs.claim.sale.denied.self_purchase");
            case STALE_LISTING -> Component.translatable("command.ucs.claim.sale.denied.stale", failure.detail());
            case BUYER_LIMIT_EXCEEDED -> Component.translatable("command.ucs.claim.sale.denied.buyer_limit", failure.detail());
            case PAYMENT_FAILED -> Component.translatable("command.ucs.claim.denied.payment_failed", failure.detail());
            case SAVE_FAILED -> Component.translatable("command.ucs.claim.denied.save_failed", failure.detail());
        };
    }

    private static Component leaseSuccessMessage(UcsServices services, ClaimLeaseResult result) {
        ClaimLeaseView lease = result.lease().orElseThrow();
        String tenant = lease.tenant().playerName().orElse(lease.tenant().stableKey());
        String amount = activeEconomyProvider(services).format(lease.price());
        long days = Math.max(1L, Duration.ofSeconds(lease.durationSeconds()).toDays());
        return switch (result.action()) {
            case OFFER -> Component.translatable(
                    "command.ucs.claim.lease.offered",
                    tenant,
                    result.claim().orElseThrow().displayName(),
                    amount,
                    days,
                    lease.roleId().value(),
                    lease.id().value().toString()
            );
            case ACCEPT -> appendEconomyMessage(Component.translatable(
                    "command.ucs.claim.lease.accepted",
                    result.claim().orElseThrow().displayName(),
                    lease.expiresAt().map(Instant::toString).orElse("unknown")
            ), result.economyResult().orElse(null), true);
            case RENEW -> appendEconomyMessage(Component.translatable(
                    "command.ucs.claim.lease.renewed",
                    result.claim().orElseThrow().displayName(),
                    lease.expiresAt().map(Instant::toString).orElse("unknown")
            ), result.economyResult().orElse(null), true);
            case CANCEL -> Component.translatable(
                    "command.ucs.claim.lease.cancelled",
                    result.claim().orElseThrow().displayName(),
                    lease.id().value().toString()
            );
            case EVICT -> Component.translatable(
                    "command.ucs.claim.lease.evicted",
                    tenant,
                    result.claim().orElseThrow().displayName()
            );
            case EXPIRE -> Component.translatable(
                    "command.ucs.claim.lease.expired",
                    result.claim().orElseThrow().displayName(),
                    lease.id().value().toString()
            );
        };
    }

    private static Component leaseFailureMessage(ClaimLeaseFailure failure) {
        return switch (failure.reason()) {
            case NO_CLAIM_AT_CHUNK -> Component.translatable("command.ucs.claim.edit.denied.no_claim", failure.detail());
            case NO_LEASE -> Component.translatable("command.ucs.claim.lease.denied.no_lease", failure.detail());
            case NOT_OWNER -> Component.translatable("command.ucs.claim.edit.denied.not_owner", failure.detail());
            case NOT_PLAYER_OWNED -> Component.translatable("command.ucs.claim.sale.denied.not_player_owned", failure.detail());
            case TENANT_IS_OWNER -> Component.translatable("command.ucs.claim.lease.denied.tenant_owner");
            case ALREADY_HAS_LEASE -> Component.translatable("command.ucs.claim.lease.denied.already_has_lease", failure.detail());
            case TENANT_BANNED -> Component.translatable("command.ucs.claim.roles.denied.banned", failure.detail());
            case ROLE_NOT_CONFIGURED -> Component.translatable("command.ucs.claim.roles.denied.role_not_configured", failure.detail());
            case PRICE_TOO_LOW -> Component.translatable("command.ucs.claim.sale.denied.price_too_low");
            case DURATION_TOO_SHORT -> Component.translatable("command.ucs.claim.lease.denied.duration_too_short");
            case NOT_TENANT -> Component.translatable("command.ucs.claim.lease.denied.not_tenant");
            case NOT_OFFERED -> Component.translatable("command.ucs.claim.lease.denied.not_offered", failure.detail());
            case NOT_ACTIVE -> Component.translatable("command.ucs.claim.lease.denied.not_active", failure.detail());
            case PAYMENT_FAILED -> Component.translatable("command.ucs.claim.denied.payment_failed", failure.detail());
            case SAVE_FAILED -> Component.translatable("command.ucs.claim.denied.save_failed", failure.detail());
        };
    }

    private static Optional<UUID> parseListingId(CommandContext<CommandSourceStack> context, String raw) {
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.translatable("command.ucs.claim.sale.denied.invalid_listing_id"));
            return Optional.empty();
        }
    }

    private static Optional<LeaseId> parseLeaseId(CommandContext<CommandSourceStack> context, String raw) {
        try {
            return Optional.of(new LeaseId(UUID.fromString(raw)));
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.translatable("command.ucs.claim.lease.denied.invalid_lease_id"));
            return Optional.empty();
        }
    }

    private static CompletableFuture<Suggestions> leaseIdSuggestions(
            CommandContext<CommandSourceStack> context,
            UcsServices services,
            SuggestionsBuilder builder
    ) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || services.claimRepository().isEmpty()) {
            return builder.buildFuture();
        }
        return services.claimRepository().orElseThrow()
                .findByChunk(currentChunk(player))
                .map(claim -> SharedSuggestionProvider.suggest(
                        claim.leases().keySet().stream().map(leaseId -> leaseId.value().toString()),
                        builder
                ))
                .orElse(builder.buildFuture());
    }

    private static Component appendEconomyMessage(Component base, ClaimEconomyResult economyResult, boolean charge) {
        if (economyResult == null) {
            return base;
        }
        Component suffix;
        if (economyResult.success()) {
            suffix = Component.translatable(
                    charge ? "command.ucs.claim.economy.charged" : "command.ucs.claim.economy.refunded",
                    economyResult.formattedAmount()
            );
        } else {
            suffix = Component.translatable("command.ucs.claim.economy.refund_failed", economyResult.userMessage());
        }
        return Component.empty().append(base).append(Component.literal(" ")).append(suffix);
    }

    private static ClaimEconomyProvider activeEconomyProvider(UcsServices services) {
        return services.economyProviders().activeProvider();
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

    private static Component roleSuccessMessage(ClaimRoleResult result) {
        ClaimRoleTarget target = result.target().orElseThrow();
        String role = result.role().orElseThrow().value();
        return switch (result.action()) {
            case TRUST -> result.pendingInvite()
                    ? Component.translatable("command.ucs.claim.roles.invited", target.playerName(), role, result.claim().orElseThrow().displayName())
                    : Component.translatable("command.ucs.claim.roles.trusted", target.playerName(), role, result.claim().orElseThrow().displayName());
            case ASSIGN_ROLE -> result.pendingInvite()
                    ? Component.translatable("command.ucs.claim.roles.invited", target.playerName(), role, result.claim().orElseThrow().displayName())
                    : Component.translatable("command.ucs.claim.roles.assigned", target.playerName(), role, result.claim().orElseThrow().displayName());
            case BAN -> Component.translatable("command.ucs.claim.bans.banned", target.playerName(), result.claim().orElseThrow().displayName());
            case UNBAN -> Component.translatable("command.ucs.claim.bans.unbanned", target.playerName(), result.claim().orElseThrow().displayName());
            case UNTRUST -> Component.translatable("command.ucs.claim.roles.untrusted", target.playerName(), result.claim().orElseThrow().displayName());
            case ACCEPT_INVITE -> Component.translatable("command.ucs.claim.roles.accepted", role, result.claim().orElseThrow().displayName());
            case DECLINE_INVITE -> Component.translatable("command.ucs.claim.roles.declined", role, result.claim().orElseThrow().displayName());
        };
    }

    private static Component roleFailureMessage(ClaimRoleFailure failure) {
        return switch (failure.reason()) {
            case NO_CLAIM_AT_CHUNK -> Component.translatable("command.ucs.claim.edit.denied.no_claim", failure.detail());
            case NOT_OWNER -> Component.translatable("command.ucs.claim.edit.denied.not_owner", failure.detail());
            case TARGET_IS_SELF -> Component.translatable("command.ucs.claim.roles.denied.self", failure.detail());
            case TARGET_IS_OWNER -> Component.translatable("command.ucs.claim.roles.denied.owner", failure.detail());
            case ROLE_NOT_CONFIGURED -> Component.translatable("command.ucs.claim.roles.denied.role_not_configured", failure.detail());
            case TARGET_BANNED -> Component.translatable("command.ucs.claim.roles.denied.banned", failure.detail());
            case NO_PENDING_INVITE -> Component.translatable("command.ucs.claim.roles.denied.no_pending_invite", failure.detail());
            case SAVE_FAILED -> Component.translatable("command.ucs.claim.denied.save_failed", failure.detail());
        };
    }

    @FunctionalInterface
    private interface MetadataCommand {
        ClaimMetadataResult execute(ClaimMetadataRequest request);
    }

    @FunctionalInterface
    private interface RoleCommand {
        ClaimRoleResult execute(ClaimRoleRequest request);
    }

    @FunctionalInterface
    private interface LeaseCommand {
        ClaimLeaseResult execute(CommandLeaseContext context, ClaimLeaseRequest request);
    }

    private record CommandLeaseContext(
            ServerPlayer player,
            ClaimRepository repository,
            UcsClaimService claimService
    ) {
    }
}
