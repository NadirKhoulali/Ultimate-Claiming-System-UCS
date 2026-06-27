package com.nadirkhoulali.ucs.economy;

import com.nadirkhoulali.ucs.api.economy.ClaimEconomyAccountRef;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyFailureReason;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyProvider;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultClaimEconomyProviderRegistryTest {
    @Test
    void activeProviderPrefersFirstAvailableRegisteredProvider() {
        DefaultClaimEconomyProviderRegistry registry = DefaultClaimEconomyProviderRegistry.noopOnly();
        registry.registerProvider(new FakeProvider("fake:offline", false));
        registry.registerProvider(new FakeProvider("fake:online", true));

        assertEquals("fake:online", registry.activeProvider().id());
    }

    @Test
    void duplicateProviderIdsAreRejected() {
        DefaultClaimEconomyProviderRegistry registry = DefaultClaimEconomyProviderRegistry.noopOnly();
        registry.registerProvider(new FakeProvider("fake:one", true));

        assertThrows(IllegalArgumentException.class, () -> registry.registerProvider(new FakeProvider("fake:one", true)));
    }

    private record FakeProvider(String id, boolean available) implements ClaimEconomyProvider {
        @Override
        public String displayName() {
            return id;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public ClaimEconomyResult balance(ClaimEconomyAccountRef account) {
            return ClaimEconomyResult.ok(BigDecimal.ZERO, BigDecimal.ZERO, "", "$0");
        }

        @Override
        public ClaimEconomyResult validateCanCharge(ClaimEconomyAccountRef account, BigDecimal amount) {
            return ClaimEconomyResult.ok(amount, BigDecimal.ZERO, "", "$0");
        }

        @Override
        public ClaimEconomyResult charge(ClaimEconomyAccountRef account, BigDecimal amount, String reference) {
            return ClaimEconomyResult.ok(amount, BigDecimal.ZERO, "tx", "$0");
        }

        @Override
        public ClaimEconomyResult refund(ClaimEconomyAccountRef account, BigDecimal amount, String reference) {
            return ClaimEconomyResult.ok(amount, BigDecimal.ZERO, "tx", "$0");
        }

        @Override
        public ClaimEconomyResult transfer(
                ClaimEconomyAccountRef sender,
                ClaimEconomyAccountRef receiver,
                BigDecimal amount,
                String reference) {
            return ClaimEconomyResult.ok(amount, BigDecimal.ZERO, "tx", "$0");
        }

        @Override
        public String format(BigDecimal amount) {
            return "$" + amount;
        }
    }
}
