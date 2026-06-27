package com.nadirkhoulali.ucs.api.economy;

import java.math.BigDecimal;
import java.util.Objects;

public record ClaimEconomyResult(
        boolean success,
        ClaimEconomyFailureReason failureReason,
        String userMessage,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String providerReference,
        String formattedAmount
) {
    public ClaimEconomyResult {
        Objects.requireNonNull(failureReason, "failureReason");
        userMessage = userMessage == null ? "" : userMessage.trim();
        amount = amount == null ? BigDecimal.ZERO : amount;
        balanceAfter = balanceAfter == null ? BigDecimal.ZERO : balanceAfter;
        providerReference = providerReference == null ? "" : providerReference.trim();
        formattedAmount = formattedAmount == null ? amount.toPlainString() : formattedAmount.trim();
        if (success && failureReason != ClaimEconomyFailureReason.NONE) {
            throw new IllegalArgumentException("Successful economy results must use failure reason NONE");
        }
        if (!success && failureReason == ClaimEconomyFailureReason.NONE) {
            throw new IllegalArgumentException("Failed economy results must include a failure reason");
        }
    }

    public static ClaimEconomyResult ok(
            BigDecimal amount,
            BigDecimal balanceAfter,
            String providerReference,
            String formattedAmount) {
        return new ClaimEconomyResult(
                true,
                ClaimEconomyFailureReason.NONE,
                "",
                amount,
                balanceAfter,
                providerReference,
                formattedAmount);
    }

    public static ClaimEconomyResult fail(
            ClaimEconomyFailureReason failureReason,
            String userMessage,
            BigDecimal amount,
            String formattedAmount) {
        return new ClaimEconomyResult(
                false,
                failureReason,
                userMessage,
                amount,
                BigDecimal.ZERO,
                "",
                formattedAmount);
    }
}
