package com.nadirkhoulali.ucs.api.economy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimEconomyResultTest {
    @Test
    void successResultsUseNoneFailureReason() {
        ClaimEconomyResult result = ClaimEconomyResult.ok(
                BigDecimal.TEN,
                BigDecimal.valueOf(90),
                "tx-1",
                "$10");

        assertTrue(result.success());
        assertEquals(ClaimEconomyFailureReason.NONE, result.failureReason());
        assertEquals("tx-1", result.providerReference());
    }

    @Test
    void failedResultsRequireFailureReason() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimEconomyResult(
                false,
                ClaimEconomyFailureReason.NONE,
                "failed",
                BigDecimal.ONE,
                BigDecimal.ZERO,
                "",
                "$1"));
    }

    @Test
    void accountReferencesValidateRequiredUuid() {
        assertThrows(IllegalArgumentException.class, () -> ClaimEconomyAccountRef.playerPrimary(null));
    }
}
