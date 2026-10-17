package com.freebuilding.registry;

import com.freebuilding.FreeBuilding;
import com.freebuilding.item.CityPlannerItem;
import com.freebuilding.item.CityWandItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FreeBuilding.MOD_ID);

    public static final RegistryObject<Item> CITY_WAND = ITEMS.register("city_wand",
            () -> new CityWandItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CITY_PLANNER = ITEMS.register("city_planner",
            () -> new CityPlannerItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
