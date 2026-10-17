package com.freebuilding.network;

import com.freebuilding.client.ClientPacketHandler;
import com.freebuilding.preset.PresetInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record PresetListPacket(String configJson, List<PresetInfo> presets) {
    public static void encode(PresetListPacket packet, FriendlyByteBuf buffer) {
        ModNetwork.writeConfig(buffer, packet.configJson);
        ModNetwork.writePresetInfos(buffer, packet.presets);
    }

    public static PresetListPacket decode(FriendlyByteBuf buffer) {
        return new PresetListPacket(ModNetwork.readConfig(buffer), ModNetwork.readPresetInfos(buffer));
    }

    public static void handle(PresetListPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handlePresetList(packet)));
        context.setPacketHandled(true);
    }
}
