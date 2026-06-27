package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.economy.ClaimEconomyAccountRef;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyProvider;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyResult;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimId;
import com.nadirkhoulali.ucs.core.model.ClaimTaxLedgerEntry;
import com.nadirkhoulali.ucs.core.model.ClaimTaxLedgerStatus;
import com.nadirkhoulali.ucs.core.model.ClaimTaxState;
import com.nadirkhoulali.ucs.core.model.PlayerOwner;
import com.nadirkhoulali.ucs.storage.ClaimRepository;
import net.minecraft.server.MinecraftServer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ClaimTaxService {
    private static final int TAX_SCAN_INTERVAL_TICKS = 200;
    private final ClaimPricingService pricing = new ClaimPricingService();
    private long nextTaxScanTick;

    public List<ClaimTaxPreview> previewUpcomingTaxes(
            ClaimRepository repository,
            UcsConfigSnapshot config,
            Instant now,
            int limit
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(now, "now");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }

        Instant warningCutoff = now.plus(Duration.ofHours(config.claimTax().warningHoursBeforeDue()));
        return repository.claims().stream()
                .map(claim -> preview(repository, config, claim, now, warningCutoff))
                .sorted(Comparator.comparing(ClaimTaxPreview::dueAt).thenComparing(preview -> preview.claimId().value()))
                .limit(limit)
                .toList();
    }

    public void tick(
            MinecraftServer server,
            ClaimRepository repository,
            UcsConfigSnapshot config,
            ClaimEconomyProvider economyProvider
    ) {
        Objects.requireNonNull(server, "server");
        if (server.getTickCount() < nextTaxScanTick) {
            return;
        }
        nextTaxScanTick = server.getTickCount() + TAX_SCAN_INTERVAL_TICKS;
        processDueTaxes(repository, config, economyProvider, Instant.now(), config.claimTax().maxClaimsPerTick());
    }

    public void clear() {
        nextTaxScanTick = 0L;
    }

    public ClaimTaxBatchResult processDueTaxes(
            ClaimRepository repository,
            UcsConfigSnapshot config,
            ClaimEconomyProvider economyProvider,
            Instant now,
            int maxClaims
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(now, "now");
        if (maxClaims <= 0) {
            throw new IllegalArgumentException("maxClaims must be greater than zero");
        }
        if (!config.claimTax().enabled()) {
            return new ClaimTaxBatchResult(0, 0, 0, 0, List.of());
        }

        int scanned = 0;
        int billed = 0;
        int paid = 0;
        int failed = 0;
        List<ClaimTaxLedgerEntry> entries = new ArrayList<>();
        List<Claim> claims = repository.claims().stream()
                .sorted(Comparator.comparing(claim -> dueState(repository, config, claim, now).nextDueAt()))
                .toList();

        for (Claim claim : claims) {
            if (scanned >= maxClaims) {
                break;
            }
            scanned++;
            ClaimTaxState state = ensureState(repository, config, claim, now);
            if (state.nextDueAt().isAfter(now)) {
                continue;
            }

            BigDecimal amount = calculateTax(config, claim);
            String reference = referenceFor(claim.id(), state.nextDueAt());
            ClaimTaxLedgerEntry entry;
            ClaimTaxState updatedState;
            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                entry = paidEntry(claim, amount, state.nextDueAt(), now, reference, "", "zero tax amount");
                updatedState = state.recordPaid(now, nextRegularDue(now, config));
                paid++;
            } else if (!pricing.economyActive(config, economyProvider)) {
                entry = failedEntry(claim, amount, state.nextDueAt(), now, reference, "No economy provider is available.");
                updatedState = state.recordMissed(amount, now, nextRetryDue(now, config));
                failed++;
            } else if (claim.owner() instanceof PlayerOwner owner) {
                ClaimEconomyResult charge = economyProvider.charge(
                        ClaimEconomyAccountRef.playerPrimary(owner.playerId()),
                        amount,
                        reference
                );
                if (charge.success()) {
                    entry = paidEntry(
                            claim,
                            amount,
                            state.nextDueAt(),
                            now,
                            reference,
                            charge.providerReference(),
                            "charged " + charge.formattedAmount()
                    );
                    updatedState = state.recordPaid(now, nextRegularDue(now, config));
                    paid++;
                } else {
                    entry = failedEntry(claim, amount, state.nextDueAt(), now, reference, charge.userMessage());
                    updatedState = state.recordMissed(amount, now, nextRetryDue(now, config));
                    failed++;
                }
            } else {
                entry = failedEntry(claim, amount, state.nextDueAt(), now, reference, "Claim owner has no player payment source.");
                updatedState = state.recordMissed(amount, now, nextRetryDue(now, config));
                failed++;
            }

            repository.saveTaxState(updatedState);
            entries.add(repository.appendTaxLedgerEntry(entry));
            billed++;
        }

        return new ClaimTaxBatchResult(scanned, billed, paid, failed, entries);
    }

    public BigDecimal calculateTax(UcsConfigSnapshot config, Claim claim) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(claim, "claim");
        BigDecimal base = BigDecimal.valueOf(config.claimTax().baseAmount());
        BigDecimal perChunk = BigDecimal.valueOf(config.claimTax().perChunkAmount())
                .multiply(BigDecimal.valueOf(claim.chunks().size()));
        return base.add(perChunk).max(BigDecimal.ZERO);
    }

    private ClaimTaxPreview preview(
            ClaimRepository repository,
            UcsConfigSnapshot config,
            Claim claim,
            Instant now,
            Instant warningCutoff
    ) {
        ClaimTaxState state = dueState(repository, config, claim, now);
        return new ClaimTaxPreview(
                claim.id(),
                claim.metadata().displayName(),
                claim.owner(),
                claim.chunks().size(),
                calculateTax(config, claim),
                state.nextDueAt(),
                !state.nextDueAt().isAfter(now),
                !state.nextDueAt().isAfter(warningCutoff),
                state
        );
    }

    private ClaimTaxState ensureState(
            ClaimRepository repository,
            UcsConfigSnapshot config,
            Claim claim,
            Instant now
    ) {
        Optional<ClaimTaxState> existing = repository.findTaxState(claim.id());
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        ClaimTaxState state = ClaimTaxState.scheduled(
                claim.id(),
                now.plus(Duration.ofHours(config.claimTax().initialDelayHours())),
                now
        );
        return repository.saveTaxState(state);
    }

    private ClaimTaxState dueState(
            ClaimRepository repository,
            UcsConfigSnapshot config,
            Claim claim,
            Instant now
    ) {
        return repository.findTaxState(claim.id())
                .orElseGet(() -> ClaimTaxState.scheduled(
                        claim.id(),
                        now.plus(Duration.ofHours(config.claimTax().initialDelayHours())),
                        now
                ));
    }

    private static Instant nextRegularDue(Instant now, UcsConfigSnapshot config) {
        return now.plus(Duration.ofHours(config.claimTax().intervalHours()));
    }

    private static Instant nextRetryDue(Instant now, UcsConfigSnapshot config) {
        return now.plus(Duration.ofHours(config.nonpayment().retryIntervalHours()));
    }

    private static String referenceFor(ClaimId claimId, Instant dueAt) {
        return ClaimPricingService.REF_CLAIM_TAX + ":" + claimId.value() + ":" + dueAt.toEpochMilli();
    }

    private static ClaimTaxLedgerEntry paidEntry(
            Claim claim,
            BigDecimal amount,
            Instant dueAt,
            Instant processedAt,
            String reference,
            String providerReference,
            String detail
    ) {
        return ledgerEntry(
                claim,
                amount,
                dueAt,
                processedAt,
                reference,
                ClaimTaxLedgerStatus.PAID,
                providerReference,
                detail
        );
    }

    private static ClaimTaxLedgerEntry failedEntry(
            Claim claim,
            BigDecimal amount,
            Instant dueAt,
            Instant processedAt,
            String reference,
            String detail
    ) {
        return ledgerEntry(
                claim,
                amount,
                dueAt,
                processedAt,
                reference,
                ClaimTaxLedgerStatus.FAILED,
                "",
                detail
        );
    }

    private static ClaimTaxLedgerEntry ledgerEntry(
            Claim claim,
            BigDecimal amount,
            Instant dueAt,
            Instant processedAt,
            String reference,
            ClaimTaxLedgerStatus status,
            String providerReference,
            String detail
    ) {
        return new ClaimTaxLedgerEntry(
                UUID.randomUUID(),
                claim.id(),
                claim.owner().stableKey(),
                amount,
                dueAt,
                processedAt,
                reference,
                status,
                providerReference,
                detail
        );
    }
}
