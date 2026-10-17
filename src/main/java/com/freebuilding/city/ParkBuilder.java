package com.freebuilding.city;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public final class ParkBuilder {
    private ParkBuilder() {
    }

    public static void place(LevelAccessor level, int minX, int minZ, int maxX, int maxZ, int groundY,
                             CityStyle style, RandomSource random, @Nullable ChunkPos filter) {
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                set(level, x, groundY, z, style.parkState(), filter);
            }
        }

        int trees = 1 + random.nextInt(3);
        for (int i = 0; i < trees; i++) {
            int x = minX + 2 + random.nextInt(Math.max(1, width - 4));
            int z = minZ + 2 + random.nextInt(Math.max(1, depth - 4));
            int height = 3 + random.nextInt(2);
            for (int y = 1; y <= height; y++) {
                set(level, x, groundY + y, z, Blocks.OAK_LOG.defaultBlockState(), filter);
            }
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (Math.abs(dx) + Math.abs(dz) + Math.abs(dy) > 3) {
                            continue;
                        }
                        set(level, x + dx, groundY + height + dy, z + dz,
                                Blocks.OAK_LEAVES.defaultBlockState(), filter);
                    }
                }
            }
        }

        for (int i = 0; i < 6; i++) {
            int x = minX + 1 + random.nextInt(Math.max(1, width - 2));
            int z = minZ + 1 + random.nextInt(Math.max(1, depth - 2));
            BlockState flower = random.nextBoolean() ? Blocks.POPPY.defaultBlockState() : Blocks.DANDELION.defaultBlockState();
            set(level, x, groundY + 1, z, flower, filter);
        }

        for (int i = 0; i < 2; i++) {
            int x = minX + 1 + random.nextInt(Math.max(1, width - 2));
            int z = minZ + 1 + random.nextInt(Math.max(1, depth - 2));
            set(level, x, groundY + 1, z, Blocks.OAK_SLAB.defaultBlockState(), filter);
        }
    }

    private static void set(LevelAccessor level, int x, int y, int z, BlockState state, @Nullable ChunkPos filter) {
        if (filter != null && (x >> 4 != filter.x || z >> 4 != filter.z)) {
            return;
        }
        level.setBlock(new BlockPos(x, y, z), state, 2);
    }
}
