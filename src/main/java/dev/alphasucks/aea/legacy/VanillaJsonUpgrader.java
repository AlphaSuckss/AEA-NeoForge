package dev.alphasucks.aea.legacy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Upgrades 1.20.1-era recipes, advancements, loot tables, item modifiers and predicates to 1.21.1.
 * See COVERAGE-vanilla.md for the full list of changes handled here.
 */
public final class VanillaJsonUpgrader {
    private VanillaJsonUpgrader() {}

    public static JsonElement upgrade(String category, JsonElement root, int dv) {
        switch (category) {
            case "recipe" -> { if (root instanceof JsonObject o) recipe(o, dv); }
            case "advancement" -> {
                if (root instanceof JsonObject o) advancement(o, dv);
                walk(root, dv, true);
            }
            default -> walk(root, dv, false);
        }
        return root;
    }

    // ---------------------------------------------------------------- recipes

    private static void recipe(JsonObject o, int dv) {
        if (o.has("result")) {
            JsonElement r = o.get("result");
            if (r.isJsonPrimitive()) {
                JsonObject res = new JsonObject();
                res.add("id", r);
                if (o.has("count") && o.get("count").isJsonPrimitive()) {
                    res.add("count", o.get("count"));
                    o.remove("count");
                }
                o.add("result", res);
            } else if (r.isJsonObject()) {
                stackToId(r.getAsJsonObject(), dv);
            }
        }
        ingredients(o, dv);
        walk(o, dv, false);
    }

