package com.freebuilding.city;

public final class PresetSetting {
    public int weight = 10;
    public boolean enabled = true;
    public String category = "city";

    public PresetSetting() {
    }

    public PresetSetting(int weight, boolean enabled) {
        this.weight = weight;
        this.enabled = enabled;
    }

    public PresetSetting copy() {
        PresetSetting copy = new PresetSetting(weight, enabled);
        copy.category = category;
        return copy;
    }
}
