package com.freebuilding.worldgen;

import com.freebuilding.city.CityConfig;
import com.freebuilding.city.CityLayout;
import com.freebuilding.city.CityStyle;
import com.freebuilding.city.Plot;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Village-like decoration for rural sites: farms, dry wasteland gardens and a central well.
 */
public final class RuralDecorator {
    private RuralDecorator() {
    }

    public static void placePark(WorldGenLevel level, CityLayout layout, Plot plot, CityConfig config, ChunkPos chunk) {
        RandomSource random = RandomSource.create(layout.seed
                ^ ((long) plot.gridX * 0x9E3779B97F4A7C15L)
                ^ ((long) plot.gridZ * 0xC2B2AE3D27D4EB4FL)
                ^ 0x727572616C706172L);
        boolean center = layout.gridSize >= 3
                && plot.gridX == layout.gridSize / 2
                && plot.gridZ == layout.gridSize / 2;
        if (center) {
            placeWell(level, layout.groundY, plot, config, chunk);
        } else if (config.style == CityStyle.RUINED) {
            placeDryGarden(level, layout.groundY, plot, chunk, random);
        } else {
            placeFarm(level, layout.groundY, plot, chunk);
        }
    }

    private static void placeFarm(WorldGenLevel level, int groundY, Plot plot, ChunkPos chunk) {
        int waterZ = plot.minZ + (plot.maxZ - plot.minZ) / 2;
        for (int x = plot.minX; x <= plot.maxX; x++) {
            for (int z = plot.minZ; z <= plot.maxZ; z++) {
                if (z == waterZ) {
                    set(level, x, groundY, z, Blocks.WATER.defaultBlockState(), chunk);
                } else {
                    set(level, x, groundY, z, Blocks.FARMLAND.defaultBlockState(), chunk);
                    set(level, x, groundY + 1, z,
                            Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7), chunk);
                }
            }
        }
        int gateX = plot.minX + (plot.maxX - plot.minX) / 2;
        for (int x = plot.minX; x <= plot.maxX; x++) {
            if (x != gateX) {
                set(level, x, groundY + 1, plot.minZ, Blocks.OAK_FENCE.defaultBlockState(), chunk);
            }
            set(level, x, groundY + 1, plot.maxZ, Blocks.OAK_FENCE.defaultBlockState(), chunk);
        }
        for (int z = plot.minZ; z <= plot.maxZ; z++) {
            set(level, plot.minX, groundY + 1, z, Blocks.OAK_FENCE.defaultBlockState(), chunk);
            set(level, plot.maxX, groundY + 1, z, Blocks.OAK_FENCE.defaultBlockState(), chunk);
        }
        set(level, gateX, groundY + 1, plot.minZ, Blocks.AIR.defaultBlockState(), chunk);
    }

    private static void placeDryGarden(WorldGenLevel level, int groundY, Plot plot, ChunkPos chunk, RandomSource random) {
        for (int x = plot.minX; x <= plot.maxX; x++) {
            for (int z = plot.minZ; z <= plot.maxZ; z++) {
                BlockState ground = random.nextInt(4) == 0
                        ? Blocks.SAND.defaultBlockState()
                        : Blocks.COARSE_DIRT.defaultBlockState();
                set(level, x, groundY, z, ground, chunk);
                if (random.nextInt(9) == 0) {
                    if (ground.is(Blocks.SAND)) {
                        set(level, x, groundY + 1, z, Blocks.CACTUS.defaultBlockState(), chunk);
                    } else {
                        set(level, x, groundY + 1, z, Blocks.DEAD_BUSH.defaultBlockState(), chunk);
                    }
                }
            }
        }
    }

    private static void placeWell(WorldGenLevel level, int groundY, Plot plot, CityConfig config, ChunkPos chunk) {
        for (int x = plot.minX; x <= plot.maxX; x++) {
            for (int z = plot.minZ; z <= plot.maxZ; z++) {
                set(level, x, groundY, z, config.style.parkState(), chunk);
            }
        }
        int centerX = plot.minX + (plot.maxX - plot.minX) / 2;
        int centerZ = plot.minZ + (plot.maxZ - plot.minZ) / 2;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(level, centerX + dx, groundY, centerZ + dz, Blocks.COBBLESTONE.defaultBlockState(), chunk);
                set(level, centerX + dx, groundY + 2, centerZ + dz, Blocks.COBBLESTONE_SLAB.defaultBlockState(), chunk);
            }
        }
        set(level, centerX, groundY, centerZ, Blocks.WATER.defaultBlockState(), chunk);
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                set(level, centerX + dx, groundY + 1, centerZ + dz, Blocks.COBBLESTONE_WALL.defaultBlockState(), chunk);
            }
        }
        set(level, centerX, groundY + 1, centerZ, Blocks.LANTERN.defaultBlockState(), chunk);
    }

    private static void set(WorldGenLevel level, int x, int y, int z, BlockState state, ChunkPos chunk) {
        if (x >> 4 != chunk.x || z >> 4 != chunk.z) {
            return;
        }
        level.setBlock(new BlockPos(x, y, z), state, 2);
    }
}
