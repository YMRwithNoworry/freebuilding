package com.freebuilding.city;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.util.Mth;

import java.util.LinkedHashMap;
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
    public Map<String, PresetSetting> presets = new LinkedHashMap<>();

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
        copy.presets = new LinkedHashMap<>();
        for (Map.Entry<String, PresetSetting> entry : presets.entrySet()) {
            copy.presets.put(entry.getKey(), entry.getValue().copy());
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
        if (style == null) {
            style = CityStyle.MODERN;
        }
        if (rotation == null) {
            rotation = RotationMode.RANDOM;
        }
        if (presets == null) {
            presets = new LinkedHashMap<>();
        }
        for (Map.Entry<String, PresetSetting> entry : presets.entrySet()) {
            if (entry.getValue() == null) {
                entry.setValue(new PresetSetting());
            } else {
                entry.getValue().weight = Mth.clamp(entry.getValue().weight, 0, 100);
            }
        }
    }

    public long ensureSeed() {
        if (seed == 0L) {
            seed = new Random().nextLong();
        }
        return seed;
    }

    public PresetSetting setting(String name) {
        return presets.computeIfAbsent(name, key -> new PresetSetting());
    }

    public void ensurePreset(String name) {
        presets.computeIfAbsent(name, key -> new PresetSetting());
    }

    public void removePreset(String name) {
        presets.remove(name);
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
