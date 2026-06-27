package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.api.UcsClaimService;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.ArchiveId;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimId;
import com.nadirkhoulali.ucs.core.model.ClaimTaxState;
import com.nadirkhoulali.ucs.core.model.PlayerOwner;
import com.nadirkhoulali.ucs.storage.ClaimRepository;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public final class ClaimNonpaymentService {
    private static final int NONPAYMENT_SCAN_INTERVAL_TICKS = 200;
    private long nextNonpaymentScanTick;

    public void tick(
            MinecraftServer server,
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config
    ) {
        Objects.requireNonNull(server, "server");
        if (server.getTickCount() < nextNonpaymentScanTick) {
            return;
        }
        nextNonpaymentScanTick = server.getTickCount() + NONPAYMENT_SCAN_INTERVAL_TICKS;
        processNonpayment(server, repository, claimService, config, Instant.now(), config.nonpayment().maxClaimsPerTick());
    }

    public void clear() {
        nextNonpaymentScanTick = 0L;
    }

    public ClaimNonpaymentResult processNonpayment(
            MinecraftServer server,
            ClaimRepository repository,
            UcsClaimService claimService,
            UcsConfigSnapshot config,
            Instant now,
            int maxClaims
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(claimService, "claimService");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(now, "now");
        if (maxClaims <= 0) {
            throw new IllegalArgumentException("maxClaims must be greater than zero");
        }

        int scanned = 0;
        int warnings = 0;
        int archived = 0;
        for (ClaimTaxState state : repository.taxStates().stream()
                .filter(ClaimNonpaymentService::isDelinquent)
                .sorted(Comparator.comparing(taxState -> taxState.delinquentSince().orElse(Instant.MAX)))
                .toList()) {
            if (scanned >= maxClaims) {
                break;
            }
            scanned++;
            Optional<Claim> claim = repository.findById(state.claimId());
            if (claim.isEmpty()) {
                continue;
            }

            ClaimTaxState updatedState = state;
            if (shouldWarn(state, config, now)) {
                if (server != null) {
                    sendWarning(server, claim.orElseThrow(), state, config);
                    warnings++;
                }
                updatedState = updatedState.markWarningSent(now);
                repository.saveTaxState(updatedState);
            }

            if (config.nonpayment().archiveAfterGrace() && graceExpired(updatedState, config, now)) {
                ClaimTaxState stateForArchive = updatedState;
                Optional<?> archivedClaim = claimService.archiveClaim(
                        updatedState.claimId(),
                        ArchiveId.random(),
                        now,
                        "nonpayment debt " + updatedState.outstandingDebt().toPlainString(),
                        "system:nonpayment"
                );
                if (archivedClaim.isPresent()) {
                    if (server != null) {
                        sendArchived(server, claim.orElseThrow(), stateForArchive);
                    }
                    repository.saveTaxState(stateForArchive.markWarningSent(now));
                    archived++;
                }
            }
        }
        return new ClaimNonpaymentResult(scanned, warnings, archived);
    }

    public boolean hasBlockingDebt(ClaimRepository repository, ClaimId claimId, UcsConfigSnapshot config) {
        return config.nonpayment().requireDebtPaidBeforeRestore()
                && repository.findTaxState(claimId)
                .map(ClaimNonpaymentService::hasDebt)
                .orElse(false);
    }

    public Optional<ClaimTaxState> clearDebt(
            ClaimRepository repository,
            ClaimId claimId,
            UcsConfigSnapshot config,
            Instant now
    ) {
        return repository.findTaxState(claimId)
                .filter(ClaimNonpaymentService::hasDebt)
                .map(state -> repository.saveTaxState(state.clearDebt(nextRegularDue(now, config), now)));
    }

    public Optional<ClaimTaxState> deferAfterRestore(
            ClaimRepository repository,
            ClaimId claimId,
            UcsConfigSnapshot config,
            Instant now
    ) {
        return repository.findTaxState(claimId)
                .filter(ClaimNonpaymentService::hasDebt)
                .map(state -> repository.saveTaxState(state.deferAfterRestore(nextRetryDue(now, config), now)));
    }

    private static boolean isDelinquent(ClaimTaxState state) {
        return hasDebt(state) && state.delinquentSince().isPresent();
    }

    private static boolean hasDebt(ClaimTaxState state) {
        return state.outstandingDebt().compareTo(BigDecimal.ZERO) > 0;
    }

    private static boolean shouldWarn(ClaimTaxState state, UcsConfigSnapshot config, Instant now) {
        Instant earliestWarning = state.lastWarningAt()
                .map(lastWarning -> lastWarning.plus(Duration.ofHours(config.nonpayment().warningIntervalHours())))
                .orElse(Instant.MIN);
        return !earliestWarning.isAfter(now);
    }

    private static boolean graceExpired(ClaimTaxState state, UcsConfigSnapshot config, Instant now) {
        Instant archiveAt = state.delinquentSince().orElseThrow()
                .plus(Duration.ofHours(config.nonpayment().graceHours()));
        return !archiveAt.isAfter(now);
    }

    private static void sendWarning(
            MinecraftServer server,
            Claim claim,
            ClaimTaxState state,
            UcsConfigSnapshot config
    ) {
        playerOwner(claim).map(owner -> server.getPlayerList().getPlayer(owner.playerId()))
                .ifPresent(player -> player.sendSystemMessage(Component.translatable(
                        "command.ucs.tax.nonpayment.warning",
                        claim.metadata().displayName(),
                        state.outstandingDebt().toPlainString(),
                        config.nonpayment().graceHours()
                )));
    }

    private static void sendArchived(MinecraftServer server, Claim claim, ClaimTaxState state) {
        playerOwner(claim).map(owner -> server.getPlayerList().getPlayer(owner.playerId()))
                .ifPresent(player -> player.sendSystemMessage(Component.translatable(
                        "command.ucs.tax.nonpayment.archived",
                        claim.metadata().displayName(),
                        state.outstandingDebt().toPlainString()
                )));
    }

    private static Optional<PlayerOwner> playerOwner(Claim claim) {
        return claim.owner() instanceof PlayerOwner player ? Optional.of(player) : Optional.empty();
    }

    private static Instant nextRegularDue(Instant now, UcsConfigSnapshot config) {
        return now.plus(Duration.ofHours(config.claimTax().intervalHours()));
    }

    private static Instant nextRetryDue(Instant now, UcsConfigSnapshot config) {
        return now.plus(Duration.ofHours(config.nonpayment().retryIntervalHours()));
    }
}
