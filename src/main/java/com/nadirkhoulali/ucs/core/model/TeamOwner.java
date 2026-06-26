package com.nadirkhoulali.ucs.core.model;

public record TeamOwner(String teamId) implements OwnerRef {
    public TeamOwner {
        teamId = IdentifierRules.requireSimpleKey(teamId, "teamId");
    }

    @Override
    public OwnerType type() {
        return OwnerType.TEAM;
    }

    @Override
    public String stableKey() {
        return "team:" + teamId;
    }
}
