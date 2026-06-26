package com.nadirkhoulali.ucs.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.nadirkhoulali.ucs.UcsMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

public final class UcsCommands {
    private UcsCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ucs")
                .executes(UcsCommands::showAbout)
                .then(Commands.literal("about").executes(UcsCommands::showAbout))
                .then(Commands.literal("version").executes(UcsCommands::showVersion)));
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
}
