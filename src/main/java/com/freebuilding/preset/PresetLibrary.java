package com.freebuilding.preset;

import com.freebuilding.FreeBuilding;
import com.freebuilding.util.NameUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** The instance-wide preset library shared by every save on this installation. */
public final class PresetLibrary {
    private static final String DIRECTORY = "presets";

    private PresetLibrary() {
    }

    public static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve(FreeBuilding.MOD_ID).resolve(DIRECTORY);
    }

    public static Path portablePath(String name) {
        return directory().resolve(name + PortablePreset.EXTENSION);
    }

    public static boolean exists(String name) {
        return Files.isRegularFile(directory().resolve(name + ".nbt"))
                || Files.isRegularFile(portablePath(name));
    }

    public static void save(String name, CompoundTag structure) throws IOException {
        if (!NameUtil.isValid(name)) {
            throw new IOException("Invalid preset name");
        }
        Files.createDirectories(directory());
        NbtIo.writeCompressed(structure, directory().resolve(name + ".nbt").toFile());
        PortablePreset.write(portablePath(name), name, structure);
    }

    public static void migrateFrom(Path legacyDirectory) {
        if (!Files.isDirectory(legacyDirectory)) {
            return;
        }
        try (Stream<Path> stream = Files.list(legacyDirectory)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".nbt"))
                    .sorted()
                    .forEach(path -> migrateFile(path, legacyDirectory));
        } catch (IOException exception) {
            FreeBuilding.LOGGER.warn("Failed to migrate legacy presets from {}", legacyDirectory, exception);
        }
    }

    private static void migrateFile(Path source, Path legacyDirectory) {
        String fileName = source.getFileName().toString();
        String name = fileName.substring(0, fileName.length() - 4);
        if (!NameUtil.isValid(name) || exists(name)) {
            return;
        }
        try {
            Files.createDirectories(directory());
            Path target = directory().resolve(name + ".nbt");
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            PortablePreset.write(portablePath(name), name, NbtIo.readCompressed(target.toFile()));
            FreeBuilding.LOGGER.info("Migrated preset {} from legacy save directory {}", name, legacyDirectory);
        } catch (Exception exception) {
            FreeBuilding.LOGGER.warn("Failed to migrate preset {}", source, exception);
        }
    }


    public static Path exportPortable(String name) throws IOException {
        if (!NameUtil.isValid(name)) {
            throw new IOException("Invalid preset name");
        }
        Path source = portablePath(name);
        if (!Files.isRegularFile(source)) {
            Path nbt = directory().resolve(name + ".nbt");
            if (!Files.isRegularFile(nbt)) {
                throw new IOException("Preset not found");
            }
            PortablePreset.write(source, name, NbtIo.readCompressed(nbt.toFile()));
        }
        Path exports = FMLPaths.CONFIGDIR.get().resolve(FreeBuilding.MOD_ID).resolve("exports");
        Files.createDirectories(exports);
        Path target = exports.resolve(name + PortablePreset.EXTENSION);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    public static Path findImport(String fileName) {
        String normalized = fileName.endsWith(PortablePreset.EXTENSION)
                ? fileName : fileName + PortablePreset.EXTENSION;
        Path configRoot = FMLPaths.CONFIGDIR.get().resolve(FreeBuilding.MOD_ID);
        String stem = normalized.substring(0, normalized.length() - PortablePreset.EXTENSION.length());
        if (!NameUtil.isValid(stem)) {
            return configRoot.resolve("missing" + PortablePreset.EXTENSION);
        }
        Path imports = configRoot.resolve("imports").resolve(normalized);
        if (Files.isRegularFile(imports)) {
            return imports;
        }
        Path exports = configRoot.resolve("exports").resolve(normalized);
        if (Files.isRegularFile(exports)) {
            return exports;
        }
        return configRoot.resolve(normalized);
    }

    public static List<PresetFileInfo> listInfos() {
        if (!Files.isDirectory(directory())) {
            return List.of();
        }
        Map<String, Path> files = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.list(directory())) {
            stream.filter(path -> path.getFileName().toString().endsWith(".nbt")
                            || path.getFileName().toString().endsWith(PortablePreset.EXTENSION))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        String suffix = fileName.endsWith(PortablePreset.EXTENSION)
                                ? PortablePreset.EXTENSION : ".nbt";
                        String name = fileName.substring(0, fileName.length() - suffix.length());
                        files.putIfAbsent(name, path);
                    });
        } catch (IOException exception) {
            FreeBuilding.LOGGER.warn("Failed to list shared presets", exception);
            return List.of();
        }

        List<PresetFileInfo> result = new ArrayList<>();
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            if (!NameUtil.isValid(entry.getKey())) {
                continue;
            }
            try {
                CompoundTag tag = entry.getValue().getFileName().toString().endsWith(".nbt")
                        ? NbtIo.readCompressed(entry.getValue().toFile())
                        : PortablePreset.read(entry.getValue());
                int[] size = PortablePreset.readSize(tag);
                result.add(new PresetFileInfo(entry.getKey(), size[0], size[1], size[2]));
            } catch (Exception exception) {
                FreeBuilding.LOGGER.warn("Skipping unreadable shared preset {}", entry.getValue(), exception);
            }
        }
        result.sort(Comparator.comparing(PresetFileInfo::name));
        return List.copyOf(result);
    }
}
