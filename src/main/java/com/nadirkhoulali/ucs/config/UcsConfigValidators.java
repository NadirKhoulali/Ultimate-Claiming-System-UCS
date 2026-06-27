package com.nadirkhoulali.ucs.config;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class UcsConfigValidators {
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern SIMPLE_KEY = Pattern.compile("[a-z][a-z0-9_-]{1,63}");
    private static final Pattern LOCALE_KEY = Pattern.compile("[a-z]{2}_[a-z]{2}");

    private UcsConfigValidators() {
    }

    public static boolean isResourceId(Object value) {
        return value instanceof String text && RESOURCE_ID.matcher(text).matches();
    }

    public static boolean isRegistryOrTagReference(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return false;
        }
        return text.startsWith("#")
                ? RESOURCE_ID.matcher(text.substring(1)).matches()
                : RESOURCE_ID.matcher(text).matches();
    }

    public static boolean isSimpleKey(Object value) {
        return value instanceof String text && SIMPLE_KEY.matcher(text).matches();
    }

    public static boolean isLocaleKey(Object value) {
        return value instanceof String text && LOCALE_KEY.matcher(text).matches();
    }

    public static UcsConfigValidationReport validate(UcsConfigSnapshot config) {
        Objects.requireNonNull(config, "config");
        UcsConfigValidationReport.Builder report = UcsConfigValidationReport.builder();

        validateMigration(config, report);
        validateDimensions(config.dimensions(), report);
        validateClaimLimits(config.claimLimits(), report);
        validateClaimMetadata(config.claimMetadata(), report);
        validateClaimTeleport(config.claimTeleport(), report);
        validateRoleDefaults(config.roles(), report);
        validateBans(config.bans(), report);
        validateFlagDefaults(config.flags(), report);
        validateProtection(config.protection(), report);
        validateEconomy(config.economy(), report);
        validateClaimTax(config.claimTax(), report);
        validateMapCache(config.mapCache(), report);
        validateAudit(config.audit(), report);
        validateArchive(config.archive(), report);
        validateInactivePurge(config.inactivePurge(), report);
        validateCommands(config.commands(), report);
        validateMessages(config.messages(), report);

        return report.build();
    }

    private static void validateMigration(UcsConfigSnapshot config, UcsConfigValidationReport.Builder report) {
        UcsConfigMigration.MigrationPlan plan = UcsConfigMigration.planFor(config.schemaVersion());
        if (!plan.valid()) {
            plan.notes().forEach(report::error);
        } else if (!plan.current()) {
            plan.notes().forEach(report::warning);
        }
    }

    private static void validateDimensions(
            UcsConfigSnapshot.DimensionPolicy dimensions,
            UcsConfigValidationReport.Builder report
    ) {
        validateResourceList("dimensions.enabledDimensions", dimensions.enabledDimensions(), report);
        validateResourceList("dimensions.disabledDimensions", dimensions.disabledDimensions(), report);
        if (dimensions.enabledDimensions().isEmpty()) {
            report.error("dimensions.enabledDimensions must contain at least one claimable dimension");
        }

        Set<String> enabled = new HashSet<>(dimensions.enabledDimensions());
        for (String disabled : dimensions.disabledDimensions()) {
            if (enabled.contains(disabled)) {
                report.error("Dimension " + disabled + " is both enabled and disabled");
            }
        }
    }

    private static void validateClaimLimits(
            UcsConfigSnapshot.ClaimLimitPolicy limits,
            UcsConfigValidationReport.Builder report
    ) {
        requireAtLeast("claimLimits.maxClaimsPerPlayer", limits.maxClaimsPerPlayer(), 1, report);
        requireAtLeast("claimLimits.maxChunksPerPlayer", limits.maxChunksPerPlayer(), 1, report);
        requireAtLeast("claimLimits.maxChunksPerClaim", limits.maxChunksPerClaim(), 1, report);
        requireAtLeast("claimLimits.maxRadiusClaim", limits.maxRadiusClaim(), 0, report);

        if (limits.maxChunksPerClaim() > limits.maxChunksPerPlayer()) {
            report.warning("claimLimits.maxChunksPerClaim is greater than maxChunksPerPlayer");
        }
        long squareRadiusChunks = (long) (limits.maxRadiusClaim() * 2 + 1) * (limits.maxRadiusClaim() * 2 + 1);
        if (squareRadiusChunks > limits.maxChunksPerClaim()) {
            report.warning("claimLimits.maxRadiusClaim can select more chunks than maxChunksPerClaim");
        }
    }

    private static void validateClaimMetadata(
            UcsConfigSnapshot.ClaimMetadataPolicy metadata,
            UcsConfigValidationReport.Builder report
    ) {
        requireAtLeast("claimMetadata.maxNameLength", metadata.maxNameLength(), 1, report);
        requireAtLeast("claimMetadata.maxDescriptionLength", metadata.maxDescriptionLength(), 0, report);
    }

    private static void validateClaimTeleport(
            UcsConfigSnapshot.ClaimTeleportPolicy teleport,
            UcsConfigValidationReport.Builder report
    ) {
        requireAtLeast("claimTeleport.delaySeconds", teleport.delaySeconds(), 0, report);
    }

    private static void validateRoleDefaults(
            UcsConfigSnapshot.RoleDefaults roles,
            UcsConfigValidationReport.Builder report
    ) {
        validateSimpleList("roles.defaultRoleIds", roles.defaultRoleIds(), report);
        if (!isSimpleKey(roles.defaultTrustRoleId())) {
            report.error("roles.defaultTrustRoleId must be a lowercase simple key");
        }
        if (!isSimpleKey(roles.bannedRoleId())) {
            report.error("roles.bannedRoleId must be a lowercase simple key");
        }
        if (!roles.defaultRoleIds().contains("owner")) {
            report.error("roles.defaultRoleIds must include owner");
        }
        if (!roles.defaultRoleIds().contains(roles.defaultTrustRoleId())) {
            report.error("roles.defaultRoleIds must include defaultTrustRoleId " + roles.defaultTrustRoleId());
        }
        if (!roles.defaultRoleIds().contains(roles.bannedRoleId())) {
            report.error("roles.defaultRoleIds must include bannedRoleId " + roles.bannedRoleId());
        }
    }

    private static void validateBans(
            UcsConfigSnapshot.BanPolicy bans,
            UcsConfigValidationReport.Builder report
    ) {
        requireAtLeast("bans.expulsionSearchRadiusBlocks", bans.expulsionSearchRadiusBlocks(), 8, report);
        requireAtLeast("bans.expulsionCooldownTicks", bans.expulsionCooldownTicks(), 1, report);
        if (!bans.preventEntry()) {
            report.warning("Claim ban entry prevention is disabled; banned players can remain inside claims");
        }
    }

    private static void validateFlagDefaults(
            UcsConfigSnapshot.FlagDefaults flags,
            UcsConfigValidationReport.Builder report
    ) {
        validateResourceList("flags.defaultProtectionFlagIds", flags.defaultProtectionFlagIds(), report);
    }

    private static void validateProtection(
            UcsConfigSnapshot.ProtectionPolicy protection,
            UcsConfigValidationReport.Builder report
    ) {
        validateResourceList("protection.ignoredBlockIds", protection.ignoredBlockIds(), report);
        validateResourceList("protection.allowedBlockIds", protection.allowedBlockIds(), report);
        validateResourceList("protection.specialBlockIds", protection.specialBlockIds(), report);
        validateRegistryReferenceList("protection.containerTargetIds", protection.containerTargetIds(), report);
        validateRegistryReferenceList("protection.doorTargetIds", protection.doorTargetIds(), report);
        validateRegistryReferenceList("protection.buttonTargetIds", protection.buttonTargetIds(), report);
        validateRegistryReferenceList("protection.leverTargetIds", protection.leverTargetIds(), report);
        validateRegistryReferenceList("protection.redstoneTargetIds", protection.redstoneTargetIds(), report);
        validateRegistryReferenceList("protection.entityTargetIds", protection.entityTargetIds(), report);
        validateRegistryReferenceList("protection.vehicleTargetIds", protection.vehicleTargetIds(), report);
        for (String ignored : protection.ignoredBlockIds()) {
            if (protection.specialBlockIds().contains(ignored)) {
                report.warning("Block " + ignored + " is both ignored and special; ignored takes precedence");
            }
        }
    }

    private static void validateEconomy(
            UcsConfigSnapshot.EconomyPolicy economy,
            UcsConfigValidationReport.Builder report
    ) {
        requireAtLeast("economy.starterClaimPrice", economy.starterClaimPrice(), 0.0D, report);
        requireAtLeast("economy.pricePerExtraChunk", economy.pricePerExtraChunk(), 0.0D, report);
        requireAtLeast("economy.maxClaimSalePrice", economy.maxClaimSalePrice(), 1.0D, report);
        if (economy.unclaimRefundRatio() < 0.0D || economy.unclaimRefundRatio() > 1.0D) {
            report.error("economy.unclaimRefundRatio must be between 0.0 and 1.0");
        }
        if (economy.enableWhenProviderExists()
                && economy.starterClaimPrice() == 0.0D
                && economy.pricePerExtraChunk() == 0.0D) {
            report.warning("Economy is enabled when a provider exists, but claim prices are zero");
        }
    }

    private static void validateClaimTax(
            UcsConfigSnapshot.ClaimTaxPolicy tax,
            UcsConfigValidationReport.Builder report
    ) {
        requireAtLeast("claimTax.intervalHours", tax.intervalHours(), 1, report);
        requireAtLeast("claimTax.initialDelayHours", tax.initialDelayHours(), 0, report);
        requireAtLeast("claimTax.baseAmount", tax.baseAmount(), 0.0D, report);
        requireAtLeast("claimTax.perChunkAmount", tax.perChunkAmount(), 0.0D, report);
        requireAtLeast("claimTax.maxClaimsPerTick", tax.maxClaimsPerTick(), 1, report);
        requireAtLeast("claimTax.warningHoursBeforeDue", tax.warningHoursBeforeDue(), 0, report);
        if (tax.enabled() && tax.baseAmount() == 0.0D && tax.perChunkAmount() == 0.0D) {
            report.warning("Claim tax is enabled, but baseAmount and perChunkAmount are zero");
        }
    }

    private static void validateMapCache(
            UcsConfigSnapshot.MapCachePolicy mapCache,
            UcsConfigValidationReport.Builder report
    ) {
        requireAtLeast("mapCache.maxSizeMiB", mapCache.maxSizeMiB(), 16, report);
        requireAtLeast("mapCache.maxTileAgeDays", mapCache.maxTileAgeDays(), 1, report);
        requireAtLeast("mapCache.maxTileRequestsPerPlayer", mapCache.maxTileRequestsPerPlayer(), 1, report);
        requireAtLeast("mapCache.maxGlobalTileJobs", mapCache.maxGlobalTileJobs(), 1, report);
    }

    private static void validateAudit(
            UcsConfigSnapshot.AuditPolicy audit,
            UcsConfigValidationReport.Builder report
    ) {
        requireAtLeast("audit.maxEntriesPerClaim", audit.maxEntriesPerClaim(), 1, report);
        requireAtLeast("audit.retentionDays", audit.retentionDays(), 1, report);
        if (!audit.enabled()) {
            report.warning("Audit logging is disabled; admin/economy traceability will be limited");
        }
    }

    private static void validateArchive(
            UcsConfigSnapshot.ArchivePolicy archive,
            UcsConfigValidationReport.Builder report
    ) {
        requireAtLeast("archive.retentionDays", archive.retentionDays(), 1, report);
    }

    private static void validateInactivePurge(
            UcsConfigSnapshot.InactivePurgePolicy purge,
            UcsConfigValidationReport.Builder report
    ) {
        requireAtLeast("inactivePurge.afterDays", purge.afterDays(), 1, report);
        if (purge.enabled() && !purge.archiveBeforeDelete()) {
            report.error("inactivePurge.archiveBeforeDelete must stay true when inactive purge is enabled");
        }
    }

    private static void validateCommands(
            UcsConfigSnapshot.CommandPolicy commands,
            UcsConfigValidationReport.Builder report
    ) {
        if (!isSimpleKey(commands.permissionNodePrefix())) {
            report.error("commands.permissionNodePrefix must be a lowercase simple key");
        }
    }

    private static void validateMessages(
            UcsConfigSnapshot.MessagePolicy messages,
            UcsConfigValidationReport.Builder report
    ) {
        if (!isLocaleKey(messages.defaultLocale())) {
            report.error("messages.defaultLocale must use a language_country key such as en_us");
        } else if (!messages.defaultLocale().equals(messages.defaultLocale().toLowerCase(Locale.ROOT))) {
            report.error("messages.defaultLocale must be lowercase");
        }
    }

    private static void validateResourceList(
            String fieldName,
            List<String> values,
            UcsConfigValidationReport.Builder report
    ) {
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (!isResourceId(value)) {
                report.error(fieldName + " contains invalid resource id: " + value);
            }
            if (!seen.add(value)) {
                report.error(fieldName + " contains duplicate value: " + value);
            }
        }
    }

    private static void validateRegistryReferenceList(
            String fieldName,
            List<String> values,
            UcsConfigValidationReport.Builder report
    ) {
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (!isRegistryOrTagReference(value)) {
                report.error(fieldName + " contains invalid resource id or #tag id: " + value);
            }
            if (!seen.add(value)) {
                report.error(fieldName + " contains duplicate value: " + value);
            }
        }
    }

    private static void validateSimpleList(
            String fieldName,
            List<String> values,
            UcsConfigValidationReport.Builder report
    ) {
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (!isSimpleKey(value)) {
                report.error(fieldName + " contains invalid simple key: " + value);
            }
            if (!seen.add(value)) {
                report.error(fieldName + " contains duplicate value: " + value);
            }
        }
    }

    private static void requireAtLeast(
            String fieldName,
            int value,
            int minimum,
            UcsConfigValidationReport.Builder report
    ) {
        if (value < minimum) {
            report.error(fieldName + " must be at least " + minimum);
        }
    }

    private static void requireAtLeast(
            String fieldName,
            double value,
            double minimum,
            UcsConfigValidationReport.Builder report
    ) {
        if (value < minimum) {
            report.error(fieldName + " must be at least " + minimum);
        }
    }
}
