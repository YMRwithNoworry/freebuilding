package com.freebuilding.network;

import com.freebuilding.FreeBuilding;
import com.freebuilding.preset.PresetInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ModNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FreeBuilding.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SelectionPacket.class,
                SelectionPacket::encode, SelectionPacket::decode, SelectionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, OpenCityScreenPacket.class,
                OpenCityScreenPacket::encode, OpenCityScreenPacket::decode, OpenCityScreenPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, PreviewRequestPacket.class,
                PreviewRequestPacket::encode, PreviewRequestPacket::decode, PreviewRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, PreviewPacket.class,
                PreviewPacket::encode, PreviewPacket::decode, PreviewPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, UpdateConfigPacket.class,
                UpdateConfigPacket::encode, UpdateConfigPacket::decode, UpdateConfigPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, BuildCityPacket.class,
                BuildCityPacket::encode, BuildCityPacket::decode, BuildCityPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, BuildProgressPacket.class,
                BuildProgressPacket::encode, BuildProgressPacket::decode, BuildProgressPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, DeletePresetPacket.class,
                DeletePresetPacket::encode, DeletePresetPacket::decode, DeletePresetPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, PresetListPacket.class,
                PresetListPacket::encode, PresetListPacket::decode, PresetListPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void writeConfig(FriendlyByteBuf buffer, String json) {
        buffer.writeUtf(json == null ? "" : json, 1_000_000);
    }

    public static String readConfig(FriendlyByteBuf buffer) {
        return buffer.readUtf(1_000_000);
    }

    public static void writePresetInfos(FriendlyByteBuf buffer, List<PresetInfo> infos) {
        buffer.writeVarInt(infos.size());
        for (PresetInfo info : infos) {
            buffer.writeUtf(info.name(), 64);
            buffer.writeVarInt(info.width());
            buffer.writeVarInt(info.height());
            buffer.writeVarInt(info.depth());
            buffer.writeVarInt(info.weight());
            buffer.writeBoolean(info.enabled());
        }
    }

    public static List<PresetInfo> readPresetInfos(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<PresetInfo> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new PresetInfo(buffer.readUtf(64), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean()));
        }
        return result;
    }
}
