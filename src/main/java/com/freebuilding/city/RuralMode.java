package com.freebuilding.city;

public enum RuralMode {
    SINGLE("freebuilding.rural.single"),
    VILLAGE("freebuilding.rural.village"),
    MIXED("freebuilding.rural.mixed");

    private final String translationKey;

    RuralMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }

    public static RuralMode byName(String name) {
        if (name != null) {
            for (RuralMode mode : values()) {
                if (mode.name().equalsIgnoreCase(name)) {
                    return mode;
                }
            }
        }
        return MIXED;
    }
}
