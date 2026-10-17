package com.freebuilding.city;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public enum CityStyle {
    MODERN("freebuilding.style.modern",
            Blocks.GRAY_CONCRETE,
            Blocks.SMOOTH_STONE,
            Blocks.GRASS_BLOCK,
            Blocks.STONE_BRICKS,
            Blocks.SMOOTH_STONE,
            Blocks.DARK_OAK_FENCE,
            Blocks.LANTERN,
            Blocks.GRASS_BLOCK,
            Blocks.WHITE_CONCRETE,
            0xFF4A4A4A, 0xFF8A8A8A, 0xFF6B8E4E, 0xFF7A7A72, 0xFF4F7A3A, 0xFFFFD54F),
    RUINED("freebuilding.style.ruined",
            Blocks.GRAVEL,
            Blocks.COBBLESTONE,
            Blocks.COARSE_DIRT,
            Blocks.MOSSY_COBBLESTONE,
            Blocks.COBBLESTONE,
            Blocks.DARK_OAK_FENCE,
            Blocks.LANTERN,
            Blocks.GRASS_BLOCK,
            Blocks.MOSSY_COBBLESTONE,
            0xFF5A5148, 0xFF6E655B, 0xFF5E6B4A, 0xFF6A6258, 0xFF557040, 0xFFD9B44A),
    INDUSTRIAL("freebuilding.style.industrial",
            Blocks.POLISHED_BLACKSTONE,
            Blocks.POLISHED_DEEPSLATE,
            Blocks.COARSE_DIRT,
            Blocks.DEEPSLATE_BRICKS,
            Blocks.POLISHED_DEEPSLATE,
            Blocks.IRON_BARS,
            Blocks.SEA_LANTERN,
            Blocks.GRASS_BLOCK,
            Blocks.YELLOW_CONCRETE,
            0xFF2E2E33, 0xFF55555C, 0xFF4A4A44, 0xFF5A5A52, 0xFF4A6A38, 0xFF8CE6FF),
    VERDANT("freebuilding.style.verdant",
            Blocks.DIRT_PATH,
            Blocks.MOSS_BLOCK,
            Blocks.GRASS_BLOCK,
            Blocks.DIRT,
            Blocks.GRASS_BLOCK,
            Blocks.OAK_FENCE,
            Blocks.LANTERN,
            Blocks.GRASS_BLOCK,
            Blocks.OAK_LEAVES,
            0xFF8B7D5A, 0xFF9C8E6A, 0xFF4F7A3A, 0xFF5E8A44, 0xFF3F6A2E, 0xFFFFE082);

    private final String translationKey;
    private final Block road;
    private final Block sidewalk;
    private final Block ground;
    private final Block foundation;
    private final Block plotGround;
    private final Block lampBase;
    private final Block lamp;
    private final Block park;
    private final Block accent;
    private final int roadColor;
    private final int sidewalkColor;
    private final int groundColor;
    private final int plotColor;
    private final int parkColor;
    private final int lampColor;

    CityStyle(String translationKey, Block road, Block sidewalk, Block ground, Block foundation, Block plotGround,
              Block lampBase, Block lamp, Block park, Block accent, int roadColor, int sidewalkColor,
              int groundColor, int plotColor, int parkColor, int lampColor) {
        this.translationKey = translationKey;
        this.road = road;
        this.sidewalk = sidewalk;
        this.ground = ground;
        this.foundation = foundation;
        this.plotGround = plotGround;
        this.lampBase = lampBase;
        this.lamp = lamp;
        this.park = park;
        this.accent = accent;
        this.roadColor = roadColor;
        this.sidewalkColor = sidewalkColor;
        this.groundColor = groundColor;
        this.plotColor = plotColor;
        this.parkColor = parkColor;
        this.lampColor = lampColor;
    }

    public String translationKey() {
        return translationKey;
    }

    public BlockState roadState() {
        return road.defaultBlockState();
    }

    public BlockState sidewalkState() {
        return sidewalk.defaultBlockState();
    }

    public BlockState groundState() {
        return ground.defaultBlockState();
    }

    public BlockState foundationState() {
        return foundation.defaultBlockState();
    }

    public BlockState plotGroundState() {
        return plotGround.defaultBlockState();
    }

    public BlockState lampBaseState() {
        return lampBase.defaultBlockState();
    }

    public BlockState lampState() {
        return lamp.defaultBlockState();
    }

    public BlockState parkState() {
        return park.defaultBlockState();
    }

    public BlockState accentState() {
        return accent.defaultBlockState();
    }

    public int roadColor() {
        return roadColor;
    }

    public int sidewalkColor() {
        return sidewalkColor;
    }

    public int groundColor() {
        return groundColor;
    }

    public int plotColor() {
        return plotColor;
    }

    public int parkColor() {
        return parkColor;
    }

    public int lampColor() {
        return lampColor;
    }

    public static CityStyle byName(String name) {
        if (name != null) {
            for (CityStyle style : values()) {
                if (style.name().equalsIgnoreCase(name)) {
                    return style;
                }
            }
        }
        return MODERN;
    }
}
