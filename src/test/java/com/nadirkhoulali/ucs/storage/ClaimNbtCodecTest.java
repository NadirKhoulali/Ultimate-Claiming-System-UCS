package com.nadirkhoulali.ucs.storage;

import com.nadirkhoulali.ucs.core.model.Claim;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClaimNbtCodecTest {
    @Test
    void claimRoundTripsThroughNbt() {
        Claim claim = ClaimFixtures.claimAt(-4, 9);

        CompoundTag tag = ClaimNbtCodec.encodeClaim(claim);
        Claim decoded = ClaimNbtCodec.decodeClaim(tag);

        assertEquals(claim, decoded);
    }
}
