package com.freebuilding.worldgen;

import com.freebuilding.FreeBuilding;
import com.freebuilding.WorldData;
import com.freebuilding.city.CityConfig;
import com.freebuilding.city.CityGround;
import com.freebuilding.city.CityLayout;
import com.freebuilding.city.CityStyle;
import com.freebuilding.city.RuralMode;
import com.freebuilding.preset.Preset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.List;

/**
 * Generates standalone rural houses or small vanilla-like villages outside city cells.
 *
 * <p>Rural sites use a 128x128 grid. A site is skipped when it overlaps any 256x256 city
 * cell, when it is in ocean/river/beach biomes, or when a vanilla village is already present
 * at the site center. Each chunk only places its own slice, so the feature is stateless across
 * chunk generation.</p>
 */
public final class RuralFeature extends Feature<NoneFeatureConfiguration> {
    public RuralFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!WorldData.isInitialized()) {
            return false;
        }
        CityConfig config = WorldData.getConfig();
        if (config == null || !config.ruralEnabled || config.ruralChance <= 0) {
            return false;
        }

        ChunkPos chunk = new ChunkPos(context.origin());
        int cellX = Math.floorDiv(chunk.getMinBlockX(), WorldData.RURAL_CELL_SIZE);
        int cellZ = Math.floorDiv(chunk.getMinBlockZ(), WorldData.RURAL_CELL_SIZE);
        if (!WorldData.ruralExists(cellX, cellZ)) {
            return false;
        }

        WorldGenLevel level = context.level();
        ServerLevel serverLevel = level.getLevel();
        int cellOriginX = cellX * WorldData.RURAL_CELL_SIZE;
        int cellOriginZ = cellZ * WorldData.RURAL_CELL_SIZE;
        int groundY = CityGround.compute(serverLevel, cellOriginX, cellOriginZ, WorldData.RURAL_CELL_SIZE);
        if (groundY == Integer.MIN_VALUE) {
            return false;
        }

        int centerX = cellOriginX + WorldData.RURAL_CELL_SIZE / 2;
        int centerZ = cellOriginZ + WorldData.RURAL_CELL_SIZE / 2;
        RandomState randomState = serverLevel.getChunkSource().randomState();
        Holder<Biome> biome = context.chunkGenerator().getBiomeSource()
                .getNoiseBiome(centerX >> 2, groundY >> 2, centerZ >> 2, randomState.sampler());
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)) {
            return false;
        }
        BlockPos center = new BlockPos(centerX, groundY + 1, centerZ);
        if (serverLevel.structureManager().getStructureWithPieceAt(center, StructureTags.VILLAGE) != null) {
            return false;
        }

        long seed = WorldData.getWorldSeed()
                ^ ((long) cellX * 341873128712L)
                ^ ((long) cellZ * 132897987541L)
                ^ 0x6C6F737463697479L;
        RandomSource cellRandom = RandomSource.create(seed);
        boolean village = config.ruralMode == RuralMode.VILLAGE
                || (config.ruralMode == RuralMode.MIXED && cellRandom.nextDouble() < 0.35D);
        boolean dry = biome.is(BiomeTags.IS_BADLANDS) || biome.is(BiomeTags.HAS_VILLAGE_DESERT);

        CityConfig ruralConfig = config.copy();
        ruralConfig.style = dry ? CityStyle.RUINED : CityStyle.VERDANT;
        ruralConfig.rotation = config.rotation;
        ruralConfig.jitter = 1;
        ruralConfig.ruinChance = dry ? 20 : 0;
        ruralConfig.lampChance = 10;
        if (village) {
            ruralConfig.gridSize = 3;
            ruralConfig.plotSize = Math.min(32, Math.max(12, config.ruralPlotSize));
            ruralConfig.roadWidth = 3;
            ruralConfig.parkChance = 35;
        } else {
            ruralConfig.gridSize = 1;
            ruralConfig.plotSize = Math.min(32, Math.max(12, config.ruralPlotSize));
            ruralConfig.roadWidth = 4;
            ruralConfig.parkChance = 0;
        }

        List<Preset> presets = WorldData.getPresets();
        CityLayout layout = CityLayout.manual(center, ruralConfig, presets, seed);
        try {
            return CityWorldgen.generateChunk(level, chunk, layout, ruralConfig, presets,
                    context.random(), true);
        } catch (Exception exception) {
            FreeBuilding.LOGGER.error("FreeBuilding failed to generate rural site at cell ({}, {})",
                    cellX, cellZ, exception);
            return false;
        }
    }
}
