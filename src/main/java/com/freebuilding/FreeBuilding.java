package com.freebuilding;

import com.freebuilding.event.ServerEvents;
import com.freebuilding.event.SelectionEvents;
import com.freebuilding.compat.lostcities.LostCitiesCompat;
import com.freebuilding.network.ModNetwork;
import com.freebuilding.registry.ModCreativeTabs;
import com.freebuilding.registry.ModFeatures;
import com.freebuilding.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(FreeBuilding.MOD_ID)
public final class FreeBuilding {
    public static final String MOD_ID = "freebuilding";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FreeBuilding(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModFeatures.FEATURES.register(modBus);
        ModNetwork.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        MinecraftForge.EVENT_BUS.register(SelectionEvents.class);
        MinecraftForge.EVENT_BUS.register(ServerEvents.class);
        LostCitiesCompat.registerWorldgenCompatibility();
        LOGGER.info("FreeBuilding initialized");
    }
}
