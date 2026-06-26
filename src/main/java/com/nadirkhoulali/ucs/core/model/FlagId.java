package com.nadirkhoulali.ucs.core.model;

public record FlagId(String value) {
    public FlagId {
        value = IdentifierRules.requireResourceId(value, "value");
    }
}
