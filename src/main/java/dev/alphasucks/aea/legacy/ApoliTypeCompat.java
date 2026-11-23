package dev.alphasucks.aea.legacy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.overgrown.apoli.power.PowerTypeRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Apoli 2.9.2 / Origins 1.10.2 JSON that Overgrown reads differently, found by comparing every field of the old
 * types with Overgrown's codecs.
 */
final class ApoliTypeCompat {
    private ApoliTypeCompat() {}

    /** Power fields every Apoli 2.9 power accepted (not sub-powers of a multiple). */
    private static final Set<String> POWER_COMMON = Set.of("name", "description", "hidden", "badges", "loading_priority", "condition", "type");

    private static boolean apoliNs(JsonObject o) {
        if (!o.has("type") || !o.get("type").isJsonPrimitive()) return false;
        String t = o.get("type").getAsString();
        int c = t.indexOf(':');
        if (c < 0) return true;
        String ns = t.substring(0, c);
        return ns.equals("apoli") || ns.equals("origins");
    }

    // Per object

    static void fix(JsonObject o, String type, int dv) {
        if (!apoliNs(o)) return;
        switch (type) {
            // Overgrown only reads the plural forms; Apoli 2.9 applied both.
            case "modify_food" -> {
                mergeInto(o, "food_modifier", "food_modifiers");
                mergeInto(o, "saturation_modifier", "saturation_modifiers");
            }
            // Overgrown reads "effects" only when "effect" is absent; Apoli 2.9 applied both.
            case "apply_effect" -> {
                if (o.has("effect") || o.has("effects")) {
                    JsonArray all = new JsonArray();
                    addAll(all, o.remove("effect"));
                    addAll(all, o.remove("effects"));
                    o.add("effect", all);
                } else {
                    o.add("effect", new JsonArray());
                }
            }
            case "damage" -> {
                if (!o.has("damage_type") && !o.has("source")) o.addProperty("damage_type", "minecraft:generic");
            }
            case "action_on_land" -> {
                if (!o.has("entity_action")) o.add("entity_action", nothing());
            }
            case "elytra_flight_possible" -> rename(o, "check_ability", "check_abilities");
            case "undirected" -> {
                if (o.has("condition")) o.addProperty("type", "aea:legacy_undirected");
            }
            case "distance_from_coordinates" -> o.addProperty("type", "aea:legacy_distance_from_coordinates");
            case "distance_from_spawn" -> o.addProperty("type", "aea:legacy_distance_from_spawn");
            // Apoli 2.9 defaults that Overgrown changed.
            case "particle" -> {
                if (o.has("particle")) {
                    if (!o.has("spread")) o.add("spread", vec(0.25, 0.5, 0.25));
                    if (!o.has("offset_y")) o.addProperty("offset_y", 1.0);
                }
            }
            case "spawn_particles" -> {
                if (!o.has("spread")) o.add("spread", vec(0.5, 0.25, 0.5));
            }
            case "raycast" -> {
                if (!o.has("shape_type")) o.addProperty("shape_type", "outline");
            }
            // Apoli 2.9 passed these strings to Text.translatable; Overgrown reads a bare string as literal text.
            case "inventory" -> {
                if (o.has("title") && o.get("title").isJsonPrimitive()) o.add("title", translate(o.get("title").getAsString()));
            }
            case "prevent_sleep" -> {
                if (o.has("message") && o.get("message").isJsonPrimitive()) o.add("message", translate(o.get("message").getAsString()));
            }
            // Overgrown dropped the protection-enchantment onset scaling; keep Apoli 2.9's power for packs that use it.
            case "damage_over_time" -> {
                if (o.has("protection_enchantment") && !o.has("entity_action")) {
                    o.addProperty("type", "aea:legacy_damage_over_time");
                    o.remove("damage_source");
                }
            }
            default -> {
            }
        }
    }

    // Whole files

