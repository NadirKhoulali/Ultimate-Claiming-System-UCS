package com.nadirkhoulali.ucs.core.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaseContractTest {
    @Test
    void offeredLeaseHasNoActiveWindowOrRoleGrant() {
        LeaseContract lease = LeaseContract.offer(
                LeaseId.random(),
                ClaimId.random(),
                new PlayerOwner(java.util.UUID.randomUUID(), "Tenant"),
                new RoleId("tenant"),
                BigDecimal.valueOf(50),
                Duration.ofDays(7),
                Instant.EPOCH
        );

        assertEquals(LeaseStatus.OFFERED, lease.status());
        assertTrue(lease.startsAt().isEmpty());
        assertTrue(lease.expiresAt().isEmpty());
        assertEquals(Duration.ofDays(7).toSeconds(), lease.durationSeconds());
    }

    @Test
    void activationAddsWindowFromDuration() {
        LeaseContract active = LeaseContract.offer(
                LeaseId.random(),
                ClaimId.random(),
                new PlayerOwner(java.util.UUID.randomUUID(), "Tenant"),
                new RoleId("tenant"),
                BigDecimal.valueOf(50),
                Duration.ofDays(3),
                Instant.EPOCH
        ).activate(Instant.EPOCH.plusSeconds(10), true);

        assertEquals(LeaseStatus.ACTIVE, active.status());
        assertEquals(Instant.EPOCH.plusSeconds(10), active.startsAt().orElseThrow());
        assertEquals(Instant.EPOCH.plusSeconds(10 + Duration.ofDays(3).toSeconds()), active.expiresAt().orElseThrow());
        assertTrue(active.roleGranted());
    }

    @Test
    void invalidPriceAndDurationAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> LeaseContract.offer(
                LeaseId.random(),
                ClaimId.random(),
                new PlayerOwner(java.util.UUID.randomUUID(), "Tenant"),
                new RoleId("tenant"),
                BigDecimal.ZERO,
                Duration.ofDays(1),
                Instant.EPOCH
        ));
        assertThrows(IllegalArgumentException.class, () -> LeaseContract.offer(
                LeaseId.random(),
                ClaimId.random(),
                new PlayerOwner(java.util.UUID.randomUUID(), "Tenant"),
                new RoleId("tenant"),
                BigDecimal.ONE,
                Duration.ZERO,
                Instant.EPOCH
        ));
    }
}
