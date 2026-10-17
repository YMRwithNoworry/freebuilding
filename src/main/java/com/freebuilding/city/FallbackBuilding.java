package com.freebuilding.city;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public final class FallbackBuilding {
    private static final Vec3i[] SIZES = {
            new Vec3i(9, 6, 9),
            new Vec3i(11, 6, 9),
            new Vec3i(7, 13, 7)
    };

    private FallbackBuilding() {
    }

    public static Vec3i size(int variant) {
        return SIZES[Math.floorMod(variant, SIZES.length)];
    }

    public static Vec3i size(int variant, Rotation rotation) {
        Vec3i size = size(variant);
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            return new Vec3i(size.getZ(), size.getY(), size.getX());
        }
        return size;
    }

    public static void place(LevelAccessor level, int minX, int minY, int minZ, Rotation rotation,
                             int variant, @Nullable ChunkPos filter) {
        int v = Math.floorMod(variant, SIZES.length);
        switch (v) {
            case 0 -> placeHouse(level, minX, minY, minZ, rotation, filter);
            case 1 -> placeShop(level, minX, minY, minZ, rotation, filter);
            default -> placeTower(level, minX, minY, minZ, rotation, filter);
        }
    }

    private static void placeHouse(LevelAccessor level, int minX, int minY, int minZ, Rotation rotation,
                                   @Nullable ChunkPos filter) {
        int w = 9;
        int d = 9;
        BlockState floor = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState corner = Blocks.OAK_LOG.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState roof = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState slab = Blocks.SPRUCE_SLAB.defaultBlockState();
        BlockState glow = Blocks.GLOWSTONE.defaultBlockState();

        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                set(level, minX, minY, minZ, rotation, w, d, x, 0, z, floor, filter);
            }
        }
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    if (x != 0 && x != w - 1 && z != 0 && z != d - 1) {
                        continue;
                    }
                    BlockState state = wall;
                    if ((x == 0 || x == w - 1) && (z == 0 || z == d - 1)) {
                        state = corner;
                    }
                    if (y == 2) {
                        if ((x == 0 || x == w - 1) && (z == 2 || z == 4 || z == 6)) {
                            state = glass;
                        }
                        if ((z == 0 || z == d - 1) && (x == 2 || x == 6)) {
                            state = glass;
                        }
                    }
                    if (z == 0 && x == 4 && y <= 2) {
                        state = Blocks.AIR.defaultBlockState();
                    }
                    set(level, minX, minY, minZ, rotation, w, d, x, y, z, state, filter);
                }
            }
        }
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                set(level, minX, minY, minZ, rotation, w, d, x, 4, z, roof, filter);
                if (x == 0 || x == w - 1 || z == 0 || z == d - 1) {
                    set(level, minX, minY, minZ, rotation, w, d, x, 5, z, slab, filter);
                }
            }
        }
        set(level, minX, minY, minZ, rotation, w, d, 4, 3, 4, glow, filter);
    }

    private static void placeShop(LevelAccessor level, int minX, int minY, int minZ, Rotation rotation,
                                  @Nullable ChunkPos filter) {
        int w = 11;
        int d = 9;
        BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState wall = Blocks.WHITE_CONCRETE.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState roof = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState glow = Blocks.SEA_LANTERN.defaultBlockState();

        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                set(level, minX, minY, minZ, rotation, w, d, x, 0, z, floor, filter);
            }
        }
        for (int y = 1; y <= 4; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    if (x != 0 && x != w - 1 && z != 0 && z != d - 1) {
                        continue;
                    }
                    BlockState state = wall;
                    if (z == 0 && y <= 3 && x >= 2 && x <= 8) {
                        state = glass;
                    }
                    if (z == 0 && x == 5 && y <= 2) {
                        state = Blocks.AIR.defaultBlockState();
                    }
                    set(level, minX, minY, minZ, rotation, w, d, x, y, z, state, filter);
                }
            }
        }
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                set(level, minX, minY, minZ, rotation, w, d, x, 5, z, roof, filter);
            }
        }
        set(level, minX, minY, minZ, rotation, w, d, 5, 4, 4, glow, filter);
    }

    private static void placeTower(LevelAccessor level, int minX, int minY, int minZ, Rotation rotation,
                                   @Nullable ChunkPos filter) {
        int w = 7;
        int d = 7;
        BlockState floor = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState roof = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState glow = Blocks.SEA_LANTERN.defaultBlockState();

        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                set(level, minX, minY, minZ, rotation, w, d, x, 0, z, floor, filter);
            }
        }
        for (int y = 1; y <= 10; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    if (x != 0 && x != w - 1 && z != 0 && z != d - 1) {
                        continue;
                    }
                    BlockState state = wall;
                    if ((y == 3 || y == 6 || y == 9)
                            && ((x == 0 || x == w - 1) && z == 3 || (z == 0 || z == d - 1) && x == 3)) {
                        state = glass;
                    }
                    if (z == 0 && x == 3 && y <= 2) {
                        state = Blocks.AIR.defaultBlockState();
                    }
                    set(level, minX, minY, minZ, rotation, w, d, x, y, z, state, filter);
                }
            }
        }
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                set(level, minX, minY, minZ, rotation, w, d, x, 11, z, roof, filter);
                if ((x + z) % 2 == 0 && (x == 0 || x == w - 1 || z == 0 || z == d - 1)) {
                    set(level, minX, minY, minZ, rotation, w, d, x, 12, z, wall, filter);
                }
            }
        }
        set(level, minX, minY, minZ, rotation, w, d, 3, 10, 3, glow, filter);
    }

    private static void set(LevelAccessor level, int minX, int minY, int minZ, Rotation rotation,
                            int width, int depth, int x, int y, int z, BlockState state,
                            @Nullable ChunkPos filter) {
        int[] rotated = rotateLocal(rotation, x, z, width, depth);
        int worldX = minX + rotated[0];
        int worldZ = minZ + rotated[1];
        int worldY = minY + y;
        if (filter != null && (worldX >> 4 != filter.x || worldZ >> 4 != filter.z)) {
            return;
        }
        level.setBlock(new BlockPos(worldX, worldY, worldZ), state, 2);
    }

    private static int[] rotateLocal(Rotation rotation, int x, int z, int width, int depth) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new int[]{depth - 1 - z, x};
            case CLOCKWISE_180 -> new int[]{width - 1 - x, depth - 1 - z};
            case COUNTERCLOCKWISE_90 -> new int[]{z, width - 1 - x};
            default -> new int[]{x, z};
        };
    }
}
