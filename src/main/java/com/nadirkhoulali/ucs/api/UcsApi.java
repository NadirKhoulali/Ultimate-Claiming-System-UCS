package com.nadirkhoulali.ucs.api;

import com.nadirkhoulali.ucs.api.protection.ProtectionFlagRegistry;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyProviderRegistry;

import java.util.Optional;

public final class UcsApi {
    private UcsApi() {
    }

    /**
     * Returns the server-side claim service when UCS has initialized a server world.
     *
     * <p>Threading: methods on the returned service must be called from the logical server thread unless a method
     * explicitly documents otherwise. The v1 API does not expose async Minecraft-object access.</p>
     */
    public static Optional<UcsClaimService> claimService() {
        return UcsApiProvider.access().map(UcsApiAccess::claimService);
    }

    public static Optional<ProtectionFlagRegistry> protectionFlags() {
        return UcsApiProvider.access().map(UcsApiAccess::protectionFlags);
    }

    public static Optional<ClaimEconomyProviderRegistry> economyProviders() {
        return UcsApiProvider.access().map(UcsApiAccess::economyProviders);
    }
}
