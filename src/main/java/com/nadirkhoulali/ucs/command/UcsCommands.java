package com.nadirkhoulali.ucs.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.nadirkhoulali.ucs.UcsMod;
import com.nadirkhoulali.ucs.permission.UcsPermission;
import com.nadirkhoulali.ucs.permission.UcsPermissionNodes;
import com.nadirkhoulali.ucs.permission.UcsPermissionService;
import com.nadirkhoulali.ucs.service.UcsServices;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

public final class UcsCommands {
    private UcsCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, UcsServices services) {
        dispatcher.register(Commands.literal("ucs")
                .executes(UcsCommands::showAbout)
                .then(Commands.literal("about").executes(UcsCommands::showAbout))
                .then(Commands.literal("version").executes(UcsCommands::showVersion))
                .then(Commands.literal("permissions").executes(context -> showPermissions(context, services))));
    }

    private static int showAbout(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.translatable("command.ucs.about"), false);
        return Command.SINGLE_SUCCESS;
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
}
