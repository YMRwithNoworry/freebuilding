package com.freebuilding.registry;

import com.freebuilding.FreeBuilding;
import com.freebuilding.worldgen.CityFeature;
import com.freebuilding.worldgen.RuralFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(ForgeRegistries.FEATURES, FreeBuilding.MOD_ID);

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> CITY =
            FEATURES.register("city", CityFeature::new);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> RURAL =
            FEATURES.register("rural", RuralFeature::new);

    private ModFeatures() {
    }
}
