package com.nadirkhoulali.ucs.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class UcsCommonConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue CONFIG_SCHEMA_VERSION;
    public static final ModConfigSpec.BooleanValue LOG_STARTUP_SUMMARY;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ENABLED_DIMENSIONS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_DIMENSIONS;
    public static final ModConfigSpec.BooleanValue DISABLE_TEMPORARY_DIMENSIONS_BY_DEFAULT;

    public static final ModConfigSpec.IntValue MAX_CLAIMS_PER_PLAYER;
    public static final ModConfigSpec.IntValue MAX_CHUNKS_PER_PLAYER;
    public static final ModConfigSpec.IntValue MAX_CHUNKS_PER_CLAIM;
    public static final ModConfigSpec.IntValue MAX_RADIUS_CLAIM;
    public static final ModConfigSpec.BooleanValue REQUIRE_CONNECTED_CLAIMS;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> DEFAULT_ROLE_IDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DEFAULT_PROTECTION_FLAG_IDS;

    public static final ModConfigSpec.BooleanValue ENABLE_ECONOMY_WHEN_PROVIDER_EXISTS;
    public static final ModConfigSpec.DoubleValue STARTER_CLAIM_PRICE;
    public static final ModConfigSpec.DoubleValue PRICE_PER_EXTRA_CHUNK;
    public static final ModConfigSpec.DoubleValue UNCLAIM_REFUND_RATIO;
    public static final ModConfigSpec.BooleanValue WARN_ABOUT_ECONOMY_DEFAULTS_ON_FIRST_RUN;

    public static final ModConfigSpec.IntValue MAP_CACHE_MAX_SIZE_MIB;
    public static final ModConfigSpec.IntValue MAP_CACHE_MAX_TILE_AGE_DAYS;
    public static final ModConfigSpec.IntValue MAP_MAX_TILE_REQUESTS_PER_PLAYER;
    public static final ModConfigSpec.IntValue MAP_MAX_GLOBAL_TILE_JOBS;

    public static final ModConfigSpec.BooleanValue AUDIT_ENABLED;
    public static final ModConfigSpec.IntValue AUDIT_MAX_ENTRIES_PER_CLAIM;
    public static final ModConfigSpec.IntValue AUDIT_RETENTION_DAYS;

    public static final ModConfigSpec.BooleanValue INACTIVE_PURGE_ENABLED;
    public static final ModConfigSpec.IntValue INACTIVE_PURGE_AFTER_DAYS;
    public static final ModConfigSpec.BooleanValue ARCHIVE_BEFORE_DELETE;

    public static final ModConfigSpec.ConfigValue<String> PERMISSION_NODE_PREFIX;
    public static final ModConfigSpec.BooleanValue OP_FALLBACK_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> DEFAULT_LOCALE;
    public static final ModConfigSpec.BooleanValue SEND_ACTION_BAR_DENIALS;

    public static final ModConfigSpec SPEC;

    static {
        CONFIG_SCHEMA_VERSION = BUILDER
                .comment("Internal UCS common config schema version. Future migrations use this value.")
                .defineInRange("schemaVersion", UcsConfigDefaults.CURRENT_SCHEMA_VERSION, 1, Integer.MAX_VALUE);

        LOG_STARTUP_SUMMARY = BUILDER
                .comment("Whether UCS logs a short startup summary when the dedicated or integrated server starts.")
                .define("logStartupSummary", true);

        BUILDER.push("dimensions");
        ENABLED_DIMENSIONS = BUILDER
                .comment("Dimensions where players may create claims. Use namespaced ids such as minecraft:overworld.")
                .defineListAllowEmpty("enabledDimensions", UcsConfigDefaults.ENABLED_DIMENSIONS, () -> "", UcsConfigValidators::isResourceId);
        DISABLED_DIMENSIONS = BUILDER
                .comment("Dimensions where claims are always blocked, even if enabledDimensions contains the id.")
                .defineListAllowEmpty("disabledDimensions", UcsConfigDefaults.DISABLED_DIMENSIONS, () -> "", UcsConfigValidators::isResourceId);
        DISABLE_TEMPORARY_DIMENSIONS_BY_DEFAULT = BUILDER
                .comment("Blocks claim creation in dimensions that look temporary or resource-world-like unless explicitly enabled later.")
                .define("disableTemporaryDimensionsByDefault", true);
        BUILDER.pop();

        BUILDER.push("claimLimits");
        MAX_CLAIMS_PER_PLAYER = BUILDER
                .comment("Maximum number of active player-owned claims per player.")
                .defineInRange("maxClaimsPerPlayer", 16, 1, 100_000);
        MAX_CHUNKS_PER_PLAYER = BUILDER
                .comment("Maximum total claimed chunks per player.")
                .defineInRange("maxChunksPerPlayer", 256, 1, 1_000_000);
        MAX_CHUNKS_PER_CLAIM = BUILDER
                .comment("Maximum chunks in a single connected claim.")
                .defineInRange("maxChunksPerClaim", 128, 1, 1_000_000);
        MAX_RADIUS_CLAIM = BUILDER
                .comment("Maximum radius accepted by /claim <radius>.")
                .defineInRange("maxRadiusClaim", 5, 0, 512);
        REQUIRE_CONNECTED_CLAIMS = BUILDER
                .comment("Whether claims must remain connected in v1.")
                .define("requireConnectedClaims", true);
        BUILDER.pop();

        BUILDER.push("roles");
        DEFAULT_ROLE_IDS = BUILDER
                .comment("Default claim role ids. Role behavior is configured by later UCS slices.")
                .defineListAllowEmpty("defaultRoleIds", UcsConfigDefaults.DEFAULT_ROLE_IDS, () -> "", UcsConfigValidators::isSimpleKey);
        BUILDER.pop();

        BUILDER.push("flags");
        DEFAULT_PROTECTION_FLAG_IDS = BUILDER
                .comment("Default protection flag ids enabled for new claims.")
                .defineListAllowEmpty("defaultProtectionFlagIds", UcsConfigDefaults.DEFAULT_PROTECTION_FLAG_IDS, () -> "", UcsConfigValidators::isResourceId);
        BUILDER.pop();

        BUILDER.push("economy");
        ENABLE_ECONOMY_WHEN_PROVIDER_EXISTS = BUILDER
                .comment("When true, UCS economy systems activate automatically if a compatible provider is present.")
                .define("enableWhenProviderExists", true);
        STARTER_CLAIM_PRICE = BUILDER
                .comment("Base price for the first chunk of a new claim.")
                .defineInRange("starterClaimPrice", 25.0D, 0.0D, 1_000_000_000.0D);
        PRICE_PER_EXTRA_CHUNK = BUILDER
                .comment("Additional price per claimed chunk after the first chunk.")
                .defineInRange("pricePerExtraChunk", 5.0D, 0.0D, 1_000_000_000.0D);
        UNCLAIM_REFUND_RATIO = BUILDER
                .comment("Refund ratio when unclaiming, from 0.0 to 1.0.")
                .defineInRange("unclaimRefundRatio", 0.75D, 0.0D, 1.0D);
        WARN_ABOUT_ECONOMY_DEFAULTS_ON_FIRST_RUN = BUILDER
                .comment("Logs a warning when economy defaults may affect an existing server.")
                .define("warnAboutEconomyDefaultsOnFirstRun", true);
        BUILDER.pop();

        BUILDER.push("mapCache");
        MAP_CACHE_MAX_SIZE_MIB = BUILDER
                .comment("Maximum file-backed terrain tile cache size in MiB.")
                .defineInRange("maxSizeMiB", 1024, 16, 1_048_576);
        MAP_CACHE_MAX_TILE_AGE_DAYS = BUILDER
                .comment("Maximum age for cached map tiles before pruning or regeneration.")
                .defineInRange("maxTileAgeDays", 30, 1, 36_500);
        MAP_MAX_TILE_REQUESTS_PER_PLAYER = BUILDER
                .comment("Maximum queued tile requests per player.")
                .defineInRange("maxTileRequestsPerPlayer", 64, 1, 10_000);
        MAP_MAX_GLOBAL_TILE_JOBS = BUILDER
                .comment("Maximum global tile generation/streaming jobs.")
                .defineInRange("maxGlobalTileJobs", 512, 1, 1_000_000);
        BUILDER.pop();

        BUILDER.push("audit");
        AUDIT_ENABLED = BUILDER
                .comment("Whether UCS records admin, economy, and claim-management audit entries.")
                .define("enabled", true);
        AUDIT_MAX_ENTRIES_PER_CLAIM = BUILDER
                .comment("Maximum audit entries retained per claim.")
                .defineInRange("maxEntriesPerClaim", 250, 1, 1_000_000);
        AUDIT_RETENTION_DAYS = BUILDER
                .comment("Audit retention in days. Use a high value if permanent records are desired.")
                .defineInRange("retentionDays", 180, 1, 36_500);
        BUILDER.pop();

        BUILDER.push("inactivePurge");
        INACTIVE_PURGE_ENABLED = BUILDER
                .comment("Whether inactive claims can be purged automatically. Disabled by default.")
                .define("enabled", false);
        INACTIVE_PURGE_AFTER_DAYS = BUILDER
                .comment("Days of owner inactivity before purge candidates are reported.")
                .defineInRange("afterDays", 90, 1, 36_500);
        ARCHIVE_BEFORE_DELETE = BUILDER
                .comment("Whether UCS archives claims before destructive cleanup.")
                .define("archiveBeforeDelete", true);
        BUILDER.pop();

        BUILDER.push("commands");
        PERMISSION_NODE_PREFIX = BUILDER
                .comment("Global NeoForge permission node prefix for UCS admin/bypass commands.")
                .define("permissionNodePrefix", UcsConfigDefaults.PERMISSION_NODE_PREFIX, UcsConfigValidators::isSimpleKey);
        OP_FALLBACK_ENABLED = BUILDER
                .comment("Whether OP fallback is enabled when no permission manager is present.")
                .define("opFallbackEnabled", UcsConfigDefaults.OP_FALLBACK_ENABLED);
        BUILDER.pop();

        BUILDER.push("messages");
        DEFAULT_LOCALE = BUILDER
                .comment("Default locale used for generated messages and docs references.")
                .define("defaultLocale", "en_us", UcsConfigValidators::isLocaleKey);
        SEND_ACTION_BAR_DENIALS = BUILDER
                .comment("Whether protection denials should be eligible for action-bar messages.")
                .define("sendActionBarDenials", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private UcsCommonConfig() {
    }

    public static UcsConfigSnapshot snapshot() {
        return new UcsConfigSnapshot(
                CONFIG_SCHEMA_VERSION.get(),
                LOG_STARTUP_SUMMARY.get(),
                new UcsConfigSnapshot.DimensionPolicy(
                        List.copyOf(ENABLED_DIMENSIONS.get()),
                        List.copyOf(DISABLED_DIMENSIONS.get()),
                        DISABLE_TEMPORARY_DIMENSIONS_BY_DEFAULT.get()
                ),
                new UcsConfigSnapshot.ClaimLimitPolicy(
                        MAX_CLAIMS_PER_PLAYER.get(),
                        MAX_CHUNKS_PER_PLAYER.get(),
                        MAX_CHUNKS_PER_CLAIM.get(),
                        MAX_RADIUS_CLAIM.get(),
                        REQUIRE_CONNECTED_CLAIMS.get()
                ),
                new UcsConfigSnapshot.RoleDefaults(List.copyOf(DEFAULT_ROLE_IDS.get())),
                new UcsConfigSnapshot.FlagDefaults(List.copyOf(DEFAULT_PROTECTION_FLAG_IDS.get())),
                new UcsConfigSnapshot.EconomyPolicy(
                        ENABLE_ECONOMY_WHEN_PROVIDER_EXISTS.get(),
                        STARTER_CLAIM_PRICE.get(),
                        PRICE_PER_EXTRA_CHUNK.get(),
                        UNCLAIM_REFUND_RATIO.get(),
                        WARN_ABOUT_ECONOMY_DEFAULTS_ON_FIRST_RUN.get()
                ),
                new UcsConfigSnapshot.MapCachePolicy(
                        MAP_CACHE_MAX_SIZE_MIB.get(),
                        MAP_CACHE_MAX_TILE_AGE_DAYS.get(),
                        MAP_MAX_TILE_REQUESTS_PER_PLAYER.get(),
                        MAP_MAX_GLOBAL_TILE_JOBS.get()
                ),
                new UcsConfigSnapshot.AuditPolicy(
                        AUDIT_ENABLED.get(),
                        AUDIT_MAX_ENTRIES_PER_CLAIM.get(),
                        AUDIT_RETENTION_DAYS.get()
                ),
                new UcsConfigSnapshot.InactivePurgePolicy(
                        INACTIVE_PURGE_ENABLED.get(),
                        INACTIVE_PURGE_AFTER_DAYS.get(),
                        ARCHIVE_BEFORE_DELETE.get()
                ),
                new UcsConfigSnapshot.CommandPolicy(PERMISSION_NODE_PREFIX.get(), OP_FALLBACK_ENABLED.get()),
                new UcsConfigSnapshot.MessagePolicy(DEFAULT_LOCALE.get(), SEND_ACTION_BAR_DENIALS.get())
        );
    }
}
