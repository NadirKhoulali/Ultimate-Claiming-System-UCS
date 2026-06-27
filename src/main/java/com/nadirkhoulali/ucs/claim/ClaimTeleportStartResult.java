package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.ClaimView;

import java.util.Objects;
import java.util.Optional;

public record ClaimTeleportStartResult(
        Optional<ClaimView> claim,
        Optional<ClaimTeleportFailure> failure,
        int delaySeconds,
        boolean immediate
) {
    public ClaimTeleportStartResult {
        claim = Objects.requireNonNull(claim, "claim");
        failure = Objects.requireNonNull(failure, "failure");
        if (claim.isPresent() == failure.isPresent()) {
            throw new IllegalArgumentException("claim teleport result must contain either a claim or a failure");
        }
        if (delaySeconds < 0) {
            throw new IllegalArgumentException("delaySeconds must not be negative");
        }
        if (failure.isPresent() && (delaySeconds != 0 || immediate)) {
            throw new IllegalArgumentException("failed teleport cannot be queued or immediate");
        }
    }

    public static ClaimTeleportStartResult success(ClaimView claim, int delaySeconds, boolean immediate) {
        return new ClaimTeleportStartResult(Optional.of(claim), Optional.empty(), delaySeconds, immediate);
    }

    public static ClaimTeleportStartResult failure(ClaimTeleportFailure failure) {
        return new ClaimTeleportStartResult(Optional.empty(), Optional.of(failure), 0, false);
    }
}
