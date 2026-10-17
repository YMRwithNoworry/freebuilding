package com.freebuilding.compat.lostcities;

import com.freebuilding.FreeBuilding;
import com.freebuilding.util.NameUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Converts Lost Cities datapack building definitions into ordinary StructureTemplate presets.
 *
 * <p>Lost Cities buildings are made of 16x16 parts stacked by floor. This importer picks a
 * representative set of ground/upper/roof parts, resolves the standard Lost Cities palette and
 * writes a normal structure NBT. It intentionally does not depend on Lost Cities classes, so the
 * mod still compiles and runs without Lost Cities installed.</p>
 */
public final class LostCitiesImporter {
    private static final String NAMESPACE = "lostcities";
    private static final String DATA = "lostcities/";
    private static final String[] GLOBAL_PALETTES = {
            "common", "default", "bricks_standard", "glass_full", "glass_side_variant_glass"
    };
    private static final BlockInfo FALLBACK =
            new BlockInfo(Blocks.STONE_BRICKS.defaultBlockState(), null);

    private LostCitiesImporter() {
    }

    public static List<ImportedPreset> importBuildings(MinecraftServer server, HolderLookup<Block> blockLookup) {
        ResourceManager resources = server.getResourceManager();
        Map<ResourceLocation, Resource> found;
        try {
            found = resources.listResources(DATA + "buildings",
                    id -> id.getNamespace().equals(NAMESPACE) && id.getPath().endsWith(".json"));
            if (found.isEmpty()) {
                found = resources.listResources(DATA.substring(0, DATA.length() - 1),
                        id -> id.getNamespace().equals(NAMESPACE)
                                && id.getPath().startsWith(DATA + "buildings/")
                                && id.getPath().endsWith(".json"));
            }
            if (found.isEmpty()) {
                found = resources.listResources(DATA.substring(0, DATA.length() - 1),
                        id -> id.getNamespace().equals(NAMESPACE)
                                && id.getPath().contains("/buildings/")
                                && id.getPath().endsWith(".json"));
            }
        } catch (Exception exception) {
            FreeBuilding.LOGGER.error("Failed to list Lost Cities building resources", exception);
            return List.of();
        }
        if (found.isEmpty()) {
            FreeBuilding.LOGGER.warn("Lost Cities is loaded, but no building resources were found");
            return List.of();
        }

        List<ResourceLocation> ids = new ArrayList<>(found.keySet());
        ids.sort(Comparator.comparing(ResourceLocation::toString));
        Context context = new Context(resources, blockLookup, new Random(0L),
                new HashMap<String, BlockState>(), new HashMap<String, JsonObject>());
        List<ImportedPreset> result = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();
        for (ResourceLocation id : ids) {
            String buildingName = baseName(id);
            if (buildingName.isEmpty()) {
                continue;
            }
            try {
                Context buildingContext = new Context(context.resources(), context.blocks(),
                        new Random(0x4C435F + buildingName.hashCode()), context.variantCache(), context.jsonCache());
                ImportedPreset preset = convert(buildingContext, buildingName);
                if (preset == null) {
                    continue;
                }
                if (!usedNames.add(preset.name())) {
                    FreeBuilding.LOGGER.warn("Skipping duplicate imported Lost Cities preset {}", preset.name());
                    continue;
                }
                result.add(preset);
            } catch (Exception exception) {
                FreeBuilding.LOGGER.error("Failed to import Lost Cities building {}", buildingName, exception);
            }
        }
        FreeBuilding.LOGGER.info("Converted {} Lost Cities buildings into presets", result.size());
        return result;
    }

