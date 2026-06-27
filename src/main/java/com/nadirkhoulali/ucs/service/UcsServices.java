package com.nadirkhoulali.ucs.service;

import com.nadirkhoulali.ucs.api.UcsClaimService;
import com.nadirkhoulali.ucs.api.UcsApiProvider;
import com.nadirkhoulali.ucs.api.economy.ClaimEconomyProviderRegistry;
import com.nadirkhoulali.ucs.api.internal.DefaultUcsClaimService;
import com.nadirkhoulali.ucs.api.internal.DefaultUcsApiAccess;
import com.nadirkhoulali.ucs.api.protection.ProtectionFlagRegistry;
import com.nadirkhoulali.ucs.claim.ClaimChunkEditService;
import com.nadirkhoulali.ucs.claim.ClaimCreationService;
import com.nadirkhoulali.ucs.claim.ClaimExpulsionService;
import com.nadirkhoulali.ucs.claim.ClaimMetadataService;
import com.nadirkhoulali.ucs.claim.ClaimRoleService;
import com.nadirkhoulali.ucs.claim.ClaimSaleService;
import com.nadirkhoulali.ucs.claim.ClaimTeleportService;
import com.nadirkhoulali.ucs.economy.DefaultClaimEconomyProviderRegistry;
import com.nadirkhoulali.ucs.permission.UcsPermissionNodes;
import com.nadirkhoulali.ucs.permission.UcsPermissionService;
import com.nadirkhoulali.ucs.protection.DefaultProtectionFlagRegistry;
import com.nadirkhoulali.ucs.protection.ClaimProtectionService;
import com.nadirkhoulali.ucs.protection.ClaimMovementService;
import com.nadirkhoulali.ucs.protection.ProtectionAdminService;
import com.nadirkhoulali.ucs.storage.ClaimRepository;
import com.nadirkhoulali.ucs.storage.SavedDataClaimRepository;
import com.nadirkhoulali.ucs.storage.UcsClaimsSavedData;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;

public final class UcsServices {
    private final UcsPermissionService permissionService = new UcsPermissionService();
    private final ProtectionAdminService protectionAdminService = new ProtectionAdminService();
    private final ClaimCreationService claimCreationService = new ClaimCreationService();
    private final ClaimChunkEditService claimChunkEditService = new ClaimChunkEditService();
    private final ClaimExpulsionService claimExpulsionService = new ClaimExpulsionService();
    private final ClaimMetadataService claimMetadataService = new ClaimMetadataService();
    private final ClaimRoleService claimRoleService = new ClaimRoleService();
    private final ClaimSaleService claimSaleService = new ClaimSaleService();
    private final ClaimTeleportService claimTeleportService = new ClaimTeleportService();
    private final ClaimProtectionService claimProtectionService = new ClaimProtectionService(protectionAdminService, permissionService);
    private final ClaimMovementService claimMovementService = new ClaimMovementService();
    private final ProtectionFlagRegistry protectionFlags = DefaultProtectionFlagRegistry.withBuiltIns();
    private final ClaimEconomyProviderRegistry economyProviders = DefaultClaimEconomyProviderRegistry.withBuiltIns();
    private ClaimRepository claimRepository;
    private UcsClaimService claimService;

    public synchronized ClaimRepository initializeClaimRepository(MinecraftServer server) {
        UcsClaimsSavedData savedData = server.overworld()
                .getDataStorage()
                .computeIfAbsent(UcsClaimsSavedData.factory(), UcsClaimsSavedData.DATA_NAME);
        this.claimRepository = new SavedDataClaimRepository(savedData);
        this.claimService = new DefaultUcsClaimService(claimRepository);
        UcsApiProvider.setActiveAccess(new DefaultUcsApiAccess(claimService, protectionFlags, economyProviders));
        return claimRepository;
    }

    public synchronized Optional<ClaimRepository> claimRepository() {
        return Optional.ofNullable(claimRepository);
    }

    public synchronized Optional<UcsClaimService> claimService() {
        return Optional.ofNullable(claimService);
    }

    public UcsPermissionService permissions() {
        return permissionService;
    }

    public ClaimCreationService claimCreation() {
        return claimCreationService;
    }

    public ClaimChunkEditService claimChunkEdit() {
        return claimChunkEditService;
    }

    public ClaimExpulsionService claimExpulsion() {
        return claimExpulsionService;
    }

    public ClaimMetadataService claimMetadata() {
        return claimMetadataService;
    }

    public ClaimRoleService claimRoles() {
        return claimRoleService;
    }

    public ClaimSaleService claimSales() {
        return claimSaleService;
    }

    public ClaimTeleportService claimTeleport() {
        return claimTeleportService;
    }

    public ProtectionFlagRegistry protectionFlags() {
        return protectionFlags;
    }

    public ClaimProtectionService claimProtection() {
        return claimProtectionService;
    }

    public ProtectionAdminService protectionAdmin() {
        return protectionAdminService;
    }

    public ClaimMovementService claimMovement() {
        return claimMovementService;
    }

    public ClaimEconomyProviderRegistry economyProviders() {
        return economyProviders;
    }

    public synchronized void clearServerState() {
        claimTeleportService.clear();
        claimExpulsionService.clear();
        claimMovementService.clear();
        protectionAdminService.clear();
        this.claimRepository = null;
        this.claimService = null;
        UcsApiProvider.clearActiveAccess();
    }

    public synchronized String summary() {
        String permissionsSummary = ", permissions=" + UcsPermissionNodes.count();
        return claimRepository == null
                ? "bootstrap" + permissionsSummary
                : "claims=" + claimRepository.claims().size()
                + ", archives=" + claimRepository.archives().size()
                + permissionsSummary;
    }
}
