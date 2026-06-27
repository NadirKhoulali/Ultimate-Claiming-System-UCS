package com.nadirkhoulali.ucs.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.nadirkhoulali.ucs.api.ClaimArchiveView;
import com.nadirkhoulali.ucs.UcsMod;
import com.nadirkhoulali.ucs.claim.ClaimTaxPreview;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.ArchiveId;
import com.nadirkhoulali.ucs.core.model.ClaimId;
import com.nadirkhoulali.ucs.core.model.ClaimTaxState;
import com.nadirkhoulali.ucs.permission.UcsPermission;
import com.nadirkhoulali.ucs.permission.UcsPermissionNodes;
import com.nadirkhoulali.ucs.permission.UcsPermissionService;
import com.nadirkhoulali.ucs.service.UcsServices;
import com.nadirkhoulali.ucs.storage.ClaimRepositoryException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.util.Collection;
import java.util.Comparator;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class UcsCommands {
    private UcsCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, UcsServices services) {
        dispatcher.register(Commands.literal("ucs")
                .executes(UcsCommands::showAbout)
                .then(Commands.literal("about").executes(UcsCommands::showAbout))
                .then(Commands.literal("help").executes(UcsCommands::showHelp))
                .then(Commands.literal("version").executes(UcsCommands::showVersion))
                .then(Commands.literal("bypass").executes(context -> toggleBypass(context, services)))
                .then(Commands.literal("debug").executes(context -> toggleDebug(context, services)))
                .then(Commands.literal("archive")
                        .then(Commands.literal("list").executes(context -> listArchives(context, services)))
                        .then(Commands.literal("restore")
                                .then(Commands.argument("archiveId", StringArgumentType.word())
                                        .suggests((context, builder) -> services.claimService()
                                                .map(service -> SharedSuggestionProvider.suggest(
                                                        service.archives().stream()
                                                                .map(archive -> archive.id().value().toString()),
                                                        builder
                                                ))
                                                .orElse(builder.buildFuture()))
                                        .executes(context -> restoreArchive(
                                                context,
                                                services,
                                                StringArgumentType.getString(context, "archiveId")
                                        )))))
                .then(Commands.literal("tax")
                        .then(Commands.literal("preview")
                                .executes(context -> previewTaxes(context, services, 5))
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 100))
                                        .executes(context -> previewTaxes(
                                                context,
                                                services,
                                                IntegerArgumentType.getInteger(context, "limit")
                                        )))))
                .then(Commands.literal("debt")
                        .then(Commands.literal("list")
                                .executes(context -> listDebts(context, services, 5))
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 100))
                                        .executes(context -> listDebts(
                                                context,
                                                services,
                                                IntegerArgumentType.getInteger(context, "limit")
                                        ))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("claimId", StringArgumentType.word())
                                        .suggests((context, builder) -> services.claimRepository()
                                                .map(repository -> SharedSuggestionProvider.suggest(
                                                        repository.taxStates().stream()
                                                                .filter(UcsCommands::hasDebt)
                                                                .map(state -> state.claimId().value().toString()),
                                                        builder
                                                ))
                                                .orElse(builder.buildFuture()))
                                        .executes(context -> clearDebt(
                                                context,
                                                services,
                                                StringArgumentType.getString(context, "claimId")
                                        )))))
                .then(Commands.literal("permissions").executes(context -> showPermissions(context, services))));
    }

    private static int showAbout(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.translatable("command.ucs.about"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.translatable("command.ucs.help.header"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.version"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.permissions"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.archive_list"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.archive_restore"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.bypass"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.debug"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.tax_preview"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.debt_list"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.debt_clear"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_radius"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_add"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_remove"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_split"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_merge"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_name"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_description"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_setspawn"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_home"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_trust"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_untrust"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_role"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_invite"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_ban"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_unban"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_kick"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_sale"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_sale_cancel"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_sale_buy"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_lease_offer"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_lease_accept"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_lease_renew"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_lease_cancel"), false);
        source.sendSuccess(() -> Component.translatable("command.ucs.help.claim_lease_evict"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int toggleBypass(CommandContext<CommandSourceStack> context, UcsServices services) {
        CommandSourceStack source = context.getSource();
        if (!services.permissions().require(source, UcsPermission.BYPASS)) {
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.ucs.player_only"));
            return 0;
        }
        boolean enabled = services.protectionAdmin().toggleBypass(player);
        source.sendSuccess(
                () -> Component.translatable(enabled ? "command.ucs.bypass.enabled" : "command.ucs.bypass.disabled"),
                true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int toggleDebug(CommandContext<CommandSourceStack> context, UcsServices services) {
        CommandSourceStack source = context.getSource();
        if (!services.permissions().require(source, UcsPermission.DEBUG)) {
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.ucs.player_only"));
            return 0;
        }
        boolean enabled = services.protectionAdmin().toggleDebug(player);
        source.sendSuccess(
                () -> Component.translatable(enabled ? "command.ucs.debug.enabled" : "command.ucs.debug.disabled"),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int listArchives(CommandContext<CommandSourceStack> context, UcsServices services) {
        CommandSourceStack source = context.getSource();
        if (!services.permissions().require(source, UcsPermission.ARCHIVE_RESTORE)) {
            return 0;
        }
        if (services.claimService().isEmpty()) {
            source.sendFailure(Component.translatable("command.ucs.claim.service_unavailable"));
            return 0;
        }

        Collection<ClaimArchiveView> archives = services.claimService().orElseThrow().archives();
        if (archives.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.ucs.archive.list.empty"), false);
            return Command.SINGLE_SUCCESS;
        }

        source.sendSuccess(() -> Component.translatable("command.ucs.archive.list.header", archives.size()), false);
        archives.stream()
                .sorted(Comparator.comparing(ClaimArchiveView::archivedAt).reversed())
                .limit(5)
                .forEach(archive -> source.sendSuccess(
                        () -> Component.translatable(
                                "command.ucs.archive.list.entry",
                                archive.id().value().toString(),
                                archive.claim().displayName(),
                                archive.reason(),
                                archive.actor()
                        ),
                        false
                ));
        return Command.SINGLE_SUCCESS;
    }

    private static int previewTaxes(CommandContext<CommandSourceStack> context, UcsServices services, int limit) {
        CommandSourceStack source = context.getSource();
        if (!services.permissions().require(source, UcsPermission.ECONOMY_ADMIN)) {
            return 0;
        }
        if (services.claimRepository().isEmpty()) {
            source.sendFailure(Component.translatable("command.ucs.claim.service_unavailable"));
            return 0;
        }

        UcsConfigSnapshot config = com.nadirkhoulali.ucs.config.UcsCommonConfig.snapshot();
        Collection<ClaimTaxPreview> previews = services.claimTaxes().previewUpcomingTaxes(
                services.claimRepository().orElseThrow(),
                config,
                Instant.now(),
                limit
        );
        if (previews.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.ucs.tax.preview.empty"), false);
            return Command.SINGLE_SUCCESS;
        }

        source.sendSuccess(() -> Component.translatable("command.ucs.tax.preview.header", previews.size()), false);
        previews.forEach(preview -> source.sendSuccess(
                () -> Component.translatable(
                        "command.ucs.tax.preview.entry",
                        preview.displayName(),
                        services.economyProviders().activeProvider().format(preview.amount()),
                        preview.dueAt().toString(),
                        taxPreviewStatus(preview),
                        services.economyProviders().activeProvider().format(preview.state().outstandingDebt())
                ),
                false
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static int listDebts(CommandContext<CommandSourceStack> context, UcsServices services, int limit) {
        CommandSourceStack source = context.getSource();
        if (!services.permissions().require(source, UcsPermission.ECONOMY_ADMIN)) {
            return 0;
        }
        if (services.claimRepository().isEmpty()) {
            source.sendFailure(Component.translatable("command.ucs.claim.service_unavailable"));
            return 0;
        }

        var debts = services.claimRepository().orElseThrow().taxStates().stream()
                .filter(UcsCommands::hasDebt)
                .sorted(Comparator.comparing(state -> state.delinquentSince().orElse(Instant.MAX)))
                .limit(limit)
                .toList();
        if (debts.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.ucs.debt.list.empty"), false);
            return Command.SINGLE_SUCCESS;
        }

        source.sendSuccess(() -> Component.translatable("command.ucs.debt.list.header", debts.size()), false);
        debts.forEach(state -> source.sendSuccess(
                () -> Component.translatable(
                        "command.ucs.debt.list.entry",
                        state.claimId().value().toString(),
                        services.economyProviders().activeProvider().format(state.outstandingDebt()),
                        state.missedPayments(),
                        state.delinquentSince().map(Instant::toString).orElse("unknown")
                ),
                false
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static int clearDebt(CommandContext<CommandSourceStack> context, UcsServices services, String claimIdValue) {
        CommandSourceStack source = context.getSource();
        if (!services.permissions().require(source, UcsPermission.ECONOMY_ADMIN)) {
            return 0;
        }
        if (services.claimRepository().isEmpty()) {
            source.sendFailure(Component.translatable("command.ucs.claim.service_unavailable"));
            return 0;
        }

        Optional<ClaimId> claimId = parseClaimId(source, claimIdValue);
        if (claimId.isEmpty()) {
            return 0;
        }
        UcsConfigSnapshot config = com.nadirkhoulali.ucs.config.UcsCommonConfig.snapshot();
        return services.claimNonpayment()
                .clearDebt(services.claimRepository().orElseThrow(), claimId.orElseThrow(), config, Instant.now())
                .map(state -> {
                    source.sendSuccess(
                            () -> Component.translatable("command.ucs.debt.clear.success", state.claimId().value().toString()),
                            true
                    );
                    return Command.SINGLE_SUCCESS;
                })
                .orElseGet(() -> {
                    source.sendFailure(Component.translatable("command.ucs.debt.clear.not_found", claimIdValue));
                    return 0;
                });
    }

    private static int restoreArchive(CommandContext<CommandSourceStack> context, UcsServices services, String archiveIdValue) {
        CommandSourceStack source = context.getSource();
        if (!services.permissions().require(source, UcsPermission.ARCHIVE_RESTORE)) {
            return 0;
        }
        if (services.claimService().isEmpty() || services.claimRepository().isEmpty()) {
            source.sendFailure(Component.translatable("command.ucs.claim.service_unavailable"));
            return 0;
        }

        ArchiveId archiveId;
        try {
            archiveId = new ArchiveId(UUID.fromString(archiveIdValue));
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.translatable("command.ucs.archive.restore.invalid_id", archiveIdValue));
            return 0;
        }

        try {
            Optional<ClaimArchiveView> archive = services.claimService().orElseThrow().findArchive(archiveId);
            if (archive.isEmpty()) {
                source.sendFailure(Component.translatable("command.ucs.archive.restore.not_found", archiveIdValue));
                return 0;
            }
            UcsConfigSnapshot config = com.nadirkhoulali.ucs.config.UcsCommonConfig.snapshot();
            if (services.claimNonpayment().hasBlockingDebt(
                    services.claimRepository().orElseThrow(),
                    archive.orElseThrow().claim().id(),
                    config
            )) {
                source.sendFailure(Component.translatable("command.ucs.archive.restore.blocked_debt", archive.orElseThrow().claim().id().value().toString()));
                return 0;
            }
            return services.claimService().orElseThrow().restoreClaim(archiveId)
                    .map(claim -> {
                        services.claimNonpayment().deferAfterRestore(
                                services.claimRepository().orElseThrow(),
                                claim.id(),
                                config,
                                Instant.now()
                        );
                        source.sendSuccess(
                                () -> Component.translatable("command.ucs.archive.restore.success", claim.displayName()),
                                false
                        );
                        return Command.SINGLE_SUCCESS;
                    })
                    .orElseGet(() -> {
                        source.sendFailure(Component.translatable("command.ucs.archive.restore.not_found", archiveIdValue));
                        return 0;
                    });
        } catch (ClaimRepositoryException exception) {
            source.sendFailure(Component.translatable("command.ucs.archive.restore.failed", exceptionDetail(exception)));
            return 0;
        }
    }

    private static int showVersion(CommandContext<CommandSourceStack> context) {
        String version = ModList.get()
                .getModContainerById(UcsMod.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("development");

        context.getSource().sendSuccess(
                () -> Component.translatable("command.ucs.version", UcsMod.MOD_NAME, version),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static String taxPreviewStatus(ClaimTaxPreview preview) {
        if (preview.dueNow()) {
            return "due";
        }
        if (preview.warningWindow()) {
            return "warning";
        }
        return "scheduled";
    }

    private static Optional<ClaimId> parseClaimId(CommandSourceStack source, String value) {
        try {
            return Optional.of(new ClaimId(UUID.fromString(value)));
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.translatable("command.ucs.debt.invalid_claim_id", value));
            return Optional.empty();
        }
    }

    private static boolean hasDebt(ClaimTaxState state) {
        return state.outstandingDebt().signum() > 0;
    }

    private static int showPermissions(CommandContext<CommandSourceStack> context, UcsServices services) {
        CommandSourceStack source = context.getSource();
        UcsPermissionService permissions = services.permissions();
        if (!permissions.require(source, UcsPermission.ADMIN)) {
            return 0;
        }

        String handler = permissions.activeHandlerId()
                .map(Object::toString)
                .orElse("not_initialized");

        source.sendSuccess(
                () -> Component.translatable(
                        "command.ucs.permissions.summary",
                        handler,
                        Boolean.toString(permissions.opFallbackEnabled()),
                        UcsPermissionNodes.nodePrefix()
                ),
                false
        );

        for (UcsPermission permission : UcsPermission.values()) {
            source.sendSuccess(
                    () -> Component.translatable(
                            "command.ucs.permissions.node",
                            UcsPermissionNodes.nodeName(permission),
                            Integer.toString(permission.defaultOpLevel()),
                            Boolean.toString(permission.auditCandidate())
                    ),
                    false
            );
        }
        return Command.SINGLE_SUCCESS;
    }

    private static String exceptionDetail(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
