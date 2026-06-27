package com.nadirkhoulali.ucs.permission;

import com.nadirkhoulali.ucs.config.UcsCommonConfig;
import com.nadirkhoulali.ucs.config.UcsConfigDefaults;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class UcsPermissionNodes {
    private static final Object LOCK = new Object();
    private static volatile String nodePrefix = UcsConfigDefaults.PERMISSION_NODE_PREFIX;
    private static volatile Map<UcsPermission, PermissionNode<Boolean>> nodes = createNodes(nodePrefix);

    private UcsPermissionNodes() {
    }

    public static void register(PermissionGatherEvent.Nodes event) {
        configureNodePrefix(configuredNodePrefix());
        event.addNodes(nodes());
    }

    public static PermissionNode<Boolean> node(UcsPermission permission) {
        return nodes.get(permission);
    }

    public static List<PermissionNode<?>> nodes() {
        return Arrays.stream(UcsPermission.values())
                .<PermissionNode<?>>map(permission -> nodes.get(permission))
                .toList();
    }

    public static String nodePrefix() {
        return nodePrefix;
    }

    public static String nodeName(UcsPermission permission) {
        return permission.nodeName(nodePrefix);
    }

    public static int count() {
        return nodes.size();
    }

    private static void configureNodePrefix(String prefix) {
        synchronized (LOCK) {
            if (!nodePrefix.equals(prefix)) {
                nodePrefix = prefix;
                nodes = createNodes(prefix);
            }
        }
    }

    private static String configuredNodePrefix() {
        try {
            return UcsCommonConfig.PERMISSION_NODE_PREFIX.get();
        } catch (IllegalStateException ignored) {
            return UcsConfigDefaults.PERMISSION_NODE_PREFIX;
        }
    }

    private static Map<UcsPermission, PermissionNode<Boolean>> createNodes(String prefix) {
        return Arrays.stream(UcsPermission.values())
                .collect(Collectors.toUnmodifiableMap(permission -> permission, permission -> createNode(prefix, permission)));
    }

    private static PermissionNode<Boolean> createNode(String prefix, UcsPermission permission) {
        PermissionNode<Boolean> node = new PermissionNode<>(
                prefix,
                permission.path(),
                PermissionTypes.BOOLEAN,
                (player, playerUUID, context) -> resolveDefault(permission, player, playerUUID)
        );
        node.setInformation(Component.literal(permission.readableName()), Component.literal(permission.description()));
        return node;
    }

    private static boolean resolveDefault(
            UcsPermission permission,
            @Nullable ServerPlayer player,
            UUID playerUUID
    ) {
        if (permission.publicByDefault()) {
            return true;
        }
        boolean hasFallbackLevel = player != null
                && player.getUUID().equals(playerUUID)
                && player.createCommandSourceStack().hasPermission(permission.defaultOpLevel());
        return UcsPermissionPolicy.resolveDefault(permission, UcsCommonConfig.OP_FALLBACK_ENABLED.get(), hasFallbackLevel);
    }
}
