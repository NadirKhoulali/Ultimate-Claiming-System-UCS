package com.nadirkhoulali.ucs.api;

import com.nadirkhoulali.ucs.api.protection.ProtectionFlagRegistry;

public interface UcsApiAccess {
    UcsClaimService claimService();

    ProtectionFlagRegistry protectionFlags();
}
