package com.nadirkhoulali.ucs.permission;

public enum UcsPermission {
    ADMIN("admin", "Admin Commands", "Use UCS administrative commands.", 2, false, true),
    BYPASS("bypass", "Protection Bypass", "Bypass UCS claim protections.", 2, false, true),
    MAP_VIEW("map.view", "Map Claim Visibility", "View UCS claim overlays and map data.", 0, true, false),
    ECONOMY_ADMIN("economy.admin", "Economy Administration", "Manage UCS economy settings and transactions.", 2, false, true),
    ARCHIVE_RESTORE("archive.restore", "Archive Restore", "Restore archived UCS claims.", 2, false, true),
    DEBUG("debug", "Debug Tools", "Use UCS debugging and inspection tools.", 2, false, true);

    private final String path;
    private final String readableName;
    private final String description;
    private final int defaultOpLevel;
    private final boolean publicByDefault;
    private final boolean auditCandidate;

    UcsPermission(
            String path,
            String readableName,
            String description,
            int defaultOpLevel,
            boolean publicByDefault,
            boolean auditCandidate
    ) {
        this.path = path;
        this.readableName = readableName;
        this.description = description;
        this.defaultOpLevel = defaultOpLevel;
        this.publicByDefault = publicByDefault;
        this.auditCandidate = auditCandidate;
    }

    public String path() {
        return path;
    }

    public String readableName() {
        return readableName;
    }

    public String description() {
        return description;
    }

    public int defaultOpLevel() {
        return defaultOpLevel;
    }

    public boolean publicByDefault() {
        return publicByDefault;
    }

    public boolean auditCandidate() {
        return auditCandidate;
    }

    public String nodeName(String prefix) {
        return prefix + "." + path;
    }
}
