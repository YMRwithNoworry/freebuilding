package com.freebuilding.network;

import com.freebuilding.WorldData;
import com.freebuilding.city.CityBuildJob;
import com.freebuilding.city.CityConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record BuildCityPacket(String configJson) {
    public static void encode(BuildCityPacket packet, FriendlyByteBuf buffer) {
        ModNetwork.writeConfig(buffer, packet.configJson);
    }

    public static BuildCityPacket decode(FriendlyByteBuf buffer) {
        return new BuildCityPacket(ModNetwork.readConfig(buffer));
    }

    public static void handle(BuildCityPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player == null) {
                return;
            }
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.translatable("freebuilding.msg.no_permission"));
                return;
            }
            CityConfig config = WorldData.updateConfig(CityConfig.fromJson(packet.configJson));
            CityBuildJob.start(player, config);
        });
        context.setPacketHandled(true);
    }
}
