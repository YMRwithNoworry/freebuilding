package com.freebuilding.selection;

import com.freebuilding.network.ModNetwork;
import com.freebuilding.network.SelectionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public final class SelectionManager {
    private static final String DIMENSION = "fb_dim";
    private static final String POS1 = "fb_p1";
    private static final String POS2 = "fb_p2";

    private SelectionManager() {
    }

    public static Selection load(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        ResourceKey<Level> dimension = player.level().dimension();
        if (tag.contains(DIMENSION)) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString(DIMENSION));
            if (id != null) {
                dimension = ResourceKey.create(Registries.DIMENSION, id);
            }
        }
        BlockPos pos1 = tag.contains(POS1) ? BlockPos.of(tag.getLong(POS1)) : null;
        BlockPos pos2 = tag.contains(POS2) ? BlockPos.of(tag.getLong(POS2)) : null;
        return new Selection(dimension, pos1, pos2);
    }

    private static void save(ServerPlayer player, Selection selection) {
        CompoundTag tag = player.getPersistentData();
        tag.putString(DIMENSION, selection.dimension().location().toString());
        if (selection.pos1() != null) {
            tag.putLong(POS1, selection.pos1().asLong());
        } else {
            tag.remove(POS1);
        }
        if (selection.pos2() != null) {
            tag.putLong(POS2, selection.pos2().asLong());
        } else {
            tag.remove(POS2);
        }
    }

    public static void setPos1(ServerPlayer player, BlockPos pos) {
        Selection old = load(player);
        ResourceKey<Level> dimension = player.level().dimension();
        Selection selection = old.dimension().equals(dimension)
                ? new Selection(dimension, pos, old.pos2())
                : new Selection(dimension, pos, null);
        apply(player, selection, "freebuilding.msg.pos1");
    }

    public static void setPos2(ServerPlayer player, BlockPos pos) {
        Selection old = load(player);
        ResourceKey<Level> dimension = player.level().dimension();
        Selection selection = old.dimension().equals(dimension)
                ? new Selection(dimension, old.pos1(), pos)
                : new Selection(dimension, null, pos);
        apply(player, selection, "freebuilding.msg.pos2");
    }

    private static void apply(ServerPlayer player, Selection selection, String messageKey) {
        save(player, selection);
        sync(player);
        BlockPos pos = messageKey.endsWith("pos1") ? selection.pos1() : selection.pos2();
        if (pos != null) {
            if (selection.isComplete()) {
                player.displayClientMessage(Component.translatable(messageKey,
                        pos.getX(), pos.getY(), pos.getZ(), selection.volume()), true);
            } else {
                player.displayClientMessage(Component.translatable(messageKey,
                        pos.getX(), pos.getY(), pos.getZ(), 0L), true);
            }
        }
    }

    public static void clear(ServerPlayer player) {
        clearPersistentData(player);
        sync(player);
        player.displayClientMessage(Component.translatable("freebuilding.msg.selection.cleared"), true);
    }

    /**
     * Removes the transient selection without sending packets or chat messages. This must run
     * before Minecraft saves players during server shutdown.
     */
    public static void clearForShutdown(ServerPlayer player) {
        clearPersistentData(player);
    }

    private static void clearPersistentData(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        tag.remove(DIMENSION);
        tag.remove(POS1);
        tag.remove(POS2);
    }

    public static void sync(ServerPlayer player) {
        Selection selection = load(player);
        ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new SelectionPacket(selection.dimension().location().toString(),
                        selection.pos1() != null, selection.pos1() == null ? 0L : selection.pos1().asLong(),
                        selection.pos2() != null, selection.pos2() == null ? 0L : selection.pos2().asLong()));
    }

    @Nullable
    public static BlockPos targetPos(ServerPlayer player) {
        var hit = player.pick(64.0D, 0.0F, false);
        if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
            return blockHit.getBlockPos();
        }
        return player.blockPosition();
    }
}
