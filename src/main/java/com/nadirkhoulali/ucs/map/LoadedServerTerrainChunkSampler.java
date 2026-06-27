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
                SurfaceSample surface = findSurface(chunk, blockX, blockZ, localX, localZ);
                int index = localZ * TerrainChunkSnapshot.CHUNK_SIZE + localX;
                colors[index] = sampleColor(chunk, surface, localX, localZ);
                heights[index] = surface.y();
            }
        }
        return Optional.of(new TerrainChunkSnapshot(dimension, chunkX, chunkZ, colors, heights));
    }

    private SurfaceSample findSurface(LevelChunk chunk, int blockX, int blockZ, int localX, int localZ) {
        int topY = clamp(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ) - 1, level);
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos(blockX, topY, blockZ);
        BlockState state = chunk.getBlockState(position);
        if (isDisplaySurface(state, position)) {
            return new SurfaceSample(state, position.immutable(), topY, topY);
        }

        int minY = level.getMinBuildHeight();
        int y = topY;
        while (y > minY && !isDisplaySurface(state, position)) {
            y--;
            position.setY(y);
            state = chunk.getBlockState(position);
        }
        return new SurfaceSample(state, position.immutable(), y, topY);
    }

    private boolean isDisplaySurface(BlockState state, BlockPos position) {
        if (state.isAir()) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return true;
        }
        if (state.is(BlockTags.LEAVES) || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
            return true;
        }
        if (state.is(BlockTags.REPLACEABLE)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.CROPS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)) {
            return false;
        }
        return !state.getCollisionShape(level, position).isEmpty() || state.canOcclude();
    }

    private int sampleColor(LevelChunk chunk, SurfaceSample surface, int localX, int localZ) {
        BlockState state = surface.state();
        BlockPos position = surface.position();
        Biome biome = level.getBiome(position).value();
        int mapColor = 0xFF000000 | state.getMapColor(level, position).col;
        if (state.getFluidState().is(FluidTags.WATER)) {
            int oceanFloorY = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, localX, localZ);
            int depth = Math.max(0, surface.worldSurfaceY() - oceanFloorY);
            return TerrainColorEnhancer.waterSurfaceColor(biome.getWaterColor(), depth);
        }
        if (state.is(Blocks.GRASS_BLOCK)) {
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

    private record SurfaceSample(BlockState state, BlockPos position, int y, int worldSurfaceY) {
    }
}
