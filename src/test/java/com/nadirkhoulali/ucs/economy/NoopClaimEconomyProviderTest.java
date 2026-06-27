package com.nadirkhoulali.ucs.economy;

import com.nadirkhoulali.ucs.api.economy.ClaimEconomyAccountRef;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyFailureReason;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NoopClaimEconomyProviderTest {
    @Test
    void operationsFailWithProviderUnavailable() {
        NoopClaimEconomyProvider provider = new NoopClaimEconomyProvider();

        ClaimEconomyResult result = provider.charge(
                ClaimEconomyAccountRef.playerPrimary(UUID.randomUUID()),
                BigDecimal.TEN,
                "test");

        assertFalse(result.success());
        assertEquals(ClaimEconomyFailureReason.PROVIDER_UNAVAILABLE, result.failureReason());
    }
}