    @Nullable
    private static ImportedPreset convert(Context context, String name) {
        JsonObject buildingJson = getJson(context, DATA + "buildings/" + name + ".json");
        if (buildingJson == null) {
            return null;
        }

        Palette basePalette = new Palette();
        for (String paletteName : GLOBAL_PALETTES) {
            JsonObject paletteJson = getJson(context, DATA + "palettes/" + paletteName + ".json");
            if (paletteJson != null) {
                basePalette.merge(parsePalette(context, paletteJson));
            }
        }
        mergePaletteReference(context, basePalette, buildingJson);

        List<PartRef> partRefs = parsePartRefs(buildingJson);
        if (partRefs.isEmpty()) {
            return null;
        }
        List<String> groundNames = new ArrayList<>();
        List<String> bodyNames = new ArrayList<>();
        List<String> roofNames = new ArrayList<>();
        for (PartRef ref : partRefs) {
            if (ref.top()) {
                roofNames.add(ref.part());
            } else if (ref.ground()) {
                groundNames.add(ref.part());
            } else {
                bodyNames.add(ref.part());
            }
        }

        Map<String, Part> partCache = new HashMap<>();
        Part ground = loadPart(context, pick(context, groundNames.isEmpty() ? bodyNames : groundNames), partCache);
        Part upper = loadPart(context, pick(context, bodyNames.isEmpty() ? groundNames : bodyNames), partCache);
        Part roof = loadPart(context, pick(context, roofNames), partCache);
        if (ground == null && upper == null) {
            return null;
        }

        Map<BlockPos, BlockInfo> blocks = new LinkedHashMap<>();
        int width = 0;
        int depth = 0;
        int height = 0;
        if (roof == null) {
            Part single = ground != null ? ground : upper;
            if (single == null) {
                return null;
            }
            placePart(context, basePalette, single, 0, blocks);
            width = Math.max(width, single.width());
            depth = Math.max(depth, single.depth());
            height = single.height();
        } else {
            int floors = floorCount(buildingJson, context.random());
            Part first = ground != null ? ground : upper;
            if (first == null) {
                return null;
            }
            placePart(context, basePalette, first, 0, blocks);
            width = Math.max(width, first.width());
            depth = Math.max(depth, first.depth());
            height = first.height();
            for (int floor = 1; floor < floors; floor++) {
                Part next = upper != null ? upper : first;
                placePart(context, basePalette, next, height, blocks);
                width = Math.max(width, next.width());
                depth = Math.max(depth, next.depth());
                height += next.height();
            }
            placePart(context, basePalette, roof, height, blocks);
            width = Math.max(width, roof.width());
            depth = Math.max(depth, roof.depth());
            height += roof.height();
        }

        if (blocks.isEmpty() || width <= 0 || depth <= 0 || height <= 0) {
            return null;
        }
        StructureTemplate template = buildTemplate(context.blocks(), blocks, width, height, depth);
        String presetName = NameUtil.sanitize("lc_" + name);
        return new ImportedPreset(presetName, template);
    }

    private static int floorCount(JsonObject buildingJson, Random random) {
        int min = buildingJson.has("minfloors") ? buildingJson.get("minfloors").getAsInt() : 3;
        int max = buildingJson.has("maxfloors") ? buildingJson.get("maxfloors").getAsInt() : 5;
        min = Math.max(1, min);
        max = Math.max(1, max);
        if (min > max) {
            min = max;
        }
        int floors = min + random.nextInt(max - min + 1);
        return Math.min(8, floors);
    }

