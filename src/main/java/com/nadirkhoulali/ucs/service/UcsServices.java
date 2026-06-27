package com.nadirkhoulali.ucs.service;

import com.nadirkhoulali.ucs.api.UcsApiProvider;
import com.nadirkhoulali.ucs.api.internal.DefaultUcsApiAccess;
import com.nadirkhoulali.ucs.storage.ClaimRepository;
import com.nadirkhoulali.ucs.storage.SavedDataClaimRepository;
import com.nadirkhoulali.ucs.storage.UcsClaimsSavedData;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;

public final class UcsServices {
    private ClaimRepository claimRepository;

    public synchronized ClaimRepository initializeClaimRepository(MinecraftServer server) {
        UcsClaimsSavedData savedData = server.overworld()
                .getDataStorage()
                .computeIfAbsent(UcsClaimsSavedData.factory(), UcsClaimsSavedData.DATA_NAME);
        this.claimRepository = new SavedDataClaimRepository(savedData);
        UcsApiProvider.setActiveAccess(new DefaultUcsApiAccess(claimRepository));
        return claimRepository;
    }

    public synchronized Optional<ClaimRepository> claimRepository() {
        return Optional.ofNullable(claimRepository);
    }

    public synchronized void clearServerState() {
        this.claimRepository = null;
        UcsApiProvider.clearActiveAccess();
    }

    public synchronized String summary() {
        return claimRepository == null
                ? "bootstrap"
                : "claims=" + claimRepository.claims().size() + ", archives=" + claimRepository.archives().size();
    }
}
