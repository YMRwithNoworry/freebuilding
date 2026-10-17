package com.freebuilding.city;

import com.freebuilding.preset.Preset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CityLayout {
    public final int originX;
    public final int originZ;
    public final int groundY;
    public final int gridSize;
    public final int plotSize;
    public final int roadWidth;
    public final int totalSize;
    public final long seed;

    private final Plot[] plots;
    private final List<Plot> plotList;

    private CityLayout(int originX, int originZ, int groundY, int gridSize, int plotSize, int roadWidth,
                       long seed, CityConfig config, List<Preset> presets) {
        this.originX = originX;
        this.originZ = originZ;
        this.groundY = groundY;
        this.gridSize = gridSize;
        this.plotSize = plotSize;
        this.roadWidth = roadWidth;
        this.totalSize = gridSize * plotSize + (gridSize + 1) * roadWidth;
        this.seed = seed;
        this.plots = new Plot[gridSize * gridSize];
        List<Plot> list = new ArrayList<>(gridSize * gridSize);
        for (int gx = 0; gx < gridSize; gx++) {
            for (int gz = 0; gz < gridSize; gz++) {
                Plot plot = createPlot(gx, gz, config, presets);
                plots[gx * gridSize + gz] = plot;
                list.add(plot);
            }
        }
        this.plotList = List.copyOf(list);
    }

    public static CityLayout manual(BlockPos center, CityConfig config, List<Preset> presets, long seed) {
        int total = config.gridSize * config.plotSize + (config.gridSize + 1) * config.roadWidth;
        int originX = center.getX() - total / 2;
        int originZ = center.getZ() - total / 2;
        return new CityLayout(originX, originZ, center.getY() - 1, config.gridSize, config.plotSize,
                config.roadWidth, seed, config, presets);
    }

    public static CityLayout auto(BlockPos cellOrigin, int cellSize, CityConfig config, List<Preset> presets,
                                  long seed, int groundY) {
        int usable = Math.max(config.plotSize, cellSize - config.roadWidth);
        int grid = Math.max(1, usable / (config.plotSize + config.roadWidth));
        int total = grid * config.plotSize + (grid + 1) * config.roadWidth;
        int margin = Math.max(0, (cellSize - total) / 2);
        return new CityLayout(cellOrigin.getX() + margin, cellOrigin.getZ() + margin, groundY,
                grid, config.plotSize, config.roadWidth, seed, config, presets);
    }

    private Plot createPlot(int gx, int gz, CityConfig config, List<Preset> presets) {
        int minX = originX + roadWidth + gx * (plotSize + roadWidth);
        int minZ = originZ + roadWidth + gz * (plotSize + roadWidth);
        int maxX = minX + plotSize - 1;
        int maxZ = minZ + plotSize - 1;
        long plotSeed = seed
                ^ ((long) gx * 341873128712L)
                ^ ((long) gz * 132897987541L)
                ^ 0x9E3779B97F4A7C15L;
        RandomSource random = RandomSource.create(plotSeed);
        boolean park = random.nextDouble() * 100.0D < config.parkChance;
        Placement placement = park ? null : choosePlacement(config, presets, random);
        if (!park && placement == null) {
            placement = chooseFallback(config, random);
        }
        if (placement == null) {
            return new Plot(gx, gz, minX, minZ, maxX, maxZ, Integer.MIN_VALUE, Integer.MIN_VALUE,
                    Integer.MIN_VALUE, Integer.MIN_VALUE, null, Rotation.NONE, 0, park);
        }


        int width = placement.width;
        int depth = placement.depth;
        int jitterX = config.jitter > 0 ? random.nextInt(config.jitter * 2 + 1) - config.jitter : 0;
        int jitterZ = config.jitter > 0 ? random.nextInt(config.jitter * 2 + 1) - config.jitter : 0;
        int buildMinX = minX + (plotSize - width) / 2 + jitterX;
        int buildMinZ = minZ + (plotSize - depth) / 2 + jitterZ;
        buildMinX = Mth.clamp(buildMinX, minX, maxX - width + 1);
        buildMinZ = Mth.clamp(buildMinZ, minZ, maxZ - depth + 1);
        return new Plot(gx, gz, minX, minZ, maxX, maxZ,
                buildMinX, buildMinZ, buildMinX + width - 1, buildMinZ + depth - 1,
                placement.preset, placement.rotation, placement.fallbackVariant, false);
    }

    @Nullable
    private Placement choosePlacement(CityConfig config, List<Preset> presets, RandomSource random) {
        Map<String, List<Preset>> categoryPools = new LinkedHashMap<>();
        Map<String, Integer> categoryWeights = new LinkedHashMap<>();
        for (Preset preset : presets) {
            PresetSetting setting = config.presets.get(preset.name());
            if (setting == null || !setting.enabled || setting.weight <= 0) {
                continue;
            }
            String category = CityConfig.normalizeCategory(setting.category);
            CategorySetting categorySetting = config.categories.get(category);
            if (categorySetting == null || categorySetting.weight <= 0) {
                continue;
            }
            categoryPools.computeIfAbsent(category, key -> new ArrayList<>()).add(preset);
            categoryWeights.put(category, categorySetting.weight);
        }

        while (!categoryPools.isEmpty()) {
            int totalCategoryWeight = 0;
            for (int weight : categoryWeights.values()) {
                totalCategoryWeight += Math.max(1, weight);
            }
            int categoryRoll = random.nextInt(Math.max(1, totalCategoryWeight));
            String pickedCategory = categoryPools.keySet().iterator().next();
            int categoryAccumulated = 0;
            for (String category : categoryPools.keySet()) {
                categoryAccumulated += Math.max(1, categoryWeights.getOrDefault(category, 0));
                if (categoryRoll < categoryAccumulated) {
                    pickedCategory = category;
                    break;
                }
            }

            List<Preset> pool = categoryPools.get(pickedCategory);
            int totalWeight = 0;
            for (Preset preset : pool) {
                totalWeight += Math.max(1, config.presets.get(preset.name()).weight);
            }
            int roll = random.nextInt(Math.max(1, totalWeight));
            int accumulated = 0;
            Preset picked = pool.get(0);
            for (Preset preset : pool) {
                accumulated += Math.max(1, config.presets.get(preset.name()).weight);
                if (roll < accumulated) {
                    picked = preset;
                    break;
                }
            }
            Rotation rotation = chooseRotation(picked.size(), config.rotation, random);
            if (rotation != null) {
                Vec3i size = picked.size(rotation);
                return new Placement(picked, rotation, -1, size.getX(), size.getZ());
            }
            pool.remove(picked);
            if (pool.isEmpty()) {
                categoryPools.remove(pickedCategory);
                categoryWeights.remove(pickedCategory);
            }
        }
        return null;
    }

    @Nullable
    private Placement chooseFallback(CityConfig config, RandomSource random) {
        int start = random.nextInt(3);
        for (int i = 0; i < 3; i++) {
            int variant = (start + i) % 3;
            Vec3i size = FallbackBuilding.size(variant);
            Rotation rotation = chooseRotation(size, config.rotation, random);
            if (rotation != null) {
                Vec3i rotated = FallbackBuilding.size(variant, rotation);
                return new Placement(null, rotation, variant, rotated.getX(), rotated.getZ());
            }
        }
        return null;
    }

    @Nullable
    private Rotation chooseRotation(Vec3i size, RotationMode mode, RandomSource random) {
        for (Rotation rotation : mode.candidates(random)) {
            Vec3i rotated = rotateSize(size, rotation);
            if (rotated.getX() <= plotSize && rotated.getZ() <= plotSize) {
                return rotation;
            }
        }
        return null;
    }

    private static Vec3i rotateSize(Vec3i size, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            return new Vec3i(size.getZ(), size.getY(), size.getX());
        }
        return size;
    }

    @Nullable
    public Plot plotAt(int x, int z) {
        int relativeX = x - originX;
        int relativeZ = z - originZ;
        if (relativeX < 0 || relativeZ < 0 || relativeX >= totalSize || relativeZ >= totalSize) {
            return null;
        }
        int cell = plotSize + roadWidth;
        int gx = relativeX / cell;
        int gz = relativeZ / cell;
        if (gx >= gridSize || gz >= gridSize) {
            return null;
        }
        if (relativeX % cell < roadWidth || relativeZ % cell < roadWidth) {
            return null;
        }
        return plots[gx * gridSize + gz];
    }

    public boolean isInside(int x, int z) {
        int relativeX = x - originX;
        int relativeZ = z - originZ;
        return relativeX >= 0 && relativeZ >= 0 && relativeX < totalSize && relativeZ < totalSize;
    }

    public boolean isRoad(int x, int z) {
        return isInside(x, z) && plotAt(x, z) == null;
    }

    public boolean isSidewalk(int x, int z) {
        if (!isRoad(x, z)) {
            return false;
        }
        return plotAt(x - 1, z) != null || plotAt(x + 1, z) != null
                || plotAt(x, z - 1) != null || plotAt(x, z + 1) != null;
    }

    public List<Plot> getPlots() {
        return plotList;
    }

    public int minX() {
        return originX;
    }

    public int minZ() {
        return originZ;
    }

    public int maxX() {
        return originX + totalSize - 1;
    }

    public int maxZ() {
        return originZ + totalSize - 1;
    }

    private record Placement(@Nullable Preset preset, Rotation rotation, int fallbackVariant, int width, int depth) {
    }
}
