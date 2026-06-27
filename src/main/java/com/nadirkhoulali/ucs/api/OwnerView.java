package com.nadirkhoulali.ucs.api;

import com.nadirkhoulali.ucs.core.model.OwnerRef;
import com.nadirkhoulali.ucs.core.model.OwnerType;
import com.nadirkhoulali.ucs.core.model.PlayerOwner;
import com.nadirkhoulali.ucs.core.model.ServerOwner;
import com.nadirkhoulali.ucs.core.model.TeamOwner;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record OwnerView(
        OwnerType type,
        String stableKey,
        Optional<UUID> playerId,
        Optional<String> playerName,
        Optional<String> teamId,
        Optional<String> serverNamespace
) {
    public OwnerView {
        playerId = Objects.requireNonNull(playerId, "playerId");
        playerName = Objects.requireNonNull(playerName, "playerName");
        teamId = Objects.requireNonNull(teamId, "teamId");
        serverNamespace = Objects.requireNonNull(serverNamespace, "serverNamespace");
    }

    public static OwnerView from(OwnerRef owner) {
        return switch (owner) {
            case PlayerOwner player -> new OwnerView(
                    player.type(),
                    player.stableKey(),
                    Optional.of(player.playerId()),
                    Optional.of(player.lastKnownName()),
                    Optional.empty(),
                    Optional.empty()
            );
            case TeamOwner team -> new OwnerView(
                    team.type(),
                    team.stableKey(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(team.teamId()),
                    Optional.empty()
            );
            case ServerOwner server -> new OwnerView(
                    server.type(),
                    server.stableKey(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(server.namespace())
            );
        };
    }
}
