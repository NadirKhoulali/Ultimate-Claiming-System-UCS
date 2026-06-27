package com.nadirkhoulali.ucs.api;

import java.util.Objects;
import java.util.Optional;

public final class UcsApiProvider {
    private static volatile UcsApiAccess activeAccess;

    private UcsApiProvider() {
    }

    public static Optional<UcsApiAccess> access() {
        return Optional.ofNullable(activeAccess);
    }

    /**
     * Internal UCS lifecycle hook. Addons should read through {@link UcsApi} instead of replacing the provider.
     */
    public static void setActiveAccess(UcsApiAccess access) {
        activeAccess = Objects.requireNonNull(access, "access");
    }

    /**
     * Internal UCS lifecycle hook for server shutdown and test cleanup.
     */
    public static void clearActiveAccess() {
        activeAccess = null;
    }
}
