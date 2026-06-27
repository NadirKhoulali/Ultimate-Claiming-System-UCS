package com.nadirkhoulali.ucs.storage;

import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimChunk;
import com.nadirkhoulali.ucs.core.model.ClaimId;
import com.nadirkhoulali.ucs.core.model.ClaimMetadata;
import com.nadirkhoulali.ucs.core.model.FlagId;
import com.nadirkhoulali.ucs.core.model.RoleId;
import com.nadirkhoulali.ucs.core.model.ServerOwner;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClaimFixtures {
    private ClaimFixtures() {
    }

    public static Claim claimAt(int chunkX, int chunkZ) {
        return claimAt(ClaimId.random(), chunkX, chunkZ);
    }

    public static Claim claimAt(ClaimId claimId, int chunkX, int chunkZ) {
        UUID member = UUID.randomUUID();
        return new Claim(
                claimId,
                new ServerOwner("spawn"),
                Set.of(new ClaimChunk(new ChunkKey("minecraft:overworld", chunkX, chunkZ))),
                ClaimMetadata.create("Spawn", Instant.EPOCH),
                Map.of(new RoleId("member"), Set.of(member)),
                Set.of(new FlagId("ucs:block_break"))
        );
    }
}
