package com.nadirkhoulali.ucs.permission;

import java.util.Objects;

public final class UcsPermissionPolicy {
    private UcsPermissionPolicy() {
    }

    public static boolean resolveDefault(UcsPermission permission, boolean opFallbackEnabled, boolean hasFallbackLevel) {
        Objects.requireNonNull(permission, "permission");
        if (permission.publicByDefault()) {
            return true;
        }
        return opFallbackEnabled && hasFallbackLevel;
    }
}
