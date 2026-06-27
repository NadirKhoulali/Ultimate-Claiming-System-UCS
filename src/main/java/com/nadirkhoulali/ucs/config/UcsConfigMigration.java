package com.nadirkhoulali.ucs.config;

import java.util.List;

public final class UcsConfigMigration {
    private UcsConfigMigration() {
    }

    public static MigrationPlan planFor(int schemaVersion) {
        if (schemaVersion < 1) {
            return MigrationPlan.invalid(List.of("schemaVersion must be at least 1"));
        }
        if (schemaVersion == UcsConfigDefaults.CURRENT_SCHEMA_VERSION) {
            return MigrationPlan.currentSchema();
        }
        if (schemaVersion > UcsConfigDefaults.CURRENT_SCHEMA_VERSION) {
            return MigrationPlan.invalid(List.of(
                    "schemaVersion " + schemaVersion + " is newer than supported schema "
                            + UcsConfigDefaults.CURRENT_SCHEMA_VERSION
            ));
        }
        return MigrationPlan.needsMigration(List.of(
                "Migrate common config from schema " + schemaVersion
                        + " to " + UcsConfigDefaults.CURRENT_SCHEMA_VERSION
        ));
    }

    public record MigrationPlan(boolean current, boolean valid, List<String> notes) {
        public MigrationPlan {
            notes = List.copyOf(notes);
        }

        static MigrationPlan currentSchema() {
            return new MigrationPlan(true, true, List.of());
        }

        static MigrationPlan needsMigration(List<String> notes) {
            return new MigrationPlan(false, true, notes);
        }

        static MigrationPlan invalid(List<String> notes) {
            return new MigrationPlan(false, false, notes);
        }
    }
}
