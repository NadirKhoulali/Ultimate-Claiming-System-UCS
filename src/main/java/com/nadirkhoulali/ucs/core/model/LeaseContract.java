package com.nadirkhoulali.ucs.core.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record LeaseContract(
        LeaseId id,
        ClaimId claimId,
        OwnerRef tenant,
        RoleId roleId,
        BigDecimal price,
        long durationSeconds,
        Instant offeredAt,
        Optional<Instant> startsAt,
        Optional<Instant> expiresAt,
        LeaseStatus status,
        boolean roleGranted
) {
    public LeaseContract {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(roleId, "roleId");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(offeredAt, "offeredAt");
        startsAt = Objects.requireNonNull(startsAt, "startsAt");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(status, "status");
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be greater than zero");
        }
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds must be greater than zero");
        }
        if (status == LeaseStatus.ACTIVE || status == LeaseStatus.EXPIRED) {
            if (startsAt.isEmpty() || expiresAt.isEmpty()) {
                throw new IllegalArgumentException("started lease statuses require startsAt and expiresAt");
            }
        }
        if (startsAt.isPresent() != expiresAt.isPresent()) {
            throw new IllegalArgumentException("startsAt and expiresAt must both be present or absent");
        }
        if (startsAt.isPresent() && !expiresAt.orElseThrow().isAfter(startsAt.orElseThrow())) {
            throw new IllegalArgumentException("expiresAt must be after startsAt");
        }
        if (roleGranted && status == LeaseStatus.OFFERED) {
            throw new IllegalArgumentException("offered leases cannot grant roles");
        }
    }

    public static LeaseContract offer(
            LeaseId id,
            ClaimId claimId,
            OwnerRef tenant,
            RoleId roleId,
            BigDecimal price,
            Duration duration,
            Instant offeredAt
    ) {
        return new LeaseContract(
                id,
                claimId,
                tenant,
                roleId,
                price,
                duration.toSeconds(),
                offeredAt,
                Optional.empty(),
                Optional.empty(),
                LeaseStatus.OFFERED,
                false
        );
    }

    public LeaseContract activate(Instant startsAt, boolean roleGranted) {
        Instant expiresAt = startsAt.plusSeconds(durationSeconds);
        return withLifecycle(startsAt, expiresAt, LeaseStatus.ACTIVE, roleGranted);
    }

    public LeaseContract renew(Instant startsAt, Instant expiresAt, boolean roleGranted) {
        return withLifecycle(startsAt, expiresAt, LeaseStatus.ACTIVE, roleGranted);
    }

    public LeaseContract cancel() {
        return new LeaseContract(
                id,
                claimId,
                tenant,
                roleId,
                price,
                durationSeconds,
                offeredAt,
                startsAt,
                expiresAt,
                LeaseStatus.CANCELLED,
                roleGranted
        );
    }

    public LeaseContract expire() {
        return new LeaseContract(
                id,
                claimId,
                tenant,
                roleId,
                price,
                durationSeconds,
                offeredAt,
                startsAt,
                expiresAt,
                LeaseStatus.EXPIRED,
                roleGranted
        );
    }

    private LeaseContract withLifecycle(Instant startsAt, Instant expiresAt, LeaseStatus status, boolean roleGranted) {
        return new LeaseContract(
                id,
                claimId,
                tenant,
                roleId,
                price,
                durationSeconds,
                offeredAt,
                Optional.of(startsAt),
                Optional.of(expiresAt),
                status,
                roleGranted
        );
    }
}
