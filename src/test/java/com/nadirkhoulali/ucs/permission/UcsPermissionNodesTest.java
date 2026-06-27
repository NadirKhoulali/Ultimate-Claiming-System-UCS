package com.nadirkhoulali.ucs.permission;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UcsPermissionNodesTest {
    @Test
    void registersExpectedDefaultNodeNames() {
        Set<String> nodeNames = UcsPermissionNodes.nodes().stream()
                .map(node -> node.getNodeName())
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                "ucs.admin",
                "ucs.bypass",
                "ucs.map.view",
                "ucs.economy.admin",
                "ucs.archive.restore",
                "ucs.debug"
        ), nodeNames);
    }

    @Test
    void exposesNodeForEveryPermission() {
        assertEquals(UcsPermission.values().length, UcsPermissionNodes.count());
    }
}
