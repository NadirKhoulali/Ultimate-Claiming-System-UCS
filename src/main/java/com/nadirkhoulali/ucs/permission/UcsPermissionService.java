package com.nadirkhoulali.ucs.permission;

import com.nadirkhoulali.ucs.config.UcsCommonConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.exceptions.UnregisteredPermissionException;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public final class UcsPermissionService {
    private final BooleanSupplier opFallbackEnabled;

    public UcsPermissionService() {
        this(() -> UcsCommonConfig.OP_FALLBACK_ENABLED.get());
    }

    UcsPermissionService(BooleanSupplier opFallbackEnabled) {
        this.opFallbackEnabled = Objects.requireNonNull(opFallbackEnabled, "opFallbackEnabled");
    }

    public boolean has(CommandSourceStack source, UcsPermission permission) {
        return decide(source, permission).allowed();
    }

    public boolean has(ServerPlayer player, UcsPermission permission) {
        return decide(player, permission).allowed();
    }

    public UcsPermissionDecision decide(CommandSourceStack source, UcsPermission permission) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(permission, "permission");

        ServerPlayer player = source.getPlayer();
        if (player != null) {
            return decide(player, permission);
        }

        boolean allowed = permission.publicByDefault() || source.hasPermission(permission.defaultOpLevel());
        return new UcsPermissionDecision(
                permission,
                UcsPermissionNodes.nodeName(permission),
                allowed,
                permission.publicByDefault()
                        ? UcsPermissionDecision.Source.PUBLIC_DEFAULT
                        : UcsPermissionDecision.Source.COMMAND_SOURCE
        );
    }

    public UcsPermissionDecision decide(ServerPlayer player, UcsPermission permission) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(permission, "permission");

        ResourceLocation activeHandler = PermissionAPI.getActivePermissionHandler();
        if (activeHandler != null) {
            try {
                boolean allowed = PermissionAPI.getPermission(player, UcsPermissionNodes.node(permission));
                return new UcsPermissionDecision(
                        permission,
                        UcsPermissionNodes.nodeName(permission),
                        allowed,
                        UcsPermissionDecision.Source.NEOFORGE_HANDLER
                );
            } catch (UnregisteredPermissionException ignored) {
                return fallbackForPlayer(player, permission, UcsPermissionDecision.Source.UNREGISTERED_NODE);
            }
        }

        return fallbackForPlayer(player, permission, fallbackSource(permission));
    }

    public boolean require(CommandSourceStack source, UcsPermission permission) {
        UcsPermissionDecision decision = decide(source, permission);
        if (decision.allowed()) {
            return true;
        }
        source.sendFailure(Component.translatable("command.ucs.permission.denied", decision.nodeName()));
        return false;
    }

    public Optional<ResourceLocation> activeHandlerId() {
        return Optional.ofNullable(PermissionAPI.getActivePermissionHandler());
    }

    public boolean opFallbackEnabled() {
        return opFallbackEnabled.getAsBoolean();
    }

    private UcsPermissionDecision fallbackForPlayer(
            ServerPlayer player,
            UcsPermission permission,
            UcsPermissionDecision.Source source
    ) {
        boolean hasFallbackLevel = player.createCommandSourceStack().hasPermission(permission.defaultOpLevel());
        boolean allowed = UcsPermissionPolicy.resolveDefault(permission, opFallbackEnabled(), hasFallbackLevel);
        return new UcsPermissionDecision(permission, UcsPermissionNodes.nodeName(permission), allowed, source);
    }

    private UcsPermissionDecision.Source fallbackSource(UcsPermission permission) {
        if (permission.publicByDefault()) {
            return UcsPermissionDecision.Source.PUBLIC_DEFAULT;
        }
        return opFallbackEnabled()
                ? UcsPermissionDecision.Source.PLAYER_OP_FALLBACK
                : UcsPermissionDecision.Source.FALLBACK_DISABLED;
    }
}
