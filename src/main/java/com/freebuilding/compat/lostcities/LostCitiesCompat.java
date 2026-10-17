package com.freebuilding.compat.lostcities;

import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;

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
        return LostCitiesImporter.importBuildings(server, blockLookup);
    }
}
