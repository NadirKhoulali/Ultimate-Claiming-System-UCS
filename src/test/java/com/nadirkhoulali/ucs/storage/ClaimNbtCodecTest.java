package com.nadirkhoulali.ucs.storage;

import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.PlayerOwner;
import com.nadirkhoulali.ucs.core.model.ServerOwner;
import com.nadirkhoulali.ucs.core.model.TeamOwner;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

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

    private static void assertOwnerRoundTrip(Claim claim) {
        CompoundTag tag = ClaimNbtCodec.encodeClaim(claim);
        Claim decoded = ClaimNbtCodec.decodeClaim(tag);

        assertEquals(claim.owner(), decoded.owner());
    }
}
