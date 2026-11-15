package dev.alphasucks.aea.legacy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fabric's 1.20.1 conventional tags (fabric-convention-tags-v1, {@code c:iron_ingots}, {@code c:in_overworld} ...) were
 * renamed to the shared 1.21 convention ({@code c:ingots/iron}, {@code c:is_overworld} ...) that NeoForge uses.
 */
public final class ConventionalTags {
    private ConventionalTags() {}

    private static final Map<String, String> EXACT = new HashMap<>();
    private static final Set<String> MATERIALS = Set.of("iron", "gold", "copper", "netherite", "diamond", "emerald", "lapis",
        "redstone", "coal", "quartz", "amethyst", "raw_iron", "raw_gold", "raw_copper", "glowstone", "netherite_scrap");
    private static final Set<String> COLORS = Set.of("black", "blue", "brown", "cyan", "gray", "green", "light_blue",
        "light_gray", "lime", "magenta", "orange", "pink", "purple", "red", "white", "yellow");
    private static final Set<String> BIOMES = Set.of("taiga", "jungle", "plains", "savanna", "icy", "river", "ocean", "desert",
        "beach", "mushroom", "swamp", "badlands", "floral", "forest", "snowy", "underground", "void", "aquatic", "wasteland",
        "dead", "shallow_ocean", "deep_ocean", "stony_shores", "snowy_plains", "birch_forest", "flower_forest", "windswept",
        "mountain", "lush", "magical", "rare", "plateau", "spooky", "hot", "cold", "wet", "dry", "temperate");

    static {
        // tools / weapons
        EXACT.put("shears", "tools/shear");
        EXACT.put("spears", "tools/spear");
        EXACT.put("tridents", "tools/spear");
        EXACT.put("bows", "tools/bow");
        EXACT.put("crossbows", "tools/crossbow");
        EXACT.put("shields", "tools/shield");
        EXACT.put("fishing_rods", "tools/fishing_rod");
        EXACT.put("brushes", "tools/brush");
        // gems
        EXACT.put("diamonds", "gems/diamond");
        EXACT.put("emeralds", "gems/emerald");
        EXACT.put("lapis", "gems/lapis");
        EXACT.put("quartz", "gems/quartz");
        EXACT.put("amethysts", "gems/amethyst");
        EXACT.put("prismarine_crystals", "gems/prismarine");
        // buckets
        EXACT.put("water_buckets", "buckets/water");
        EXACT.put("lava_buckets", "buckets/lava");
        EXACT.put("milk_buckets", "buckets/milk");
        EXACT.put("empty_buckets", "buckets/empty");
        EXACT.put("entity_water_buckets", "buckets/entity_water");
        EXACT.put("powder_snow_buckets", "buckets/powder_snow");
        // containers
        EXACT.put("wooden_chests", "chests/wooden");
        EXACT.put("wooden_barrels", "barrels/wooden");
        EXACT.put("ender_chests", "chests/ender");
        EXACT.put("trapped_chests", "chests/trapped");
        // sandstone
        EXACT.put("sandstone_blocks", "sandstone/blocks");
        EXACT.put("sandstone_slabs", "sandstone/slabs");
        EXACT.put("sandstone_stairs", "sandstone/stairs");
        EXACT.put("red_sandstone_blocks", "sandstone/red_blocks");
        EXACT.put("red_sandstone_slabs", "sandstone/red_slabs");
        EXACT.put("red_sandstone_stairs", "sandstone/red_stairs");
        EXACT.put("uncolored_sandstone_blocks", "sandstone/uncolored_blocks");
        EXACT.put("uncolored_sandstone_slabs", "sandstone/uncolored_slabs");
        EXACT.put("uncolored_sandstone_stairs", "sandstone/uncolored_stairs");
        // misc items
        EXACT.put("wooden_rods", "rods/wooden");
        EXACT.put("blaze_rods", "rods/blaze");
        EXACT.put("string", "strings");
        EXACT.put("leather", "leathers");
        EXACT.put("raw_fishes", "foods/raw_fish");
        EXACT.put("raw_meats", "foods/raw_meat");
        EXACT.put("cooked_meats", "foods/cooked_meat");
        EXACT.put("cooked_fishes", "foods/cooked_fish");
        EXACT.put("fruits", "foods/fruit");
        EXACT.put("vegetables", "foods/vegetable");
        EXACT.put("berries", "foods/berry");
        EXACT.put("breads", "foods/bread");
        EXACT.put("cookies", "foods/cookie");
        EXACT.put("soups", "foods/soup");
        EXACT.put("candies", "foods/candy");
        EXACT.put("seeds", "seeds");
        EXACT.put("music_discs", "music_discs");
        EXACT.put("movement_restricted", "relocation_not_supported");
        EXACT.put("glass", "glass_blocks");
        // biomes (v1 names that don't follow the "is_" + name rule)
        EXACT.put("in_overworld", "is_overworld");
        EXACT.put("in_the_end", "is_end");
        EXACT.put("in_nether", "is_nether");
        EXACT.put("mesa", "is_badlands");
        EXACT.put("extreme_hills", "is_windswept");
        EXACT.put("caves", "is_cave");
        EXACT.put("climate_hot", "is_hot");
        EXACT.put("climate_temperate", "is_temperate");
        EXACT.put("climate_cold", "is_cold");
        EXACT.put("climate_wet", "is_wet");
        EXACT.put("climate_dry", "is_dry");
        EXACT.put("vegetation_sparse", "is_sparse_vegetation");
        EXACT.put("vegetation_dense", "is_dense_vegetation");
        EXACT.put("tree_coniferous", "is_tree/coniferous");
        EXACT.put("tree_savanna", "is_tree/savanna");
        EXACT.put("tree_jungle", "is_tree/jungle");
        EXACT.put("tree_deciduous", "is_tree/deciduous");
        EXACT.put("mountain_peak", "is_mountain/peak");
        EXACT.put("mountain_slope", "is_mountain/slope");
        EXACT.put("snowy_plains", "is_snowy_plains");
    }

