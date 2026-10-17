package com.freebuilding.compat.lostcities;

import com.freebuilding.FreeBuilding;
import com.freebuilding.WorldData;
import com.freebuilding.preset.PresetLibrary;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Optional compatibility with McJty's The Lost Cities.
 *
 * <p>No compile-time dependency is used. When the mod is present its datapack resources are
 * read and converted into ordinary FreeBuilding presets.</p>
 */
public final class LostCitiesCompat {
    public static final String MOD_ID = "lostcities";
    private static final String LOST_CITY_EVENT = "mcjty.lostcities.api.LostCityEvent$CharacteristicsEvent";
    private static final String PRE_GEN_CITY_EVENT = "mcjty.lostcities.api.LostCityEvent$PreGenCityChunkEvent";
    private static final String LOST_CITIES_CLASS = "mcjty.lostcities.LostCities";
    private static final String LOST_CITY_INFORMATION_CLASS = "mcjty.lostcities.api.ILostCityInformation";
    private static final String LOST_CHUNK_INFO_CLASS = "mcjty.lostcities.api.ILostChunkInfo";
    private static final AtomicBoolean BRIDGE_ERROR_LOGGED = new AtomicBoolean();

    @Nullable
    private static volatile LostCitiesBridge bridge;
    private static volatile boolean bridgeLookupAttempted;
    private static volatile boolean worldgenCompatibilityRegistered;

    private LostCitiesCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * Registers the optional Lost Cities worldgen hooks without loading any Lost Cities type in
     * a method signature. A FreeBuilding city cell reserves its chunks for FreeBuilding, while
     * Lost Cities remains unchanged everywhere else.
     */
    public static void registerWorldgenCompatibility() {
        if (!isLoaded() || worldgenCompatibilityRegistered) {
            return;
        }
        synchronized (LostCitiesCompat.class) {
            if (worldgenCompatibilityRegistered) {
                return;
            }
            boolean characteristics = registerEventListener(LOST_CITY_EVENT,
                    (event, access) -> suppressLostCityCharacteristics(event, access));
            boolean preGeneration = registerEventListener(PRE_GEN_CITY_EVENT,
                    (event, access) -> cancelLostCityGeneration(event, access));
            worldgenCompatibilityRegistered = characteristics || preGeneration;
            if (worldgenCompatibilityRegistered) {
                FreeBuilding.LOGGER.info("Lost Cities worldgen compatibility enabled (characteristics={}, preGen={})",
                        characteristics, preGeneration);
            }
        }
    }

    /**
     * Checks the native Lost Cities city map for a chunk. This is used by rural generation and
     * only returns true when the optional API is available.
     */
    public static boolean isLostCitiesCity(WorldGenLevel level, int chunkX, int chunkZ) {
        if (!isLoaded()) {
            return false;
        }
        LostCitiesBridge current = getBridge();
        if (current == null) {
            return false;
        }
        try {
            Object information = current.getLostInfo.invoke(current.api, level.getLevel());
            if (information == null) {
                return false;
            }
            Object chunkInfo = current.getChunkInfo.invoke(information, chunkX, chunkZ);
            return chunkInfo != null && Boolean.TRUE.equals(current.isCity.invoke(chunkInfo));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logBridgeError(exception);
            return false;
        }
    }

    public static List<ImportedPreset> importBuildings(MinecraftServer server, HolderLookup<Block> blockLookup) {
        if (!isLoaded()) {
            return List.of();
        }
        return LostCitiesImporter.importBuildings(server.getResourceManager(), blockLookup);
    }

    public static List<ImportedPreset> importBuildings(ResourceManager resources, HolderLookup<Block> blockLookup) {
        if (!isLoaded()) {
            return List.of();
        }
        return LostCitiesImporter.importBuildings(resources, blockLookup);
    }

    /** Imports missing Lost Cities presets before a new singleplayer world is created. */
    public static int importClientPresets(ResourceManager resources, HolderLookup<Block> blockLookup) {
        if (!isLoaded()) {
            return -1;
        }
        List<ImportedPreset> imported = importBuildings(resources, blockLookup);
        if (imported.isEmpty()) {
            try {
                Path modFile = ModList.get().getModFileById(MOD_ID).getFile().getFilePath();
                imported = LostCitiesImporter.importBuildings(modFile, blockLookup);
            } catch (Exception exception) {
                com.freebuilding.FreeBuilding.LOGGER.warn("Failed to inspect the Lost Cities mod file", exception);
            }
        }
        int count = 0;
        for (ImportedPreset preset : imported) {
            if (PresetLibrary.exists(preset.name())) {
                continue;
            }
            try {
                PresetLibrary.save(preset.name(), preset.template().save(new net.minecraft.nbt.CompoundTag()));
                count++;
            } catch (Exception exception) {
                com.freebuilding.FreeBuilding.LOGGER.warn("Failed to cache client Lost Cities preset {}",
                        preset.name(), exception);
            }
        }
        return count;
    }

