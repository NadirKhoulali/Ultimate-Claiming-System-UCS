package com.nadirkhoulali.ucs.core.model;

public record RoleId(String value) {
    public RoleId {
        value = IdentifierRules.requireSimpleKey(value, "value");
    }
}
