package com.freebuilding.network;

import com.freebuilding.WorldData;
import com.freebuilding.city.CityConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record UpdateConfigPacket(String configJson) {
    public static void encode(UpdateConfigPacket packet, FriendlyByteBuf buffer) {
        ModNetwork.writeConfig(buffer, packet.configJson);
    }

    public static UpdateConfigPacket decode(FriendlyByteBuf buffer) {
        return new UpdateConfigPacket(ModNetwork.readConfig(buffer));
    }

    public static void handle(UpdateConfigPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player == null) {
                return;
            }
            if (!player.hasPermissions(2)) {
                return;
            }
            WorldData.updateConfig(CityConfig.fromJson(packet.configJson));
        });
        context.setPacketHandled(true);
    }
}
