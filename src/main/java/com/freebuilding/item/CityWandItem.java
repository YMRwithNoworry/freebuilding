package com.freebuilding.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public final class CityWandItem extends Item {
    public CityWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.freebuilding.city_wand.tip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.freebuilding.city_wand.tip2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.freebuilding.city_wand.tip3").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.freebuilding.city_wand.tip4").withStyle(ChatFormatting.DARK_GRAY));
    }
}
