package com.nadirkhoulali.ucs.api.internal;

import com.nadirkhoulali.ucs.api.UcsApiAccess;
import com.nadirkhoulali.ucs.api.UcsClaimService;
import com.nadirkhoulali.ucs.storage.ClaimRepository;

public final class DefaultUcsApiAccess implements UcsApiAccess {
    private final UcsClaimService claimService;

    public DefaultUcsApiAccess(ClaimRepository claimRepository) {
        this.claimService = new DefaultUcsClaimService(claimRepository);
    }

    @Override
    public UcsClaimService claimService() {
        return claimService;
    }
}
