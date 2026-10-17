package com.freebuilding.city;

import com.freebuilding.preset.Preset;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;

import javax.annotation.Nullable;

public final class Plot {
    public final int gridX;
    public final int gridZ;
    public final int minX;
    public final int minZ;
    public final int maxX;
    public final int maxZ;
    public final int buildMinX;
    public final int buildMinZ;
    public final int buildMaxX;
    public final int buildMaxZ;
    @Nullable
    public final Preset preset;
    public final Rotation rotation;
    public final int fallbackVariant;
    public final boolean park;

    public Plot(int gridX, int gridZ, int minX, int minZ, int maxX, int maxZ,
                int buildMinX, int buildMinZ, int buildMaxX, int buildMaxZ,
                @Nullable Preset preset, Rotation rotation, int fallbackVariant, boolean park) {
        this.gridX = gridX;
        this.gridZ = gridZ;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.buildMinX = buildMinX;
        this.buildMinZ = buildMinZ;
        this.buildMaxX = buildMaxX;
        this.buildMaxZ = buildMaxZ;
        this.preset = preset;
        this.rotation = rotation;
        this.fallbackVariant = fallbackVariant;
        this.park = park;
    }

    public boolean hasBuilding() {
        return buildMinX != Integer.MIN_VALUE && buildMaxX >= buildMinX && buildMaxZ >= buildMinZ;
    }

    public boolean intersects(ChunkPos chunk) {
        return maxX >= chunk.getMinBlockX() && minX <= chunk.getMaxBlockX()
                && maxZ >= chunk.getMinBlockZ() && minZ <= chunk.getMaxBlockZ();
    }

    public boolean buildingIntersects(ChunkPos chunk) {
        return hasBuilding() && buildMaxX >= chunk.getMinBlockX() && buildMinX <= chunk.getMaxBlockX()
                && buildMaxZ >= chunk.getMinBlockZ() && buildMinZ <= chunk.getMaxBlockZ();
    }
}
