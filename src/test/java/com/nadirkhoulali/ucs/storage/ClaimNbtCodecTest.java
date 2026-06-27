package com.nadirkhoulali.ucs.storage;

import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimMetadata;
import com.nadirkhoulali.ucs.core.model.ClaimSpawn;
import com.nadirkhoulali.ucs.core.model.PlayerOwner;
import com.nadirkhoulali.ucs.core.model.ServerOwner;
import com.nadirkhoulali.ucs.core.model.TeamOwner;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Optional;
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

    private static void assertOwnerRoundTrip(Claim claim) {
        CompoundTag tag = ClaimNbtCodec.encodeClaim(claim);
        Claim decoded = ClaimNbtCodec.decodeClaim(tag);

        assertEquals(claim.owner(), decoded.owner());
    }
}
