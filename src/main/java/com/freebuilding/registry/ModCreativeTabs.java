package com.freebuilding.registry;

import com.freebuilding.FreeBuilding;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FreeBuilding.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.freebuilding"))
            .icon(() -> new ItemStack(ModItems.CITY_WAND.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.CITY_WAND.get());
                output.accept(ModItems.CITY_PLANNER.get());
            })
            .build());

    private ModCreativeTabs() {
    }
}
