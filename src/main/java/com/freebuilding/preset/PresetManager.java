package com.freebuilding.preset;

import com.freebuilding.FreeBuilding;
import com.freebuilding.util.NameUtil;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class PresetManager {
    private final Path directory;
    private final HolderGetter<Block> blockGetter;
    private final ServerLevel level;
    private final Map<String, Preset> presets = new LinkedHashMap<>();

    public PresetManager(Path directory, HolderGetter<Block> blockGetter, ServerLevel level) {
        this.directory = directory;
        this.blockGetter = blockGetter;
        this.level = level;
    }

    public void loadAll() {
        presets.clear();
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".nbt"))
                    .sorted()
                    .forEach(this::loadFile);
        } catch (IOException exception) {
            FreeBuilding.LOGGER.error("Failed to list building presets in {}", directory, exception);
        }
    }

    private void loadFile(Path path) {
        String fileName = path.getFileName().toString();
        String name = fileName.substring(0, fileName.length() - 4);
        if (!NameUtil.isValid(name)) {
            FreeBuilding.LOGGER.warn("Skipping preset with invalid name: {}", fileName);
            return;
        }
        try {
            CompoundTag tag = NbtIo.readCompressed(path.toFile());
            StructureTemplate template = new StructureTemplate();
            template.load(blockGetter, tag);
            PreviewData preview = PreviewData.fromNbt(tag, blockGetter, level);
            presets.put(name, new Preset(name, template.getSize(), template, preview));
        } catch (Exception exception) {
            FreeBuilding.LOGGER.error("Failed to load building preset {}", path, exception);
        }
    }

    public void save(String name, StructureTemplate template) throws IOException {
        if (!NameUtil.isValid(name)) {
            throw new IOException("Invalid preset name");
        }
        Files.createDirectories(directory);
        CompoundTag tag = template.save(new CompoundTag());
        Path file = directory.resolve(name + ".nbt");
        NbtIo.writeCompressed(tag, file.toFile());
        PreviewData preview = PreviewData.fromNbt(tag, blockGetter, level);
        presets.put(name, new Preset(name, template.getSize(), template, preview));
    }

    public boolean delete(String name) {
        if (!NameUtil.isValid(name)) {
            return false;
        }
        presets.remove(name);
        try {
            return Files.deleteIfExists(directory.resolve(name + ".nbt"));
        } catch (IOException exception) {
            FreeBuilding.LOGGER.error("Failed to delete preset {}", name, exception);
            return false;
        }
    }

    @Nullable
    public Preset get(String name) {
        return presets.get(name);
    }

    public List<Preset> getPresets() {
        List<Preset> result = new ArrayList<>(presets.values());
        result.sort(Comparator.comparing(Preset::name));
        return List.copyOf(result);
    }

    public Path directory() {
        return directory;
    }
}
