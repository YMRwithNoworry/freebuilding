package com.freebuilding.preset;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import javax.annotation.Nullable;
import java.util.Arrays;

public final class PreviewData {
    private final int width;
    private final int depth;
    private final int height;
    private final int[] topColors;

    public PreviewData(int width, int depth, int height, int[] topColors) {
        this.width = Math.max(1, width);
        this.depth = Math.max(1, depth);
        this.height = Math.max(1, height);
        this.topColors = topColors;
    }

    public int width() {
        return width;
    }

    public int depth() {
        return depth;
    }

    public int height() {
        return height;
    }

    public int colorAt(int x, int z) {
        if (x < 0 || z < 0 || x >= width || z >= depth) {
            return 0xFF666666;
        }
        return topColors[x + z * width];
    }

    public PreviewData rotated(Rotation rotation) {
        if (rotation == Rotation.NONE) {
            return this;
        }
        int newWidth = (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) ? depth : width;
        int newDepth = (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) ? width : depth;
        int[] rotated = new int[newWidth * newDepth];
        Arrays.fill(rotated, 0xFF666666);
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                int nx;
                int nz;
                switch (rotation) {
                    case CLOCKWISE_90 -> {
                        nx = depth - 1 - z;
                        nz = x;
                    }
                    case CLOCKWISE_180 -> {
                        nx = width - 1 - x;
                        nz = depth - 1 - z;
                    }
                    case COUNTERCLOCKWISE_90 -> {
                        nx = z;
                        nz = width - 1 - x;
                    }
                    default -> {
                        nx = x;
                        nz = z;
                    }
                }
                rotated[nx + nz * newWidth] = topColors[x + z * width];
            }
        }
        return new PreviewData(newWidth, newDepth, height, rotated);
    }

    public static PreviewData fromNbt(CompoundTag tag, HolderGetter<Block> blockGetter, @Nullable BlockGetter level) {
        int width = 1;
        int height = 1;
        int depth = 1;
        ListTag size = tag.getList("size", Tag.TAG_INT);
        if (size.size() >= 3) {
            width = Math.max(1, size.getInt(0));
            height = Math.max(1, size.getInt(1));
            depth = Math.max(1, size.getInt(2));
        }

        CompoundTag paletteEntry = tag;
        ListTag palettes = tag.getList("palettes", Tag.TAG_COMPOUND);
        if (!palettes.isEmpty()) {
            paletteEntry = palettes.getCompound(0);
        }

        ListTag palette = paletteEntry.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = paletteEntry.getList("blocks", Tag.TAG_COMPOUND);
        BlockState[] states = new BlockState[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            try {
                states[i] = NbtUtils.readBlockState(blockGetter, palette.getCompound(i));
            } catch (Exception exception) {
                states[i] = Blocks.AIR.defaultBlockState();
            }
        }

        int[] topY = new int[width * depth];
        int[] colors = new int[width * depth];
        Arrays.fill(topY, Integer.MIN_VALUE);
        Arrays.fill(colors, 0xFF666666);
        int maxY = 0;
        int minY = Integer.MAX_VALUE;

        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            if (pos.size() < 3) {
                continue;
            }
            int x = pos.getInt(0);
            int y = pos.getInt(1);
            int z = pos.getInt(2);
            if (x < 0 || z < 0 || x >= width || z >= depth) {
                continue;
            }
            int stateIndex = block.getInt("state");
            BlockState state = stateIndex >= 0 && stateIndex < states.length ? states[stateIndex] : Blocks.AIR.defaultBlockState();
            if (state.isAir() || state.is(Blocks.STRUCTURE_VOID)) {
                continue;
            }
            int index = x + z * width;
            if (y >= topY[index]) {
                topY[index] = y;
                colors[index] = colorOf(state, level);
                maxY = Math.max(maxY, y);
                minY = Math.min(minY, y);
            }
        }

        if (minY == Integer.MAX_VALUE) {
            minY = 0;
        }
        int span = Math.max(1, maxY - minY);
        for (int i = 0; i < colors.length; i++) {
            if (topY[i] == Integer.MIN_VALUE) {
                colors[i] = 0xFF666666;
            } else {
                float factor = 0.68F + 0.32F * Mth.clamp((topY[i] - minY) / (float) span, 0.0F, 1.0F);
                colors[i] = shade(colors[i], factor);
            }
        }
        return new PreviewData(width, depth, height, colors);
    }

    private static int colorOf(BlockState state, @Nullable BlockGetter level) {
        try {
            MapColor mapColor = state.getMapColor(level, BlockPos.ZERO);
            if (mapColor != null && mapColor.col != 0) {
                return 0xFF000000 | mapColor.col;
            }
        } catch (Exception ignored) {
        }
        return 0xFF8A8A8A;
    }

    private static int shade(int argb, float factor) {
        int r = Mth.clamp((int) (((argb >> 16) & 0xFF) * factor), 0, 255);
        int g = Mth.clamp((int) (((argb >> 8) & 0xFF) * factor), 0, 255);
        int b = Mth.clamp((int) ((argb & 0xFF) * factor), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