    /** Fabric mining level API tags (fabric:needs_tool_level_N) → the vanilla / NeoForge tool tier tags. */
    private static String miningLevel(String id) {
        return switch (id) {
            case "fabric:needs_tool_level_0" -> null;
            case "fabric:needs_tool_level_1" -> "minecraft:needs_stone_tool";
            case "fabric:needs_tool_level_2" -> "minecraft:needs_iron_tool";
            case "fabric:needs_tool_level_3" -> "minecraft:needs_diamond_tool";
            case "fabric:needs_tool_level_4" -> "neoforge:needs_netherite_tool";
            default -> null;
        };
    }

    /** {@code c:<v1 path>} → {@code c:<v2 path>}; returns the input when it isn't a v1 conventional tag. */
    public static String map(String id) {
        if (id.startsWith("fabric:needs_tool_level_")) {
            String m = miningLevel(id);
            return m == null ? id : m;
        }
        if (!id.startsWith("c:")) return id;
        String p = id.substring(2);
        if (p.contains("/")) return id;
        String r = mapPath(p);
        return r == null ? id : "c:" + r;
    }

    static String mapPath(String p) {
        String e = EXACT.get(p);
        if (e != null) return e;
        if (BIOMES.contains(p)) return "is_" + p;
        if (p.startsWith("raw_") && p.endsWith("_ores")) {
            String m = p.substring(4, p.length() - 5);
            if (MATERIALS.contains(m)) return "raw_materials/" + m;
        }
        if (p.startsWith("raw_") && p.endsWith("_blocks")) {
            String m = p.substring(4, p.length() - 7);
            if (MATERIALS.contains(m)) return "storage_blocks/raw_" + m;
        }
        String[][] suffixes = {{"_ingots", "ingots/"}, {"_nuggets", "nuggets/"}, {"_ores", "ores/"}, {"_dusts", "dusts/"},
            {"_gems", "gems/"}, {"_blocks", "storage_blocks/"}};
        for (String[] s : suffixes) {
            if (p.endsWith(s[0])) {
                String m = p.substring(0, p.length() - s[0].length());
                if (MATERIALS.contains(m)) return s[1] + m;
            }
        }
        if (p.endsWith("_dyes")) {
            String c = p.substring(0, p.length() - 5);
            if (COLORS.contains(c)) return "dyes/" + c;
        }
        return null;
    }

    /** Same for a reference that may carry a leading {@code #}. */
    public static String mapRef(String s) {
        if (s.startsWith("#")) {
            String m = map(s.substring(1));
            return m.equals(s.substring(1)) ? s : "#" + m;
        }
        return map(s);
    }

    /** True when {@code s} is (a reference to) a conventional tag. */
    public static boolean isConventional(String s) {
        return s.startsWith("c:") || s.startsWith("#c:") || s.startsWith("fabric:needs_tool_level_") || s.startsWith("#fabric:needs_tool_level_");
    }

    /** Replace every JSON string value that is exactly a v1 conventional tag (with or without {@code #}). */
    public static JsonElement walk(JsonElement e) {
        if (e instanceof JsonObject o) {
            for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                JsonElement v = en.getValue();
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                    String s = v.getAsString();
                    if (isConventional(s)) {
                        String m = mapRef(s);
                        if (!m.equals(s)) en.setValue(new JsonPrimitive(m));
                    }
                } else walk(v);
            }
        } else if (e instanceof JsonArray a) {
            for (int i = 0; i < a.size(); i++) {
                JsonElement v = a.get(i);
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                    String s = v.getAsString();
                    if (isConventional(s)) {
                        String m = mapRef(s);
                        if (!m.equals(s)) a.set(i, new JsonPrimitive(m));
                    }
                } else walk(v);
            }
        }
        return e;
    }

    private static final Pattern IN_TEXT = Pattern.compile("(?<![a-z0-9_:/.-])(#?)c:([a-z0-9_]+)(?![a-z0-9_/:.-])");

    /** Commands: {@code #c:iron_ingots} → {@code #c:ingots/iron}. */
    public static String inCommand(String cmd) {
        if (!cmd.contains("c:")) return cmd;
        Matcher m = IN_TEXT.matcher(cmd);
        StringBuilder out = new StringBuilder();
        boolean changed = false;
        while (m.find()) {
            String r = mapPath(m.group(2));
            if (r != null && m.group(1).equals("#")) {
                m.appendReplacement(out, Matcher.quoteReplacement("#c:" + r));
                changed = true;
            } else {
                m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
            }
        }
        if (!changed) return cmd;
        m.appendTail(out);
        return out.toString();
    }
}
