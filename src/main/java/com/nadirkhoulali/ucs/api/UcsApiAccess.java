package com.nadirkhoulali.ucs.api;

import com.nadirkhoulali.ucs.api.economy.ClaimEconomyProviderRegistry;
import com.nadirkhoulali.ucs.api.protection.ProtectionFlagRegistry;

public interface UcsApiAccess {
    UcsClaimService claimService();

    ProtectionFlagRegistry protectionFlags();

    ClaimEconomyProviderRegistry economyProviders();
}
