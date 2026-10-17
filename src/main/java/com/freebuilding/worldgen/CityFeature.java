package com.freebuilding.worldgen;

import com.freebuilding.FreeBuilding;
import com.freebuilding.WorldData;
import com.freebuilding.city.CityConfig;
import com.freebuilding.city.CityGround;
import com.freebuilding.city.CityLayout;
import com.freebuilding.preset.Preset;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.List;

public final class CityFeature extends Feature<NoneFeatureConfiguration> {
    public CityFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!WorldData.isInitialized()) {
            return false;
        }
        CityConfig config = WorldData.getConfig();
        if (config == null || !config.worldgenEnabled || config.worldgenChance <= 0) {
            return false;
        }

        ChunkPos chunk = new ChunkPos(context.origin());
        int cellX = Math.floorDiv(chunk.getMinBlockX(), WorldData.WORLDGEN_CITY_SIZE);
        int cellZ = Math.floorDiv(chunk.getMinBlockZ(), WorldData.WORLDGEN_CITY_SIZE);
        if (!WorldData.cityExists(cellX, cellZ)) {
            return false;
        }

        WorldGenLevel level = context.level();
        ServerLevel serverLevel = level.getLevel();
        int cellOriginX = cellX * WorldData.WORLDGEN_CITY_SIZE;
        int cellOriginZ = cellZ * WorldData.WORLDGEN_CITY_SIZE;
        int groundY = CityGround.compute(serverLevel, cellOriginX, cellOriginZ, WorldData.WORLDGEN_CITY_SIZE);
        if (groundY == Integer.MIN_VALUE) {
            return false;
        }

        List<Preset> presets = WorldData.getPresets();
        long seed = WorldData.getWorldSeed()
                ^ ((long) cellX * 341873128712L)
                ^ ((long) cellZ * 132897987541L);
        CityLayout layout = CityLayout.auto(new BlockPos(cellOriginX, groundY, cellOriginZ),
                WorldData.WORLDGEN_CITY_SIZE, config, presets, seed, groundY);
        try {
            boolean generated = CityWorldgen.generateChunk(level, chunk, layout, config, presets, context.random(), false);
            return generated;
        } catch (Exception exception) {
            FreeBuilding.LOGGER.error("FreeBuilding failed to generate city chunk {} in cell ({}, {})",
                    chunk, cellX, cellZ, exception);
            return false;
        }
    }
}
