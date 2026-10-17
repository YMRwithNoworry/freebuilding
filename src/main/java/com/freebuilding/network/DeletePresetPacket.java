package com.freebuilding.network;

import com.freebuilding.WorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record DeletePresetPacket(String name) {
    public static void encode(DeletePresetPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.name, 64);
    }

    public static DeletePresetPacket decode(FriendlyByteBuf buffer) {
        return new DeletePresetPacket(buffer.readUtf(64));
    }

    public static void handle(DeletePresetPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
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
            WorldData.deletePreset(packet.name);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new PresetListPacket(WorldData.getConfig().toJson(), WorldData.getPresetInfos()));
        });
        context.setPacketHandled(true);
    }
}
