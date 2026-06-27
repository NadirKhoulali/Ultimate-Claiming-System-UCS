package com.nadirkhoulali.ucs.api.internal;

import com.nadirkhoulali.ucs.api.UcsApiAccess;
import com.nadirkhoulali.ucs.api.UcsClaimService;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyProviderRegistry;
import com.nadirkhoulali.ucs.api.protection.ProtectionFlagRegistry;
import com.nadirkhoulali.ucs.economy.DefaultClaimEconomyProviderRegistry;
import com.nadirkhoulali.ucs.protection.DefaultProtectionFlagRegistry;
import com.nadirkhoulali.ucs.storage.ClaimRepository;

import java.util.Objects;

public final class DefaultUcsApiAccess implements UcsApiAccess {
    private final UcsClaimService claimService;
    private final ProtectionFlagRegistry protectionFlags;
    private final ClaimEconomyProviderRegistry economyProviders;

    public DefaultUcsApiAccess(ClaimRepository claimRepository) {
        this(new DefaultUcsClaimService(claimRepository));
    }

    public DefaultUcsApiAccess(UcsClaimService claimService) {
        this(claimService, DefaultProtectionFlagRegistry.withBuiltIns());
    }

    public DefaultUcsApiAccess(UcsClaimService claimService, ProtectionFlagRegistry protectionFlags) {
        this(claimService, protectionFlags, DefaultClaimEconomyProviderRegistry.noopOnly());
    }

    public DefaultUcsApiAccess(
            UcsClaimService claimService,
            ProtectionFlagRegistry protectionFlags,
            ClaimEconomyProviderRegistry economyProviders) {
        this.claimService = Objects.requireNonNull(claimService, "claimService");
        this.protectionFlags = Objects.requireNonNull(protectionFlags, "protectionFlags");
        this.economyProviders = Objects.requireNonNull(economyProviders, "economyProviders");
    }

    @Override
    public UcsClaimService claimService() {
        return claimService;
    }

    @Override
    public ProtectionFlagRegistry protectionFlags() {
        return protectionFlags;
    }

    @Override
    public ClaimEconomyProviderRegistry economyProviders() {
        return economyProviders;
    }
}
