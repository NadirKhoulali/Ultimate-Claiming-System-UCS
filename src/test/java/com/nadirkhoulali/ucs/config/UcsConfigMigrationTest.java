package com.nadirkhoulali.ucs.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UcsConfigMigrationTest {
    @Test
    void currentSchemaNeedsNoMigration() {
        UcsConfigMigration.MigrationPlan plan = UcsConfigMigration.planFor(UcsConfigDefaults.CURRENT_SCHEMA_VERSION);

        assertTrue(plan.valid());
        assertTrue(plan.current());
        assertTrue(plan.notes().isEmpty());
    }

    @Test
    void futureSchemaIsRejected() {
        UcsConfigMigration.MigrationPlan plan = UcsConfigMigration.planFor(UcsConfigDefaults.CURRENT_SCHEMA_VERSION + 1);

        assertFalse(plan.valid());
        assertFalse(plan.current());
        assertFalse(plan.notes().isEmpty());
    }
}
