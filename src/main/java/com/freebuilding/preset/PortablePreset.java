package com.freebuilding.preset;

import com.freebuilding.util.NameUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Text wrapper for a standard structure template. The wrapper keeps the format version and
 * source game version next to the compressed NBT, so a file can be copied between instances
 * and inspected or migrated by a later FreeBuilding version.
 */
public final class PortablePreset {
    public static final int FORMAT_VERSION = 1;
    public static final String EXTENSION = ".fbp";
    private static final String DATA_KEY = "structure_nbt_gzip_base64";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PortablePreset() {
    }

    public static void write(Path file, String name, CompoundTag structure) throws IOException {
        CompoundTag copy = structure.copy();
        NbtUtils.addCurrentDataVersion(copy);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(copy, output);

        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("name", name);
        root.addProperty("minecraft", SharedConstants.VERSION_STRING);
        root.addProperty(DATA_KEY, Base64.getEncoder().encodeToString(output.toByteArray()));
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    public static CompoundTag read(Path file) throws IOException {
        JsonObject root;
        try {
            root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("Invalid portable preset JSON", exception);
        }
        int format = root.has("format") ? root.get("format").getAsInt() : 0;
        if (format != FORMAT_VERSION || !root.has(DATA_KEY)) {
            throw new IOException("Unsupported portable preset format: " + format);
        }
        try {
            byte[] compressed = Base64.getDecoder().decode(root.get(DATA_KEY).getAsString());
            CompoundTag structure = NbtIo.readCompressed(new ByteArrayInputStream(compressed));
            int dataVersion = NbtUtils.getDataVersion(structure, 0);
            if (dataVersion > 0 && dataVersion < SharedConstants.WORLD_VERSION) {
                structure = DataFixTypes.STRUCTURE.updateToCurrentVersion(
                        DataFixers.getDataFixer(), structure, dataVersion);
            }
            return structure;
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid portable preset data", exception);
        }
    }

    public static String readName(Path file) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            String name = root.has("name") ? root.get("name").getAsString() : "";
            if (!NameUtil.isValid(name)) {
                throw new IOException("Portable preset contains an invalid name");
            }
            return name;
        } catch (RuntimeException exception) {
            throw new IOException("Invalid portable preset JSON", exception);
        }
    }

    public static int[] readSize(CompoundTag structure) {
        var size = structure.getList("size", Tag.TAG_INT);
        if (size.size() < 3) {
            return new int[]{1, 1, 1};
        }
        return new int[]{Math.max(1, size.getInt(0)), Math.max(1, size.getInt(1)),
                Math.max(1, size.getInt(2))};
    }
}
