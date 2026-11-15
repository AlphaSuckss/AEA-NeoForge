package dev.alphasucks.aea.legacy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.Set;

/**
 * Worldgen / dimension type JSON from before 1.20.5.
 * <ul>
 *   <li>Int and float providers lost their {@code "value"} wrapper:
 *   {@code {"type":"uniform","value":{"min_inclusive":0,"max_inclusive":3}}} → {@code {"type":"uniform","min_inclusive":0,"max_inclusive":3}}.
 *   {@code constant} keeps its {@code value} (it is a field there, not a wrapper).</li>
 *   <li>Biome {@code music} given as a list (weighted-list form) is reduced to the single entry 1.21.1 reads.</li>
 * </ul>
 */
public final class WorldgenUpgrader {
    private WorldgenUpgrader() {}

    private static final Set<String> WRAPPED = Set.of("uniform", "biased_to_bottom", "clamped", "clamped_normal", "trapezoid", "weighted_list");

    public static JsonElement upgrade(JsonElement el) {
        walk(el);
        return el;
    }

    private static void walk(JsonElement el) {
        if (el instanceof JsonArray a) {
            for (JsonElement e : a) walk(e);
            return;
        }
        if (!(el instanceof JsonObject o)) return;
        unwrapProvider(o);
        particleOptions(o);
        if (o.has("music") && o.get("music").isJsonArray()) {
            JsonArray list = o.getAsJsonArray("music");
            if (list.isEmpty()) o.remove("music");
            else {
                JsonElement first = list.get(0);
                if (first.isJsonObject() && first.getAsJsonObject().has("data")) first = first.getAsJsonObject().get("data");
                o.add("music", first);
            }
        }
        for (Map.Entry<String, JsonElement> e : o.entrySet()) walk(e.getValue());
    }

    private static final Set<String> BLOCK_PARTICLES = Set.of("block", "falling_dust", "block_marker", "dust_pillar");

    /**
     * Particle options (biome ambient particles): 1.20.1 wrapped non-record options in "value";
     * 1.20.5 made them fields: block particles take "block_state", item particles "item" (a 1.21 item stack).
     */
    private static void particleOptions(JsonObject o) {
        if (!o.has("type") || !o.has("value") || !o.get("type").isJsonPrimitive()) return;
        String type = o.get("type").getAsString();
        if (type.startsWith("minecraft:")) type = type.substring(10);
        if (BLOCK_PARTICLES.contains(type) && !o.has("block_state")) {
            o.add("block_state", o.remove("value"));
        } else if (type.equals("item") && !o.has("item") && o.get("value").isJsonObject()) {
            JsonObject stack = o.remove("value").getAsJsonObject();
            if (stack.has("Count") && !stack.has("count")) { stack.add("count", stack.get("Count")); stack.remove("Count"); }
            stack.remove("tag");
            o.add("item", stack);
        }
    }

    private static void unwrapProvider(JsonObject o) {
        if (o.size() != 2 || !o.has("type") || !o.has("value")) return;
        if (!o.get("type").isJsonPrimitive() || !o.get("value").isJsonObject()) return;
        String type = o.get("type").getAsString();
        if (type.startsWith("minecraft:")) type = type.substring(10);
        if (!WRAPPED.contains(type)) return;
        JsonObject inner = o.remove("value").getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : inner.entrySet()) o.add(e.getKey(), e.getValue());
        unwrapProvider(o);
    }
}
