package com.nadirkhoulali.ucs.api.protection;

import com.nadirkhoulali.ucs.core.model.FlagId;

public final class DuplicateProtectionFlagException extends RuntimeException {
    public DuplicateProtectionFlagException(FlagId flagId) {
        super("Protection flag " + flagId.value() + " is already registered");
    }
}
