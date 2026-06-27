package com.nadirkhoulali.ucs.api.protection;

import com.nadirkhoulali.ucs.core.model.FlagId;
import com.nadirkhoulali.ucs.core.model.RoleId;

import java.util.List;
import java.util.Set;

public final class UcsBuiltInProtectionFlags {
    public static final RoleId OWNER = new RoleId("owner");
    public static final RoleId MEMBER = new RoleId("member");
    public static final RoleId TENANT = new RoleId("tenant");

    public static final FlagId BLOCK_BREAK = new FlagId("ucs:block_break");
    public static final FlagId BLOCK_PLACE = new FlagId("ucs:block_place");
    public static final FlagId SPECIAL_BLOCK_USE = new FlagId("ucs:special_block_use");
    public static final FlagId CONTAINER_OPEN = new FlagId("ucs:container_open");
    public static final FlagId DOOR_USE = new FlagId("ucs:door_use");
    public static final FlagId BUTTON_USE = new FlagId("ucs:button_use");
    public static final FlagId LEVER_USE = new FlagId("ucs:lever_use");
    public static final FlagId REDSTONE_USE = new FlagId("ucs:redstone_use");
    public static final FlagId ENTITY_INTERACT = new FlagId("ucs:entity_interact");
    public static final FlagId ENTITY_DAMAGE = new FlagId("ucs:entity_damage");
    public static final FlagId VEHICLE_USE = new FlagId("ucs:vehicle_use");
    public static final FlagId ITEM_PICKUP = new FlagId("ucs:item_pickup");
    public static final FlagId ITEM_DROP = new FlagId("ucs:item_drop");
    public static final FlagId PVP = new FlagId("ucs:pvp");
    public static final FlagId EXPLOSION = new FlagId("ucs:explosion");
    public static final FlagId FIRE_SPREAD = new FlagId("ucs:fire_spread");
    public static final FlagId LIQUID_FLOW = new FlagId("ucs:liquid_flow");
    public static final FlagId MOB_GRIEFING = new FlagId("ucs:mob_griefing");
    public static final FlagId MOB_SPAWN = new FlagId("ucs:mob_spawn");
    public static final FlagId WEATHER_CHANGE = new FlagId("ucs:weather_change");
    public static final FlagId PORTAL_USE = new FlagId("ucs:portal_use");
    public static final FlagId TELEPORT = new FlagId("ucs:teleport");
    public static final FlagId FLY = new FlagId("ucs:fly");
    public static final FlagId ELYTRA = new FlagId("ucs:elytra");
    public static final FlagId WIND_CHARGE = new FlagId("ucs:wind_charge");
    public static final FlagId ENTRY = new FlagId("ucs:entry");
    public static final FlagId EXPEL = new FlagId("ucs:expel");

    private static final Set<RoleId> TRUSTED_ROLES = Set.of(OWNER, MEMBER, TENANT);

    private UcsBuiltInProtectionFlags() {
    }

    public static List<ProtectionFlagDefinition> definitions() {
        return List.of(
                playerFlag(BLOCK_BREAK, "Block Break", ProtectionFlagCategory.BLOCKS),
                playerFlag(BLOCK_PLACE, "Block Place", ProtectionFlagCategory.BLOCKS),
                playerFlag(SPECIAL_BLOCK_USE, "Special Block Use", ProtectionFlagCategory.INTERACTIONS),
                playerFlag(CONTAINER_OPEN, "Container Open", ProtectionFlagCategory.CONTAINERS),
                playerFlag(DOOR_USE, "Door Use", ProtectionFlagCategory.INTERACTIONS),
                playerFlag(BUTTON_USE, "Button Use", ProtectionFlagCategory.REDSTONE),
                playerFlag(LEVER_USE, "Lever Use", ProtectionFlagCategory.REDSTONE),
                playerFlag(REDSTONE_USE, "Redstone Use", ProtectionFlagCategory.REDSTONE),
                playerFlag(ENTITY_INTERACT, "Entity Interact", ProtectionFlagCategory.ENTITIES),
                playerFlag(ENTITY_DAMAGE, "Entity Damage", ProtectionFlagCategory.ENTITIES),
                playerFlag(VEHICLE_USE, "Vehicle Use", ProtectionFlagCategory.ENTITIES),
                playerFlag(ITEM_PICKUP, "Item Pickup", ProtectionFlagCategory.ITEMS),
                playerFlag(ITEM_DROP, "Item Drop", ProtectionFlagCategory.ITEMS),
                playerFlag(PVP, "PvP", ProtectionFlagCategory.COMBAT),
                naturalFlag(EXPLOSION, "Explosion", ProtectionFlagCategory.ENVIRONMENT),
                naturalFlag(FIRE_SPREAD, "Fire Spread", ProtectionFlagCategory.ENVIRONMENT),
                naturalFlag(LIQUID_FLOW, "Liquid Flow", ProtectionFlagCategory.ENVIRONMENT),
                naturalFlag(MOB_GRIEFING, "Mob Griefing", ProtectionFlagCategory.MOBS),
                naturalFlag(MOB_SPAWN, "Mob Spawn", ProtectionFlagCategory.MOBS),
                naturalFlag(WEATHER_CHANGE, "Weather Change", ProtectionFlagCategory.ENVIRONMENT),
                playerFlag(PORTAL_USE, "Portal Use", ProtectionFlagCategory.MOVEMENT),
                playerFlag(TELEPORT, "Teleport", ProtectionFlagCategory.MOVEMENT),
                playerFlag(FLY, "Fly", ProtectionFlagCategory.MOVEMENT),
                playerFlag(ELYTRA, "Elytra", ProtectionFlagCategory.MOVEMENT),
                playerFlag(WIND_CHARGE, "Wind Charge", ProtectionFlagCategory.COMBAT),
                playerFlag(ENTRY, "Entry", ProtectionFlagCategory.MOVEMENT),
                playerFlag(EXPEL, "Expel", ProtectionFlagCategory.MOVEMENT)
        );
    }

    private static ProtectionFlagDefinition playerFlag(FlagId id, String displayName, ProtectionFlagCategory category) {
        return new ProtectionFlagDefinition(id, displayName, category, ProtectionDecisionType.DENY, TRUSTED_ROLES, true);
    }

    private static ProtectionFlagDefinition naturalFlag(FlagId id, String displayName, ProtectionFlagCategory category) {
        return new ProtectionFlagDefinition(id, displayName, category, ProtectionDecisionType.DENY, Set.of(), false);
    }
}
