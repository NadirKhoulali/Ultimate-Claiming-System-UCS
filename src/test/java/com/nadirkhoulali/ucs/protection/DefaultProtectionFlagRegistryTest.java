package com.nadirkhoulali.ucs.protection;

import com.nadirkhoulali.ucs.api.protection.DuplicateProtectionFlagException;
import com.nadirkhoulali.ucs.api.protection.ProtectionDecisionType;
import com.nadirkhoulali.ucs.api.protection.ProtectionFlagCategory;
import com.nadirkhoulali.ucs.api.protection.ProtectionFlagDefinition;
import com.nadirkhoulali.ucs.api.protection.UcsBuiltInProtectionFlags;
import com.nadirkhoulali.ucs.core.model.FlagId;
import com.nadirkhoulali.ucs.core.model.RoleId;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultProtectionFlagRegistryTest {
    @Test
    void registersBuiltInProtectionFlags() {
        DefaultProtectionFlagRegistry registry = DefaultProtectionFlagRegistry.withBuiltIns();

        assertTrue(registry.find(UcsBuiltInProtectionFlags.BLOCK_BREAK).isPresent());
        assertFalse(registry.find(UcsBuiltInProtectionFlags.EXPLOSION).orElseThrow().actorRequired());
        assertEquals(
                Set.of(ProtectionFlagCategory.values()),
                registry.flags().stream().map(ProtectionFlagDefinition::category).collect(Collectors.toSet())
        );
    }

    @Test
    void rejectsDuplicateFlagIds() {
        DefaultProtectionFlagRegistry registry = DefaultProtectionFlagRegistry.withBuiltIns();

        assertThrows(DuplicateProtectionFlagException.class, () -> registry.register(
                new ProtectionFlagDefinition(
                        UcsBuiltInProtectionFlags.BLOCK_BREAK,
                        "Duplicate Block Break",
                        ProtectionFlagCategory.BLOCKS,
                        ProtectionDecisionType.DENY,
                        Set.of(new RoleId("member")),
                        true
                )
        ));
    }

    @Test
    void registersAddonFlags() {
        DefaultProtectionFlagRegistry registry = DefaultProtectionFlagRegistry.withBuiltIns();
        ProtectionFlagDefinition definition = new ProtectionFlagDefinition(
                new FlagId("addon:custom_machine_use"),
                "Custom Machine Use",
                ProtectionFlagCategory.INTERACTIONS,
                ProtectionDecisionType.DENY,
                Set.of(new RoleId("member")),
                true
        );

        assertEquals(definition, registry.register(definition));
        assertEquals(definition, registry.find(definition.id()).orElseThrow());
    }
}
