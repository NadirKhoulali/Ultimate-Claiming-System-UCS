package com.nadirkhoulali.ucs.api.event;

import com.nadirkhoulali.ucs.api.ClaimView;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.FlagId;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class UcsProtectionDecisionEvent extends Event implements ICancellableEvent {
    private final ChunkKey chunkKey;
    private final Optional<ClaimView> claim;
    private final FlagId flagId;
    private final Optional<UUID> actor;
    private boolean allowed;
    private String reason;

    public UcsProtectionDecisionEvent(
            ChunkKey chunkKey,
            Optional<ClaimView> claim,
            FlagId flagId,
            Optional<UUID> actor,
            boolean allowed,
            String reason
    ) {
        this.chunkKey = chunkKey;
        this.claim = Objects.requireNonNull(claim, "claim");
        this.flagId = flagId;
        this.actor = Objects.requireNonNull(actor, "actor");
        this.allowed = allowed;
        this.reason = reason;
    }

    public ChunkKey chunkKey() {
        return chunkKey;
    }

    public Optional<ClaimView> claim() {
        return claim;
    }

    public FlagId flagId() {
        return flagId;
    }

    public Optional<UUID> actor() {
        return actor;
    }

    public boolean allowed() {
        return allowed && !isCanceled();
    }

    public String reason() {
        return reason;
    }

    public void allow(String reason) {
        this.allowed = true;
        this.reason = reason;
        setCanceled(false);
    }

    public void deny(String reason) {
        this.allowed = false;
        this.reason = reason;
        setCanceled(true);
    }
}
