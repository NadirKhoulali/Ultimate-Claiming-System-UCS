package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.economy.ClaimEconomyAccountRef;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyFailureReason;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyProvider;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class FakeClaimEconomyProvider implements ClaimEconomyProvider {
    private boolean failCharge;
    private boolean failRefund;
    private final List<String> chargeReferences = new ArrayList<>();
    private final List<String> refundReferences = new ArrayList<>();
    private final List<String> transferReferences = new ArrayList<>();
    private BigDecimal charged = BigDecimal.ZERO;
    private BigDecimal refunded = BigDecimal.ZERO;
    private BigDecimal transferred = BigDecimal.ZERO;

    void failCharge() {
        this.failCharge = true;
    }

    void failRefund() {
        this.failRefund = true;
    }

    BigDecimal charged() {
        return charged;
    }

    BigDecimal refunded() {
        return refunded;
    }

    List<String> chargeReferences() {
        return List.copyOf(chargeReferences);
    }

    List<String> refundReferences() {
        return List.copyOf(refundReferences);
    }

    BigDecimal transferred() {
        return transferred;
    }

    List<String> transferReferences() {
        return List.copyOf(transferReferences);
    }

    @Override
    public String id() {
        return "fake:economy";
    }

    @Override
    public String displayName() {
        return "Fake Economy";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public ClaimEconomyResult balance(ClaimEconomyAccountRef account) {
        return ClaimEconomyResult.ok(BigDecimal.ZERO, BigDecimal.valueOf(1_000), "", "$0");
    }

    @Override
    public ClaimEconomyResult validateCanCharge(ClaimEconomyAccountRef account, BigDecimal amount) {
        if (failCharge) {
            return ClaimEconomyResult.fail(
                    ClaimEconomyFailureReason.INSUFFICIENT_FUNDS,
                    "Insufficient funds",
                    amount,
                    format(amount)
            );
        }
        return ClaimEconomyResult.ok(amount, BigDecimal.valueOf(1_000).subtract(amount), "", format(amount));
    }

    @Override
    public ClaimEconomyResult charge(ClaimEconomyAccountRef account, BigDecimal amount, String reference) {
        if (failCharge) {
            return ClaimEconomyResult.fail(
                    ClaimEconomyFailureReason.INSUFFICIENT_FUNDS,
                    "Insufficient funds",
                    amount,
                    format(amount)
            );
        }
        charged = charged.add(amount);
        chargeReferences.add(reference);
        return ClaimEconomyResult.ok(amount, BigDecimal.valueOf(1_000).subtract(charged), "charge:" + reference, format(amount));
    }

    @Override
    public ClaimEconomyResult refund(ClaimEconomyAccountRef account, BigDecimal amount, String reference) {
        if (failRefund) {
            return ClaimEconomyResult.fail(
                    ClaimEconomyFailureReason.OPERATION_FAILED,
                    "Refund unavailable",
                    amount,
                    format(amount)
            );
        }
        refunded = refunded.add(amount);
        refundReferences.add(reference);
        return ClaimEconomyResult.ok(amount, BigDecimal.valueOf(1_000).add(refunded), "refund:" + reference, format(amount));
    }

    @Override
    public ClaimEconomyResult transfer(
            ClaimEconomyAccountRef sender,
            ClaimEconomyAccountRef receiver,
            BigDecimal amount,
            String reference) {
        transferred = transferred.add(amount);
        transferReferences.add(reference);
        return ClaimEconomyResult.ok(amount, BigDecimal.valueOf(1_000), "transfer:" + reference, format(amount));
    }

    @Override
    public String format(BigDecimal amount) {
        return "$" + amount.stripTrailingZeros().toPlainString();
    }
}
