package com.freebuilding.network;

import com.freebuilding.client.ClientPacketHandler;
import com.freebuilding.selection.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public record SelectionPacket(String dimension, boolean has1, long pos1, boolean has2, long pos2) {
    public static void encode(SelectionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.dimension, 256);
        buffer.writeBoolean(packet.has1);
        buffer.writeLong(packet.pos1);
        buffer.writeBoolean(packet.has2);
        buffer.writeLong(packet.pos2);
    }

    public static SelectionPacket decode(FriendlyByteBuf buffer) {
        return new SelectionPacket(buffer.readUtf(256), buffer.readBoolean(), buffer.readLong(),
                buffer.readBoolean(), buffer.readLong());
    }

    public static void handle(SelectionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handleSelection(packet)));
        context.setPacketHandled(true);
    }

    @Nullable
    public Selection toSelection() {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null) {
            return null;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        return new Selection(key, has1 ? BlockPos.of(pos1) : null, has2 ? BlockPos.of(pos2) : null);
    }
}
