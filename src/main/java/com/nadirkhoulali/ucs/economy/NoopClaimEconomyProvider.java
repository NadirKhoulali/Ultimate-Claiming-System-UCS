package com.nadirkhoulali.ucs.economy;

import com.nadirkhoulali.ucs.api.economy.ClaimEconomyAccountRef;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyFailureReason;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyProvider;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyResult;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class NoopClaimEconomyProvider implements ClaimEconomyProvider {
    public static final String ID = "ucs:none";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "No economy provider";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public ClaimEconomyResult balance(ClaimEconomyAccountRef account) {
        return unavailable(BigDecimal.ZERO);
    }

    @Override
    public ClaimEconomyResult validateCanCharge(ClaimEconomyAccountRef account, BigDecimal amount) {
        return unavailable(amount);
    }

    @Override
    public ClaimEconomyResult charge(ClaimEconomyAccountRef account, BigDecimal amount, String reference) {
        return unavailable(amount);
    }

    @Override
    public ClaimEconomyResult refund(ClaimEconomyAccountRef account, BigDecimal amount, String reference) {
        return unavailable(amount);
    }

    @Override
    public ClaimEconomyResult transfer(
            ClaimEconomyAccountRef sender,
            ClaimEconomyAccountRef receiver,
            BigDecimal amount,
            String reference) {
        return unavailable(amount);
    }

    @Override
    public String format(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return value.setScale(Math.max(0, value.scale()), RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private ClaimEconomyResult unavailable(BigDecimal amount) {
        return ClaimEconomyResult.fail(
                ClaimEconomyFailureReason.PROVIDER_UNAVAILABLE,
                "No compatible economy provider is available.",
                amount,
                format(amount));
    }
}
