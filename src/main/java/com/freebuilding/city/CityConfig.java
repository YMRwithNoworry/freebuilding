package com.freebuilding.city;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.freebuilding.preset.Preset;
import net.minecraft.util.Mth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class CityConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int gridSize = 5;
    public int plotSize = 20;
    public int roadWidth = 5;
    public int jitter = 2;
    public int ruinChance = 20;
    public int lampChance = 40;
    public int parkChance = 15;
    public CityStyle style = CityStyle.MODERN;
    public RotationMode rotation = RotationMode.RANDOM;
    public long seed = 0L;
    public boolean worldgenEnabled = true;
    public int worldgenChance = 25;
    public boolean ruralEnabled = true;
    public int ruralChance = 15;
    public RuralMode ruralMode = RuralMode.MIXED;
    public int ruralPlotSize = 16;
    public Map<String, PresetSetting> presets = new LinkedHashMap<>();
    public Map<String, CategorySetting> categories = new LinkedHashMap<>();

    public CityConfig copy() {
        CityConfig copy = new CityConfig();
        copy.gridSize = gridSize;
        copy.plotSize = plotSize;
        copy.roadWidth = roadWidth;
        copy.jitter = jitter;
        copy.ruinChance = ruinChance;
        copy.lampChance = lampChance;
        copy.parkChance = parkChance;
        copy.style = style;
        copy.rotation = rotation;
        copy.seed = seed;
        copy.worldgenEnabled = worldgenEnabled;
        copy.worldgenChance = worldgenChance;
        copy.ruralEnabled = ruralEnabled;
        copy.ruralChance = ruralChance;
        copy.ruralMode = ruralMode;
        copy.ruralPlotSize = ruralPlotSize;
        copy.presets = new LinkedHashMap<>();
        for (Map.Entry<String, PresetSetting> entry : presets.entrySet()) {
            copy.presets.put(entry.getKey(), entry.getValue().copy());
        }
        for (Map.Entry<String, CategorySetting> entry : categories.entrySet()) {
            copy.categories.put(entry.getKey(), entry.getValue().copy());
        }
        return copy;
    }

    public void validate() {
        gridSize = Mth.clamp(gridSize, 3, 15);
        plotSize = Mth.clamp(plotSize, 12, 64);
        roadWidth = Mth.clamp(roadWidth, 3, 9);
        jitter = Mth.clamp(jitter, 0, 4);
        ruinChance = Mth.clamp(ruinChance, 0, 100);
        lampChance = Mth.clamp(lampChance, 0, 100);
        parkChance = Mth.clamp(parkChance, 0, 100);
        worldgenChance = Mth.clamp(worldgenChance, 0, 100);
        ruralChance = Mth.clamp(ruralChance, 0, 100);
        ruralPlotSize = Mth.clamp(ruralPlotSize, 12, 32);
        if (style == null) {
            style = CityStyle.MODERN;
        }
        if (rotation == null) {
            rotation = RotationMode.RANDOM;
        }
        if (ruralMode == null) {
            ruralMode = RuralMode.MIXED;
        }
        if (presets == null) {
            presets = new LinkedHashMap<>();
        }
        if (categories == null) {
            categories = new LinkedHashMap<>();
        }
        Map<String, CategorySetting> normalizedCategories = new LinkedHashMap<>();
        for (Map.Entry<String, CategorySetting> entry : categories.entrySet()) {
            String category = normalizeCategory(entry.getKey());
            CategorySetting setting = entry.getValue() == null ? new CategorySetting() : entry.getValue();
            setting.weight = Mth.clamp(setting.weight, 0, 100);
            normalizedCategories.putIfAbsent(category, setting);
        }
        categories = normalizedCategories;
        for (Map.Entry<String, PresetSetting> entry : presets.entrySet()) {
            if (entry.getValue() == null) {
                entry.setValue(new PresetSetting());
            }
            PresetSetting setting = entry.getValue();
            setting.weight = Mth.clamp(setting.weight, 0, 100);
            setting.category = normalizeCategory(setting.category);
            ensureCategory(setting.category);
        }
    }

    public long ensureSeed() {
        if (seed == 0L) {
            seed = new Random().nextLong();
        }
        return seed;
    }

    public PresetSetting setting(String name) {
        PresetSetting setting = presets.computeIfAbsent(name, key -> new PresetSetting());
        setting.category = normalizeCategory(setting.category);
        ensureCategory(setting.category);
        return setting;
    }

    public void ensurePreset(String name) {
        setting(name);
    }

    public void removePreset(String name) {
        presets.remove(name);
    }

    public CategorySetting categorySetting(String category) {
        String normalized = normalizeCategory(category);
        return categories.computeIfAbsent(normalized, key -> new CategorySetting());
    }

    public void ensureCategory(String category) {
        categorySetting(category);
    }

    public static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "city";
        }
        String normalized = category.trim().toLowerCase(Locale.ROOT);
        return normalized.length() > 32 ? normalized.substring(0, 32) : normalized;
    }

    /**
     * Largest footprint among enabled, non-zero-weight presets. The plot must be at least this
     * large, otherwise the preset is silently skipped by the city layout.
     */
    public int requiredPlotSize(List<Preset> availablePresets) {
        int required = 12;
        for (Preset preset : availablePresets) {
            PresetSetting setting = presets.get(preset.name());
            CategorySetting category = setting == null ? null : categories.get(normalizeCategory(setting.category));
            if (setting != null && setting.enabled && setting.weight > 0
                    && category != null && category.weight > 0) {
                required = Math.max(required, Math.max(preset.size().getX(), preset.size().getZ()));
            }
        }
        return Mth.clamp(required, 12, 64);
    }

    /**
     * Grows the plot size when the current value cannot fit an enabled preset.
     *
     * @return true when the value was changed
     */
    public boolean ensurePlotSizeFor(List<Preset> availablePresets) {
        int required = requiredPlotSize(availablePresets);
        if (plotSize < required) {
            plotSize = required;
            return true;
        }
        return false;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static CityConfig fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new CityConfig();
        }
        try {
            CityConfig config = GSON.fromJson(json, CityConfig.class);
            if (config == null) {
                return new CityConfig();
            }
            config.validate();
            return config;
        } catch (JsonSyntaxException exception) {
            return new CityConfig();
        }
    }
}
