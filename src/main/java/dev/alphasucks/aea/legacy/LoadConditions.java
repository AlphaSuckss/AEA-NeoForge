package dev.alphasucks.aea.legacy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.util.Locale;

/**
 * Fabric resource conditions ({@code "fabric:load_conditions"}) and Forge 1.20.1 conditions ({@code "forge:conditions"}) in
 * legacy packs. They are evaluated when the file is read: a failing condition makes the file act absent, as it did on
 * the loader it was written for. Conditions that need tags (only known later in the reload) are handed to NeoForge as
 * {@code "neoforge:conditions"} on the file kinds NeoForge evaluates, and treated as passing elsewhere.
 */
public final class LoadConditions {
    private LoadConditions() {}

    public enum Result { TRUE, FALSE, UNKNOWN }

    public static final String FABRIC = "fabric:load_conditions";
    public static final String FORGE = "forge:conditions";

    public static boolean mentions(String text) {
        return text.contains(FABRIC) || text.contains(FORGE);
    }

    /**
     * Evaluate and strip the loader conditions of a parsed file.
     *
     * @param neoForgeEvaluates whether NeoForge's own condition support covers this file kind (recipes, advancements,
     *                          loot tables, worldgen and other dynamic registry entries).
     * @return false when the file must be treated as absent.
     */
    public static boolean apply(JsonObject root, boolean neoForgeEvaluates) {
        Result r = Result.TRUE;
        JsonArray neo = new JsonArray();
        if (root.has(FABRIC) && root.get(FABRIC).isJsonArray()) {
            for (JsonElement c : root.getAsJsonArray(FABRIC)) {
                Result x = eval(c, false);
                if (x == Result.FALSE) return false;
                if (x == Result.UNKNOWN) { r = Result.UNKNOWN; neo.add(toNeo(c, false)); }
            }
            root.remove(FABRIC);
        }
        if (root.has(FORGE) && root.get(FORGE).isJsonArray()) {
            for (JsonElement c : root.getAsJsonArray(FORGE)) {
                Result x = eval(c, true);
                if (x == Result.FALSE) return false;
                if (x == Result.UNKNOWN) { r = Result.UNKNOWN; neo.add(toNeo(c, true)); }
            }
            root.remove(FORGE);
        }
        if (r == Result.UNKNOWN && neoForgeEvaluates && !root.has("neoforge:conditions")) root.add("neoforge:conditions", neo);
        return true;
    }

    private static String type(JsonObject c) {
        String t = c.has("condition") ? c.get("condition").getAsString() : c.has("type") ? c.get("type").getAsString() : "";
        int colon = t.indexOf(':');
        return (colon < 0 ? t : t.substring(colon + 1)).toLowerCase(Locale.ROOT);
    }

    static Result eval(JsonElement e, boolean forge) {
        if (!e.isJsonObject()) return Result.TRUE;
        JsonObject c = e.getAsJsonObject();
        switch (type(c)) {
            case "true": return Result.TRUE;
            case "false": return Result.FALSE;
            case "not": {
                Result r = eval(c.has("value") ? c.get("value") : new JsonObject(), forge);
                return r == Result.TRUE ? Result.FALSE : r == Result.FALSE ? Result.TRUE : Result.UNKNOWN;
            }
            case "and": {
                Result acc = Result.TRUE;
                for (JsonElement x : list(c)) {
                    Result r = eval(x, forge);
                    if (r == Result.FALSE) return Result.FALSE;
                    if (r == Result.UNKNOWN) acc = Result.UNKNOWN;
                }
                return acc;
            }
            case "or": {
                Result acc = Result.FALSE;
                for (JsonElement x : list(c)) {
                    Result r = eval(x, forge);
                    if (r == Result.TRUE) return Result.TRUE;
                    if (r == Result.UNKNOWN) acc = Result.UNKNOWN;
                }
                return acc;
            }
            case "all_mods_loaded": {
                for (JsonElement m : values(c)) if (!modLoaded(m.getAsString())) return Result.FALSE;
                return Result.TRUE;
            }
            case "any_mod_loaded": {
                for (JsonElement m : values(c)) if (modLoaded(m.getAsString())) return Result.TRUE;
                return Result.FALSE;
            }
            case "mod_loaded":
                return modLoaded(c.has("modid") ? c.get("modid").getAsString() : "") ? Result.TRUE : Result.FALSE;
            case "item_exists": {
                ResourceLocation id = ResourceLocation.tryParse(c.has("item") ? c.get("item").getAsString() : "");
                return id != null && BuiltInRegistries.ITEM.containsKey(id) ? Result.TRUE : Result.FALSE;
            }
            case "registry_contains": {
                Registry<?> reg = registry(c);
                if (reg == null) return Result.UNKNOWN;
                for (JsonElement v : values(c)) {
                    ResourceLocation id = ResourceLocation.tryParse(v.getAsString());
                    if (id == null || !reg.containsKey(id)) return Result.FALSE;
                }
                return Result.TRUE;
            }
            case "tags_populated", "tag_empty":
                return Result.UNKNOWN;
            case "features_enabled":
                // Feature flags are per-world; vanilla's own feature filtering still applies afterwards.
                return Result.TRUE;
            default:
                return Result.TRUE;
        }
    }

