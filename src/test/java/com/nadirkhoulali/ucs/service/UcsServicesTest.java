package com.nadirkhoulali.ucs.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UcsServicesTest {
    @Test
    void summaryIdentifiesBootstrapState() {
        assertEquals("bootstrap, permissions=6", new UcsServices().summary());
    }
}
