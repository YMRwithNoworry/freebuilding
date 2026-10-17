package com.freebuilding.network;

import com.freebuilding.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PreviewPacket(byte[] png, String summary) {
    public static void encode(PreviewPacket packet, FriendlyByteBuf buffer) {
        buffer.writeByteArray(packet.png);
        buffer.writeUtf(packet.summary, 4096);
    }

    public static PreviewPacket decode(FriendlyByteBuf buffer) {
        return new PreviewPacket(buffer.readByteArray(), buffer.readUtf(4096));
    }

    public static void handle(PreviewPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handlePreview(packet)));
        context.setPacketHandled(true);
    }
}
