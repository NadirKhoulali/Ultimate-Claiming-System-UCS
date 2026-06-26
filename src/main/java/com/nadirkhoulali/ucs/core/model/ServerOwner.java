package com.nadirkhoulali.ucs.core.model;

public record ServerOwner(String namespace) implements OwnerRef {
    public ServerOwner {
        namespace = IdentifierRules.requireSimpleKey(namespace, "namespace");
    }

    @Override
    public OwnerType type() {
        return OwnerType.SERVER;
    }

    @Override
    public String stableKey() {
        return "server:" + namespace;
    }
}
