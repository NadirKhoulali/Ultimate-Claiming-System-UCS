package com.nadirkhoulali.ucs.api.economy;

import java.util.Objects;
import java.util.UUID;

/**
 * Provider-neutral reference to an economy account.
 *
 * <p>Threading: economy providers may require server-thread access. Callers should treat account resolution as
 * server-thread only unless a provider explicitly documents async support.</p>
 */
public record ClaimEconomyAccountRef(
        ClaimEconomyAccountType type,
        UUID id,
        String reference
) {
    public ClaimEconomyAccountRef {
        Objects.requireNonNull(type, "type");
        if (type.requiresUuid() && id == null) {
            throw new IllegalArgumentException(type + " account references require an id");
        }
        reference = reference == null ? "" : reference.trim();
    }

    public static ClaimEconomyAccountRef playerPrimary(UUID playerId) {
        return new ClaimEconomyAccountRef(ClaimEconomyAccountType.PLAYER_PRIMARY, playerId, "");
    }

    public static ClaimEconomyAccountRef providerAccount(UUID accountId) {
        return new ClaimEconomyAccountRef(ClaimEconomyAccountType.PROVIDER_ACCOUNT, accountId, "");
    }

    public static ClaimEconomyAccountRef serverLedger(String reference) {
        return new ClaimEconomyAccountRef(ClaimEconomyAccountType.SERVER_LEDGER, null, reference);
    }
}
