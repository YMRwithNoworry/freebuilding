package com.freebuilding.preset;

import com.freebuilding.city.CityConfig;
import com.freebuilding.city.PresetSetting;

public record PresetInfo(String name, int width, int height, int depth, int weight, boolean enabled) {
    public static PresetInfo from(Preset preset, CityConfig config) {
        PresetSetting setting = config.presets.get(preset.name());
        int weight = setting == null ? 10 : setting.weight;
        boolean enabled = setting == null || setting.enabled;
        return new PresetInfo(preset.name(), preset.size().getX(), preset.size().getY(), preset.size().getZ(), weight, enabled);
    }
}
