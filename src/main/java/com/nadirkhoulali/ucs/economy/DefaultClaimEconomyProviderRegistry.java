package com.nadirkhoulali.ucs.economy;

import com.nadirkhoulali.ucs.api.economy.ClaimEconomyProvider;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyProviderRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DefaultClaimEconomyProviderRegistry implements ClaimEconomyProviderRegistry {
    private final NoopClaimEconomyProvider fallback = new NoopClaimEconomyProvider();
    private final CopyOnWriteArrayList<ClaimEconomyProvider> providers = new CopyOnWriteArrayList<>();

    private DefaultClaimEconomyProviderRegistry(boolean includeBuiltIns) {
        providers.add(fallback);
        if (includeBuiltIns) {
            providers.add(new UbsClaimEconomyProvider());
        }
    }

    public static DefaultClaimEconomyProviderRegistry noopOnly() {
        return new DefaultClaimEconomyProviderRegistry(false);
    }

    public static DefaultClaimEconomyProviderRegistry withBuiltIns() {
        return new DefaultClaimEconomyProviderRegistry(true);
    }

    @Override
    public void registerProvider(ClaimEconomyProvider provider) {
        Objects.requireNonNull(provider, "provider");
        String id = provider.id();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Economy provider id cannot be blank");
        }
        if (provider(id).isPresent()) {
            throw new IllegalArgumentException("Economy provider already registered: " + id);
        }
        providers.add(provider);
    }

    @Override
    public ClaimEconomyProvider activeProvider() {
        for (ClaimEconomyProvider provider : providers) {
            if (provider != fallback && provider.isAvailable()) {
                return provider;
            }
        }
        return fallback;
    }

    @Override
    public List<ClaimEconomyProvider> providers() {
        return List.copyOf(providers);
    }

    @Override
    public Optional<ClaimEconomyProvider> provider(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return providers.stream()
                .filter(provider -> id.equals(provider.id()))
                .findFirst();
    }

    public List<ClaimEconomyProvider> availableProviders() {
        List<ClaimEconomyProvider> available = new ArrayList<>();
        for (ClaimEconomyProvider provider : providers) {
            if (provider.isAvailable()) {
                available.add(provider);
            }
        }
        return List.copyOf(available);
    }
}
