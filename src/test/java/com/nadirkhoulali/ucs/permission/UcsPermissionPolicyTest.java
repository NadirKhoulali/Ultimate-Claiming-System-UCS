package com.nadirkhoulali.ucs.permission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UcsPermissionPolicyTest {
    @Test
    void publicPermissionsDoNotRequireOpFallback() {
        assertTrue(UcsPermissionPolicy.resolveDefault(UcsPermission.MAP_VIEW, false, false));
    }

    @Test
    void adminPermissionsUseConfiguredOpFallback() {
        assertTrue(UcsPermissionPolicy.resolveDefault(UcsPermission.ADMIN, true, true));
        assertFalse(UcsPermissionPolicy.resolveDefault(UcsPermission.ADMIN, true, false));
        assertFalse(UcsPermissionPolicy.resolveDefault(UcsPermission.ADMIN, false, true));
    }

    @Test
    void bypassDecisionMarksAuditCandidate() {
        UcsPermissionDecision decision = new UcsPermissionDecision(
                UcsPermission.BYPASS,
                "ucs.bypass",
                true,
                UcsPermissionDecision.Source.NEOFORGE_HANDLER
        );

        assertTrue(decision.shouldAuditUse());
    }
}
