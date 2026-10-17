package com.freebuilding.worldgen;

import com.freebuilding.city.CityConfig;
import com.freebuilding.city.CityLayout;
import com.freebuilding.city.CityStyle;
import com.freebuilding.city.FallbackBuilding;
import com.freebuilding.city.ParkBuilder;
import com.freebuilding.city.Plot;
import com.freebuilding.preset.Preset;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.List;

public final class CityWorldgen {
    private CityWorldgen() {
    }

    public static boolean generateChunk(WorldGenLevel level, ChunkPos chunk, CityLayout layout,
                                        CityConfig config, List<Preset> presets, RandomSource random,
                                        boolean rural) {
        int minX = Math.max(chunk.getMinBlockX(), layout.minX());
        int maxX = Math.min(chunk.getMaxBlockX(), layout.maxX());
        int minZ = Math.max(chunk.getMinBlockZ(), layout.minZ());
        int maxZ = Math.min(chunk.getMaxBlockZ(), layout.maxZ());
        if (minX > maxX || minZ > maxZ) {
            return false;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                flattenColumn(level, x, z, layout.groundY, config.style);
                placeSurface(level, layout, config, x, z);
            }
        }

        for (Plot plot : layout.getPlots()) {
            if (plot.hasBuilding() && plot.buildingIntersects(chunk)) {
                if (plot.preset != null) {
                    placePresetSlice(level, plot, layout.groundY, chunk);
                } else {
                    FallbackBuilding.place(level, plot.buildMinX, layout.groundY + 1, plot.buildMinZ,
                            plot.rotation, plot.fallbackVariant, chunk);
                }
                applyRuinSlice(level, layout, plot, config, chunk, random);
            }
            if (plot.park && plot.intersects(chunk)) {
                if (rural) {
                    RuralDecorator.placePark(level, layout, plot, config, chunk);
                } else {
                    ParkBuilder.place(level, plot.minX, plot.minZ, plot.maxX, plot.maxZ,
                            layout.groundY, config.style, random, chunk);
                }
            }
            placeLamp(level, layout, plot, config, chunk);
        }
        return true;
    }

    private static void flattenColumn(WorldGenLevel level, int x, int z, int groundY, CityStyle style) {
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        int top = Math.min(surface, groundY + 96);
        for (int y = groundY + 1; y <= top; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        int bottom = Math.max(surface + 1, groundY - 96);
        for (int y = bottom; y < groundY; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty()) {
                level.setBlock(pos, style.foundationState(), 2);
            }
        }
        level.setBlock(new BlockPos(x, groundY, z), style.groundState(), 2);
    }

    private static void placeSurface(WorldGenLevel level, CityLayout layout, CityConfig config, int x, int z) {
        Plot plot = layout.plotAt(x, z);
        BlockState state;
        if (plot != null) {
            state = config.style.plotGroundState();
        } else if (layout.isSidewalk(x, z)) {
            state = config.style.sidewalkState();
        } else {
            state = config.style.roadState();
        }
        level.setBlock(new BlockPos(x, layout.groundY, z), state, 2);
    }

    private static void placePresetSlice(WorldGenLevel level, Plot plot, int groundY, ChunkPos chunk) {
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(plot.rotation)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(true)
                .setKeepLiquids(false);
        BlockPos origin = new BlockPos(plot.buildMinX, groundY + 1, plot.buildMinZ);
        List<StructureTemplate.StructureBlockInfo> blocks =
                plot.preset.template().filterBlocks(origin, settings, Blocks.STRUCTURE_VOID);
        for (StructureTemplate.StructureBlockInfo info : blocks) {
            BlockPos pos = info.pos();
            if (pos.getX() >> 4 != chunk.x || pos.getZ() >> 4 != chunk.z) {
                continue;
            }
            level.setBlock(pos, info.state(), 2);
            if (info.nbt() != null) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) {
                    try {
                        blockEntity.load(info.nbt());
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    private static void applyRuinSlice(WorldGenLevel level, CityLayout layout, Plot plot, CityConfig config,
                                       ChunkPos chunk, RandomSource random) {
        if (config.style != CityStyle.RUINED && random.nextDouble() * 100.0D >= config.ruinChance) {
            return;
        }
        int width = plot.buildMaxX - plot.buildMinX + 1;
        int depth = plot.buildMaxZ - plot.buildMinZ + 1;
        for (int i = 0; i < 8; i++) {
            int x = plot.buildMinX + random.nextInt(width);
            int z = plot.buildMinZ + random.nextInt(depth);
            if (x >> 4 != chunk.x || z >> 4 != chunk.z) {
                continue;
            }
            int y = layout.groundY + 2 + random.nextInt(8);
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            level.setBlock(pos, random.nextBoolean()
                    ? Blocks.AIR.defaultBlockState()
                    : Blocks.COBWEB.defaultBlockState(), 2);
        }
        for (int i = 0; i < 4; i++) {
            int x = plot.buildMinX - 1 + random.nextInt(width + 2);
            int z = plot.buildMinZ - 1 + random.nextInt(depth + 2);
            if (x >> 4 != chunk.x || z >> 4 != chunk.z) {
                continue;
            }
            BlockPos pos = new BlockPos(x, layout.groundY + 1, z);
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 2);
            }
        }
    }

    private static void placeLamp(WorldGenLevel level, CityLayout layout, Plot plot, CityConfig config,
                                  ChunkPos chunk) {
        RandomSource lampRandom = RandomSource.create(layout.seed
                ^ ((long) plot.gridX * 0x9E3779B97F4A7C15L)
                ^ ((long) plot.gridZ * 0xC2B2AE3D27D4EB4FL)
                ^ 0x51ED270BL);
        if (lampRandom.nextDouble() * 100.0D >= config.lampChance) {
            return;
        }
        int x = plot.minX - 1;
        int z = plot.minZ;
        if (x >> 4 != chunk.x || z >> 4 != chunk.z || !layout.isRoad(x, z)) {
            return;
        }
        level.setBlock(new BlockPos(x, layout.groundY + 1, z), config.style.lampBaseState(), 2);
        level.setBlock(new BlockPos(x, layout.groundY + 2, z), config.style.lampState(), 2);
    }
}
