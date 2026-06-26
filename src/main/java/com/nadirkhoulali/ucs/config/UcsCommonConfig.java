package com.nadirkhoulali.ucs.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class UcsCommonConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue CONFIG_SCHEMA_VERSION = BUILDER
            .comment("Internal UCS common config schema version. Future migrations will use this value.")
            .defineInRange("schemaVersion", 1, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.BooleanValue LOG_STARTUP_SUMMARY = BUILDER
            .comment("Whether UCS logs a short startup summary when the dedicated or integrated server starts.")
            .define("logStartupSummary", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private UcsCommonConfig() {
    }
}
