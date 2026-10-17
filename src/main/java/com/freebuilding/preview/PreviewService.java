package com.freebuilding.preview;

import com.freebuilding.WorldData;
import com.freebuilding.city.CityConfig;
import com.freebuilding.city.CityGround;
import com.freebuilding.city.CityLayout;
import com.freebuilding.city.Plot;
import com.freebuilding.network.ModNetwork;
import com.freebuilding.network.OpenCityScreenPacket;
import com.freebuilding.preset.Preset;
import com.freebuilding.preset.PresetInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

public final class PreviewService {
    private PreviewService() {
    }

    public static void openGui(ServerPlayer player) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.translatable("freebuilding.msg.no_permission"));
            return;
        }
        CityConfig config = WorldData.getConfig().copy();
        config.validate();
        config.ensureSeed();
        WorldData.updateConfig(config);
        PreviewResult result = generate(player, config, PreviewMode.MANUAL);
        List<PresetInfo> presets = WorldData.getPresetInfos();
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenCityScreenPacket(config.toJson(), presets, result.png(), result.summary()));
    }

    public static PreviewResult generate(ServerPlayer player, CityConfig input, PreviewMode mode) {
        return generateAt(player.serverLevel(), player.blockPosition(), input, mode);
    }

    public static PreviewResult generateAt(ServerLevel level, net.minecraft.core.BlockPos center,
                                           CityConfig input, PreviewMode mode) {
        CityConfig config = input.copy();
        config.validate();
        config.ensureSeed();
        List<Preset> presets = WorldData.getPresets();
        CityLayout layout;
        if (mode == PreviewMode.AUTO) {
            int cellX = Math.floorDiv(center.getX(), WorldData.WORLDGEN_CITY_SIZE);
            int cellZ = Math.floorDiv(center.getZ(), WorldData.WORLDGEN_CITY_SIZE);
            int cellOriginX = cellX * WorldData.WORLDGEN_CITY_SIZE;
            int cellOriginZ = cellZ * WorldData.WORLDGEN_CITY_SIZE;
            int groundY = CityGround.compute(level, cellOriginX, cellOriginZ, WorldData.WORLDGEN_CITY_SIZE);
            if (groundY == Integer.MIN_VALUE) {
                groundY = level.getSeaLevel() + 4;
            }
            long seed = WorldData.getWorldSeed()
                    ^ ((long) cellX * 341873128712L)
                    ^ ((long) cellZ * 132897987541L);
            layout = CityLayout.auto(new net.minecraft.core.BlockPos(cellOriginX, groundY, cellOriginZ),
                    WorldData.WORLDGEN_CITY_SIZE, config, presets, seed, groundY);
        } else {
            layout = CityLayout.manual(center, config, presets, config.seed);
        }
        byte[] png = CityPreviewRenderer.render(layout, config, presets);
        int buildings = 0;
        int parks = 0;
        for (Plot plot : layout.getPlots()) {
            if (plot.hasBuilding()) {
                buildings++;
            }
            if (plot.park) {
                parks++;
            }
        }
        String summary = String.format(Locale.ROOT,
                "%s | %dx%d | plots %dx%d | buildings %d | parks %d | seed %d",
                mode == PreviewMode.AUTO ? "AUTO" : "MANUAL",
                layout.totalSize, layout.totalSize, layout.gridSize, layout.gridSize,
                buildings, parks, config.seed);
        return new PreviewResult(png, summary);
    }
}
