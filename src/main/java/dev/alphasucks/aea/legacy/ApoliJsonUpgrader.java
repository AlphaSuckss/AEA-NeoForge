package dev.alphasucks.aea.legacy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import dev.alphasucks.aea.gui.GuiPlaceholders;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Upgrades Apoli 2.9 / Origins 1.10 / Sync 1.11 power, origin and layer JSON from legacy (≤1.20.4) packs
 * into what Overgrown's Apoli/Origins for NeoForge 1.21.1 parses.
 */
public final class ApoliJsonUpgrader {
    private ApoliJsonUpgrader() {}

    private static final String[] DAMAGE_TABLE = "cactus,explosion,arrow,arrow,cactus,explosion,arrow,arrow,thorns,explosion,thorns,explosion,thorns,explosion,thorns,explosion,falling_anvil,falling_anvil,arrow,arrow,out_of_world,out_of_world,arrow,arrow,magic,magic,magic,magic,magic,magic,magic,magic,campfire,campfire,fireball,fireball,campfire,campfire,fireball,fireball,thorns,explosion,fireball,fireball,thorns,explosion,fireball,fireball,on_fire,on_fire,fireball,fireball,on_fire,on_fire,fireball,fireball,magic,magic,magic,magic,magic,magic,magic,magic,generic,generic,arrow,arrow,out_of_world,out_of_world,arrow,arrow,magic,magic,magic,magic,magic,magic,magic,magic,generic,generic,generic,generic,out_of_world,out_of_world,out_of_world,out_of_world,magic,magic,magic,magic,magic,magic,magic,magic,on_fire,on_fire,fireball,fireball,on_fire,on_fire,fireball,fireball,magic,magic,magic,magic,magic,magic,magic,magic,on_fire,on_fire,on_fire,on_fire,on_fire,on_fire,on_fire,on_fire,magic,magic,magic,magic,magic,magic,magic,magic".split(",");
    private static final String[] DAMAGE_FLAGS = {"bypasses_armor", "fire", "unblockable", "magic", "out_of_world", "projectile", "explosive"};
    private static final Set<String> SPACES = Set.of("world", "local", "local_horizontal", "local_horizontal_normalized",
        "velocity", "velocity_normalized", "velocity_horizontal", "velocity_horizontal_normalized");
    private static final Set<String> COMMAND_FIELDS = Set.of("command", "command_at_hit", "command_along_ray");
    private static final Set<String> ENTITY_NBT_HINTS = Set.of("SelectedItem", "Inventory", "Item", "HandItems",
        "ArmorItems", "ActiveEffects", "Attributes", "EnderItems", "cardinal_components");

    public static JsonElement upgrade(JsonElement root, int dv) {
        return ApoliTypeCompat.root(walk(root, dv));
    }

    private static JsonElement walk(JsonElement e, int dv) {
        if (e instanceof JsonArray arr) {
            for (int i = 0; i < arr.size(); i++) arr.set(i, walk(arr.get(i), dv));
            return arr;
        }
        if (!(e instanceof JsonObject o)) return e;
        for (Map.Entry<String, JsonElement> entry : List.copyOf(o.entrySet())) {
            o.add(entry.getKey(), walk(entry.getValue(), dv));
        }
        fixObject(o, dv);
        return o;
    }

    private static String type(JsonObject o) {
        if (!o.has("type") || !o.get("type").isJsonPrimitive()) return "";
        String t = o.get("type").getAsString();
        int c = t.indexOf(':');
        return c < 0 ? t : t.substring(c + 1);
    }

