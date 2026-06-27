package com.nadirkhoulali.ucs.api.internal;

import com.nadirkhoulali.ucs.api.UcsApiAccess;
import com.nadirkhoulali.ucs.api.UcsClaimService;
import com.nadirkhoulali.ucs.storage.ClaimRepository;

import java.util.Objects;

public final class DefaultUcsApiAccess implements UcsApiAccess {
    private final UcsClaimService claimService;

    public DefaultUcsApiAccess(ClaimRepository claimRepository) {
        this(new DefaultUcsClaimService(claimRepository));
    }

    public DefaultUcsApiAccess(UcsClaimService claimService) {
        this.claimService = Objects.requireNonNull(claimService, "claimService");
    }

    @Override
    public UcsClaimService claimService() {
        return claimService;
    }
}
