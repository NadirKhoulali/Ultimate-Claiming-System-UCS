package com.nadirkhoulali.ucs.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record UcsConfigSnapshot(
        int schemaVersion,
        boolean logStartupSummary,
        DimensionPolicy dimensions,
        ClaimLimitPolicy claimLimits,
        ClaimMetadataPolicy claimMetadata,
        ClaimTeleportPolicy claimTeleport,
        RoleDefaults roles,
        BanPolicy bans,
        FlagDefaults flags,
        ProtectionPolicy protection,
        EconomyPolicy economy,
        MapCachePolicy mapCache,
        AuditPolicy audit,
        ArchivePolicy archive,
        InactivePurgePolicy inactivePurge,
        CommandPolicy commands,
        MessagePolicy messages
) {
    public UcsConfigValidationReport validate() {
        return UcsConfigValidators.validate(this);
    }

    public record DimensionPolicy(
            List<String> enabledDimensions,
            List<String> disabledDimensions,
            boolean disableTemporaryDimensionsByDefault
    ) {
        public DimensionPolicy {
            enabledDimensions = List.copyOf(enabledDimensions);
            disabledDimensions = List.copyOf(disabledDimensions);
        }
    }

    public record ClaimLimitPolicy(
            int maxClaimsPerPlayer,
            int maxChunksPerPlayer,
            int maxChunksPerClaim,
            int maxRadiusClaim,
            boolean requireConnectedClaims
    ) {
    }

    public record ClaimMetadataPolicy(
            int maxNameLength,
            int maxDescriptionLength
    ) {
    }

    public record ClaimTeleportPolicy(
            int delaySeconds,
            boolean cancelOnMove,
            boolean requireSafeLanding
    ) {
    }

    public record RoleDefaults(
            List<String> defaultRoleIds,
            String defaultTrustRoleId,
            String bannedRoleId,
            boolean requireInviteAcceptance
    ) {
        public RoleDefaults {
            defaultTrustRoleId = Objects.requireNonNull(defaultTrustRoleId, "defaultTrustRoleId").trim();
            bannedRoleId = Objects.requireNonNull(bannedRoleId, "bannedRoleId").trim();
            Set<String> mergedRoleIds = new LinkedHashSet<>(defaultRoleIds);
            mergedRoleIds.add(defaultTrustRoleId);
            mergedRoleIds.add(bannedRoleId);
            defaultRoleIds = List.copyOf(mergedRoleIds);
        }
    }

    public record BanPolicy(
            boolean preventEntry,
            int expulsionSearchRadiusBlocks,
            int expulsionCooldownTicks
    ) {
    }

    public record FlagDefaults(List<String> defaultProtectionFlagIds) {
        public FlagDefaults {
            defaultProtectionFlagIds = List.copyOf(defaultProtectionFlagIds);
        }
    }

    public record ProtectionPolicy(
            List<String> ignoredBlockIds,
            List<String> allowedBlockIds,
            List<String> specialBlockIds
    ) {
        public ProtectionPolicy {
            ignoredBlockIds = List.copyOf(ignoredBlockIds);
            allowedBlockIds = List.copyOf(allowedBlockIds);
            specialBlockIds = List.copyOf(specialBlockIds);
        }
    }

    public record EconomyPolicy(
            boolean enableWhenProviderExists,
            double starterClaimPrice,
            double pricePerExtraChunk,
            double unclaimRefundRatio,
            boolean warnAboutDefaultsOnFirstRun
    ) {
    }

    public record MapCachePolicy(
            int maxSizeMiB,
            int maxTileAgeDays,
            int maxTileRequestsPerPlayer,
            int maxGlobalTileJobs
    ) {
    }

    public record AuditPolicy(
            boolean enabled,
            int maxEntriesPerClaim,
            int retentionDays
    ) {
    }

    public record ArchivePolicy(
            int retentionDays
    ) {
    }

    public record InactivePurgePolicy(
            boolean enabled,
            int afterDays,
            boolean archiveBeforeDelete
    ) {
    }

    public record CommandPolicy(
            String permissionNodePrefix,
            boolean opFallbackEnabled
    ) {
    }

    public record MessagePolicy(
            String defaultLocale,
            boolean sendActionBarDenials
    ) {
    }
}
