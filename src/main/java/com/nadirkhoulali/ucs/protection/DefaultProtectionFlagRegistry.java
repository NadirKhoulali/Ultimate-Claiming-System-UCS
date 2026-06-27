package com.nadirkhoulali.ucs.protection;

import com.nadirkhoulali.ucs.api.protection.DuplicateProtectionFlagException;
import com.nadirkhoulali.ucs.api.protection.ProtectionFlagDefinition;
import com.nadirkhoulali.ucs.api.protection.ProtectionFlagRegistry;
import com.nadirkhoulali.ucs.api.protection.UcsBuiltInProtectionFlags;
import com.nadirkhoulali.ucs.core.model.FlagId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class DefaultProtectionFlagRegistry implements ProtectionFlagRegistry {
    private final Map<FlagId, ProtectionFlagDefinition> definitions = new LinkedHashMap<>();

    public static DefaultProtectionFlagRegistry withBuiltIns() {
        DefaultProtectionFlagRegistry registry = new DefaultProtectionFlagRegistry();
        UcsBuiltInProtectionFlags.definitions().forEach(registry::register);
        return registry;
    }

    @Override
    public synchronized Collection<ProtectionFlagDefinition> flags() {
        return definitions.values().stream().toList();
    }

    @Override
    public synchronized Optional<ProtectionFlagDefinition> find(FlagId flagId) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(flagId, "flagId")));
    }

    @Override
    public synchronized ProtectionFlagDefinition register(ProtectionFlagDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (definitions.containsKey(definition.id())) {
            throw new DuplicateProtectionFlagException(definition.id());
        }
        definitions.put(definition.id(), definition);
        return definition;
    }
}
