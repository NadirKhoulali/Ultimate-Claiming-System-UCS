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
            "ucs:special_block_use",
            "ucs:container_open",
            "ucs:door_use",
            "ucs:button_use",
            "ucs:lever_use",
            "ucs:redstone_use",
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

    public static final List<String> DEFAULT_CONTAINER_TARGET_IDS = List.of(
            "minecraft:chest",
            "minecraft:trapped_chest",
            "minecraft:barrel",
            "minecraft:ender_chest",
            "#minecraft:shulker_boxes",
            "minecraft:furnace",
            "minecraft:blast_furnace",
            "minecraft:smoker",
            "minecraft:brewing_stand",
            "minecraft:hopper",
            "minecraft:dispenser",
            "minecraft:dropper",
            "minecraft:crafter",
            "minecraft:jukebox",
            "minecraft:lectern"
    );

    public static final List<String> DEFAULT_DOOR_TARGET_IDS = List.of(
            "#minecraft:doors",
            "#minecraft:trapdoors",
            "#minecraft:fence_gates"
    );

    public static final List<String> DEFAULT_BUTTON_TARGET_IDS = List.of(
            "#minecraft:buttons",
            "#minecraft:pressure_plates",
            "minecraft:bell",
            "minecraft:tripwire",
            "minecraft:tripwire_hook"
    );

    public static final List<String> DEFAULT_LEVER_TARGET_IDS = List.of(
            "minecraft:lever"
    );

    public static final List<String> DEFAULT_REDSTONE_TARGET_IDS = List.of(
            "minecraft:redstone_wire",
            "minecraft:repeater",
            "minecraft:comparator",
            "minecraft:observer",
            "minecraft:daylight_detector",
            "minecraft:calibrated_sculk_sensor",
            "minecraft:sculk_sensor",
            "minecraft:target",
            "minecraft:piston",
            "minecraft:sticky_piston",
            "minecraft:dispenser",
            "minecraft:dropper",
            "minecraft:hopper",
            "minecraft:note_block",
            "minecraft:tripwire",
            "minecraft:tripwire_hook"
    );

    private UcsConfigDefaults() {
    }
}