    private static void fixObject(JsonObject o, int dv) {
        String type = type(o);

        // Commands embedded in actions/conditions.
        for (String f : COMMAND_FIELDS) {
            if (o.has(f) && o.get(f).isJsonPrimitive() && o.get(f).getAsJsonPrimitive().isString()) {
                String cmd = o.get(f).getAsString();
                String up = CommandUpgrader.upgrade(ApoliCommandUpgrader.upgrade(cmd.strip()), dv);
                if (!up.equals(cmd)) o.addProperty(f, up);
                // aea:gui_command fills {value}/{player}/{slider:id} at run time: validate it with sample values.
                Diagnostics.command(GuiPlaceholders.has(up)
                    ? GuiPlaceholders.apply(up, t -> t.equals("player") ? "Steve" : "0") : up);
            }
        }

        // Item stacks: {"item": ..., "tag": "{...}"} → components.
        if (o.has("item") && o.get("item").isJsonPrimitive() && (o.has("tag") || o.has("nbt")) && !o.has("components")) {
            JsonElement tagEl = o.has("tag") ? o.get("tag") : o.get("nbt");
            CompoundTag tag = nbtOf(tagEl);
            if (tag != null) {
                CompoundTag legacy = new CompoundTag();
                legacy.putString("id", o.get("item").getAsString());
                legacy.putByte("Count", (byte) 1);
                legacy.put("tag", tag);
                CompoundTag fixed = NbtUpgrader.fixItemStack(legacy, dv);
                if (fixed.contains("components", Tag.TAG_COMPOUND)) {
                    o.add("components", TextFixes.componentsToJson(fixed.getCompound("components")));
                    o.remove(o.has("tag") ? "tag" : "nbt");
                }
            }
        }

        // Entity NBT carried by spawning actions.
        if ((o.has("entity_type") || type.equals("fire_projectile") || type.equals("spawn_entity") || type.equals("custom_projectile"))
            && o.has("tag") && o.get("tag").isJsonPrimitive()) {
            CompoundTag nbt = NbtUpgrader.parse(o.get("tag").getAsString());
            if (nbt != null) {
                String et = o.has("entity_type") && o.get("entity_type").isJsonPrimitive() ? o.get("entity_type").getAsString() : "minecraft:marker";
                o.addProperty("tag", NbtUpgrader.fixEntity(nbt, et, dv).toString());
            }
        }
        if ((type.equals("disguise_as") || o.has("entity_type")) && o.has("nbt") && o.get("nbt").isJsonPrimitive()) {
            CompoundTag nbt = NbtUpgrader.parse(o.get("nbt").getAsString());
            if (nbt != null) {
                String et = o.has("entity_type") && o.get("entity_type").isJsonPrimitive() ? o.get("entity_type").getAsString() : "minecraft:marker";
                o.addProperty("nbt", NbtUpgrader.fixEntity(nbt, et, dv).toString());
            }
        }

        // Entity NBT conditions that look into items/effects.
        if (type.equals("nbt") && o.has("nbt") && o.get("nbt").isJsonPrimitive()) {
            String s = o.get("nbt").getAsString();
            for (String hint : ENTITY_NBT_HINTS) {
                if (s.contains(hint)) {
                    CompoundTag nbt = NbtUpgrader.parse(s);
                    if (nbt != null) {
                        NbtUpgrader.fixEntityLike(nbt, dv);
                        o.addProperty("nbt", nbt.toString());
                    }
                    break;
                }
            }
        }

        // Item nbt conditions on vanilla item tags: those moved out of custom_data in 1.20.5.
        if (type.equals("nbt") && o.has("nbt") && o.get("nbt").isJsonPrimitive()) {
            String s = o.get("nbt").getAsString();
            boolean entityish = false;
            for (String hint : ENTITY_NBT_HINTS) if (s.contains(hint)) { entityish = true; break; }
            if (!entityish) {
                for (String k : VANILLA_ITEM_TAGS) {
                    if (s.contains(k + ":") || s.contains("\"" + k + "\"")) {
                        o.addProperty("type", "aea:legacy_nbt");
                        break;
                    }
                }
            }
        }

        // Damage "type" condition given a 1.19-style message id ("indirectMagic", "magic.player"): use the damage type id.
        if (type.equals("type") && o.has("damage_type") && o.get("damage_type").isJsonPrimitive()) {
            String dt = o.get("damage_type").getAsString();
            if (ResourceLocation.tryParse(dt) == null || !dt.equals(dt.toLowerCase(Locale.ROOT))) {
                o.addProperty("damage_type", legacyDamageTypeId(dt));
            }
        }

        // Apoli 2.9's hud_render had no "max" or "fallback"; Overgrown reads hud_render.max as the bar's full value, so a stray
        // "max" there (e.g. pasted in with other junk) froze the bar. Drop what 2.9 ignored.
        if (o.has("hud_render")) {
            JsonElement h = o.get("hud_render");
            if (h.isJsonObject()) stripHud(h.getAsJsonObject());
            else if (h.isJsonArray()) for (JsonElement e : h.getAsJsonArray()) if (e.isJsonObject()) stripHud(e.getAsJsonObject());
        }

        // Inline item modifiers (apoli:modify) are loot functions.
        if (o.has("function") && o.get("function").isJsonPrimitive()) VanillaJsonUpgrader.lootFunction(o, dv);

        // merge_nbt wrote straight into the item tag on 1.20.1; Overgrown's merges everything into custom_data, which buries
        // display names, lore, enchantments, potions and book pages. Convert through the DataFixer at runtime instead.
        // Only for packs from before 1.20.5's item components (data version 3818); later packs already meant custom_data.
        if (dv < 3818 && type.equals("merge_nbt") && o.has("nbt") && o.get("nbt").isJsonPrimitive()) {
            o.addProperty("type", "aea:legacy_merge_nbt");
            o.addProperty("aea_data_version", dv);
        }

        // Origins 1.10 key shorthands.
        if (o.has("key")) {
            JsonElement k = o.get("key");
            if (k.isJsonPrimitive()) {
                String fixed = legacyKey(k.getAsString());
                if (fixed != null) o.addProperty("key", fixed);
            } else if (k.isJsonObject() && k.getAsJsonObject().has("key") && k.getAsJsonObject().get("key").isJsonPrimitive()) {
                String fixed = legacyKey(k.getAsJsonObject().get("key").getAsString());
                if (fixed != null) k.getAsJsonObject().addProperty("key", fixed);
            }
        }

        // Legacy particle objects/strings.
        for (String f : new String[]{"particle", "particle_type"}) {
            if (!o.has(f)) continue;
            JsonElement p = o.get(f);
            JsonElement fixed = fixParticle(p, dv);
            if (fixed != null) o.add(f, fixed);
        }

        // Bi-entity add_velocity had no "space"; packs that wrote one with a made-up value were silently ignored.
        if (type.equals("add_velocity") && o.has("space") && o.get("space").isJsonPrimitive()
            && !SPACES.contains(o.get("space").getAsString())) {
            o.remove("space");
        }

        // Legacy damage source description → damage type.
        if (o.has("source") && o.get("source").isJsonObject() && !o.has("damage_type")
            && type.equals("damage")) {
            o.addProperty("damage_type", damageTypeFor(o.getAsJsonObject("source")));
            o.remove("source");
        }
        if (type.equals("damage_over_time") && o.has("damage_source") && o.get("damage_source").isJsonObject() && !o.has("damage_type")) {
            o.addProperty("damage_type", damageTypeFor(o.getAsJsonObject("damage_source")));
            o.remove("damage_source");
        }
        if (type.equals("damage_over_time") && !o.has("damage_type") && !o.has("damage_source") && !o.has("entity_action")) {
            o.addProperty("damage_type", "apoli:damage_over_time"); // Apoli 2.9's own damage type (shipped by AEA)
        }

        // block_state with an enum value written in "value".
        if (type.equals("block_state") && o.has("value") && o.get("value").isJsonPrimitive()) {
            JsonPrimitive v = o.getAsJsonPrimitive("value");
            if (v.isString() && !v.getAsString().equalsIgnoreCase("true") && !v.getAsString().equalsIgnoreCase("false") && !o.has("enum")) {
                o.add("enum", v);
                o.remove("value");
            }
        }

        // Recipes embedded in apoli:recipe powers use the 1.20.1 result format.
        if (type.equals("recipe") && o.has("recipe") && o.get("recipe").isJsonObject()) {
            VanillaJsonUpgrader.upgrade("recipe", o.getAsJsonObject("recipe"), dv);
        }

        // Inline badges without a type were tooltip/sprite badges in Origins 1.10.
        if (o.has("badges") && o.get("badges").isJsonArray()) {
            for (JsonElement b : o.getAsJsonArray("badges")) if (b.isJsonObject()) upgradeBadge(b.getAsJsonObject(), dv);
        }
        // Inline recipe of an origins:crafting_recipe badge (result "item" -> "id").
        if (type.equals("crafting_recipe") && o.has("recipe") && o.get("recipe").isJsonObject()) {
            VanillaJsonUpgrader.upgrade("recipe", o.getAsJsonObject("recipe"), dv);
        }

        // Sync accepted enum names in any case (e.g. "CROSSBOW_HOLD").
        if (type.equals("pose")) {
            for (String f : new String[]{"arm_pose", "entity_pose"}) {
                if (o.has(f) && o.get(f).isJsonPrimitive()) o.addProperty(f, o.get(f).getAsString().toLowerCase(Locale.ROOT));
            }
        }

        // Fields Apoli 2.9 treated as optional.
        if (type.equals("prevent_block_use") && !o.has("block_condition")) o.add("block_condition", constantTrue());
        if (type.equals("prevent_item_use") && !o.has("item_condition")) o.add("item_condition", constantTrue());
        if (type.equals("prevent_entity_use") && !o.has("bientity_condition") && !o.has("item_condition")) o.add("bientity_condition", constantTrue());
    
        ApoliTypeCompat.fix(o, type, dv);
    }

