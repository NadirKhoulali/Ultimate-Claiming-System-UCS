package com.nadirkhoulali.ucs.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class UbsClaimEconomyProviderTest {
    @Test
    void unavailableWhenUbsIsNotLoaded() {
        assertFalse(new UbsClaimEconomyProvider().isAvailable());
    }
}
