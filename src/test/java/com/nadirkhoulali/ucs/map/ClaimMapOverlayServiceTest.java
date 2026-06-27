package com.nadirkhoulali.ucs.map;

import com.nadirkhoulali.ucs.config.UcsConfigDefaults;
import com.nadirkhoulali.ucs.config.UcsConfigSnapshot;
import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimChunk;
import com.nadirkhoulali.ucs.core.model.ClaimId;
import com.nadirkhoulali.ucs.core.model.ClaimMetadata;
import com.nadirkhoulali.ucs.core.model.FlagId;
import com.nadirkhoulali.ucs.core.model.LeaseContract;
import com.nadirkhoulali.ucs.core.model.LeaseId;
import com.nadirkhoulali.ucs.core.model.MapTileKey;
import com.nadirkhoulali.ucs.core.model.OwnerRef;
import com.nadirkhoulali.ucs.core.model.PlayerOwner;
import com.nadirkhoulali.ucs.core.model.RoleId;
import com.nadirkhoulali.ucs.core.model.ServerOwner;
import com.nadirkhoulali.ucs.storage.SavedDataClaimRepository;
import com.nadirkhoulali.ucs.storage.UcsClaimsSavedData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimMapOverlayServiceTest {
    private final ClaimMapOverlayService service = new ClaimMapOverlayService();
    private final UcsConfigSnapshot config = validConfig();

    @Test
    void hidesServerClaimsFromNonStaffOverlayViewers() {
        SavedDataClaimRepository repository = new SavedDataClaimRepository(new UcsClaimsSavedData());
        repository.save(claim("Spawn", new ServerOwner("spawn"), 0, 0, Map.of(), Map.of()));

        List<ClaimMapOverlayEntry> hidden = service.visibleOverlays(
                repository,
                config,
                UUID.randomUUID(),
                "minecraft:overworld",
                List.of(new MapTileKey("minecraft:overworld", 0, 0, 0)),
                false
        );
        List<ClaimMapOverlayEntry> visibleToStaff = service.visibleOverlays(
                repository,
                config,
                UUID.randomUUID(),
                "minecraft:overworld",
                List.of(new MapTileKey("minecraft:overworld", 0, 0, 0)),
                true
        );

        assertTrue(hidden.isEmpty());
        assertEquals(ClaimMapOverlayRelation.SERVER, visibleToStaff.getFirst().relation());
    }

    @Test
    void classifiesOwnerMemberTenantAndBannedRelations() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID banned = UUID.randomUUID();
        SavedDataClaimRepository repository = new SavedDataClaimRepository(new UcsClaimsSavedData());
        repository.save(claim("Owner", new PlayerOwner(owner, "Owner"), 0, 0, Map.of(), Map.of()));
        repository.save(claim("Member", new PlayerOwner(UUID.randomUUID(), "Other"), 1, 0, Map.of(new RoleId("member"), Set.of(member)), Map.of()));
        LeaseContract activeLease = LeaseContract.offer(
                LeaseId.random(),
                ClaimId.random(),
                new PlayerOwner(tenant, "Tenant"),
                new RoleId("tenant"),
                BigDecimal.ONE,
                Duration.ofDays(7),
                Instant.EPOCH
        ).activate(Instant.EPOCH.plusSeconds(1), true);
        repository.save(claim(
                "Tenant",
                new PlayerOwner(UUID.randomUUID(), "Other"),
                2,
                0,
                Map.of(),
                Map.of(activeLease.id(), activeLease)
        ));
        repository.save(claim("Banned", new PlayerOwner(UUID.randomUUID(), "Other"), 3, 0, Map.of(new RoleId("banned"), Set.of(banned)), Map.of()));

        assertEquals(ClaimMapOverlayRelation.OWNER, firstRelation(repository, owner));
        assertEquals(ClaimMapOverlayRelation.MEMBER, firstRelation(repository, member));
        assertEquals(ClaimMapOverlayRelation.TENANT, firstRelation(repository, tenant));
        assertEquals(ClaimMapOverlayRelation.BANNED, firstRelation(repository, banned));
    }

    @Test
    void onlyIncludesChunksCoveredByRequestedTiles() {
        SavedDataClaimRepository repository = new SavedDataClaimRepository(new UcsClaimsSavedData());
        repository.save(claim("Near", new PlayerOwner(UUID.randomUUID(), "Owner"), 0, 0, Map.of(), Map.of()));
        repository.save(claim("Far", new PlayerOwner(UUID.randomUUID(), "Owner"), 32, 32, Map.of(), Map.of()));

        List<ClaimMapOverlayEntry> entries = service.visibleOverlays(
                repository,
                config,
                UUID.randomUUID(),
                "minecraft:overworld",
                List.of(new MapTileKey("minecraft:overworld", 0, 0, 0)),
                true
        );

        assertEquals(1, entries.size());
        assertEquals("Near", entries.getFirst().displayName());
        assertEquals(new ClaimMapOverlayChunk(0, 0), entries.getFirst().chunks().getFirst());
    }

    private ClaimMapOverlayRelation firstRelation(SavedDataClaimRepository repository, UUID viewer) {
        return service.visibleOverlays(
                repository,
                config,
                viewer,
                "minecraft:overworld",
                List.of(new MapTileKey("minecraft:overworld", 0, 0, 0)),
                true
        ).stream()
                .filter(entry -> entry.relation() != ClaimMapOverlayRelation.VISITOR)
                .findFirst()
                .orElseThrow()
                .relation();
    }

    private static Claim claim(
            String name,
            OwnerRef owner,
            int chunkX,
            int chunkZ,
            Map<RoleId, Set<UUID>> roles,
            Map<LeaseId, LeaseContract> leases
    ) {
        ClaimId claimId = ClaimId.random();
        Map<LeaseId, LeaseContract> normalizedLeases = leases;
        if (!leases.isEmpty()) {
            normalizedLeases = leases.values().stream()
                    .map(lease -> new LeaseContract(
                            lease.id(),
                            claimId,
                            lease.tenant(),
                            lease.roleId(),
                            lease.price(),
                            lease.durationSeconds(),
                            lease.offeredAt(),
                            lease.startsAt(),
                            lease.expiresAt(),
                            lease.status(),
                            lease.roleGranted()
                    ))
                    .collect(java.util.stream.Collectors.toMap(LeaseContract::id, lease -> lease));
        }
        return new Claim(
                claimId,
                owner,
                Set.of(new ClaimChunk(new ChunkKey("minecraft:overworld", chunkX, chunkZ))),
                ClaimMetadata.create(name, Instant.EPOCH),
                roles,
                Map.of(),
                Set.of(new FlagId("ucs:block_break")),
                Optional.empty(),
                normalizedLeases
        );
    }

    private static UcsConfigSnapshot validConfig() {
        return new UcsConfigSnapshot(
                UcsConfigDefaults.CURRENT_SCHEMA_VERSION,
                true,
                new UcsConfigSnapshot.DimensionPolicy(UcsConfigDefaults.ENABLED_DIMENSIONS, UcsConfigDefaults.DISABLED_DIMENSIONS, true),
                new UcsConfigSnapshot.ClaimLimitPolicy(16, 256, 128, 5, true),
                new UcsConfigSnapshot.ClaimMetadataPolicy(48, 240),
                new UcsConfigSnapshot.ClaimTeleportPolicy(3, true, true),
                new UcsConfigSnapshot.RoleDefaults(UcsConfigDefaults.DEFAULT_ROLE_IDS, "member", "banned", false),
                new UcsConfigSnapshot.BanPolicy(true, 48, 40),
                new UcsConfigSnapshot.FlagDefaults(UcsConfigDefaults.DEFAULT_PROTECTION_FLAG_IDS),
                new UcsConfigSnapshot.ProtectionPolicy(List.of(), List.of(), UcsConfigDefaults.DEFAULT_SPECIAL_BLOCK_IDS),
                new UcsConfigSnapshot.EconomyPolicy(true, 25.0D, 5.0D, 0.75D, 1_000_000.0D, true),
                new UcsConfigSnapshot.MapCachePolicy(1024, 30, 64, 512),
                new UcsConfigSnapshot.AuditPolicy(true, 250, 180),
                new UcsConfigSnapshot.ArchivePolicy(365),
                new UcsConfigSnapshot.InactivePurgePolicy(false, 90, true),
                new UcsConfigSnapshot.CommandPolicy("ucs", true),
                new UcsConfigSnapshot.MessagePolicy("en_us", true)
        );
    }
}
