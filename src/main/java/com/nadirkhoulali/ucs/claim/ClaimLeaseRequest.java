package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.LeaseId;
import com.nadirkhoulali.ucs.core.model.RoleId;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ClaimLeaseRequest(
        UUID playerId,
        String playerName,
        ChunkKey chunk,
        Instant requestedAt,
        Optional<LeaseId> leaseId,
        Optional<ClaimRoleTarget> tenant,
        Optional<BigDecimal> price,
        Optional<Duration> duration,
        Optional<RoleId> role
) {
    public ClaimLeaseRequest {
        Objects.requireNonNull(playerId, "playerId");
        playerName = Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(requestedAt, "requestedAt");
        leaseId = Objects.requireNonNull(leaseId, "leaseId");
        tenant = Objects.requireNonNull(tenant, "tenant");
        price = Objects.requireNonNull(price, "price");
        duration = Objects.requireNonNull(duration, "duration");
        role = Objects.requireNonNull(role, "role");
    }

    public static ClaimLeaseRequest offer(
            UUID playerId,
            String playerName,
            ChunkKey chunk,
            Instant requestedAt,
            ClaimRoleTarget tenant,
            BigDecimal price,
            Duration duration,
            RoleId role
    ) {
        return new ClaimLeaseRequest(
                playerId,
                playerName,
                chunk,
                requestedAt,
                Optional.empty(),
                Optional.of(tenant),
                Optional.of(price),
                Optional.of(duration),
                Optional.of(role)
        );
    }

    public static ClaimLeaseRequest byLease(
            UUID playerId,
            String playerName,
            ChunkKey chunk,
            Instant requestedAt,
            LeaseId leaseId
    ) {
        return new ClaimLeaseRequest(
                playerId,
                playerName,
                chunk,
                requestedAt,
                Optional.of(leaseId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public String actorKey() {
        return "player:" + playerId;
    }
}