    private static Registry<?> registry(JsonObject c) {
        String r = c.has("registry") ? c.get("registry").getAsString() : "minecraft:item";
        ResourceLocation id = ResourceLocation.tryParse(r);
        return id == null ? null : BuiltInRegistries.REGISTRY.get(id);
    }

    private static Iterable<JsonElement> list(JsonObject c) {
        if (c.has("values") && c.get("values").isJsonArray()) return c.getAsJsonArray("values");
        if (c.has("conditions") && c.get("conditions").isJsonArray()) return c.getAsJsonArray("conditions");
        return new JsonArray();
    }

    private static Iterable<JsonElement> values(JsonObject c) {
        if (c.has("values") && c.get("values").isJsonArray()) return c.getAsJsonArray("values");
        JsonArray a = new JsonArray();
        if (c.has("values") && c.get("values").isJsonPrimitive()) a.add(c.get("values"));
        return a;
    }

    /** NeoForge condition JSON for a Fabric/Forge condition (only reached for trees that contain a tag condition). */
    static JsonObject toNeo(JsonElement e, boolean forge) {
        JsonObject out = new JsonObject();
        if (!e.isJsonObject()) { out.addProperty("type", "neoforge:true"); return out; }
        JsonObject c = e.getAsJsonObject();
        Result fixed = eval(c, forge);
        String t = type(c);
        if (fixed != Result.UNKNOWN && !t.equals("not") && !t.equals("and") && !t.equals("or")) {
            out.addProperty("type", fixed == Result.TRUE ? "neoforge:true" : "neoforge:false");
            return out;
        }
        switch (t) {
            case "not" -> {
                out.addProperty("type", "neoforge:not");
                out.add("value", toNeo(c.has("value") ? c.get("value") : new JsonObject(), forge));
            }
            case "and", "or" -> {
                out.addProperty("type", "neoforge:" + t);
                JsonArray vals = new JsonArray();
                for (JsonElement x : list(c)) vals.add(toNeo(x, forge));
                out.add("values", vals);
            }
            case "tag_empty" -> {
                out.addProperty("type", "neoforge:tag_empty");
                out.add("tag", c.get("tag"));
            }
            case "tags_populated" -> {
                // Fabric: every listed tag has entries (item registry unless "registry" says otherwise).
                String reg = c.has("registry") ? c.get("registry").getAsString() : "minecraft:item";
                if (!reg.equals("minecraft:item") && !reg.equals("item")) { out.addProperty("type", "neoforge:true"); return out; }
                out.addProperty("type", "neoforge:and");
                JsonArray vals = new JsonArray();
                for (JsonElement v : values(c)) {
                    JsonObject not = new JsonObject();
                    not.addProperty("type", "neoforge:not");
                    JsonObject empty = new JsonObject();
                    empty.addProperty("type", "neoforge:tag_empty");
                    empty.addProperty("tag", ConventionalTags.map(v.getAsString().replaceFirst("^#", "")));
                    not.add("value", empty);
                    vals.add(not);
                }
                out.add("values", vals);
            }
            default -> out.addProperty("type", "neoforge:true");
        }
        return out;
    }

    /** Fabric/Forge-era mod ids → whether the equivalent is present here. */
    public static boolean modLoaded(String modId) {
        String id = modId.toLowerCase(Locale.ROOT).trim();
        switch (id) {
            case "minecraft", "java", "fabric", "fabricloader", "fabric-api", "fabric_api", "quilt_loader", "qsl",
                 "quilted_fabric_api", "forge", "neoforge", "calio", "apoli", "origins", "aea", "sync", "pehkui",
                 "cardinal-components", "cardinal_components" -> { return true; }
            default -> {}
        }
        if (id.startsWith("fabric-") || id.startsWith("fabric_") || id.startsWith("cardinal-components")
            || id.startsWith("cardinal_components") || id.startsWith("quilt_") || id.startsWith("qsl_")) return true;
        ModList list = ModList.get();
        if (list == null) return false;
        if (list.isLoaded(id)) return true;
        // Fabric ids with dashes vs NeoForge ids with underscores.
        return list.isLoaded(id.replace('-', '_'));
    }
}
