package com.freebuilding.city;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

public final class CityGround {
    private CityGround() {
    }

    public static int compute(ServerLevel level, int cellOriginX, int cellOriginZ, int cellSize) {
        try {
            RandomState randomState = level.getChunkSource().randomState();
            int inset = Math.min(24, cellSize / 4);
            int center = cellSize / 2;
            int[] xs = {cellOriginX + center, cellOriginX + inset, cellOriginX + cellSize - inset,
                    cellOriginX + inset, cellOriginX + cellSize - inset};
            int[] zs = {cellOriginZ + center, cellOriginZ + inset, cellOriginZ + inset,
                    cellOriginZ + cellSize - inset, cellOriginZ + cellSize - inset};
            long sum = 0L;
            for (int i = 0; i < xs.length; i++) {
                sum += level.getChunkSource().getGenerator().getBaseHeight(xs[i], zs[i],
                        Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
            }
            int average = (int) (sum / xs.length);
            if (average < level.getSeaLevel() - 8) {
                return Integer.MIN_VALUE;
            }
            return Mth.clamp(average, level.getMinBuildHeight() + 16, level.getMaxBuildHeight() - 96);
        } catch (Exception exception) {
            return level.getSeaLevel() + 4;
        }
    }
}
