package com.nadirkhoulali.ucs.api;

import com.nadirkhoulali.ucs.core.model.LeaseContract;
import com.nadirkhoulali.ucs.core.model.LeaseId;
import com.nadirkhoulali.ucs.core.model.LeaseStatus;
import com.nadirkhoulali.ucs.core.model.RoleId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ClaimLeaseView(
        LeaseId id,
        OwnerView tenant,
        RoleId roleId,
        BigDecimal price,
        long durationSeconds,
        Instant offeredAt,
        Optional<Instant> startsAt,
        Optional<Instant> expiresAt,
        LeaseStatus status,
        boolean roleGranted
) {
    public ClaimLeaseView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(roleId, "roleId");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(offeredAt, "offeredAt");
        startsAt = Objects.requireNonNull(startsAt, "startsAt");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(status, "status");
    }

    public static ClaimLeaseView from(LeaseContract lease) {
        return new ClaimLeaseView(
                lease.id(),
                OwnerView.from(lease.tenant()),
                lease.roleId(),
                lease.price(),
                lease.durationSeconds(),
                lease.offeredAt(),
                lease.startsAt(),
                lease.expiresAt(),
                lease.status(),
                lease.roleGranted()
        );
    }
}
