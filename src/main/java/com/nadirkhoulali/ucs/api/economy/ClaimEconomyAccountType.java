package com.nadirkhoulali.ucs.api.economy;

public enum ClaimEconomyAccountType {
    /**
     * The economy provider should resolve the player's primary/default account.
     */
    PLAYER_PRIMARY(true),

    /**
     * A provider-native account id, such as a UBS account UUID.
     */
    PROVIDER_ACCOUNT(true),

    /**
     * A server-owned ledger or sink/source account. Providers may implement this as an external account.
     */
    SERVER_LEDGER(false);

    private final boolean requiresUuid;

    ClaimEconomyAccountType(boolean requiresUuid) {
        this.requiresUuid = requiresUuid;
    }

    public boolean requiresUuid() {
        return requiresUuid;
    }
}
