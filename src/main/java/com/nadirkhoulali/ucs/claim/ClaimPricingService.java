package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.economy.ClaimEconomyProvider;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;

import java.math.BigDecimal;

public final class ClaimPricingService {
    public static final String REF_CLAIM_CREATE = "UCS_CLAIM_CREATE";
    public static final String REF_CLAIM_CREATE_ROLLBACK = "UCS_CLAIM_CREATE_ROLLBACK";
    public static final String REF_CHUNK_ADD = "UCS_CHUNK_ADD";
    public static final String REF_CHUNK_ADD_ROLLBACK = "UCS_CHUNK_ADD_ROLLBACK";
    public static final String REF_CHUNK_REMOVE_REFUND = "UCS_CHUNK_REMOVE_REFUND";
    public static final String REF_CHUNK_SPLIT_REFUND = "UCS_CHUNK_SPLIT_REFUND";

    public boolean economyActive(UcsConfigSnapshot config, ClaimEconomyProvider provider) {
        return config.economy().enableWhenProviderExists() && provider != null && provider.isAvailable();
    }

    public BigDecimal claimCreationPrice(UcsConfigSnapshot config, int chunkCount) {
        if (chunkCount <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal starter = BigDecimal.valueOf(config.economy().starterClaimPrice());
        BigDecimal extra = BigDecimal.valueOf(config.economy().pricePerExtraChunk())
                .multiply(BigDecimal.valueOf(Math.max(0, chunkCount - 1L)));
        return starter.add(extra).max(BigDecimal.ZERO);
    }

    public BigDecimal chunkAddPrice(UcsConfigSnapshot config, int chunkCount) {
        if (chunkCount <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(config.economy().pricePerExtraChunk())
                .multiply(BigDecimal.valueOf(chunkCount))
                .max(BigDecimal.ZERO);
    }

    public BigDecimal removedChunkRefund(UcsConfigSnapshot config, int removedChunkCount) {
        if (removedChunkCount <= 0) {
            return BigDecimal.ZERO;
        }
        return chunkAddPrice(config, removedChunkCount)
                .multiply(BigDecimal.valueOf(config.economy().unclaimRefundRatio()))
                .max(BigDecimal.ZERO);
    }

    public BigDecimal wholeClaimRefund(UcsConfigSnapshot config, int chunkCount) {
        if (chunkCount <= 0) {
            return BigDecimal.ZERO;
        }
        return claimCreationPrice(config, chunkCount)
                .multiply(BigDecimal.valueOf(config.economy().unclaimRefundRatio()))
                .max(BigDecimal.ZERO);
    }

    public boolean shouldTransact(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }
}
