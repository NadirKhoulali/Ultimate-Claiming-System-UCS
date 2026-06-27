package com.nadirkhoulali.ucs.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UcsApiProviderTest {
    @AfterEach
    void cleanUp() {
        UcsApiProvider.clearActiveAccess();
    }

    @Test
    void claimServiceIsAbsentBeforeServerInitialization() {
        UcsApiProvider.clearActiveAccess();

        assertTrue(UcsApi.claimService().isEmpty());
        assertTrue(UcsApi.economyProviders().isEmpty());
    }
}
