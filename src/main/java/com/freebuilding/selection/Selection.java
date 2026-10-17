package com.freebuilding.selection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public record Selection(ResourceKey<Level> dimension, @Nullable BlockPos pos1, @Nullable BlockPos pos2) {
    public boolean isComplete() {
        return pos1 != null && pos2 != null;
    }

    @Nullable
    public BlockPos min() {
        if (pos1 == null) {
            return pos2;
        }
        if (pos2 == null) {
            return pos1;
        }
        return new BlockPos(Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ()));
    }

    @Nullable
    public BlockPos max() {
        if (pos1 == null) {
            return pos2;
        }
        if (pos2 == null) {
            return pos1;
        }
        return new BlockPos(Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ()));
    }

    @Nullable
    public Vec3i size() {
        BlockPos min = min();
        BlockPos max = max();
        if (min == null || max == null) {
            return null;
        }
        return new Vec3i(max.getX() - min.getX() + 1,
                max.getY() - min.getY() + 1,
                max.getZ() - min.getZ() + 1);
    }

    public long volume() {
        Vec3i size = size();
        if (size == null) {
            return 0L;
        }
        return (long) size.getX() * size.getY() * size.getZ();
    }
}
