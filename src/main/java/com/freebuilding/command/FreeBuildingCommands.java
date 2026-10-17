package com.freebuilding.command;

import com.freebuilding.Config;
import com.freebuilding.WorldData;
import com.freebuilding.city.CityBuildJob;
import com.freebuilding.city.CityConfig;
import com.freebuilding.city.CityLayout;
import com.freebuilding.city.FallbackBuilding;
import com.freebuilding.city.PresetSetting;
import com.freebuilding.network.ModNetwork;
import com.freebuilding.network.PresetListPacket;
import com.freebuilding.preset.Preset;
import com.freebuilding.preview.PreviewMode;
import com.freebuilding.preview.PreviewResult;
import com.freebuilding.preview.PreviewService;
import com.freebuilding.registry.ModItems;
import com.freebuilding.selection.Selection;
import com.freebuilding.selection.SelectionManager;
import com.freebuilding.util.NameUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class FreeBuildingCommands {
    private FreeBuildingCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(build("freebuilding"));
        dispatcher.register(build("fb"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return Commands.literal(name)
                .then(Commands.literal("wand")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> give(context, ModItems.CITY_WAND.get())))
                .then(Commands.literal("planner")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> give(context, ModItems.CITY_PLANNER.get())))
                .then(Commands.literal("pos1").executes(context -> setPos(context, true)))
                .then(Commands.literal("pos2").executes(context -> setPos(context, false)))
                .then(Commands.literal("clear").executes(context -> {
                    SelectionManager.clear(context.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("save")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> save(context, StringArgumentType.getString(context, "name"), false))
                                .then(Commands.literal("force")
                                        .executes(context -> save(context, StringArgumentType.getString(context, "name"), true)))))
                .then(Commands.literal("delete")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> delete(context, StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("list").executes(FreeBuildingCommands::list))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            WorldData.reload();
                            context.getSource().sendSuccess(() -> Component.translatable("freebuilding.msg.reloaded"), true);
                            ServerPlayer player = context.getSource().getPlayer();
                            if (player != null) {
                                sendPresetList(player);
                            }
                            return 1;
                        }))
                .then(Commands.literal("gui")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                    PreviewService.openGui(context.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("city")
                        .then(Commands.literal("build")
                                .executes(FreeBuildingCommands::cityInfo)
                                .then(Commands.literal("confirm")
                                        .requires(source -> source.hasPermission(2))
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            CityBuildJob.start(player, WorldData.getConfig());
                                            return 1;
                                        })))
                        .then(Commands.literal("locate").executes(FreeBuildingCommands::locate))
                        .then(Commands.literal("preview")
                                .requires(source -> source.hasPermission(2))
                                .executes(FreeBuildingCommands::exportPreview)))
                .then(Commands.literal("demo")
                        .requires(source -> source.hasPermission(2))
                        .executes(FreeBuildingCommands::demo));
    }

    private static int give(CommandContext<CommandSourceStack> context, Item item) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack stack = new ItemStack(item);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        context.getSource().sendSuccess(() -> Component.translatable("freebuilding.msg.given", stack.getHoverName()), false);
        return 1;
    }

    private static int setPos(CommandContext<CommandSourceStack> context, boolean first) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BlockPos pos = SelectionManager.targetPos(player);
        if (pos == null) {
            return 0;
        }
        if (first) {
            SelectionManager.setPos1(player, pos);
        } else {
            SelectionManager.setPos2(player, pos);
        }
        return 1;
    }

    private static int save(CommandContext<CommandSourceStack> context, String name, boolean force) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        if (!NameUtil.isValid(name)) {
            source.sendFailure(Component.translatable("freebuilding.msg.invalid_name"));
            return 0;
        }
        Selection selection = SelectionManager.load(player);
        if (!selection.isComplete()) {
            source.sendFailure(Component.translatable("freebuilding.msg.selection.incomplete"));
            return 0;
        }
        if (!selection.dimension().equals(player.level().dimension())) {
            source.sendFailure(Component.translatable("freebuilding.msg.selection.other_dimension"));
            return 0;
        }
        Vec3i size = selection.size();
        if (size == null) {
            source.sendFailure(Component.translatable("freebuilding.msg.selection.incomplete"));
            return 0;
        }
        int maxDimension = Config.MAX_SELECTION_DIMENSION.get();
        if (size.getX() > maxDimension || size.getY() > maxDimension || size.getZ() > maxDimension
                || selection.volume() > Config.MAX_SELECTION_VOLUME.get()) {
            source.sendFailure(Component.translatable("freebuilding.msg.selection.too_large",
                    maxDimension, Config.MAX_SELECTION_VOLUME.get()));
            return 0;
        }
        if (!force && WorldData.getPresetManager() != null && WorldData.getPresetManager().get(name) != null) {
            source.sendFailure(Component.translatable("freebuilding.msg.preset.exists", name));
            return 0;
        }

        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(player.serverLevel(), selection.min(), size, true, Blocks.STRUCTURE_VOID);
        template.setAuthor(player.getGameProfile().getName());
        int oldPlotSize = WorldData.getConfig().plotSize;
        try {
            boolean existed = WorldData.savePreset(name, template);
            source.sendSuccess(() -> Component.translatable(existed
                            ? "freebuilding.msg.preset.saved_over"
                            : "freebuilding.msg.preset.saved",
                    name, size.getX(), size.getY(), size.getZ(), selection.volume()), true);
            int newPlotSize = WorldData.getConfig().plotSize;
            if (newPlotSize > oldPlotSize) {
                source.sendSuccess(() -> Component.translatable("freebuilding.msg.preset.plot_resized", newPlotSize), false);
            }
            sendPresetList(player);
            return 1;
        } catch (IOException exception) {
            source.sendFailure(Component.translatable("freebuilding.msg.preset.save_failed"));
            return 0;
        }
    }

    private static void sendPresetList(ServerPlayer player) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PresetListPacket(WorldData.getConfig().toJson(), WorldData.getPresetInfos()));
    }

    private static int delete(CommandContext<CommandSourceStack> context, String name) {
        if (WorldData.deletePreset(name)) {
            context.getSource().sendSuccess(() -> Component.translatable("freebuilding.msg.preset.deleted", name), true);
            ServerPlayer player = context.getSource().getPlayer();
            if (player != null) {
                sendPresetList(player);
            }
            return 1;
        }
        context.getSource().sendFailure(Component.translatable("freebuilding.msg.preset.not_found", name));
        return 0;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        List<Preset> presets = WorldData.getPresets();
        if (presets.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("freebuilding.msg.preset.none"), false);
            return 1;
        }
        CityConfig config = WorldData.getConfig();
        for (Preset preset : presets) {
            PresetSetting setting = config.presets.get(preset.name());
            int weight = setting == null ? 10 : setting.weight;
            boolean enabled = setting == null || setting.enabled;
            context.getSource().sendSuccess(() -> Component.translatable("freebuilding.msg.preset.line",
                    preset.name(), preset.size().getX(), preset.size().getY(), preset.size().getZ(),
                    weight, enabled), false);
        }
        return 1;
    }

    private static int cityInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CityConfig config = WorldData.getConfig().copy();
        config.validate();
        config.ensureSeed();
        CityLayout layout = CityLayout.manual(player.blockPosition(), config, WorldData.getPresets(), config.seed);
        context.getSource().sendSuccess(() -> Component.translatable("freebuilding.msg.build.info",
                layout.totalSize, layout.totalSize, layout.originX, layout.minZ(), layout.groundY), false);
        return 1;
    }

    private static int locate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BlockPos found = WorldData.findNearestCity(player.blockPosition(), 24);
        if (found == null) {
            context.getSource().sendFailure(Component.translatable("freebuilding.msg.city.not_found"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("freebuilding.msg.city.found",
                found.getX(), found.getZ()), false);
        return 1;
    }

    private static int exportPreview(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayer();
        BlockPos center = player != null ? player.blockPosition() : new BlockPos(0, level.getSeaLevel(), 0);
        CityConfig config = WorldData.getConfig().copy();
        config.validate();
        config.ensureSeed();
        PreviewResult result = PreviewService.generateAt(level, center, config, PreviewMode.MANUAL);
        Path root = WorldData.getRootDir();
        if (root == null || result.png().length == 0) {
            source.sendFailure(Component.literal("Preview generation failed"));
            return 0;
        }
        try {
            Path file = root.resolve("preview-export.png");
            Files.write(file, result.png());
            source.sendSuccess(() -> Component.literal("Preview saved to " + file.toAbsolutePath()), false);
            return 1;
        } catch (IOException exception) {
            source.sendFailure(Component.literal("Failed to save preview: " + exception.getMessage()));
            return 0;
        }
    }

    private static int demo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        ServerLevel level = source.getLevel();
        BlockPos origin = player != null
                ? player.blockPosition().relative(player.getDirection(), 5).above()
                : new BlockPos(0, level.getSeaLevel() + 20, 0);
        Vec3i size = FallbackBuilding.size(0);
        for (int x = 0; x < size.getX(); x++) {
            for (int z = 0; z < size.getZ(); z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
            }
        }
        FallbackBuilding.place(level, origin.getX(), origin.getY(), origin.getZ(), Rotation.NONE, 0, null);
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, origin, size, true, Blocks.STRUCTURE_VOID);
        template.setAuthor(player != null ? player.getGameProfile().getName() : "server");
        try {
            WorldData.savePreset("demo_house", template);
            source.sendSuccess(() -> Component.translatable("freebuilding.msg.demo", "demo_house"), true);
            if (player != null) {
                sendPresetList(player);
            }
            return 1;
        } catch (IOException exception) {
            source.sendFailure(Component.translatable("freebuilding.msg.preset.save_failed"));
            return 0;
        }
    }
}
