package com.nadirkhoulali.ucs.protection;

import com.nadirkhoulali.ucs.permission.UcsPermission;
import com.nadirkhoulali.ucs.permission.UcsPermissionService;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ProtectionAdminService {
    private static final int DENIAL_COOLDOWN_TICKS = 20;

    private final Set<UUID> bypassPlayers = new HashSet<>();
    private final Set<UUID> debugPlayers = new HashSet<>();
    private final Map<UUID, Map<String, Integer>> lastDenialTickByPlayer = new HashMap<>();

    public boolean toggleBypass(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (!bypassPlayers.add(playerId)) {
            bypassPlayers.remove(playerId);
            return false;
        }
        return true;
    }

    public boolean toggleDebug(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (!debugPlayers.add(playerId)) {
            debugPlayers.remove(playerId);
            return false;
        }
        return true;
    }

    public boolean isBypassing(ServerPlayer player, UcsPermissionService permissions) {
        return bypassPlayers.contains(player.getUUID()) && permissions.has(player, UcsPermission.BYPASS);
    }

    public boolean isDebugging(ServerPlayer player, UcsPermissionService permissions) {
        return debugPlayers.contains(player.getUUID()) && permissions.has(player, UcsPermission.DEBUG);
    }

    public boolean shouldSendDenial(ServerPlayer player, String actionKey, int currentTick) {
        return shouldSendDenial(player.getUUID(), actionKey, currentTick);
    }

    boolean shouldSendDenial(UUID playerId, String actionKey, int currentTick) {
        Map<String, Integer> lastByAction = lastDenialTickByPlayer.computeIfAbsent(playerId, ignored -> new HashMap<>());
        Integer lastTick = lastByAction.get(actionKey);
        if (lastTick != null && currentTick - lastTick < DENIAL_COOLDOWN_TICKS) {
            return false;
        }
        lastByAction.put(actionKey, currentTick);
        return true;
    }

    public void clearPlayer(UUID playerId) {
        bypassPlayers.remove(playerId);
        debugPlayers.remove(playerId);
        lastDenialTickByPlayer.remove(playerId);
    }

    public void clear() {
        bypassPlayers.clear();
        debugPlayers.clear();
        lastDenialTickByPlayer.clear();
    }
}
