package com.freebuilding.city;

import com.freebuilding.Config;
import com.freebuilding.FreeBuilding;
import com.freebuilding.WorldData;
import com.freebuilding.network.BuildProgressPacket;
import com.freebuilding.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public final class CityBuildJob {
    public static final List<CityBuildJob> JOBS = new ArrayList<>();

    private enum Phase {
        TERRAIN,
        ROADS,
        BUILDINGS,
        PARKS,
        LAMPS,
        DONE
    }

    private final ServerLevel level;
    @javax.annotation.Nullable
    private final ServerPlayer player;
    private final CityLayout layout;
    private final CityConfig config;
    private final long startedAt = System.currentTimeMillis();
    private final RandomSource random;

    private Phase phase = Phase.TERRAIN;
    private int terrainX;
    private int terrainZ;
    private int roadX;
    private int roadZ;
    private int plotIndex;
    private int tickCounter;
    private int lastPercent = -1;

    private CityBuildJob(ServerLevel level, @javax.annotation.Nullable ServerPlayer player, CityLayout layout, CityConfig config) {
        this.level = level;
        this.player = player;
        this.layout = layout;
        this.config = config;
        this.random = RandomSource.create(layout.seed ^ 0x51ED270B4F5A1C3DL);
        this.terrainX = layout.minX();
        this.terrainZ = layout.minZ();
        this.roadX = layout.minX();
        this.roadZ = layout.minZ();
    }

    public static void start(ServerPlayer player, CityConfig input) {
        startAt(player.serverLevel(), player.blockPosition(), input, player);
    }

    public static void startAt(ServerLevel level, net.minecraft.core.BlockPos center, CityConfig input,
                               @javax.annotation.Nullable ServerPlayer player) {
        CityConfig config = input.copy();
        config.validate();
        config.ensureSeed();
        CityLayout layout = CityLayout.manual(center, config, WorldData.getPresets(), config.seed);
        CityBuildJob job = new CityBuildJob(level, player, layout, config);
        JOBS.add(job);
        WorldData.updateConfig(config);
        if (player != null) {
            job.movePlayerSafe();
            player.sendSystemMessage(Component.translatable("freebuilding.msg.build.start",
                    layout.totalSize, layout.totalSize, layout.originX, layout.originZ));
        }
        FreeBuilding.LOGGER.info("FreeBuilding build job started at ({}, {}) groundY={} size={}",
                layout.originX, layout.originZ, layout.groundY, layout.totalSize);
    }

    public boolean tick() {
        tickCounter++;
        int budget = Math.max(256, Config.BUILD_BLOCKS_PER_TICK.get());
        switch (phase) {
            case TERRAIN -> tickTerrain(Math.max(64, budget / 12));
            case ROADS -> tickRoads(budget);
            case BUILDINGS -> tickBuildings();
            case PARKS -> tickParks();
            case LAMPS -> tickLamps();
            case DONE -> {
                if (player != null) {
                    player.sendSystemMessage(Component.translatable("freebuilding.msg.build.done",
                            (System.currentTimeMillis() - startedAt) / 1000.0D));
                }
                FreeBuilding.LOGGER.info("FreeBuilding build job finished in {} ms",
                        System.currentTimeMillis() - startedAt);
                return true;
            }
        }
        if (tickCounter % 20 == 0) {
            sendProgress();
        }
        return false;
    }

    private void tickTerrain(int budget) {
        for (int i = 0; i < budget; i++) {
            if (terrainZ > layout.maxZ()) {
                phase = Phase.ROADS;
                sendProgress();
                return;
            }
            level.getChunk(terrainX >> 4, terrainZ >> 4);
            flattenColumn(terrainX, terrainZ);
            terrainX++;
            if (terrainX > layout.maxX()) {
                terrainX = layout.minX();
                terrainZ++;
            }
        }
    }

    private void tickRoads(int budget) {
        for (int i = 0; i < budget; i++) {
            if (roadZ > layout.maxZ()) {
                phase = Phase.BUILDINGS;
                sendProgress();
                return;
            }
            placeSurface(roadX, roadZ);
            roadX++;
            if (roadX > layout.maxX()) {
                roadX = layout.minX();
                roadZ++;
            }
        }
    }

    private void tickBuildings() {
        while (plotIndex < layout.getPlots().size()) {
            Plot plot = layout.getPlots().get(plotIndex++);
            if (!plot.hasBuilding()) {
                continue;
            }
            placeBuilding(plot);
            return;
        }
        phase = Phase.PARKS;
        plotIndex = 0;
        sendProgress();
    }

    private void tickParks() {
        while (plotIndex < layout.getPlots().size()) {
            Plot plot = layout.getPlots().get(plotIndex++);
            if (!plot.park) {
                continue;
            }
            ParkBuilder.place(level, plot.minX, plot.minZ, plot.maxX, plot.maxZ,
                    layout.groundY, config.style, random, null);
            return;
        }
        phase = Phase.LAMPS;
        plotIndex = 0;
        sendProgress();
    }

    private void tickLamps() {
        while (plotIndex < layout.getPlots().size()) {
            Plot plot = layout.getPlots().get(plotIndex++);
            placeLamp(plot);
            return;
        }
        phase = Phase.DONE;
        sendProgress();
    }

    private void flattenColumn(int x, int z) {
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int top = Math.min(surface, layout.groundY + 96);
        for (int y = layout.groundY + 1; y <= top; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        int bottom = Math.max(surface + 1, layout.groundY - 96);
        for (int y = bottom; y < layout.groundY; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty()) {
                level.setBlock(pos, config.style.foundationState(), 2);
            }
        }
        level.setBlock(new BlockPos(x, layout.groundY, z), config.style.groundState(), 2);
    }

    private void placeSurface(int x, int z) {
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

    private void placeBuilding(Plot plot) {
        if (plot.preset != null) {
            BlockPos origin = new BlockPos(plot.buildMinX, layout.groundY + 1, plot.buildMinZ);
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setRotation(plot.rotation)
                    .setMirror(Mirror.NONE)
                    .setIgnoreEntities(true)
                    .setKeepLiquids(false);
            plot.preset.template().placeInWorld(level, origin, origin, settings, random, 2);
        } else {
            FallbackBuilding.place(level, plot.buildMinX, layout.groundY + 1, plot.buildMinZ,
                    plot.rotation, plot.fallbackVariant, null);
        }
        applyRuins(plot);
    }

    private void applyRuins(Plot plot) {
        if (!plot.hasBuilding()) {
            return;
        }
        if (config.style != CityStyle.RUINED && random.nextDouble() * 100.0D >= config.ruinChance) {
            return;
        }
        int width = plot.buildMaxX - plot.buildMinX + 1;
        int depth = plot.buildMaxZ - plot.buildMinZ + 1;
        RandomSource ruinRandom = RandomSource.create(layout.seed
                ^ ((long) plot.gridX * 0x9E3779B97F4A7C15L)
                ^ ((long) plot.gridZ * 0xC2B2AE3D27D4EB4FL));
        int attempts = Math.max(6, (width * depth) / 8);
        for (int i = 0; i < attempts; i++) {
            int x = plot.buildMinX + ruinRandom.nextInt(width);
            int z = plot.buildMinZ + ruinRandom.nextInt(depth);
            int y = layout.groundY + 2 + ruinRandom.nextInt(8);
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (ruinRandom.nextBoolean()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            } else {
                level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 2);
            }
        }
        for (int i = 0; i < 6; i++) {
            int x = plot.buildMinX - 1 + ruinRandom.nextInt(width + 2);
            int z = plot.buildMinZ - 1 + ruinRandom.nextInt(depth + 2);
            BlockPos pos = new BlockPos(x, layout.groundY + 1, z);
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 2);
            }
        }
    }

    private void placeLamp(Plot plot) {
        if (random.nextDouble() * 100.0D >= config.lampChance) {
            return;
        }
        int x = plot.minX - 1;
        int z = plot.minZ;
        if (!layout.isRoad(x, z)) {
            return;
        }
        level.setBlock(new BlockPos(x, layout.groundY + 1, z), config.style.lampBaseState(), 2);
        level.setBlock(new BlockPos(x, layout.groundY + 2, z), config.style.lampState(), 2);
    }

    private void movePlayerSafe() {
        if (player == null) {
            return;
        }
        if (player.getX() >= layout.minX() && player.getX() <= layout.maxX() + 1
                && player.getZ() >= layout.minZ() && player.getZ() <= layout.maxZ() + 1) {
            player.teleportTo(layout.minX() - 4.5D, layout.groundY + 3.0D, layout.minZ() - 4.5D);
        }
    }

    private void sendProgress() {
        if (player == null) {
            return;
        }
        int percent;
        if (phase == Phase.TERRAIN) {
            int total = Math.max(1, layout.totalSize * layout.totalSize);
            int done = (terrainZ - layout.minZ()) * layout.totalSize + (terrainX - layout.minX());
            percent = 35 * done / total;
        } else if (phase == Phase.ROADS) {
            int total = Math.max(1, layout.totalSize * layout.totalSize);
            int done = (roadZ - layout.minZ()) * layout.totalSize + (roadX - layout.minX());
            percent = 35 + 20 * done / total;
        } else if (phase == Phase.BUILDINGS) {
            percent = 55 + 30 * plotIndex / Math.max(1, layout.getPlots().size());
        } else if (phase == Phase.PARKS) {
            percent = 85 + 10 * plotIndex / Math.max(1, layout.getPlots().size());
        } else {
            percent = 95 + 4 * plotIndex / Math.max(1, layout.getPlots().size());
        }
        percent = Math.min(99, percent);
        if (percent != lastPercent) {
            lastPercent = percent;
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new BuildProgressPacket(percent,
                            Component.translatable("freebuilding.msg.build.progress", percent).getString()));
        }
    }
}
