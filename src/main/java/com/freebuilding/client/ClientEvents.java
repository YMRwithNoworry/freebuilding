package com.freebuilding.client;

import com.freebuilding.FreeBuilding;
import com.freebuilding.WorldCreationSetup;
import com.freebuilding.compat.lostcities.LostCitiesCompat;
import com.freebuilding.preset.PresetFileInfo;
import com.freebuilding.preset.PresetLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = FreeBuilding.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {
    private static final Map<Object, Boolean> WIRED_STATES = new WeakHashMap<>();
    private static String targetFolder;
    private static WorldCreationSetup setup;

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof CreateWorldScreen createWorld)) {
            return;
        }
        if (WIRED_STATES.put(createWorld.getUiState(), Boolean.TRUE) == null) {
            createWorld.getUiState().addListener(state -> persistForTarget(state.getTargetFolder()));
        }
        int width = Math.min(150, Math.max(110, createWorld.width - 20));
        int x = 10;
        int y = Math.max(4, createWorld.height - 30);
        Button button = Button.builder(Component.translatable("freebuilding.world_preset.open"), ignored ->
                openPresetScreen(createWorld)).bounds(x, y, width, 20).build();
        event.addListener(button);
    }

    private static void openPresetScreen(CreateWorldScreen parent) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            if (LostCitiesCompat.isLoaded()) {
                LostCitiesCompat.importClientPresets(minecraft.getResourceManager(), BuiltInRegistries.BLOCK.asLookup());
            }
        } catch (Exception exception) {
            FreeBuilding.LOGGER.warn("Failed to cache Lost Cities presets for world creation", exception);
        }
        List<PresetFileInfo> available = PresetLibrary.listInfos();
        String folder = parent.getUiState().getTargetFolder();
        if (setup == null || !folder.equals(targetFolder)) {
            setup = WorldCreationSetup.defaults(available);
            targetFolder = folder;
        }
        WorldCreationSetup initial = setup.copy();
        minecraft.setScreen(new WorldPresetSelectionScreen(parent, available, initial, selected -> {
            setup = selected.copy();
            targetFolder = parent.getUiState().getTargetFolder();
            persistForTarget(targetFolder);
            minecraft.setScreen(parent);
        }));
    }

    private static void persistForTarget(String folder) {
        if (setup == null || folder == null || folder.isBlank()) {
            return;
        }
        targetFolder = folder;
        Minecraft minecraft = Minecraft.getInstance();
        Path file = minecraft.getLevelSource().getBaseDir().resolve(folder)
                .resolve("freebuilding").resolve("creation.json");
        try {
            setup.write(file);
        } catch (IOException exception) {
            FreeBuilding.LOGGER.warn("Failed to save new-world FreeBuilding setup {}", file, exception);
        }
    }
}
