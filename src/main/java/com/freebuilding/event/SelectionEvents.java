package com.freebuilding.event;

import com.freebuilding.registry.ModItems;
import com.freebuilding.selection.SelectionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class SelectionEvents {
    private SelectionEvents() {
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (!player.getMainHandItem().is(ModItems.CITY_WAND.get())) {
            return;
        }
        event.setCanceled(true);
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        if (player instanceof ServerPlayer serverPlayer) {
            SelectionManager.setPos2(serverPlayer, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!player.getMainHandItem().is(ModItems.CITY_WAND.get())) {
            return;
        }
        event.setCanceled(true);
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                SelectionManager.setPos2(serverPlayer, event.getPos());
            } else {
                SelectionManager.setPos1(serverPlayer, event.getPos());
            }
        }
    }
}
