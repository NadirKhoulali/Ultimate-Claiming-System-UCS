package com.nadirkhoulali.ucs.config;

import java.util.List;

public final class UcsConfigDefaults {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String PERMISSION_NODE_PREFIX = "ucs";
    public static final boolean OP_FALLBACK_ENABLED = true;

    public static final List<String> ENABLED_DIMENSIONS = List.of(
            "minecraft:overworld",
            "minecraft:the_nether",
            "minecraft:the_end"
    );

    public static final List<String> DISABLED_DIMENSIONS = List.of();

    public static final List<String> DEFAULT_ROLE_IDS = List.of(
            "owner",
            "member",
            "tenant",
            "visitor",
            "banned"
    );

    public static final List<String> DEFAULT_PROTECTION_FLAG_IDS = List.of(
            "ucs:block_break",
            "ucs:block_place",
            "ucs:container_open",
            "ucs:entity_interact",
            "ucs:pvp",
            "ucs:explosion",
            "ucs:fire_spread",
            "ucs:liquid_flow"
    );

    public static final List<String> DEFAULT_SPECIAL_BLOCK_IDS = List.of(
            "minecraft:beacon",
            "minecraft:conduit",
            "minecraft:dragon_egg",
            "minecraft:ender_chest",
            "minecraft:respawn_anchor",
            "minecraft:spawner",
            "minecraft:vault"
    );

    private UcsConfigDefaults() {
    }
}
