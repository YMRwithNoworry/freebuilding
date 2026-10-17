package com.freebuilding.client;

import com.freebuilding.FreeBuilding;
import com.freebuilding.selection.Selection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FreeBuilding.MOD_ID, value = Dist.CLIENT)
public final class HudOverlay {
    private HudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Selection selection = ClientSelection.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (selection == null || !selection.isComplete() || minecraft.level == null
                || minecraft.screen != null
                || !selection.dimension().equals(minecraft.level.dimension())) {
            return;
        }
        var min = selection.min();
        var max = selection.max();
        if (min == null || max == null) {
            return;
        }
        String text = Component.translatable("freebuilding.hud.selection",
                min.getX(), min.getY(), min.getZ(),
                max.getX(), max.getY(), max.getZ(),
                selection.volume()).getString();
        GuiGraphics graphics = event.getGuiGraphics();
        int width = minecraft.font.width(text);
        graphics.fill(4, 4, 8 + width, 18, 0x90000000);
        graphics.drawString(minecraft.font, text, 6, 7, 0xFFE0F7FA, false);
    }
}