    private static boolean registerEventListener(String className, EventHandler handler) {
        try {
            Class<?> eventClass = Class.forName(className, false, LostCitiesCompat.class.getClassLoader());
            if (!Event.class.isAssignableFrom(eventClass)) {
                return false;
            }
            EventAccess access = EventAccess.create(eventClass,
                    className.equals(LOST_CITY_EVENT));
            Consumer<Event> listener = event -> {
                try {
                    handler.accept(event, access);
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    logBridgeError(exception);
                }
            };
            addTypedListener(eventClass, listener);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addTypedListener(Class<?> eventClass, Consumer<Event> listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false,
                (Class) eventClass, (Consumer) listener);
    }

    private static void suppressLostCityCharacteristics(Event event, EventAccess access)
            throws ReflectiveOperationException {
        if (!isFreeBuildingCityCell(access.world(event), access.chunkX(event), access.chunkZ(event))) {
            return;
        }
        Object characteristics = access.characteristics.invoke(event);
        access.isCity.setBoolean(characteristics, false);
        if (access.couldHaveBuilding != null) {
            access.couldHaveBuilding.setBoolean(characteristics, false);
        }
    }

    private static void cancelLostCityGeneration(Event event, EventAccess access)
            throws ReflectiveOperationException {
        if (isFreeBuildingCityCell(access.world(event), access.chunkX(event), access.chunkZ(event))
                && event.isCancelable()) {
            event.setCanceled(true);
        }
    }

    private static boolean isFreeBuildingCityCell(Object worldObject, int chunkX, int chunkZ) {
        if (!(worldObject instanceof WorldGenLevel world)
                || world.getLevel().dimension() != Level.OVERWORLD
                || !WorldData.isInitialized()) {
            return false;
        }
        int cellX = Math.floorDiv(chunkX, WorldData.WORLDGEN_CITY_SIZE / 16);
        int cellZ = Math.floorDiv(chunkZ, WorldData.WORLDGEN_CITY_SIZE / 16);
        return WorldData.cityExists(cellX, cellZ);
    }

    @Nullable
    private static LostCitiesBridge getBridge() {
        if (bridgeLookupAttempted) {
            return bridge;
        }
        synchronized (LostCitiesCompat.class) {
            if (!bridgeLookupAttempted) {
                bridgeLookupAttempted = true;
                try {
                    ClassLoader loader = LostCitiesCompat.class.getClassLoader();
                    Class<?> lostCitiesClass = Class.forName(LOST_CITIES_CLASS, false, loader);
                    Object api = lostCitiesClass.getField("lostCitiesImp").get(null);
                    if (api == null) {
                        return null;
                    }
                    Class<?> informationClass = Class.forName(LOST_CITY_INFORMATION_CLASS, false, loader);
                    Class<?> chunkInfoClass = Class.forName(LOST_CHUNK_INFO_CLASS, false, loader);
                    bridge = new LostCitiesBridge(api,
                            api.getClass().getMethod("getLostInfo", Level.class),
                            informationClass.getMethod("getChunkInfo", int.class, int.class),
                            chunkInfoClass.getMethod("isCity"));
                } catch (ReflectiveOperationException | LinkageError exception) {
                    logBridgeError(exception);
                }
            }
        }
        return bridge;
    }

    private static void logBridgeError(Throwable exception) {
        if (BRIDGE_ERROR_LOGGED.compareAndSet(false, true)) {
            FreeBuilding.LOGGER.warn("Lost Cities compatibility bridge is unavailable", exception);
        }
    }

    @FunctionalInterface
    private interface EventHandler {
        void accept(Event event, EventAccess access) throws ReflectiveOperationException;
    }

    private static final class EventAccess {
        private final Method world;
        private final Method chunkX;
        private final Method chunkZ;
        @Nullable
        private final Method characteristics;
        private final Field isCity;
        @Nullable
        private final Field couldHaveBuilding;

        private EventAccess(Method world, Method chunkX, Method chunkZ, @Nullable Method characteristics,
                            @Nullable Field isCity, @Nullable Field couldHaveBuilding) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.characteristics = characteristics;
            this.isCity = isCity;
            this.couldHaveBuilding = couldHaveBuilding;
        }

        private static EventAccess create(Class<?> eventClass, boolean characteristicsEvent)
                throws ReflectiveOperationException {
            Method world = eventClass.getMethod("getWorld");
            Method chunkX = eventClass.getMethod("getChunkX");
            Method chunkZ = eventClass.getMethod("getChunkZ");
            if (!characteristicsEvent) {
                return new EventAccess(world, chunkX, chunkZ, null, null, null);
            }
            Method characteristics = eventClass.getMethod("getCharacteristics");
            Class<?> characteristicsClass = characteristics.getReturnType();
            Field isCity = characteristicsClass.getField("isCity");
            Field couldHaveBuilding;
            try {
                couldHaveBuilding = characteristicsClass.getField("couldHaveBuilding");
            } catch (NoSuchFieldException exception) {
                couldHaveBuilding = null;
            }
            return new EventAccess(world, chunkX, chunkZ, characteristics, isCity, couldHaveBuilding);
        }

        private Object world(Event event) throws ReflectiveOperationException {
            return world.invoke(event);
        }

        private int chunkX(Event event) throws ReflectiveOperationException {
            return ((Number) chunkX.invoke(event)).intValue();
        }

        private int chunkZ(Event event) throws ReflectiveOperationException {
            return ((Number) chunkZ.invoke(event)).intValue();
        }
    }

    private static final class LostCitiesBridge {
        private final Object api;
        private final Method getLostInfo;
        private final Method getChunkInfo;
        private final Method isCity;

        private LostCitiesBridge(Object api, Method getLostInfo, Method getChunkInfo, Method isCity) {
            this.api = api;
            this.getLostInfo = getLostInfo;
            this.getChunkInfo = getChunkInfo;
            this.isCity = isCity;
        }
    }
}
