package com.nadirkhoulali.ucs.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UcsConfigValidatorsTest {
    @Test
    void defaultSnapshotIsValid() {
        UcsConfigValidationReport report = validSnapshot().validate();

        assertTrue(report.valid(), () -> "Expected no config errors, got " + report.errors());
    }

    @Test
    void rejectsDimensionThatIsBothEnabledAndDisabled() {
        UcsConfigSnapshot snapshot = withDimensions(
                List.of("minecraft:overworld"),
                List.of("minecraft:overworld")
        );

        UcsConfigValidationReport report = snapshot.validate();

        assertFalse(report.valid());
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("both enabled and disabled")));
    }

    @Test
    void rejectsInactivePurgeWithoutArchive() {
        UcsConfigSnapshot base = validSnapshot();
        UcsConfigSnapshot snapshot = new UcsConfigSnapshot(
                base.schemaVersion(),
                base.logStartupSummary(),
                base.dimensions(),
                base.claimLimits(),
                base.claimMetadata(),
                base.claimTeleport(),
                base.roles(),
                base.flags(),
                base.economy(),
                base.mapCache(),
                base.audit(),
                new UcsConfigSnapshot.InactivePurgePolicy(true, 90, false),
                base.commands(),
                base.messages()
        );

        UcsConfigValidationReport report = snapshot.validate();

        assertFalse(report.valid());
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("archiveBeforeDelete")));
    }

    @Test
    void warnsWhenRadiusCanSelectMoreChunksThanClaimLimit() {
        UcsConfigSnapshot base = validSnapshot();
        UcsConfigSnapshot snapshot = new UcsConfigSnapshot(
                base.schemaVersion(),
                base.logStartupSummary(),
                base.dimensions(),
                new UcsConfigSnapshot.ClaimLimitPolicy(16, 256, 4, 5, true),
                base.claimMetadata(),
                base.claimTeleport(),
                base.roles(),
                base.flags(),
                base.economy(),
                base.mapCache(),
                base.audit(),
                base.inactivePurge(),
                base.commands(),
                base.messages()
        );

        UcsConfigValidationReport report = snapshot.validate();

        assertTrue(report.valid());
        assertTrue(report.warnings().stream().anyMatch(warning -> warning.contains("maxRadiusClaim")));
    }

    @Test
    void rejectsNegativeTeleportDelay() {
        UcsConfigSnapshot base = validSnapshot();
        UcsConfigSnapshot snapshot = new UcsConfigSnapshot(
                base.schemaVersion(),
                base.logStartupSummary(),
                base.dimensions(),
                base.claimLimits(),
                base.claimMetadata(),
                new UcsConfigSnapshot.ClaimTeleportPolicy(-1, true, true),
                base.roles(),
                base.flags(),
                base.economy(),
                base.mapCache(),
                base.audit(),
                base.inactivePurge(),
                base.commands(),
                base.messages()
        );

        UcsConfigValidationReport report = snapshot.validate();

        assertFalse(report.valid());
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("claimTeleport.delaySeconds")));
    }

    @Test
    void rolePolicyIdsAreIncludedInSnapshotRoles() {
        UcsConfigSnapshot base = validSnapshot();
        UcsConfigSnapshot snapshot = new UcsConfigSnapshot(
                base.schemaVersion(),
                base.logStartupSummary(),
                base.dimensions(),
                base.claimLimits(),
                base.claimMetadata(),
                base.claimTeleport(),
                new UcsConfigSnapshot.RoleDefaults(List.of("owner", "visitor"), "member", "banned", false),
                base.flags(),
                base.economy(),
                base.mapCache(),
                base.audit(),
                base.inactivePurge(),
                base.commands(),
                base.messages()
        );

        UcsConfigValidationReport report = snapshot.validate();

        assertTrue(report.valid());
        assertTrue(snapshot.roles().defaultRoleIds().contains("member"));
        assertTrue(snapshot.roles().defaultRoleIds().contains("banned"));
    }

    @Test
    void snapshotDefensivelyCopiesLists() {
        ArrayList<String> enabled = new ArrayList<>(List.of("minecraft:overworld"));
        UcsConfigSnapshot snapshot = withDimensions(enabled, List.of());

        enabled.clear();

        assertTrue(snapshot.dimensions().enabledDimensions().contains("minecraft:overworld"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.dimensions().enabledDimensions().add("minecraft:the_end"));
    }

    private static UcsConfigSnapshot withDimensions(List<String> enabled, List<String> disabled) {
        UcsConfigSnapshot base = validSnapshot();
        return new UcsConfigSnapshot(
                base.schemaVersion(),
                base.logStartupSummary(),
                new UcsConfigSnapshot.DimensionPolicy(enabled, disabled, true),
                base.claimLimits(),
                base.claimMetadata(),
                base.claimTeleport(),
                base.roles(),
                base.flags(),
                base.economy(),
                base.mapCache(),
                base.audit(),
                base.inactivePurge(),
                base.commands(),
                base.messages()
        );
    }

    private static UcsConfigSnapshot validSnapshot() {
        return new UcsConfigSnapshot(
                UcsConfigDefaults.CURRENT_SCHEMA_VERSION,
                true,
                new UcsConfigSnapshot.DimensionPolicy(
                        UcsConfigDefaults.ENABLED_DIMENSIONS,
                        UcsConfigDefaults.DISABLED_DIMENSIONS,
                        true
                ),
                new UcsConfigSnapshot.ClaimLimitPolicy(16, 256, 128, 5, true),
                new UcsConfigSnapshot.ClaimMetadataPolicy(48, 240),
                new UcsConfigSnapshot.ClaimTeleportPolicy(3, true, true),
                new UcsConfigSnapshot.RoleDefaults(UcsConfigDefaults.DEFAULT_ROLE_IDS, "member", "banned", false),
                new UcsConfigSnapshot.FlagDefaults(UcsConfigDefaults.DEFAULT_PROTECTION_FLAG_IDS),
                new UcsConfigSnapshot.EconomyPolicy(true, 25.0D, 5.0D, 0.75D, true),
                new UcsConfigSnapshot.MapCachePolicy(1024, 30, 64, 512),
                new UcsConfigSnapshot.AuditPolicy(true, 250, 180),
                new UcsConfigSnapshot.InactivePurgePolicy(false, 90, true),
                new UcsConfigSnapshot.CommandPolicy("ucs", true),
                new UcsConfigSnapshot.MessagePolicy("en_us", true)
        );
    }
}
