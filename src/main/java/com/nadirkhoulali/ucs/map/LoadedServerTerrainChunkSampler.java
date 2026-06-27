package com.nadirkhoulali.ucs.map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Objects;
import java.util.Optional;

public final class LoadedServerTerrainChunkSampler implements TerrainChunkSampler {
    private final ServerLevel level;

    public LoadedServerTerrainChunkSampler(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "level");
    }

    @Override
    public Optional<TerrainChunkSnapshot> sampleLoadedChunk(String dimension, int chunkX, int chunkZ) {
        if (!level.dimension().location().toString().equals(dimension)) {
            return Optional.empty();
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return Optional.empty();
        }

        int[] colors = new int[TerrainChunkSnapshot.SAMPLE_COUNT];
        ChunkPos chunkPos = chunk.getPos();
        for (int localZ = 0; localZ < TerrainChunkSnapshot.CHUNK_SIZE; localZ++) {
            for (int localX = 0; localX < TerrainChunkSnapshot.CHUNK_SIZE; localX++) {
                int blockX = chunkPos.getMinBlockX() + localX;
                int blockZ = chunkPos.getMinBlockZ() + localZ;
                int topY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
                int sampleY = clamp(topY - 1, level);
                BlockPos position = new BlockPos(blockX, sampleY, blockZ);
                BlockState state = chunk.getBlockState(position);
                colors[localZ * TerrainChunkSnapshot.CHUNK_SIZE + localX] = 0xFF000000 | state.getMapColor(level, position).col;
            }
        }
        return Optional.of(new TerrainChunkSnapshot(dimension, chunkX, chunkZ, colors));
    }

    private static int clamp(int y, LevelHeightAccessor height) {
        return Math.max(height.getMinBuildHeight(), Math.min(height.getMaxBuildHeight() - 1, y));
    }
}
