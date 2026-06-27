package com.nadirkhoulali.ucs.storage;

import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimMetadata;
import com.nadirkhoulali.ucs.core.model.ClaimSaleListing;
import com.nadirkhoulali.ucs.core.model.ClaimSpawn;
import com.nadirkhoulali.ucs.core.model.ClaimTaxLedgerEntry;
import com.nadirkhoulali.ucs.core.model.ClaimTaxLedgerStatus;
import com.nadirkhoulali.ucs.core.model.ClaimTaxState;
import com.nadirkhoulali.ucs.core.model.EconomyAuditAction;
import com.nadirkhoulali.ucs.core.model.EconomyAuditEntry;
import com.nadirkhoulali.ucs.core.model.EconomyAuditStatus;
import com.nadirkhoulali.ucs.core.model.LeaseContract;
import com.nadirkhoulali.ucs.core.model.PlayerOwner;
import com.nadirkhoulali.ucs.core.model.RoleId;
import com.nadirkhoulali.ucs.core.model.ServerOwner;
import com.nadirkhoulali.ucs.core.model.TeamOwner;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClaimNbtCodecTest {
    @Test
    void claimRoundTripsThroughNbt() {
        Claim claim = ClaimFixtures.claimAt(-4, 9);

        CompoundTag tag = ClaimNbtCodec.encodeClaim(claim);
        Claim decoded = ClaimNbtCodec.decodeClaim(tag);

        assertEquals(claim, decoded);
    }

    @Test
    void ownerTypesRoundTripThroughNbt() {
        assertOwnerRoundTrip(ClaimFixtures.claimAt(0, 0, new PlayerOwner(UUID.randomUUID(), "Nadir")));
        assertOwnerRoundTrip(ClaimFixtures.claimAt(1, 0, new TeamOwner("builders")));
        assertOwnerRoundTrip(ClaimFixtures.claimAt(2, 0, new ServerOwner("spawn")));
    }

    @Test
    void metadataDescriptionAndSpawnRoundTripThroughNbt() {
        Claim claim = ClaimFixtures.claimAt(0, 0);
        ClaimSpawn spawn = new ClaimSpawn(new ChunkKey("minecraft:overworld", 0, 0), 8.5D, 70.0D, 8.5D, 90.0F, 10.0F);
        Claim updated = new Claim(
                claim.id(),
                claim.owner(),
                claim.chunks(),
                new ClaimMetadata("Spawn", "Starter area", Optional.of(spawn), claim.metadata().createdAt(), claim.metadata().updatedAt()),
                claim.roleAssignments(),
                claim.flagOverrides()
        );

        CompoundTag tag = ClaimNbtCodec.encodeClaim(updated);
        Claim decoded = ClaimNbtCodec.decodeClaim(tag);

        assertEquals("Starter area", decoded.metadata().description());
        assertEquals(spawn, decoded.metadata().spawn().orElseThrow());
    }

    @Test
    void pendingRoleInvitesRoundTripThroughNbt() {
        Claim claim = ClaimFixtures.claimAt(0, 0);
        UUID invitee = UUID.randomUUID();
        Claim updated = new Claim(
                claim.id(),
                claim.owner(),
                claim.chunks(),
                claim.metadata(),
                claim.roleAssignments(),
                Map.of(new RoleId("member"), Set.of(invitee)),
                claim.flagOverrides()
        );

        CompoundTag tag = ClaimNbtCodec.encodeClaim(updated);
        Claim decoded = ClaimNbtCodec.decodeClaim(tag);

        assertEquals(Set.of(invitee), decoded.pendingRoleInvites().get(new RoleId("member")));
    }

    @Test
    void saleListingRoundTripsThroughNbt() {
        Claim claim = ClaimFixtures.claimAt(0, 0);
        ClaimSaleListing listing = new ClaimSaleListing(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Nadir",
                BigDecimal.valueOf(500),
                Instant.EPOCH
        );
        Claim updated = new Claim(
                claim.id(),
                claim.owner(),
                claim.chunks(),
                claim.metadata(),
                claim.roleAssignments(),
                claim.pendingRoleInvites(),
                claim.flagOverrides(),
                Optional.of(listing)
        );

        CompoundTag tag = ClaimNbtCodec.encodeClaim(updated);
        Claim decoded = ClaimNbtCodec.decodeClaim(tag);

        assertEquals(listing, decoded.saleListing().orElseThrow());
    }

    @Test
    void leasesRoundTripThroughNbt() {
        Claim claim = ClaimFixtures.claimAt(0, 0);
        LeaseContract lease = LeaseContract.offer(
                com.nadirkhoulali.ucs.core.model.LeaseId.random(),
                claim.id(),
                new PlayerOwner(UUID.randomUUID(), "Tenant"),
                new RoleId("tenant"),
                BigDecimal.valueOf(75),
                Duration.ofDays(5),
                Instant.EPOCH
        ).activate(Instant.EPOCH.plusSeconds(20), true);
        Claim updated = new Claim(
                claim.id(),
                claim.owner(),
                claim.chunks(),
                claim.metadata(),
                claim.roleAssignments(),
                claim.pendingRoleInvites(),
                claim.flagOverrides(),
                claim.saleListing(),
                Map.of(lease.id(), lease)
        );

        CompoundTag tag = ClaimNbtCodec.encodeClaim(updated);
        Claim decoded = ClaimNbtCodec.decodeClaim(tag);

        assertEquals(lease, decoded.leases().get(lease.id()));
    }

    @Test
    void taxStateAndLedgerRoundTripThroughNbt() {
        Claim claim = ClaimFixtures.claimAt(0, 0);
        ClaimTaxState state = new ClaimTaxState(
                claim.id(),
                Instant.EPOCH.plusSeconds(3600),
                Optional.of(Instant.EPOCH.plusSeconds(10)),
                1,
                BigDecimal.valueOf(25),
                Optional.of(Instant.EPOCH.plusSeconds(20)),
                Optional.of(Instant.EPOCH.plusSeconds(25)),
                Instant.EPOCH.plusSeconds(30)
        );
        ClaimTaxLedgerEntry entry = new ClaimTaxLedgerEntry(
                UUID.randomUUID(),
                claim.id(),
                claim.owner().stableKey(),
                BigDecimal.valueOf(25),
                Instant.EPOCH.plusSeconds(3600),
                Instant.EPOCH.plusSeconds(3610),
                "UCS_CLAIM_TAX:test",
                ClaimTaxLedgerStatus.PAID,
                "provider-ref",
                "charged $25"
        );

        assertEquals(state, ClaimNbtCodec.decodeTaxState(ClaimNbtCodec.encodeTaxState(state)));
        assertEquals(entry, ClaimNbtCodec.decodeTaxLedgerEntry(ClaimNbtCodec.encodeTaxLedgerEntry(entry)));
    }

    @Test
    void economyAuditEntryRoundTripsThroughNbt() {
        Claim claim = ClaimFixtures.claimAt(0, 0);
        EconomyAuditEntry entry = new EconomyAuditEntry(
                UUID.randomUUID(),
                Instant.EPOCH.plusSeconds(50),
                "player:" + UUID.randomUUID(),
                EconomyAuditAction.ADMIN_REFUND,
                EconomyAuditStatus.SUCCESS,
                Optional.of(claim.id()),
                claim.owner().stableKey(),
                BigDecimal.valueOf(12.5D),
                "UCS_ADMIN_REFUND:test",
                "fake:economy",
                "provider-ref",
                "manual correction",
                "refunded $12.5"
        );

        assertEquals(entry, ClaimNbtCodec.decodeEconomyAuditEntry(ClaimNbtCodec.encodeEconomyAuditEntry(entry)));
    }

    private static void assertOwnerRoundTrip(Claim claim) {
        CompoundTag tag = ClaimNbtCodec.encodeClaim(claim);
        Claim decoded = ClaimNbtCodec.decodeClaim(tag);

        assertEquals(claim.owner(), decoded.owner());
    }
}
