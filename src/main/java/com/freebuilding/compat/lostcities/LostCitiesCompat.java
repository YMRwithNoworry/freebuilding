package com.freebuilding.compat.lostcities;

import com.freebuilding.preset.PresetLibrary;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;

import java.nio.file.Path;
import java.util.List;

/**
 * Optional compatibility with McJty's The Lost Cities.
 *
 * <p>No compile-time dependency is used. When the mod is present its datapack resources are
 * read and converted into ordinary FreeBuilding presets.</p>
 */
public final class LostCitiesCompat {
    public static final String MOD_ID = "lostcities";

    private LostCitiesCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
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
}
