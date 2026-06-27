package com.nadirkhoulali.ucs.api.event;

import com.nadirkhoulali.ucs.api.ClaimArchiveView;
import com.nadirkhoulali.ucs.api.ClaimView;
import net.neoforged.bus.api.Event;

import java.time.Instant;

public abstract class UcsClaimEvent extends Event {
    private final Instant occurredAt;

    protected UcsClaimEvent(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public final Instant occurredAt() {
        return occurredAt;
    }

    public abstract static class ClaimChange extends UcsClaimEvent {
        private final ClaimView claim;

        protected ClaimChange(ClaimView claim, Instant occurredAt) {
            super(occurredAt);
            this.claim = claim;
        }

        public final ClaimView claim() {
            return claim;
        }
    }

    public static final class Created extends ClaimChange {
        public Created(ClaimView claim, Instant occurredAt) {
            super(claim, occurredAt);
        }
    }

    public static final class Updated extends ClaimChange {
        public Updated(ClaimView claim, Instant occurredAt) {
            super(claim, occurredAt);
        }
    }

    public static final class Deleted extends ClaimChange {
        public Deleted(ClaimView claim, Instant occurredAt) {
            super(claim, occurredAt);
        }
    }

    public static final class Restored extends ClaimChange {
        public Restored(ClaimView claim, Instant occurredAt) {
            super(claim, occurredAt);
        }
    }

    public static final class Archived extends UcsClaimEvent {
        private final ClaimArchiveView archive;

        public Archived(ClaimArchiveView archive, Instant occurredAt) {
            super(occurredAt);
            this.archive = archive;
        }

        public ClaimArchiveView archive() {
            return archive;
        }
    }
}
