package com.freebuilding.city;

public final class PresetSetting {
    public int weight = 10;
    public boolean enabled = true;

    public PresetSetting() {
    }

    public PresetSetting(int weight, boolean enabled) {
        this.weight = weight;
        this.enabled = enabled;
    }

    public PresetSetting copy() {
        return new PresetSetting(weight, enabled);
    }
}
