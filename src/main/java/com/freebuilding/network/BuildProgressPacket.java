package com.freebuilding.network;

import com.freebuilding.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record BuildProgressPacket(int percent, String message) {
    public static void encode(BuildProgressPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.percent);
        buffer.writeUtf(packet.message, 512);
    }

    public static BuildProgressPacket decode(FriendlyByteBuf buffer) {
        return new BuildProgressPacket(buffer.readVarInt(), buffer.readUtf(512));
    }

    public static void handle(BuildProgressPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handleBuildProgress(packet)));
        context.setPacketHandled(true);
    }
}
