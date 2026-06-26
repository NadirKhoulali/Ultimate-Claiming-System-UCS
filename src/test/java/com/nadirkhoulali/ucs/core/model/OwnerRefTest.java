package com.nadirkhoulali.ucs.core.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OwnerRefTest {
    @Test
    void playerOwnerUsesUuidStableKey() {
        UUID id = UUID.randomUUID();
        PlayerOwner owner = new PlayerOwner(id, "Nadir");

        assertEquals(OwnerType.PLAYER, owner.type());
        assertEquals("player:" + id, owner.stableKey());
    }

    @Test
    void teamOwnerUsesApiOnlyTeamId() {
        TeamOwner owner = new TeamOwner("builders");

        assertEquals(OwnerType.TEAM, owner.type());
        assertEquals("team:builders", owner.stableKey());
    }

    @Test
    void serverOwnerRejectsBlankNamespace() {
        assertThrows(IllegalArgumentException.class, () -> new ServerOwner(" "));
    }
}