    private static final String[] VANILLA_ITEM_TAGS = {"CustomModelData", "Enchantments", "StoredEnchantments", "display",
        "Damage", "Unbreakable", "Potion", "CustomPotionEffects", "CustomPotionColor", "AttributeModifiers", "SkullOwner",
        "BlockEntityTag", "HideFlags", "RepairCost", "Trim", "Fireworks", "Explosion", "pages", "author", "title",
        "generation", "EntityTag", "ChargedProjectiles", "Charged", "LodestonePos", "LodestoneDimension", "LodestoneTracked",
        "BucketVariantTag", "CanDestroy", "CanPlaceOn"};

    private static String legacyKey(String key) {
        return switch (key) {
            case "primary", "none" -> "key.origins.primary_active";
            case "secondary" -> "key.origins.secondary_active";
            default -> null;
        };
    }

    static void defaultBadgeType(JsonObject b) {
        // Origins 1.10.2's default badge factory is the keybind badge (text = translation key with the key's name).
        if (!b.has("type")) b.addProperty("type", b.has("text") ? "origins:keybind" : "origins:sprite");
    }

    private static String legacyDamageTypeId(String msgId) {
        String ns = "minecraft", path = msgId;
        int c = msgId.indexOf(':');
        if (c > 0) { ns = msgId.substring(0, c); path = msgId.substring(c + 1); }
        int dot = path.indexOf('.');
        if (dot > 0) path = path.substring(0, dot);
        String snake = path.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        if (snake.equals("out_of_world")) snake = "out_of_world";
        return ns.toLowerCase(Locale.ROOT) + ":" + snake;
    }

