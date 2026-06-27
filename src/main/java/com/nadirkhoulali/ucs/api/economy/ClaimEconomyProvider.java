package com.nadirkhoulali.ucs.api.economy;

import java.math.BigDecimal;

public interface ClaimEconomyProvider {
    String id();

    String displayName();

    boolean isAvailable();

    ClaimEconomyResult balance(ClaimEconomyAccountRef account);

    ClaimEconomyResult validateCanCharge(ClaimEconomyAccountRef account, BigDecimal amount);

    ClaimEconomyResult charge(ClaimEconomyAccountRef account, BigDecimal amount, String reference);

    ClaimEconomyResult refund(ClaimEconomyAccountRef account, BigDecimal amount, String reference);

    ClaimEconomyResult transfer(
            ClaimEconomyAccountRef sender,
            ClaimEconomyAccountRef receiver,
            BigDecimal amount,
            String reference);

    String format(BigDecimal amount);
}
