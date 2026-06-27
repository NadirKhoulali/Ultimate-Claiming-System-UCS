package com.nadirkhoulali.ucs.api.protection;

import com.nadirkhoulali.ucs.core.model.FlagId;

import java.util.Collection;
import java.util.Optional;

public interface ProtectionFlagRegistry {
    Collection<ProtectionFlagDefinition> flags();

    Optional<ProtectionFlagDefinition> find(FlagId flagId);

    ProtectionFlagDefinition register(ProtectionFlagDefinition definition);
}
