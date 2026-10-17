package com.freebuilding.network;

import com.freebuilding.WorldData;
import com.freebuilding.city.CityConfig;
import com.freebuilding.preview.PreviewMode;
import com.freebuilding.preview.PreviewResult;
import com.freebuilding.preview.PreviewService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record PreviewRequestPacket(String configJson, boolean autoMode) {
    public static void encode(PreviewRequestPacket packet, FriendlyByteBuf buffer) {
        ModNetwork.writeConfig(buffer, packet.configJson);
        buffer.writeBoolean(packet.autoMode);
    }

    public static PreviewRequestPacket decode(FriendlyByteBuf buffer) {
        return new PreviewRequestPacket(ModNetwork.readConfig(buffer), buffer.readBoolean());
    }

    public static void handle(PreviewRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player == null) {
                return;
            }
            if (!player.hasPermissions(2)) {
                return;
            }
            CityConfig config = WorldData.updateConfig(CityConfig.fromJson(packet.configJson));
            PreviewResult result = PreviewService.generate(player, config,
                    packet.autoMode ? PreviewMode.AUTO : PreviewMode.MANUAL);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new PreviewPacket(result.png(), result.summary()));
        });
        context.setPacketHandled(true);
    }
}
