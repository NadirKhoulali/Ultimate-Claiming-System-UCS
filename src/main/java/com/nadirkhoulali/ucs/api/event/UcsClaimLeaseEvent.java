package com.nadirkhoulali.ucs.api.event;

import com.nadirkhoulali.ucs.api.ClaimLeaseView;
import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.claim.ClaimLeaseAction;
import net.neoforged.bus.api.Event;

import java.time.Instant;
import java.util.Objects;

public final class UcsClaimLeaseEvent extends Event {
    private final ClaimLeaseAction action;
    private final ClaimView claim;
    private final ClaimLeaseView lease;
    private final Instant occurredAt;

    public UcsClaimLeaseEvent(ClaimLeaseAction action, ClaimView claim, ClaimLeaseView lease, Instant occurredAt) {
        this.action = Objects.requireNonNull(action, "action");
        this.claim = Objects.requireNonNull(claim, "claim");
        this.lease = Objects.requireNonNull(lease, "lease");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public ClaimLeaseAction action() {
        return action;
    }

    public ClaimView claim() {
        return claim;
    }

    public ClaimLeaseView lease() {
        return lease;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}