    /** Fabric (and Forge) custom ingredients → NeoForge custom ingredients, anywhere in a recipe. */
    private static void ingredients(JsonElement e, int dv) {
        if (e instanceof JsonArray a) {
            for (int i = 0; i < a.size(); i++) {
                JsonElement c = a.get(i);
                JsonElement r = customIngredient(c, dv);
                if (r != c) a.set(i, r);
                ingredients(a.get(i), dv);
            }
        } else if (e instanceof JsonObject o) {
            for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                JsonElement c = en.getValue();
                JsonElement r = customIngredient(c, dv);
                if (r != c) en.setValue(r);
                ingredients(en.getValue(), dv);
            }
        }
    }

    private static JsonElement customIngredient(JsonElement e, int dv) {
        if (!(e instanceof JsonObject o)) return e;
        String t = o.has("fabric:type") ? o.get("fabric:type").getAsString()
            : o.has("type") && o.get("type").isJsonPrimitive() && o.get("type").getAsString().startsWith("forge:") ? o.get("type").getAsString() : null;
        if (t == null) return e;
        JsonObject out = new JsonObject();
        switch (t) {
            case "fabric:any", "forge:compound" -> {
                out.addProperty("type", "neoforge:compound");
                out.add("children", children(o.has("ingredients") ? o.get("ingredients") : o.get("children"), dv));
            }
            case "fabric:all", "forge:intersection" -> {
                out.addProperty("type", "neoforge:intersection");
                out.add("children", children(o.has("ingredients") ? o.get("ingredients") : o.get("children"), dv));
            }
            case "fabric:difference", "forge:difference" -> {
                out.addProperty("type", "neoforge:difference");
                out.add("base", customIngredient(o.get("base"), dv));
                out.add("subtracted", customIngredient(o.get("subtracted"), dv));
            }
            case "fabric:nbt", "forge:nbt", "forge:partial_nbt" -> {
                out.addProperty("type", "neoforge:components");
                JsonElement items = null;
                JsonElement base = o.has("base") ? o.get("base") : o;
                if (base.isJsonObject()) {
                    JsonObject b = base.getAsJsonObject();
                    if (b.has("item")) items = b.get("item");
                    else if (b.has("tag")) items = new JsonPrimitive("#" + b.get("tag").getAsString());
                    else if (b.has("items")) items = b.get("items");
                } else if (base.isJsonArray()) {
                    JsonArray list = new JsonArray();
                    for (JsonElement x : base.getAsJsonArray()) if (x.isJsonObject() && x.getAsJsonObject().has("item")) list.add(x.getAsJsonObject().get("item"));
                    items = list;
                }
                if (items == null) return e;
                out.add("items", items);
                CompoundTag nbt = o.has("nbt") ? ApoliJsonUpgrader.nbtOf(o.get("nbt")) : null;
                String someItem = items.isJsonPrimitive() && !items.getAsString().startsWith("#") ? items.getAsString()
                    : items.isJsonArray() && !items.getAsJsonArray().isEmpty() ? items.getAsJsonArray().get(0).getAsString() : null;
                JsonObject comps = new JsonObject();
                if (nbt != null) {
                    CompoundTag legacy = new CompoundTag();
                    legacy.putString("id", someItem != null ? someItem : NbtUpgrader.dummyIdFor(nbt));
                    legacy.putByte("Count", (byte) 1);
                    legacy.put("tag", nbt);
                    CompoundTag fixed = NbtUpgrader.fixItemStack(legacy, dv);
                    if (fixed.contains("components", Tag.TAG_COMPOUND)) comps = TextFixes.componentsToJson(fixed.getCompound("components")).getAsJsonObject();
                }
                out.add("components", comps);
                boolean strict = t.equals("forge:nbt") || (o.has("strict") && o.get("strict").getAsBoolean());
                out.addProperty("strict", strict);
            }
            default -> { return e; }
        }
        return out;
    }

    private static JsonArray children(JsonElement list, int dv) {
        JsonArray out = new JsonArray();
        if (list != null && list.isJsonArray()) for (JsonElement x : list.getAsJsonArray()) out.add(customIngredient(x, dv));
        return out;
    }

    /** {"item": x, "count": n, "nbt"|"tag": ...} → {"id": x, "count": n, "components": {...}}. */
    static void stackToId(JsonObject s, int dv) {
        if (s.has("item") && !s.has("id") && s.get("item").isJsonPrimitive()) {
            s.add("id", s.get("item"));
            s.remove("item");
        }
        if (s.has("Count") && !s.has("count")) {
            s.add("count", s.get("Count"));
            s.remove("Count");
        }
        String key = s.has("nbt") ? "nbt" : s.has("tag") ? "tag" : s.has("data") ? "data" : null;
        if (key != null && s.has("id") && !s.has("components")) {
            CompoundTag tag = ApoliJsonUpgrader.nbtOf(s.get(key));
            if (tag != null) {
                CompoundTag legacy = new CompoundTag();
                legacy.putString("id", s.get("id").getAsString());
                legacy.putByte("Count", (byte) 1);
                legacy.put("tag", tag);
                CompoundTag fixed = NbtUpgrader.fixItemStack(legacy, dv);
                if (fixed.contains("components", Tag.TAG_COMPOUND)) {
                    s.add("components", TextFixes.componentsToJson(fixed.getCompound("components")));
                }
                s.remove(key);
            }
        }
    }

    // ---------------------------------------------------------------- advancements

    private static final List<String> TRIGGER_ITEM_FIELDS = List.of("item", "items", "tool", "rod", "ingredients", "fired_from_weapon");
    private static final List<String> TRIGGER_ENTITY_FIELDS = List.of("player", "entity", "victims", "parent", "partner", "child",
        "zombie", "villager", "lightning", "bystander", "projectile", "source", "shooter");

    private static void advancement(JsonObject o, int dv) {
        if (o.has("display") && o.get("display").isJsonObject()) {
            JsonObject d = o.getAsJsonObject("display");
            TextComponents.fixFields(d, dv, "title", "description");
            if (d.has("icon") && d.get("icon").isJsonObject()) stackToId(d.getAsJsonObject("icon"), dv);
        }
        if (o.has("criteria") && o.get("criteria").isJsonObject()) {
            for (Map.Entry<String, JsonElement> c : o.getAsJsonObject("criteria").entrySet()) {
                if (c.getValue().isJsonObject()) criterion(c.getValue().getAsJsonObject(), dv);
            }
        }
    }

    private static void criterion(JsonObject crit, int dv) {
        String trigger = crit.has("trigger") ? strip(crit.get("trigger").getAsString()) : "";
        if (!crit.has("conditions") || !crit.get("conditions").isJsonObject()) return;
        JsonObject c = crit.getAsJsonObject("conditions");
        for (String f : TRIGGER_ITEM_FIELDS) {
            if (!c.has(f)) continue;
            JsonElement v = c.get(f);
            if (v.isJsonObject()) itemPredicate(v.getAsJsonObject(), dv);
            else if (v.isJsonArray()) for (JsonElement x : v.getAsJsonArray()) if (x.isJsonObject()) itemPredicate(x.getAsJsonObject(), dv);
        }
        for (String f : TRIGGER_ENTITY_FIELDS) {
            if (!c.has(f)) continue;
            JsonElement v = c.get(f);
            if (v.isJsonObject()) entityPredicate(v.getAsJsonObject(), dv);
            else if (v.isJsonArray()) {
                for (JsonElement x : v.getAsJsonArray()) {
                    if (x.isJsonObject() && !x.getAsJsonObject().has("condition")) entityPredicate(x.getAsJsonObject(), dv);
                    else if (x.isJsonArray()) for (JsonElement y : x.getAsJsonArray()) if (y.isJsonObject() && !y.getAsJsonObject().has("condition")) entityPredicate(y.getAsJsonObject(), dv);
                }
            }
        }
        // nether_travel: "entered"/"exited" → DistanceTrigger "start_position".
        if (c.has("entered") && !c.has("start_position")) { c.add("start_position", c.get("entered")); c.remove("entered"); }
        c.remove("exited");
        for (String f : List.of("start_position", "location")) {
            if (c.has(f) && c.get(f).isJsonObject()) locationPredicate(c.getAsJsonObject(f));
        }
        // placed_block / item_used_on_block from before 1.20: top-level block/state/item/location.
        if ((trigger.equals("placed_block") || trigger.equals("item_used_on_block") || trigger.equals("allay_drop_item_on_block"))
            && (c.has("block") || c.has("item") || c.has("state") || (c.has("location") && c.get("location").isJsonObject()))) {
            JsonArray conds = new JsonArray();
            if (c.has("location") && c.get("location").isJsonArray()) for (JsonElement x : c.getAsJsonArray("location")) conds.add(x);
            if (c.has("block")) {
                JsonObject bs = new JsonObject();
                bs.addProperty("condition", "minecraft:block_state_property");
                bs.add("block", c.get("block"));
                if (c.has("state")) bs.add("properties", c.get("state"));
                conds.add(bs);
            }
            if (c.has("location") && c.get("location").isJsonObject()) {
                JsonObject lc = new JsonObject();
                lc.addProperty("condition", "minecraft:location_check");
                lc.add("predicate", c.get("location"));
                conds.add(lc);
            }
            if (c.has("item") && c.get("item").isJsonObject()) {
                JsonObject mt = new JsonObject();
                mt.addProperty("condition", "minecraft:match_tool");
                mt.add("predicate", c.get("item"));
                conds.add(mt);
            }
            c.remove("block");
            c.remove("state");
            c.remove("item");
            c.add("location", conds);
        }
    }

    // ---------------------------------------------------------------- generic walk

    private static final Set<String> ITEM_PREDICATE_KEYS = Set.of("item", "items", "tag", "durability", "enchantments",
        "stored_enchantments", "potion", "nbt", "count");

    private static void walk(JsonElement e, int dv, boolean advancement) {
        if (e instanceof JsonArray arr) {
            for (JsonElement c : arr) walk(c, dv, advancement);
            return;
        }
        if (!(e instanceof JsonObject o)) return;

        // Advancement / display icons.
        if (o.has("icon") && o.get("icon").isJsonObject()) stackToId(o.getAsJsonObject("icon"), dv);

        // Loot entries.
        if (o.has("type") && o.get("type").isJsonPrimitive() && strip(o.get("type").getAsString()).equals("loot_table")
            && o.has("name") && !o.has("value")) {
            o.add("value", o.get("name"));
            o.remove("name");
        }

        // Loot functions.
        if (o.has("function") && o.get("function").isJsonPrimitive()) lootFunction(o, dv);

        // Loot conditions.
        if (o.has("condition") && o.get("condition").isJsonPrimitive()) lootCondition(o, dv);

        // Number / NBT / score-name providers that name a loot context entity.
        if (o.has("type") && o.get("type").isJsonPrimitive() && o.has("target") && o.get("target").isJsonPrimitive()) {
            String t = strip(o.get("type").getAsString());
            if (t.equals("context") || t.equals("score")) o.addProperty("target", entityTarget(o.get("target").getAsString()));
        }

        // Entity predicates: equipment slots carry item predicates; nbt carries entity NBT.
        if (o.has("equipment") && o.get("equipment").isJsonObject()) {
            for (Map.Entry<String, JsonElement> slot : o.getAsJsonObject("equipment").entrySet()) {
                if (slot.getValue().isJsonObject()) itemPredicate(slot.getValue().getAsJsonObject(), dv);
            }
        }
        if (o.has("type_specific") && o.get("type_specific").isJsonObject()) typeSpecific(o.getAsJsonObject("type_specific"), dv);
        if (o.has("nbt") && o.get("nbt").isJsonPrimitive() && looksLikeEntityNbt(o.get("nbt").getAsString())) {
            CompoundTag nbt = NbtUpgrader.parse(o.get("nbt").getAsString());
            if (nbt != null) {
                NbtUpgrader.fixEntityLike(nbt, dv);
                o.addProperty("nbt", nbt.toString());
            }
        }
        for (String f : List.of("location", "stepping_on", "movement_affected_by")) {
            if (o.has(f) && o.get(f).isJsonObject() && looksLikeLocation(o.getAsJsonObject(f))) locationPredicate(o.getAsJsonObject(f));
        }

        for (Map.Entry<String, JsonElement> entry : o.entrySet()) walk(entry.getValue(), dv, advancement);
    }

    private static boolean looksLikeEntityNbt(String s) {
        return s.contains("SelectedItem") || s.contains("Inventory") || s.contains("Item:") || s.contains("HandItems")
            || s.contains("ArmorItems") || s.contains("ActiveEffects") || s.contains("Attributes");
    }

    static String strip(String id) {
        int c = id.indexOf(':');
        return c < 0 ? id : id.substring(c + 1);
    }

    // ---------------------------------------------------------------- loot

    /** 1.21 renamed the loot context entity targets. */
    static String entityTarget(String t) {
        return switch (t) {
            case "killer" -> "attacker";
            case "direct_killer" -> "direct_attacker";
            case "killer_player" -> "attacking_player";
            default -> t;
        };
    }

    private static void lootCondition(JsonObject c, int dv) {
        String cond = strip(c.get("condition").getAsString().toLowerCase(Locale.ROOT));
        switch (cond) {
            case "alternative" -> c.addProperty("condition", "minecraft:any_of");
            case "random_chance_with_looting" -> {
                float chance = c.has("chance") ? c.get("chance").getAsFloat() : 0f;
                float mult = c.has("looting_multiplier") ? c.get("looting_multiplier").getAsFloat() : 0f;
                c.addProperty("condition", "minecraft:random_chance_with_enchanted_bonus");
                c.addProperty("enchantment", "minecraft:looting");
                c.addProperty("unenchanted_chance", chance);
                JsonObject lin = new JsonObject();
                lin.addProperty("type", "minecraft:linear");
                lin.addProperty("base", chance + mult);
                lin.addProperty("per_level_above_first", mult);
                c.add("enchanted_chance", lin);
                c.remove("chance");
                c.remove("looting_multiplier");
            }
            case "entity_properties" -> {
                if (c.has("entity")) c.addProperty("entity", entityTarget(c.get("entity").getAsString()));
                if (c.has("predicate") && c.get("predicate").isJsonObject()) entityPredicate(c.getAsJsonObject("predicate"), dv);
            }
            case "entity_scores" -> {
                if (c.has("entity")) c.addProperty("entity", entityTarget(c.get("entity").getAsString()));
            }
            case "location_check" -> {
                if (c.has("predicate") && c.get("predicate").isJsonObject()) locationPredicate(c.getAsJsonObject("predicate"));
            }
            case "match_tool" -> {
                if (c.has("predicate") && c.get("predicate").isJsonObject()) itemPredicate(c.getAsJsonObject("predicate"), dv);
            }
            default -> {}
        }
    }

    static void lootFunction(JsonObject f, int dv) {
        String fn = strip(f.get("function").getAsString().toLowerCase(Locale.ROOT));
        switch (fn) {
            case "set_nbt" -> {
                // Same merge as 1.20.1: vanilla keys become real components, the rest goes to custom_data.
                f.addProperty("function", "aea:legacy_set_nbt");
                f.addProperty("aea_data_version", dv);
            }
            case "copy_nbt" -> {
                f.addProperty("function", "minecraft:copy_custom_data");
                nbtSource(f);
            }
            case "copy_custom_data" -> nbtSource(f);
            case "looting_enchant" -> {
                f.addProperty("function", "minecraft:enchanted_count_increase");
                f.addProperty("enchantment", "minecraft:looting");
            }
            case "set_lore" -> {
                if (!f.has("mode")) {
                    boolean replace = f.has("replace") && f.get("replace").getAsBoolean();
                    f.addProperty("mode", replace ? "replace_all" : "append");
                }
                f.remove("replace");
                if (f.has("entity")) f.addProperty("entity", entityTarget(f.get("entity").getAsString()));
                TextComponents.fixFields(f, dv, "lore");
            }
            case "set_name" -> {
                if (f.has("entity")) f.addProperty("entity", entityTarget(f.get("entity").getAsString()));
                TextComponents.fixFields(f, dv, "name");
            }
            case "fill_player_head" -> {
                if (f.has("entity")) f.addProperty("entity", entityTarget(f.get("entity").getAsString()));
            }
            case "copy_name" -> {
                if (f.has("source")) {
                    String s = f.get("source").getAsString();
                    f.addProperty("source", switch (s) {
                        case "killer" -> "attacking_entity";
                        case "killer_player" -> "last_damage_player";
                        default -> s;
                    });
                }
            }
            case "enchant_randomly" -> {
                if (f.has("enchantments")) {
                    f.add("options", f.get("enchantments"));
                    f.remove("enchantments");
                    // 1.20.1 picked from the given list whether or not the enchantment fit the item.
                    if (!f.has("only_compatible")) f.addProperty("only_compatible", false);
                } else if (!f.has("options")) {
                    // 1.20.1 default: every "discoverable" enchantment (all but soul speed / swift sneak).
                    f.addProperty("options", "#minecraft:on_random_loot");
                }
            }
            case "enchant_with_levels" -> {
                if (f.has("treasure")) {
                    if (f.get("treasure").getAsBoolean() && !f.has("options")) f.addProperty("options", "#minecraft:on_random_loot");
                    f.remove("treasure");
                }
            }
            case "set_contents" -> {
                if (f.has("type") && !f.has("component")) {
                    f.addProperty("component", "minecraft:container");
                    f.remove("type");
                }
            }
            case "set_attributes" -> setAttributes(f);
            case "add_power", "remove_power" -> {
                // Apoli 2.9 took one slot; Overgrown's Apoli takes a list.
                if (f.has("slot") && f.get("slot").isJsonPrimitive()) {
                    JsonArray a = new JsonArray();
                    a.add(f.get("slot"));
                    f.add("slot", a);
                }
            }
            default -> {}
        }
    }

    /** copy_nbt/copy_custom_data source: "killer" etc. were renamed with the loot context targets. */
    private static void nbtSource(JsonObject f) {
        if (!f.has("source")) return;
        JsonElement s = f.get("source");
        if (s.isJsonPrimitive()) f.addProperty("source", entityTarget(s.getAsString()));
        else if (s.isJsonObject() && s.getAsJsonObject().has("target") && s.getAsJsonObject().get("target").isJsonPrimitive()) {
            s.getAsJsonObject().addProperty("target", entityTarget(s.getAsJsonObject().get("target").getAsString()));
        }
    }

    private static void setAttributes(JsonObject f) {
        if (!f.has("modifiers") || !f.get("modifiers").isJsonArray()) return;
        Set<String> used = new HashSet<>();
        for (JsonElement m : f.getAsJsonArray("modifiers")) {
            if (!m.isJsonObject()) continue;
            JsonObject mo = m.getAsJsonObject();
            String id;
            if (mo.has("id") && mo.get("id").getAsString().matches("[0-9a-fA-F-]{36}")) {
                id = "aea:" + mo.get("id").getAsString().toLowerCase(Locale.ROOT);
            } else if (mo.has("id")) {
                id = mo.get("id").getAsString();
            } else if (mo.has("name")) {
                id = "aea:" + mo.get("name").getAsString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
            } else {
                id = "aea:modifier";
            }
            // 1.20.1 gave every modifier a fresh UUID; ids must be unique per attribute now.
            String attr = mo.has("attribute") ? mo.get("attribute").getAsString() : "";
            String base = id;
            for (int n = 2; !used.add(attr + "|" + id); n++) id = base + "_" + n;
            mo.addProperty("id", id);
            mo.remove("name");
            if (mo.has("operation")) {
                String op = mo.get("operation").getAsString();
                mo.addProperty("operation", switch (op) {
                    case "addition" -> "add_value";
                    case "multiply_base" -> "add_multiplied_base";
                    case "multiply_total" -> "add_multiplied_total";
                    default -> op;
                });
            }
            if (mo.has("slot") && mo.get("slot").isJsonArray()) mo.addProperty("slot", slotGroup(mo.getAsJsonArray("slot")));
        }
    }

    /** 1.20.1 allowed a list of slots; 1.21 takes one slot group. */
    static String slotGroup(JsonArray slots) {
        Set<String> s = new HashSet<>();
        for (JsonElement e : slots) s.add(e.getAsString().toLowerCase(Locale.ROOT));
        if (s.size() == 1) return s.iterator().next();
        if (s.equals(Set.of("mainhand", "offhand"))) return "hand";
        if (s.equals(Set.of("head", "chest", "legs", "feet"))) return "armor";
        return "any";
    }

    // ---------------------------------------------------------------- predicates

    private static final Set<String> LOCATION_KEYS = Set.of("position", "biome", "biomes", "structure", "structures",
        "dimension", "light", "block", "fluid", "smokey", "can_see_sky");

    private static boolean looksLikeLocation(JsonObject o) {
        for (String k : o.keySet()) if (LOCATION_KEYS.contains(k)) return true;
        return false;
    }

    /** biome→biomes, structure→structures, block tag→blocks "#tag", fluid fluid/tag→fluids. */
    static void locationPredicate(JsonObject p) {
        if (p.has("biome") && !p.has("biomes")) { p.add("biomes", p.get("biome")); p.remove("biome"); }
        if (p.has("structure") && !p.has("structures")) { p.add("structures", p.get("structure")); p.remove("structure"); }
        if (p.has("block") && p.get("block").isJsonObject()) {
            JsonObject b = p.getAsJsonObject("block");
            if (b.has("tag")) {
                if (!b.has("blocks")) b.addProperty("blocks", "#" + b.get("tag").getAsString().replaceFirst("^#", ""));
                b.remove("tag");
            }
            if (b.has("blocks") && b.get("blocks").isJsonArray() && b.getAsJsonArray("blocks").size() == 1) b.add("blocks", b.getAsJsonArray("blocks").get(0));
        }
        if (p.has("fluid") && p.get("fluid").isJsonObject()) {
            JsonObject f = p.getAsJsonObject("fluid");
            if (f.has("tag")) {
                if (!f.has("fluids")) f.addProperty("fluids", "#" + f.get("tag").getAsString().replaceFirst("^#", ""));
                f.remove("tag");
            }
            if (f.has("fluid")) {
                if (!f.has("fluids")) f.add("fluids", f.get("fluid"));
                f.remove("fluid");
            }
        }
    }

    /** Entity predicate fields that changed (nested predicates are reached by the walk). */
    static void entityPredicate(JsonObject p, int dv) {
        for (String f : List.of("location", "stepping_on", "movement_affected_by")) {
            if (p.has(f) && p.get(f).isJsonObject()) locationPredicate(p.getAsJsonObject(f));
        }
        if (p.has("type_specific") && p.get("type_specific").isJsonObject()) typeSpecific(p.getAsJsonObject("type_specific"), dv);
        if (p.has("player") && p.get("player").isJsonObject() && !p.has("type_specific")) {
            // 1.18-era player predicate at the top level.
            JsonObject ts = p.getAsJsonObject("player");
            ts.addProperty("type", "minecraft:player");
            p.add("type_specific", ts);
            p.remove("player");
            typeSpecific(ts, dv);
        }
        for (String f : List.of("vehicle", "passenger", "targeted_entity")) {
            if (p.has(f) && p.get(f).isJsonObject()) entityPredicate(p.getAsJsonObject(f), dv);
        }
        if (p.has("equipment") && p.get("equipment").isJsonObject()) {
            for (Map.Entry<String, JsonElement> slot : p.getAsJsonObject("equipment").entrySet()) {
                if (slot.getValue().isJsonObject()) itemPredicate(slot.getValue().getAsJsonObject(), dv);
            }
        }
    }

    private static void typeSpecific(JsonObject ts, int dv) {
        String t = ts.has("type") ? strip(ts.get("type").getAsString()) : "";
        if (t.equals("player")) {
            if (ts.has("gamemode") && ts.get("gamemode").isJsonPrimitive()) {
                JsonArray a = new JsonArray();
                a.add(ts.get("gamemode"));
                ts.add("gamemode", a);
            }
            if (ts.has("looking_at") && ts.get("looking_at").isJsonObject()) entityPredicate(ts.getAsJsonObject("looking_at"), dv);
        }
    }

    /** 1.20.1 item predicate → 1.21.1 item predicate (items/count/components/predicates). */
    static void itemPredicate(JsonObject p, int dv) {
        if (p.has("predicates") || p.has("components")) return;
        boolean legacy = false;
        for (String k : p.keySet()) if (ITEM_PREDICATE_KEYS.contains(k) && !k.equals("items") && !k.equals("count")) legacy = true;
        if (p.has("items") && p.get("items").isJsonArray()) legacy = true;
        if (!legacy) return;
        JsonObject predicates = new JsonObject();
        if (p.has("tag") && p.get("tag").isJsonPrimitive()) {
            String t = p.get("tag").getAsString();
            p.addProperty("items", t.startsWith("#") ? t : "#" + t);
            p.remove("tag");
        }
        if (p.has("item") && p.get("item").isJsonPrimitive()) {
            p.add("items", p.get("item"));
            p.remove("item");
        }
        if (p.has("items") && p.get("items").isJsonArray() && p.getAsJsonArray("items").size() == 1) {
            p.add("items", p.getAsJsonArray("items").get(0));
        }
        if (p.has("durability")) {
            JsonObject dmg = new JsonObject();
            dmg.add("durability", p.get("durability"));
            predicates.add("minecraft:damage", dmg);
            p.remove("durability");
        }
        for (String k : List.of("enchantments", "stored_enchantments")) {
            if (!p.has(k) || !p.get(k).isJsonArray()) continue;
            JsonArray out = new JsonArray();
            for (JsonElement en : p.getAsJsonArray(k)) {
                if (!en.isJsonObject()) continue;
                JsonObject eo = en.getAsJsonObject().deepCopy();
                if (eo.has("enchantment")) { eo.add("enchantments", eo.get("enchantment")); eo.remove("enchantment"); }
                out.add(eo);
            }
            predicates.add("minecraft:" + k, out);
            p.remove(k);
        }
        if (p.has("potion")) {
            predicates.add("minecraft:potion_contents", p.get("potion"));
            p.remove("potion");
        }
        if (p.has("nbt") && p.get("nbt").isJsonPrimitive()) {
            nbtPredicate(p, predicates, p.get("nbt").getAsString(), dv);
            p.remove("nbt");
        }
        if (!predicates.isEmpty()) p.add("predicates", predicates);
    }

    /**
     * 1.20.1 item "nbt" matched any part of the item tag (display name, Enchantments, CustomModelData, custom keys...).
     * Convert it through the DataFixer: custom keys stay a partial custom_data match, enchantments and potions become
     * the matching sub-predicates, everything else an exact component match.
     */
    private static void nbtPredicate(JsonObject p, JsonObject predicates, String snbt, int dv) {
        CompoundTag tag = NbtUpgrader.parse(snbt);
        if (tag == null) {
            predicates.addProperty("minecraft:custom_data", snbt);
            return;
        }
        String id = p.has("items") && p.get("items").isJsonPrimitive() && !p.get("items").getAsString().startsWith("#")
            ? p.get("items").getAsString() : NbtUpgrader.dummyIdFor(tag);
        CompoundTag legacy = new CompoundTag();
        legacy.putString("id", id);
        legacy.putByte("Count", (byte) 1);
        legacy.put("tag", tag);
        CompoundTag fixed = NbtUpgrader.fixItemStack(legacy, dv);
        if (!fixed.contains("components", Tag.TAG_COMPOUND) || fixed.contains("tag")) {
            predicates.addProperty("minecraft:custom_data", snbt);
            return;
        }
        CompoundTag comps = fixed.getCompound("components");
        JsonObject exact = new JsonObject();
        for (String k : comps.getAllKeys()) {
            Tag v = comps.get(k);
            switch (k) {
                case "minecraft:custom_data" -> { if (v instanceof CompoundTag ct && !ct.isEmpty()) predicates.addProperty("minecraft:custom_data", ct.toString()); }
                case "minecraft:enchantments", "minecraft:stored_enchantments" -> {
                    CompoundTag lv = v instanceof CompoundTag ct ? (ct.contains("levels", Tag.TAG_COMPOUND) ? ct.getCompound("levels") : ct) : new CompoundTag();
                    JsonArray list = new JsonArray();
                    for (String en : lv.getAllKeys()) {
                        JsonObject one = new JsonObject();
                        one.addProperty("enchantments", en);
                        one.addProperty("levels", lv.getInt(en));
                        list.add(one);
                    }
                    if (!list.isEmpty()) predicates.add(k, list);
                }
                case "minecraft:potion_contents" -> {
                    if (v instanceof CompoundTag ct && ct.contains("potion") && ct.size() == 1) predicates.addProperty(k, ct.getString("potion"));
                    else exact.add(k, TextFixes.componentsToJson(single(k, v)).getAsJsonObject().get(k));
                }
                default -> {
                    JsonElement j = TextFixes.componentsToJson(single(k, v)).getAsJsonObject().get(k);
                    if (j != null) exact.add(k, j);
                }
            }
        }
        if (!exact.isEmpty()) p.add("components", exact);
    }

    private static CompoundTag single(String k, Tag v) {
        CompoundTag c = new CompoundTag();
        c.put(k, v);
        return c;
    }
}
