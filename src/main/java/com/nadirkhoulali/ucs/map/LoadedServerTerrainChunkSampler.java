package com.nadirkhoulali.ucs.map;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
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
        int[] heights = new int[TerrainChunkSnapshot.SAMPLE_COUNT];
        ChunkPos chunkPos = chunk.getPos();
        for (int localZ = 0; localZ < TerrainChunkSnapshot.CHUNK_SIZE; localZ++) {
            for (int localX = 0; localX < TerrainChunkSnapshot.CHUNK_SIZE; localX++) {
                int blockX = chunkPos.getMinBlockX() + localX;
                int blockZ = chunkPos.getMinBlockZ() + localZ;
                int topY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
                int sampleY = clamp(topY - 1, level);
                BlockPos position = new BlockPos(blockX, sampleY, blockZ);
                BlockState state = chunk.getBlockState(position);
                int index = localZ * TerrainChunkSnapshot.CHUNK_SIZE + localX;
                colors[index] = sampleColor(chunk, state, position, localX, localZ, topY);
                heights[index] = sampleY;
            }
        }
        return Optional.of(new TerrainChunkSnapshot(dimension, chunkX, chunkZ, colors, heights));
    }

    private int sampleColor(LevelChunk chunk, BlockState state, BlockPos position, int localX, int localZ, int topY) {
        Biome biome = level.getBiome(position).value();
        int mapColor = 0xFF000000 | state.getMapColor(level, position).col;
        if (state.getFluidState().is(FluidTags.WATER)) {
            int oceanFloorY = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, localX, localZ);
            int depth = Math.max(0, topY - oceanFloorY);
            return TerrainColorEnhancer.waterSurfaceColor(biome.getWaterColor(), depth);
        }
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)) {
            return TerrainColorEnhancer.grassSurfaceColor(mapColor, biome.getGrassColor(position.getX(), position.getZ()));
        }
        if (state.is(BlockTags.LEAVES)) {
            return TerrainColorEnhancer.foliageSurfaceColor(mapColor, biome.getFoliageColor());
        }
        return mapColor;
    }

    private static int clamp(int y, LevelHeightAccessor height) {
        return Math.max(height.getMinBuildHeight(), Math.min(height.getMaxBuildHeight() - 1, y));
    }
}
