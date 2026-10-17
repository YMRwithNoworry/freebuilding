package com.freebuilding.network;

import com.freebuilding.client.ClientPacketHandler;
import com.freebuilding.preset.PresetInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record OpenCityScreenPacket(String configJson, List<PresetInfo> presets, byte[] png, String summary) {
    public static void encode(OpenCityScreenPacket packet, FriendlyByteBuf buffer) {
        ModNetwork.writeConfig(buffer, packet.configJson);
        ModNetwork.writePresetInfos(buffer, packet.presets);
        buffer.writeByteArray(packet.png);
        buffer.writeUtf(packet.summary, 4096);
    }

    public static OpenCityScreenPacket decode(FriendlyByteBuf buffer) {
        return new OpenCityScreenPacket(ModNetwork.readConfig(buffer), ModNetwork.readPresetInfos(buffer),
                buffer.readByteArray(), buffer.readUtf(4096));
    }

    public static void handle(OpenCityScreenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handleOpen(packet)));
        context.setPacketHandled(true);
    }
}
