package com.freebuilding.preset;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public record Preset(String name, Vec3i size, StructureTemplate template, PreviewData preview) {
    public Vec3i size(Rotation rotation) {
        return template.getSize(rotation);
    }
}
