package com.freebuilding;

import com.freebuilding.city.CityConfig;
import com.freebuilding.city.PresetSetting;
import com.freebuilding.preset.PresetFileInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One-shot settings written by the client world-creation screen for a new save. */
public final class WorldCreationSetup {
    public static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int format = FORMAT_VERSION;
    public boolean cityGeneration = true;
    public int cityChance = 100;
    public List<String> presets = new ArrayList<>();

    public static WorldCreationSetup defaults(Collection<? extends PresetFileInfo> available) {
        WorldCreationSetup setup = new WorldCreationSetup();
        for (PresetFileInfo info : available) {
            setup.presets.add(info.name());
        }
        return setup;
    }

    public WorldCreationSetup copy() {
        WorldCreationSetup copy = new WorldCreationSetup();
        copy.format = format;
        copy.cityGeneration = cityGeneration;
        copy.cityChance = cityChance;
        copy.presets = new ArrayList<>(presets == null ? List.of() : presets);
        return copy;
    }

    public void validate() {
        if (format != FORMAT_VERSION) {
            format = FORMAT_VERSION;
        }
        cityChance = Math.max(0, Math.min(100, cityChance));
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (presets != null) {
            for (String name : presets) {
                if (name != null && !name.isBlank()) {
                    unique.add(name);
                }
            }
        }
        presets = new ArrayList<>(unique);
    }

    public void apply(CityConfig config, Collection<String> availablePresetNames) {
        validate();
        config.worldgenEnabled = cityGeneration;
        config.worldgenChance = cityChance;
        config.ruralEnabled = false;
        config.ruralChance = 0;
        config.presets.clear();
        Set<String> selected = Set.copyOf(presets);
        for (String name : availablePresetNames) {
            PresetSetting setting = config.setting(name);
            setting.enabled = selected.contains(name);
            setting.weight = 10;
        }
        config.validate();
    }

    public static WorldCreationSetup read(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            WorldCreationSetup setup = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    WorldCreationSetup.class);
            if (setup == null) {
                return null;
            }
            setup.validate();
            return setup;
        } catch (IOException | JsonSyntaxException exception) {
            FreeBuilding.LOGGER.warn("Failed to read new-world FreeBuilding setup {}", file, exception);
            return null;
        }
    }

    public void write(Path file) throws IOException {
        validate();
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
    }
}
