package com.freebuilding;

import com.freebuilding.city.CityConfig;
import com.freebuilding.preset.Preset;
import com.freebuilding.preset.PresetInfo;
import com.freebuilding.preset.PresetManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class WorldData {
    public static final int WORLDGEN_CITY_SIZE = 256;

    private static Path rootDir;
    private static PresetManager presetManager;
    private static volatile CityConfig config = new CityConfig();
    private static volatile List<Preset> presetSnapshot = List.of();
    private static volatile long worldSeed;
    private static volatile int seaLevel = 63;
    private static volatile boolean initialized;

    private WorldData() {
    }

    public static void initialize(MinecraftServer server) {
        try {
            rootDir = server.getWorldPath(LevelResource.ROOT).resolve("freebuilding");
            Files.createDirectories(rootDir.resolve("presets"));

            CityConfig loaded = new CityConfig();
            Path configPath = rootDir.resolve("config.json");
            if (Files.isRegularFile(configPath)) {
                loaded = CityConfig.fromJson(Files.readString(configPath, StandardCharsets.UTF_8));
            }
            loaded.validate();
            loaded.ensureSeed();

            HolderGetter<Block> blockGetter = server.registryAccess().registryOrThrow(Registries.BLOCK).asLookup();
            presetManager = new PresetManager(rootDir.resolve("presets"), blockGetter, server.overworld());
            presetManager.loadAll();
            mergePresetSettings(loaded);

            config = loaded;
            presetSnapshot = List.copyOf(presetManager.getPresets());
            worldSeed = server.overworld().getSeed();
            seaLevel = server.overworld().getSeaLevel();
            saveConfig();
            initialized = true;
            FreeBuilding.LOGGER.info("Loaded {} building presets for world {}", presetSnapshot.size(), server.getWorldPath(LevelResource.ROOT));
        } catch (Exception exception) {
            FreeBuilding.LOGGER.error("Failed to initialize FreeBuilding world data", exception);
            initialized = false;
        }
    }

    private static void mergePresetSettings(CityConfig target) {
        if (presetManager == null) {
            return;
        }
        for (Preset preset : presetManager.getPresets()) {
            target.ensurePreset(preset.name());
        }
        List<String> missing = new ArrayList<>();
        for (String name : target.presets.keySet()) {
            if (presetManager.get(name) == null) {
                missing.add(name);
            }
        }
        for (String name : missing) {
            target.removePreset(name);
        }
    }

    public static CityConfig updateConfig(CityConfig input) {
        CityConfig copy = input.copy();
        copy.validate();
        copy.ensureSeed();
        mergePresetSettings(copy);
        config = copy;
        saveConfig();
        return copy;
    }

    public static void saveConfig() {
        if (rootDir == null) {
            return;
        }
        try {
            Files.createDirectories(rootDir);
            Files.writeString(rootDir.resolve("config.json"), config.toJson(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            FreeBuilding.LOGGER.error("Failed to save FreeBuilding config", exception);
        }
    }

    public static boolean savePreset(String name, StructureTemplate template) throws IOException {
        if (presetManager == null) {
            throw new IOException("World data is not initialized");
        }
        boolean existed = presetManager.get(name) != null;
        presetManager.save(name, template);
        CityConfig copy = config.copy();
        copy.ensurePreset(name);
        config = copy;
        saveConfig();
        refreshPresets();
        return existed;
    }

    public static boolean deletePreset(String name) {
        if (presetManager == null) {
            return false;
        }
        boolean deleted = presetManager.delete(name);
        CityConfig copy = config.copy();
        copy.removePreset(name);
        config = copy;
        saveConfig();
        refreshPresets();
        return deleted;
    }

    public static void reload() {
        if (presetManager == null || rootDir == null) {
            return;
        }
        CityConfig loaded = new CityConfig();
        Path configPath = rootDir.resolve("config.json");
        if (Files.isRegularFile(configPath)) {
            try {
                loaded = CityConfig.fromJson(Files.readString(configPath, StandardCharsets.UTF_8));
            } catch (IOException exception) {
                FreeBuilding.LOGGER.error("Failed to read FreeBuilding config during reload", exception);
            }
        }
        loaded.validate();
        loaded.ensureSeed();
        presetManager.loadAll();
        mergePresetSettings(loaded);
        config = loaded;
        saveConfig();
        refreshPresets();
    }

    private static void refreshPresets() {
        if (presetManager != null) {
            presetSnapshot = List.copyOf(presetManager.getPresets());
        }
    }

    public static List<PresetInfo> getPresetInfos() {
        List<PresetInfo> result = new ArrayList<>();
        for (Preset preset : presetSnapshot) {
            result.add(PresetInfo.from(preset, config));
        }
        return result;
    }

    public static List<Preset> getPresets() {
        return presetSnapshot;
    }

    @Nullable
    public static PresetManager getPresetManager() {
        return presetManager;
    }

    public static CityConfig getConfig() {
        return config;
    }

    public static long getWorldSeed() {
        return worldSeed;
    }

    public static int getSeaLevel() {
        return seaLevel;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    @Nullable
    public static Path getRootDir() {
        return rootDir;
    }

    public static boolean cityExists(int cellX, int cellZ) {
        if (!initialized) {
            return false;
        }
        CityConfig current = config;
        if (current == null || !current.worldgenEnabled || current.worldgenChance <= 0) {
            return false;
        }
        RandomSource random = RandomSource.create(worldSeed
                ^ ((long) cellX * 341873128712L)
                ^ ((long) cellZ * 132897987541L)
                ^ 0x5DEECE66DL);
        return random.nextDouble() * 100.0D < current.worldgenChance;
    }

    @Nullable
    public static BlockPos findNearestCity(BlockPos origin, int maxCells) {
        int originCellX = Math.floorDiv(origin.getX(), WORLDGEN_CITY_SIZE);
        int originCellZ = Math.floorDiv(origin.getZ(), WORLDGEN_CITY_SIZE);
        int bestDistance = Integer.MAX_VALUE;
        BlockPos best = null;
        for (int dx = -maxCells; dx <= maxCells; dx++) {
            for (int dz = -maxCells; dz <= maxCells; dz++) {
                int cellX = originCellX + dx;
                int cellZ = originCellZ + dz;
                if (!cityExists(cellX, cellZ)) {
                    continue;
                }
                int centerX = cellX * WORLDGEN_CITY_SIZE + WORLDGEN_CITY_SIZE / 2;
                int centerZ = cellZ * WORLDGEN_CITY_SIZE + WORLDGEN_CITY_SIZE / 2;
                int distance = (centerX - origin.getX()) * (centerX - origin.getX())
                        + (centerZ - origin.getZ()) * (centerZ - origin.getZ());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new BlockPos(centerX, seaLevel, centerZ);
                }
            }
        }
        return best;
    }

    public static void clear() {
        rootDir = null;
        presetManager = null;
        config = new CityConfig();
        presetSnapshot = List.of();
        worldSeed = 0L;
        seaLevel = 63;
        initialized = false;
    }
}
