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
        ClaimTaxPolicy claimTax,
        NonpaymentPolicy nonpayment,
        MapCachePolicy mapCache,
        MapOverlayPolicy mapOverlay,
        AuditPolicy audit,
        ArchivePolicy archive,
        InactivePurgePolicy inactivePurge,
        CommandPolicy commands,
        MessagePolicy messages
) {
    public UcsConfigSnapshot(
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
            ClaimTaxPolicy claimTax,
            MapCachePolicy mapCache,
            AuditPolicy audit,
            ArchivePolicy archive,
            InactivePurgePolicy inactivePurge,
            CommandPolicy commands,
            MessagePolicy messages
    ) {
        this(
                schemaVersion,
                logStartupSummary,
                dimensions,
                claimLimits,
                claimMetadata,
                claimTeleport,
                roles,
                bans,
                flags,
                protection,
                economy,
                claimTax,
                NonpaymentPolicy.defaults(),
                mapCache,
                MapOverlayPolicy.defaults(),
                audit,
                archive,
                inactivePurge,
                commands,
                messages
        );
    }

    public UcsConfigSnapshot(
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
            ClaimTaxPolicy claimTax,
            NonpaymentPolicy nonpayment,
            MapCachePolicy mapCache,
            AuditPolicy audit,
            ArchivePolicy archive,
            InactivePurgePolicy inactivePurge,
            CommandPolicy commands,
            MessagePolicy messages
    ) {
        this(
                schemaVersion,
                logStartupSummary,
                dimensions,
                claimLimits,
                claimMetadata,
                claimTeleport,
                roles,
                bans,
                flags,
                protection,
                economy,
                claimTax,
                nonpayment,
                mapCache,
                MapOverlayPolicy.defaults(),
                audit,
                archive,
                inactivePurge,
                commands,
                messages
        );
    }

    public UcsConfigSnapshot(
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
        this(
                schemaVersion,
                logStartupSummary,
                dimensions,
                claimLimits,
                claimMetadata,
                claimTeleport,
                roles,
                bans,
                flags,
                protection,
                economy,
                ClaimTaxPolicy.defaults(),
                NonpaymentPolicy.defaults(),
                mapCache,
                MapOverlayPolicy.defaults(),
                audit,
                archive,
                inactivePurge,
                commands,
                messages
        );
    }

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
            List<String> specialBlockIds,
            List<String> containerTargetIds,
            List<String> doorTargetIds,
            List<String> buttonTargetIds,
            List<String> leverTargetIds,
            List<String> redstoneTargetIds,
            List<String> entityTargetIds,
            List<String> vehicleTargetIds
    ) {
        public ProtectionPolicy(List<String> ignoredBlockIds, List<String> allowedBlockIds, List<String> specialBlockIds) {
            this(
                    ignoredBlockIds,
                    allowedBlockIds,
                    specialBlockIds,
                    UcsConfigDefaults.DEFAULT_CONTAINER_TARGET_IDS,
                    UcsConfigDefaults.DEFAULT_DOOR_TARGET_IDS,
                    UcsConfigDefaults.DEFAULT_BUTTON_TARGET_IDS,
                    UcsConfigDefaults.DEFAULT_LEVER_TARGET_IDS,
                    UcsConfigDefaults.DEFAULT_REDSTONE_TARGET_IDS,
                    UcsConfigDefaults.DEFAULT_ENTITY_TARGET_IDS,
                    UcsConfigDefaults.DEFAULT_VEHICLE_TARGET_IDS
            );
        }

        public ProtectionPolicy {
            ignoredBlockIds = List.copyOf(ignoredBlockIds);
            allowedBlockIds = List.copyOf(allowedBlockIds);
            specialBlockIds = List.copyOf(specialBlockIds);
            containerTargetIds = List.copyOf(containerTargetIds);
            doorTargetIds = List.copyOf(doorTargetIds);
            buttonTargetIds = List.copyOf(buttonTargetIds);
            leverTargetIds = List.copyOf(leverTargetIds);
            redstoneTargetIds = List.copyOf(redstoneTargetIds);
            entityTargetIds = List.copyOf(entityTargetIds);
            vehicleTargetIds = List.copyOf(vehicleTargetIds);
        }
    }

    public record EconomyPolicy(
            boolean enableWhenProviderExists,
            double starterClaimPrice,
            double pricePerExtraChunk,
            double unclaimRefundRatio,
            double maxClaimSalePrice,
            boolean warnAboutDefaultsOnFirstRun
    ) {
    }

    public record ClaimTaxPolicy(
            boolean enabled,
            int intervalHours,
            int initialDelayHours,
            double baseAmount,
            double perChunkAmount,
            int maxClaimsPerTick,
            int warningHoursBeforeDue
    ) {
        public static ClaimTaxPolicy defaults() {
            return new ClaimTaxPolicy(false, 168, 168, 0.0D, 0.0D, 64, 24);
        }
    }

    public record NonpaymentPolicy(
            int graceHours,
            int retryIntervalHours,
            int warningIntervalHours,
            boolean archiveAfterGrace,
            boolean requireDebtPaidBeforeRestore,
            int maxClaimsPerTick
    ) {
        public static NonpaymentPolicy defaults() {
            return new NonpaymentPolicy(72, 24, 24, true, false, 64);
        }
    }

    public record MapCachePolicy(
            int maxSizeMiB,
            int maxTileAgeDays,
            int maxTileRequestsPerPlayer,
            int maxGlobalTileJobs
    ) {
    }

    public record MapOverlayPolicy(
            int ownerColor,
            int memberColor,
            int tenantColor,
            int visitorColor,
            int bannedColor,
            int serverColor,
            int borderColor,
            int saleAccentColor,
            int leaseAccentColor
    ) {
        public static MapOverlayPolicy defaults() {
            return new MapOverlayPolicy(
                    UcsConfigDefaults.MAP_OVERLAY_OWNER_COLOR,
                    UcsConfigDefaults.MAP_OVERLAY_MEMBER_COLOR,
                    UcsConfigDefaults.MAP_OVERLAY_TENANT_COLOR,
                    UcsConfigDefaults.MAP_OVERLAY_VISITOR_COLOR,
                    UcsConfigDefaults.MAP_OVERLAY_BANNED_COLOR,
                    UcsConfigDefaults.MAP_OVERLAY_SERVER_COLOR,
                    UcsConfigDefaults.MAP_OVERLAY_BORDER_COLOR,
                    UcsConfigDefaults.MAP_OVERLAY_SALE_ACCENT_COLOR,
                    UcsConfigDefaults.MAP_OVERLAY_LEASE_ACCENT_COLOR
            );
        }
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
