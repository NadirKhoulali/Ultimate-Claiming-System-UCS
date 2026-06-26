package com.nadirkhoulali.ucs.core.model;

public sealed interface OwnerRef permits PlayerOwner, ServerOwner, TeamOwner {
    OwnerType type();

    String stableKey();
}
