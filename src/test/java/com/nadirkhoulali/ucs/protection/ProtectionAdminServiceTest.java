package com.nadirkhoulali.ucs.protection;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionAdminServiceTest {
    @Test
    void throttlesDenialsPerPlayerAndAction() {
        ProtectionAdminService service = new ProtectionAdminService();
        UUID player = UUID.randomUUID();

        assertTrue(service.shouldSendDenial(player, "ucs:block_break:role_not_allowed", 100));
        assertFalse(service.shouldSendDenial(player, "ucs:block_break:role_not_allowed", 110));
        assertTrue(service.shouldSendDenial(player, "ucs:container_open:role_not_allowed", 110));
        assertTrue(service.shouldSendDenial(player, "ucs:block_break:role_not_allowed", 121));
    }

    @Test
    void clearPlayerRemovesThrottleState() {
        ProtectionAdminService service = new ProtectionAdminService();
        UUID player = UUID.randomUUID();

        assertTrue(service.shouldSendDenial(player, "ucs:block_break:role_not_allowed", 100));
        service.clearPlayer(player);

        assertTrue(service.shouldSendDenial(player, "ucs:block_break:role_not_allowed", 101));
    }
}