    private static List<PartRef> parsePartRefs(JsonObject buildingJson) {
        JsonArray parts = buildingJson.getAsJsonArray("parts");
        if (parts == null) {
            return List.of();
        }
        List<PartRef> result = new ArrayList<>();
        for (JsonElement element : parts) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject part = element.getAsJsonObject();
            if (!part.has("part")) {
                continue;
            }
            String name = part.get("part").getAsString();
            if (name.isBlank()) {
                continue;
            }
            boolean top = getBoolean(part, "top", false);
            boolean ground = getBoolean(part, "ground", false);
            boolean cellar = getBoolean(part, "cellar", false);
            if (cellar) {
                continue;
            }
            result.add(new PartRef(name, top, ground));
        }
        return result;
    }

    @Nullable
    private static Part loadPart(Context context, @Nullable String name, Map<String, Part> cache) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Part cached = cache.get(name);
        if (cached != null) {
            return cached;
        }
        JsonObject partJson = getJson(context, DATA + "parts/" + normalize(name) + ".json");
        if (partJson == null) {
            return null;
        }
        int width = partJson.has("xsize") ? partJson.get("xsize").getAsInt() : 16;
        int depth = partJson.has("zsize") ? partJson.get("zsize").getAsInt() : 16;
        JsonArray slices = partJson.getAsJsonArray("slices");
        if (slices == null) {
            return null;
        }
        List<String> layers = new ArrayList<>();
        int expected = Math.max(1, width * depth);
        for (JsonElement sliceElement : slices) {
            if (!sliceElement.isJsonArray()) {
                continue;
            }
            StringBuilder builder = new StringBuilder();
            for (JsonElement rowElement : sliceElement.getAsJsonArray()) {
                builder.append(rowElement.getAsString());
            }
            String layer = builder.toString();
            if (layer.length() < expected) {
                layer = layer + " ".repeat(expected - layer.length());
            }
            layers.add(layer.substring(0, expected));
        }
        if (layers.isEmpty()) {
            return null;
        }
        String refPalette = partJson.has("refpalette") ? partJson.get("refpalette").getAsString() : null;
        JsonObject localPalette = partJson.has("palette") && partJson.get("palette").isJsonObject()
                ? partJson.getAsJsonObject("palette") : null;
        Part part = new Part(name, width, depth, List.copyOf(layers), refPalette, localPalette);
        cache.put(name, part);
        return part;
    }

    private static void placePart(Context context, Palette basePalette, Part part, int yOffset,
                                  Map<BlockPos, BlockInfo> blocks) {
        Palette palette = basePalette.copy();
        if (part.refPalette() != null) {
            JsonObject partPalette = getJson(context, DATA + "palettes/" + normalize(part.refPalette()) + ".json");
            if (partPalette != null) {
                palette.merge(parsePalette(context, partPalette));
            }
        }
        if (part.localPalette() != null) {
            palette.merge(parsePalette(context, part.localPalette()));
        }
        Map<Character, BlockInfo> resolved = palette.resolve();
        for (int y = 0; y < part.height(); y++) {
            for (int z = 0; z < part.depth(); z++) {
                for (int x = 0; x < part.width(); x++) {
                    char character = part.at(x, y, z);
                    if (character == ' ') {
                        continue;
                    }
                    BlockInfo info = resolved.get(character);
                    if (info == null) {
                        info = FALLBACK;
                    }
                    if (info.state().isAir() || info.state().is(Blocks.STRUCTURE_VOID)) {
                        continue;
                    }
                    blocks.put(new BlockPos(x, yOffset + y, z), info);
                }
            }
        }
    }

    private static void mergePaletteReference(Context context, Palette palette, JsonObject json) {
        if (json.has("refpalette")) {
            JsonObject referenced = getJson(context, DATA + "palettes/" + normalize(json.get("refpalette").getAsString()) + ".json");
            if (referenced != null) {
                palette.merge(parsePalette(context, referenced));
            }
        }
        if (json.has("palette") && json.get("palette").isJsonObject()) {
            palette.merge(parsePalette(context, json.getAsJsonObject("palette")));
        }
    }

    private static Palette parsePalette(Context context, JsonObject paletteJson) {
        Palette palette = new Palette();
        JsonArray entries = paletteJson.getAsJsonArray("palette");
        if (entries == null) {
            return palette;
        }
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("char")) {
                continue;
            }
            String characterString = entry.get("char").getAsString();
            if (characterString.isEmpty()) {
                continue;
            }
            char character = characterString.charAt(0);
            if (entry.has("block")) {
                palette.put(character, new Entry(parseBlock(context, entry), null));
            } else if (entry.has("variant")) {
                palette.put(character, new Entry(new BlockInfo(
                        pickVariant(context, entry.get("variant").getAsString()), null), null));
            } else if (entry.has("frompalette")) {
                String reference = entry.get("frompalette").getAsString();
                if (!reference.isEmpty()) {
                    palette.put(character, new Entry(null, reference.charAt(0)));
                }
            } else if (entry.has("blocks")) {
                palette.put(character, new Entry(new BlockInfo(
                        pickWeighted(context, entry.getAsJsonArray("blocks")), null), null));
            }
        }
        return palette;
    }

    private static BlockInfo parseBlock(Context context, JsonObject entry) {
        BlockState state = parseState(context, entry.get("block").getAsString());
        CompoundTag nbt = null;
        if (entry.has("tag") && entry.get("tag").isJsonObject()) {
            try {
                nbt = TagParser.parseTag(entry.get("tag").toString());
            } catch (Exception ignored) {
            }
        }
        return new BlockInfo(state, nbt);
    }

    private static BlockState parseState(Context context, String value) {
        try {
            return BlockStateParser.parseForBlock(context.blocks(), value, false).blockState();
        } catch (Exception exception) {
            return Blocks.STONE_BRICKS.defaultBlockState();
        }
    }

    private static BlockState pickVariant(Context context, String name) {
        String key = normalize(name);
        BlockState cached = context.variantCache().get(key);
        if (cached != null) {
            return cached;
        }
        JsonObject variant = getJson(context, DATA + "variants/" + key + ".json");
        BlockState state = variant == null
                ? Blocks.STONE_BRICKS.defaultBlockState()
                : pickWeighted(context, variant.getAsJsonArray("blocks"));
        context.variantCache().put(key, state);
        return state;
    }

    private static BlockState pickWeighted(Context context, @Nullable JsonArray entries) {
        if (entries == null || entries.size() == 0) {
            return Blocks.STONE_BRICKS.defaultBlockState();
        }
        int total = 0;
        for (JsonElement element : entries) {
            if (element.isJsonObject()) {
                total += Math.max(1, getInt(element.getAsJsonObject(), "random", 1));
            }
        }
        int roll = context.random().nextInt(Math.max(1, total));
        int accumulated = 0;
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            accumulated += Math.max(1, getInt(entry, "random", 1));
            if (roll < accumulated && entry.has("block")) {
                return parseState(context, entry.get("block").getAsString());
            }
        }
        return Blocks.STONE_BRICKS.defaultBlockState();
    }

    @Nullable
    private static String pick(Context context, List<String> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.get(context.random().nextInt(values.size()));
    }

    private static StructureTemplate buildTemplate(HolderLookup<Block> blockLookup,
                                                   Map<BlockPos, BlockInfo> placed,
                                                   int width, int height, int depth) {
        ListTag paletteTag = new ListTag();
        Map<BlockState, Integer> paletteIndex = new LinkedHashMap<>();
        ListTag blockTags = new ListTag();
        for (Map.Entry<BlockPos, BlockInfo> entry : placed.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockInfo info = entry.getValue();
            Integer index = paletteIndex.get(info.state());
            if (index == null) {
                index = paletteTag.size();
                paletteTag.add(NbtUtils.writeBlockState(info.state()));
                paletteIndex.put(info.state(), index);
            }
            CompoundTag blockTag = new CompoundTag();
            ListTag posTag = new ListTag();
            posTag.add(IntTag.valueOf(pos.getX()));
            posTag.add(IntTag.valueOf(pos.getY()));
            posTag.add(IntTag.valueOf(pos.getZ()));
            blockTag.put("pos", posTag);
            blockTag.putInt("state", index);
            if (info.nbt() != null) {
                blockTag.put("nbt", info.nbt().copy());
            }
            blockTags.add(blockTag);
        }

        ListTag sizeTag = new ListTag();
        sizeTag.add(IntTag.valueOf(width));
        sizeTag.add(IntTag.valueOf(height));
        sizeTag.add(IntTag.valueOf(depth));

        CompoundTag root = new CompoundTag();
        root.put("size", sizeTag);
        root.put("palette", paletteTag);
        root.put("blocks", blockTags);
        root.put("entities", new ListTag());

        StructureTemplate template = new StructureTemplate();
        template.load(blockLookup, root);
        return template;
    }

    @Nullable
    private static JsonObject getJson(Context context, String path) {
        JsonObject cached = context.jsonCache().get(path);
        if (cached != null) {
            return cached;
        }
        ResourceLocation id = new ResourceLocation(NAMESPACE, path);
        Optional<Resource> resource = context.resources().getResource(id);
        if (resource.isEmpty()) {
            return null;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element != null && element.isJsonObject()) {
                JsonObject json = element.getAsJsonObject();
                context.jsonCache().put(path, json);
                return json;
            }
        } catch (IOException exception) {
            FreeBuilding.LOGGER.warn("Failed to read Lost Cities resource {}", id, exception);
        }
        return null;
    }

    private static String baseName(ResourceLocation id) {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    }

    private static String normalize(String name) {
        String value = name;
        int colon = value.indexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        if (value.endsWith(".json")) {
            value = value.substring(0, value.length() - 5);
        }
        return value;
    }

    private static boolean getBoolean(JsonObject json, String key, boolean fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsBoolean() : fallback;
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsInt() : fallback;
    }

    private record Context(ResourceManager resources, HolderLookup<Block> blocks, Random random,
                           Map<String, BlockState> variantCache, Map<String, JsonObject> jsonCache) {
    }

    private record PartRef(String part, boolean top, boolean ground) {
    }

    private record Part(String name, int width, int depth, List<String> layers,
                        @Nullable String refPalette, @Nullable JsonObject localPalette) {
        int height() {
            return layers.size();
        }

        char at(int x, int y, int z) {
            if (x < 0 || z < 0 || y < 0 || x >= width || z >= depth || y >= layers.size()) {
                return ' ';
            }
            int index = z * width + x;
            String layer = layers.get(y);
            return index < layer.length() ? layer.charAt(index) : ' ';
        }
    }

    private record BlockInfo(BlockState state, @Nullable CompoundTag nbt) {
    }

    private record Entry(@Nullable BlockInfo info, @Nullable Character ref) {
    }

    private static final class Palette {
        private final Map<Character, Entry> entries = new HashMap<>();

        void put(char character, Entry entry) {
            entries.put(character, entry);
        }

        void merge(Palette other) {
            entries.putAll(other.entries);
        }

        Palette copy() {
            Palette copy = new Palette();
            copy.entries.putAll(entries);
            return copy;
        }

        Map<Character, BlockInfo> resolve() {
            Map<Character, BlockInfo> result = new HashMap<>();
            for (Character character : entries.keySet()) {
                BlockInfo info = resolve(character, new HashSet<>());
                if (info != null) {
                    result.put(character, info);
                }
            }
            return result;
        }

        @Nullable
        private BlockInfo resolve(char character, Set<Character> seen) {
            if (!seen.add(character)) {
                return null;
            }
            Entry entry = entries.get(character);
            if (entry == null) {
                return null;
            }
            if (entry.info() != null) {
                return entry.info();
            }
            if (entry.ref() != null) {
                return resolve(entry.ref(), seen);
            }
            return null;
        }
    }
}