    private static void stripHud(JsonObject h) {
        for (String k : new String[]{"max", "fallback", "min", "type", "hud_render", "min_action", "max_action", "start_value"}) h.remove(k);
    }

    static void upgradeBadge(JsonObject b, int dv) {
        defaultBadgeType(b);
    }

    private static JsonObject constantTrue() {
        JsonObject c = new JsonObject();
        c.addProperty("type", "apoli:constant");
        c.addProperty("value", true);
        return c;
    }

    static String damageTypeFor(JsonObject source) {
        int idx = 0;
        for (int i = 0; i < DAMAGE_FLAGS.length; i++) {
            idx <<= 1;
            if (source.has(DAMAGE_FLAGS[i]) && source.get(DAMAGE_FLAGS[i]).isJsonPrimitive() && source.get(DAMAGE_FLAGS[i]).getAsBoolean()) idx |= 1;
        }
        String name = source.has("name") ? source.get("name").getAsString() : "";
        // A few legacy names have an exact vanilla damage type.
        String exact = switch (name) {
            case "inFire" -> "in_fire";
            case "onFire" -> "on_fire";
            case "lava" -> "lava";
            case "hotFloor" -> "hot_floor";
            case "inWall" -> "in_wall";
            case "cramming" -> "cramming";
            case "drown" -> "drown";
            case "starve" -> "starve";
            case "cactus" -> "cactus";
            case "fall" -> "fall";
            case "flyIntoWall" -> "fly_into_wall";
            case "outOfWorld" -> "out_of_world";
            case "generic" -> "generic";
            case "magic" -> "magic";
            case "wither" -> "wither";
            case "dragonBreath" -> "dragon_breath";
            case "dryout" -> "dry_out";
            case "sweetBerryBush" -> "sweet_berry_bush";
            case "freeze" -> "freeze";
            case "lightningBolt" -> "lightning_bolt";
            default -> null;
        };
        return "minecraft:" + (exact != null ? exact : DAMAGE_TABLE[idx]);
    }

    static CompoundTag nbtOf(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonPrimitive()) return NbtUpgrader.parse(el.getAsString());
        if (el.isJsonObject()) {
            Tag t = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, el);
            return t instanceof CompoundTag c ? c : null;
        }
        return null;
    }

    static JsonElement fixParticle(JsonElement p, int dv) {
        String legacy = null;
        if (p.isJsonPrimitive() && p.getAsJsonPrimitive().isString()) {
            String s = p.getAsString().trim();
            if (s.contains(" ")) legacy = s;
        } else if (p.isJsonObject()) {
            JsonObject po = p.getAsJsonObject();
            if (po.has("type") && po.has("params") && po.get("params").isJsonPrimitive()) {
                String params = po.get("params").getAsString().trim();
                if (params.startsWith("{")) {
                    CompoundTag t = NbtUpgrader.parse(params);
                    if (t == null) return null;
                    JsonObject out = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, t).getAsJsonObject();
                    out.add("type", po.get("type"));
                    return out;
                }
                legacy = po.get("type").getAsString() + (params.isEmpty() ? "" : " " + params);
            }
        }
        if (legacy == null) return null;
        CompoundTag fixed = NbtUpgrader.fixParticle(legacy, dv);
        if (fixed == null) return null;
        return NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, fixed);
    }
}
