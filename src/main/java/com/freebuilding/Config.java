package com.freebuilding;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue MAX_SELECTION_VOLUME;
    public static final ForgeConfigSpec.IntValue MAX_SELECTION_DIMENSION;
    public static final ForgeConfigSpec.IntValue BUILD_BLOCKS_PER_TICK;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("FreeBuilding server settings").push("freebuilding");
        MAX_SELECTION_VOLUME = builder
                .comment("Maximum number of blocks that can be saved as one building preset.")
                .defineInRange("maxSelectionVolume", 110_592, 1, 4_000_000);
        MAX_SELECTION_DIMENSION = builder
                .comment("Maximum size of any single selection axis.")
                .defineInRange("maxSelectionDimension", 64, 1, 256);
        BUILD_BLOCKS_PER_TICK = builder
                .comment("Work budget for on-demand city generation. Lower this if the server lags.")
                .defineInRange("buildBlocksPerTick", 8192, 256, 131_072);
        builder.pop();
        SPEC = builder.build();
    }

    private Config() {
    }
}
