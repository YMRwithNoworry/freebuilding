package com.freebuilding.city;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.List;

public enum RotationMode {
    NONE("freebuilding.rotation.none"),
    AXIS("freebuilding.rotation.axis"),
    RANDOM("freebuilding.rotation.random");

    private final String translationKey;

    RotationMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }

    public List<Rotation> candidates(RandomSource random) {
        List<Rotation> result = new ArrayList<>(switch (this) {
            case NONE -> List.of(Rotation.NONE);
            case AXIS -> List.of(Rotation.NONE, Rotation.CLOCKWISE_180);
            case RANDOM -> List.of(Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90);
        });
        if (result.size() > 1) {
            int start = random.nextInt(result.size());
            List<Rotation> shuffled = new ArrayList<>(result.size());
            for (int i = 0; i < result.size(); i++) {
                shuffled.add(result.get((start + i) % result.size()));
            }
            return shuffled;
        }
        return result;
    }

    public static RotationMode byName(String name) {
        if (name != null) {
            for (RotationMode mode : values()) {
                if (mode.name().equalsIgnoreCase(name)) {
                    return mode;
                }
            }
        }
        return RANDOM;
    }
}
