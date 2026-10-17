package com.freebuilding.client;

import com.freebuilding.city.CityConfig;
import com.freebuilding.network.BuildProgressPacket;
import com.freebuilding.network.OpenCityScreenPacket;
import com.freebuilding.network.PresetListPacket;
import com.freebuilding.network.PreviewPacket;
import com.freebuilding.network.SelectionPacket;
import com.freebuilding.selection.Selection;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void handleSelection(SelectionPacket packet) {
        Selection selection = packet.toSelection();
        if (selection == null) {
            ClientSelection.clear();
        } else {
            ClientSelection.set(selection);
        }
    }

    public static void handleOpen(OpenCityScreenPacket packet) {
        PreviewTexture.update(packet.png());
        CityConfig config = CityConfig.fromJson(packet.configJson());
        Minecraft.getInstance().setScreen(new CityScreen(config, packet.presets(), packet.png(), packet.summary()));
    }

    public static void handlePreview(PreviewPacket packet) {
        PreviewTexture.update(packet.png());
        if (Minecraft.getInstance().screen instanceof CityScreen screen) {
            screen.onPreview(packet.png(), packet.summary());
        }
    }

    public static void handlePresetList(PresetListPacket packet) {
        if (Minecraft.getInstance().screen instanceof CityScreen screen) {
            screen.onPresetList(packet.presets(), CityConfig.fromJson(packet.configJson()));
        }
    }

    public static void handleBuildProgress(BuildProgressPacket packet) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(packet.message()), true);
        }
    }
}
