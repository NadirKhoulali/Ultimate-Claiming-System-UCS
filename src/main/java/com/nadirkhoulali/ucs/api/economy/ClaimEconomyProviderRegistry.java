package com.nadirkhoulali.ucs.api.economy;

import java.util.List;
import java.util.Optional;

public interface ClaimEconomyProviderRegistry {
    void registerProvider(ClaimEconomyProvider provider);

    ClaimEconomyProvider activeProvider();

    List<ClaimEconomyProvider> providers();

    Optional<ClaimEconomyProvider> provider(String id);
}
