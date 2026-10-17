package com.freebuilding;

import com.freebuilding.city.CityConfig;
import com.freebuilding.compat.lostcities.ImportedPreset;
import com.freebuilding.compat.lostcities.LostCitiesCompat;
import com.freebuilding.preset.Preset;
import com.freebuilding.preset.PresetInfo;
import com.freebuilding.preset.PresetLibrary;
import com.freebuilding.preset.PresetManager;
import com.freebuilding.preset.PortablePreset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
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
    public static final int RURAL_CELL_SIZE = 128;

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
            WorldCreationSetup creationSetup = Files.isRegularFile(configPath)
                    ? null : WorldCreationSetup.read(rootDir.resolve("creation.json"));
            loaded.validate();
            loaded.ensureSeed();

            HolderGetter<Block> blockGetter = server.registryAccess().registryOrThrow(Registries.BLOCK).asLookup();
            PresetLibrary.migrateFrom(rootDir.resolve("presets"));
            presetManager = new PresetManager(PresetLibrary.directory(), blockGetter, server.overworld());
            presetManager.loadAll();
            if (LostCitiesCompat.isLoaded() && !Files.exists(rootDir.resolve("lostcities_imported.marker"))) {
                try {
                    int imported = importLostCitiesInto(server, loaded, false);
                    if (imported > 0 || hasLostCitiesPresets()) {
                        writeLostCitiesMarker();
                    }
                } catch (Exception exception) {
                    FreeBuilding.LOGGER.error("Failed to auto-import Lost Cities buildings", exception);
                }
            }
            if (creationSetup != null) {
                creationSetup.apply(loaded, presetManager.getPresets().stream().map(Preset::name).toList());
            }
            mergePresetSettings(loaded);
            if (loaded.ensurePlotSizeFor(presetManager.getPresets())) {
                FreeBuilding.LOGGER.info("Auto-adjusted plot size to {} to fit the largest enabled preset", loaded.plotSize);
            }

            config = loaded;
            presetSnapshot = List.copyOf(presetManager.getPresets());
            worldSeed = server.overworld().getSeed();
            seaLevel = server.overworld().getSeaLevel();
            saveConfig();
            if (creationSetup != null) {
                try {
                    Files.deleteIfExists(rootDir.resolve("creation.json"));
                } catch (IOException exception) {
                    FreeBuilding.LOGGER.warn("Failed to remove consumed new-world setup", exception);
                }
            }
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
        copy.ensurePlotSizeFor(presetManager.getPresets());
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

    public static Path exportPreset(String name) throws IOException {
        if (presetManager == null || presetManager.get(name) == null) {
            throw new IOException("Preset not found");
        }
        return PresetLibrary.exportPortable(name);
    }

    /** @return 1 imported, 0 already existed, -1 source file was not found */
    public static int importPortablePreset(String fileName, boolean force) throws IOException {
        if (presetManager == null) {
            throw new IOException("World data is not initialized");
        }
        Path source = PresetLibrary.findImport(fileName);
        if (!Files.isRegularFile(source)) {
            return -1;
        }
        String name = PortablePreset.readName(source);
        boolean imported = presetManager.importPortable(name, source, force);
        if (!imported) {
            return 0;
        }
        CityConfig copy = config.copy();
        copy.ensurePreset(name);
        copy.ensurePlotSizeFor(presetManager.getPresets());
        config = copy;
        saveConfig();
        refreshPresets();
        return 1;
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
        loaded.ensurePlotSizeFor(presetManager.getPresets());
        config = loaded;
        saveConfig();
        refreshPresets();
    }

    private static void refreshPresets() {
        if (presetManager != null) {
            presetSnapshot = List.copyOf(presetManager.getPresets());
        }
    }

    /**
     * Imports/refreshes buildings from The Lost Cities into normal FreeBuilding presets.
     *
     * @return number of presets written, or -1 when Lost Cities is unavailable
     */
    public static int importLostCities(MinecraftServer server, boolean force) {
        if (!LostCitiesCompat.isLoaded() || presetManager == null) {
            return -1;
        }
        try {
            CityConfig copy = config.copy();
            int count = importLostCitiesInto(server, copy, force);
            copy.ensurePlotSizeFor(presetManager.getPresets());
            config = copy;
            saveConfig();
            refreshPresets();
            writeLostCitiesMarker();
            return count;
        } catch (Exception exception) {
            FreeBuilding.LOGGER.error("Failed to import Lost Cities buildings", exception);
            return -1;
        }
    }

    private static int importLostCitiesInto(MinecraftServer server, CityConfig target, boolean force) {
        if (presetManager == null) {
            return 0;
        }
        HolderLookup<Block> blockLookup = server.registryAccess().lookupOrThrow(Registries.BLOCK);
        List<ImportedPreset> imported = LostCitiesCompat.importBuildings(server, blockLookup);
        int count = 0;
        for (ImportedPreset preset : imported) {
            boolean existed = presetManager.get(preset.name()) != null;
            if (existed && !force) {
                continue;
            }
            try {
                presetManager.save(preset.name(), preset.template());
            } catch (IOException exception) {
                FreeBuilding.LOGGER.error("Failed to save imported Lost Cities preset {}", preset.name(), exception);
                continue;
            }
            target.ensurePreset(preset.name());
            if (!existed) {
                target.setting(preset.name()).weight = 5;
            }
            count++;
        }
        FreeBuilding.LOGGER.info("Imported {} Lost Cities presets (force={})", count, force);
        return count;
    }

    private static void writeLostCitiesMarker() {
        if (rootDir == null) {
            return;
        }
        try {
            Files.writeString(rootDir.resolve("lostcities_imported.marker"),
                    Long.toString(System.currentTimeMillis()), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            FreeBuilding.LOGGER.warn("Failed to write Lost Cities import marker", exception);
        }
    }

    private static boolean hasLostCitiesPresets() {
        if (presetManager == null) {
            return false;
        }
        for (Preset preset : presetManager.getPresets()) {
            if (preset.name().startsWith("lc_")) {
                return true;
            }
        }
        return false;
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

    /**
     * Rural sites use a 128x128 grid and are disabled in any 256x256 cell that contains a city.
     */
    public static boolean ruralExists(int cellX, int cellZ) {
        if (!initialized) {
            return false;
        }
        CityConfig current = config;
        if (current == null || !current.ruralEnabled || current.ruralChance <= 0) {
            return false;
        }
        int originX = cellX * RURAL_CELL_SIZE;
        int originZ = cellZ * RURAL_CELL_SIZE;
        int minCityX = Math.floorDiv(originX, WORLDGEN_CITY_SIZE);
        int maxCityX = Math.floorDiv(originX + RURAL_CELL_SIZE - 1, WORLDGEN_CITY_SIZE);
        int minCityZ = Math.floorDiv(originZ, WORLDGEN_CITY_SIZE);
        int maxCityZ = Math.floorDiv(originZ + RURAL_CELL_SIZE - 1, WORLDGEN_CITY_SIZE);
        for (int cityX = minCityX; cityX <= maxCityX; cityX++) {
            for (int cityZ = minCityZ; cityZ <= maxCityZ; cityZ++) {
                if (cityExists(cityX, cityZ)) {
                    return false;
                }
            }
        }
        RandomSource random = RandomSource.create(worldSeed
                ^ ((long) cellX * 0x9E3779B97F4A7C15L)
                ^ ((long) cellZ * 0xC2B2AE3D27D4EB4FL)
                ^ 0x6C6F737463697479L);
        return random.nextDouble() * 100.0D < current.ruralChance;
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