    /** Fixes that need the file's root: power names, sub-power expansion, origin names. */
    static JsonElement root(JsonElement root) {
        if (!(root instanceof JsonObject o)) return root;
        if (isPowerRoot(o)) {
            if (isMultiple(o)) {
                expandSubPowers(o);
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    if (e.getValue() instanceof JsonObject sub && sub.has("type")) translateNames(sub);
                }
                translateNames(o);
                return o;
            }
            Map<String, JsonObject> parts = expand(o);
            if (parts != null) {
                JsonObject multiple = new JsonObject();
                multiple.addProperty("type", "apoli:multiple");
                for (String k : List.of("name", "description", "hidden", "badges", "loading_priority")) {
                    if (o.has(k)) multiple.add(k, o.get(k));
                }
                for (Map.Entry<String, JsonObject> p : parts.entrySet()) {
                    JsonObject sub = p.getValue();
                    for (String k : List.of("name", "description", "badges", "loading_priority")) sub.remove(k);
                    multiple.add(p.getKey(), sub);
                }
                translateNames(multiple);
                return multiple;
            }
            translateNames(o);
            return o;
        }
        if (isOriginRoot(o)) {
            translateNames(o);
            upgrades(o);
        }
        return root;
    }

    /** Origins 1.10.2 upgrades: {@code condition} is an advancement id, {@code announcement} a translation key shown in gold. */
    private static void upgrades(JsonObject o) {
        if (!(o.get("upgrades") instanceof JsonArray arr)) return;
        for (JsonElement e : arr) {
            if (!(e instanceof JsonObject u)) continue;
            if (u.get("condition") instanceof JsonPrimitive p && p.isString()) {
                JsonObject c = new JsonObject();
                c.addProperty("type", "apoli:advancement");
                c.addProperty("advancement", p.getAsString());
                u.add("condition", c);
            }
            if (u.get("announcement") instanceof JsonPrimitive p && p.isString()) {
                if (p.getAsString().isEmpty()) u.remove("announcement");
                else {
                    JsonObject t = new JsonObject();
                    t.addProperty("translate", p.getAsString());
                    t.addProperty("color", "gold");
                    u.add("announcement", t);
                }
            }
        }
    }

    private static boolean isMultiple(JsonObject o) {
        String t = o.get("type").getAsString();
        return t.endsWith(":multiple") || t.equals("multiple");
    }

    private static boolean isPowerRoot(JsonObject o) {
        if (!o.has("type") || !o.get("type").isJsonPrimitive()) return false;
        if (o.has("origins") || o.has("powers") && !o.has("entity_types")) return false;
        ResourceLocation id = ResourceLocation.tryParse(o.get("type").getAsString());
        if (id == null) return false;
        if (PowerTypeRegistry.get(id) == null && !id.getPath().equals("toggle_night_vision")) return false;
        if (o.has("inverted")) return false;
        return true;
    }

    private static boolean isOriginRoot(JsonObject o) {
        return !o.has("type") && (o.has("powers") || o.has("impact") || o.has("icon")) && !o.has("origins");
    }

    /** Origins 1.10.2 / Apoli 2.9 used name/description as translation keys ("" = the automatic key). */
    private static void translateNames(JsonObject o) {
        for (String k : List.of("name", "description")) {
            if (!o.has(k) || !o.get(k).isJsonPrimitive()) continue;
            String s = o.get(k).getAsString();
            if (s.isEmpty()) o.remove(k);
            else o.add(k, translate(s));
        }
    }

    /** Sub-powers that became several powers: add the extra ones next to them, under derived keys. */
    private static void expandSubPowers(JsonObject multiple) {
        Map<String, JsonElement> rebuilt = new LinkedHashMap<>();
        boolean changed = false;
        for (Map.Entry<String, JsonElement> e : multiple.entrySet()) {
            if (e.getValue() instanceof JsonObject sub && sub.has("type") && !POWER_COMMON.contains(e.getKey())) {
                Map<String, JsonObject> parts = expandAs(sub, e.getKey());
                if (parts != null) {
                    changed = true;
                    for (Map.Entry<String, JsonObject> p : parts.entrySet()) {
                        rebuilt.put(p.getKey().equals("") ? e.getKey() : e.getKey() + "_" + p.getKey(), p.getValue());
                    }
                    continue;
                }
            }
            rebuilt.put(e.getKey(), e.getValue());
        }
        if (!changed) return;
        for (String k : new ArrayList<>(multiple.keySet())) multiple.remove(k);
        rebuilt.forEach(multiple::add);
    }

    /** Root power that has to become a multiple: keys are sub-power names. */
    private static Map<String, JsonObject> expand(JsonObject power) {
        Map<String, JsonObject> parts = expandAs(power, null);
        if (parts == null) return null;
        Map<String, JsonObject> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> p : parts.entrySet()) out.put(p.getKey().equals("") ? "main" : p.getKey(), p.getValue());
        return out;
    }

    /**
     * @param subKey the power's key in its multiple, or null for a root power
     * @return parts keyed by suffix ("" = the original power), or null when nothing to expand
     */
    private static Map<String, JsonObject> expandAs(JsonObject p, String subKey) {
        if (!apoliNs(p)) return null;
        String type = p.get("type").getAsString();
        String path = type.substring(type.indexOf(':') + 1);
        // "*:*" in a sub-power is the parent's id: siblings are "*:*_<key>".
        String self = subKey == null ? "*:*_" : "*:*_" + subKey + "_";
        String selfMain = subKey == null ? "*:*_main" : "*:*_" + subKey;
        switch (path) {
            case "toggle_night_vision" -> {
                // Apoli 2.9: a night vision power that the key toggles (on by default).
                Map<String, JsonObject> m = new LinkedHashMap<>();
                JsonObject vision = new JsonObject();
                vision.addProperty("type", "apoli:night_vision");
                if (p.has("strength")) vision.add("strength", p.get("strength"));
                JsonObject active = new JsonObject();
                active.addProperty("type", "apoli:power_active");
                active.addProperty("power", self + "toggle");
                if (p.has("condition")) {
                    JsonObject and = new JsonObject();
                    and.addProperty("type", "apoli:and");
                    JsonArray cs = new JsonArray();
                    cs.add(p.get("condition"));
                    cs.add(active);
                    and.add("conditions", cs);
                    vision.add("condition", and);
                } else {
                    vision.add("condition", active);
                }
                copy(p, vision, "name", "description", "badges", "hidden");
                JsonObject toggle = new JsonObject();
                toggle.addProperty("type", "apoli:toggle");
                toggle.add("active_by_default", p.has("active_by_default") ? p.get("active_by_default") : new JsonPrimitive(false));
                toggle.addProperty("retain_state", true);
                if (p.has("key")) toggle.add("key", p.get("key"));
                toggle.addProperty("hidden", true);
                m.put("", vision);
                m.put("toggle", toggle);
                return m;
            }
            case "modify_camera_submersion" -> {
                // Apoli 2.9: no "from" = from any submersion type. Overgrown needs one power per "from".
                if (p.has("from") || !p.has("to")) return null;
                String to = p.get("to").getAsString();
                Map<String, JsonObject> m = new LinkedHashMap<>();
                boolean first = true;
                for (String from : List.of("none", "water", "lava", "powder_snow")) {
                    if (from.equals(to)) continue;
                    JsonObject c = p.deepCopy();
                    c.addProperty("from", from);
                    if (!first) {
                        c.addProperty("hidden", true);
                        for (String k : List.of("name", "description", "badges")) c.remove(k);
                    }
                    m.put(first ? "" : "from_" + from, c);
                    first = false;
                }
                return m;
            }
            case "modify_player_spawn" -> {
                // Overgrown has no respawn_sound: play it from an action_on_callback next to the power.
                if (!p.has("respawn_sound")) return null;
                JsonObject main = p.deepCopy();
                JsonElement sound = main.remove("respawn_sound");
                JsonObject play = new JsonObject();
                play.addProperty("type", "apoli:play_sound");
                play.add("sound", sound);
                JsonObject cb = new JsonObject();
                cb.addProperty("type", "apoli:action_on_callback");
                cb.add("entity_action_respawned", play);
                JsonObject active = new JsonObject();
                active.addProperty("type", "apoli:power_active");
                active.addProperty("power", selfMain);
                cb.add("condition", active);
                cb.addProperty("hidden", true);
                Map<String, JsonObject> m = new LinkedHashMap<>();
                m.put("", main);
                m.put("respawn_sound", cb);
                return m;
            }
            default -> {
                return null;
            }
        }
    }

    // Helpers

    private static void copy(JsonObject from, JsonObject to, String... keys) {
        for (String k : keys) if (from.has(k)) to.add(k, from.get(k));
    }

    static JsonObject translate(String key) {
        JsonObject t = new JsonObject();
        t.addProperty("translate", key);
        return t;
    }

    private static JsonObject nothing() {
        JsonObject n = new JsonObject();
        n.addProperty("type", "apoli:nothing");
        return n;
    }

    private static JsonObject vec(double x, double y, double z) {
        JsonObject v = new JsonObject();
        v.addProperty("x", x);
        v.addProperty("y", y);
        v.addProperty("z", z);
        return v;
    }

    private static void rename(JsonObject o, String from, String to) {
        if (o.has(from) && !o.has(to)) o.add(to, o.remove(from));
    }

    private static void addAll(JsonArray into, JsonElement e) {
        if (e == null) return;
        if (e.isJsonArray()) into.addAll(e.getAsJsonArray());
        else into.add(e);
    }

    private static void mergeInto(JsonObject o, String single, String plural) {
        if (!o.has(single)) return;
        JsonArray all = new JsonArray();
        addAll(all, o.remove(single));
        addAll(all, o.remove(plural));
        o.add(plural, all);
    }
}
