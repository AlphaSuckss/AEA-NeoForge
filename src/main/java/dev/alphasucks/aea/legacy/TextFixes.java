package dev.alphasucks.aea.legacy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text-level and component-level fixes shared by the upgraders. */
public final class TextFixes {
    private TextFixes() {}

    private static final Pattern COLOR = Pattern.compile("(,\\s*)?(\\\\*)\"color\\2\"\\s*:\\s*\\2\"([^\"\\\\]*)\\2\"(\\s*,)?");
    private static final Pattern STRING_BOOL = Pattern.compile("(\\\\*)\"(bold|italic|underlined|strikethrough|obfuscated|interpret)\\1\"(\\s*:\\s*)\\1\"(true|false)\\1\"");
    private static final Set<String> COLORS = Set.of("black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
        "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white");
    private static final Pattern GRASS = Pattern.compile("minecraft:grass(?![_a-z0-9])");
    private static final Pattern SCUTE = Pattern.compile("minecraft:scute(?![_a-z0-9])");
    private static final Pattern SWEEPING = Pattern.compile("minecraft:sweeping(?![_a-z0-9])");
    private static final Pattern JUMP = Pattern.compile("(minecraft:)?horse\\.jump_strength(?![_a-z0-9])");
    /** {@code "extra": []} at any escaping depth (1.20.3+ requires a non-empty list). */
    private static final Pattern EMPTY_EXTRA = Pattern.compile("(,\\s*)?(\\\\*)\"extra\\2\"\\s*:\\s*\\[\\s*\\](\\s*,)?");
    /** {@code "text": 5} / {@code "text": true}: 1.20.3+ only takes strings. */
    private static final Pattern NUMBER_TEXT = Pattern.compile("(\\\\*)\"text\\1\"(\\s*:\\s*)(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?|true|false)(?=\\s*[,}\\]])");

    /** 1.20.1 accepted {@code "color":"reset"} in text components; 1.21.1 rejects the whole component. */
    public static String stripResetColor(String s) {
        if (s.contains("\"color") || s.contains("color\\\"")) {
            Matcher m = COLOR.matcher(s);
            StringBuilder out = new StringBuilder();
            while (m.find()) {
                String color = m.group(3).toLowerCase(Locale.ROOT);
                String fixed = fixColor(color);
                if (fixed != null && fixed.equals(m.group(3))) {
                    m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
                } else if (fixed != null) {
                    String q = m.group(2);
                    m.appendReplacement(out, Matcher.quoteReplacement((m.group(1) == null ? "" : m.group(1)) + q + "\"color" + q + "\":" + q + "\"" + fixed + q + "\"" + (m.group(4) == null ? "" : m.group(4))));
                } else {
                    boolean lead = m.group(1) != null, trail = m.group(4) != null;
                    m.appendReplacement(out, lead && trail ? "," : "");
                }
            }
            m.appendTail(out);
            s = out.toString();
        }
        if (s.contains("\"true") || s.contains("\"false")) {
            s = STRING_BOOL.matcher(s).replaceAll("$1\"$2$1\"$3$4");
        }
        return s;
    }

    /** 1.20.1 silently ignored unknown colours; 1.21.1 rejects the component. */
    private static String fixColor(String c) {
        if (c.startsWith("#") || COLORS.contains(c)) return c;
        return switch (c) {
            case "grey", "light_gray", "light_grey", "silver" -> "gray";
            case "dark_grey" -> "dark_gray";
            case "purple", "magenta", "pink" -> "light_purple";
            case "orange" -> "gold";
            case "cyan", "light_blue" -> "aqua";
            case "lime" -> "green";
            case "brown" -> "gold";
            default -> null;
        };
    }

    /**
     * Ids renamed after 1.20.1 (the full list from the 1.20.2-1.21.1 DataFixer): grass → short_grass (1.20.3),
     * scute → turtle_scute, sweeping → sweeping_edge, horse.jump_strength → generic.jump_strength (1.20.5).
     */
    public static String renameIds(String s) {
        if (s.contains("minecraft:grass")) s = GRASS.matcher(s).replaceAll("minecraft:short_grass");
        if (s.contains("minecraft:scute")) s = SCUTE.matcher(s).replaceAll("minecraft:turtle_scute");
        if (s.contains("minecraft:sweeping")) s = SWEEPING.matcher(s).replaceAll("minecraft:sweeping_edge");
        if (s.contains("horse.jump_strength")) s = JUMP.matcher(s).replaceAll("minecraft:generic.jump_strength");
        return s;
    }

    /** Text component strictness (1.20.3): empty "extra" lists and non-string "text" values. */
    public static String strictText(String s) {
        if (s.contains("extra")) {
            Matcher m = EMPTY_EXTRA.matcher(s);
            StringBuilder out = new StringBuilder();
            while (m.find()) {
                boolean lead = m.group(1) != null, trail = m.group(3) != null;
                m.appendReplacement(out, lead && trail ? "," : "");
            }
            m.appendTail(out);
            s = out.toString();
        }
        if (s.contains("text")) s = NUMBER_TEXT.matcher(s).replaceAll("$1\"text$1\"$2$1\"$3$1\"");
        return s;
    }

    public static String all(String s) {
        return renameIds(strictText(stripResetColor(s)));
    }

    private static final Set<String> CUSTOM_DATA_LIKE = Set.of("minecraft:custom_data", "minecraft:entity_data",
        "minecraft:bucket_entity_data", "minecraft:block_entity_data");
    private static final Set<String> BOOLEAN_KEYS = Set.of("show_particles", "show_icon", "ambient", "show_in_tooltip",
        "minecraft:enchantment_glint_override", "minecraft:unbreakable", "resolved", "has_twinkle", "has_trail",
        "minecraft:creative_slot_lock", "minecraft:intangible_projectile", "minecraft:fire_resistant", "minecraft:hide_tooltip",
        "minecraft:hide_additional_tooltip", "can_always_eat", "filtered", "minecraft:glider");

    /** Item components compound (NBT, from the DataFixer) → JSON that DataComponentPatch.CODEC accepts. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static JsonElement componentsToJson(CompoundTag comps) {
        JsonObject out = new JsonObject();
        for (String key : comps.getAllKeys()) {
            Tag value = comps.get(key);
            if (key.startsWith("!")) {
                out.add(key, new JsonObject());
                continue;
            }
            if (CUSTOM_DATA_LIKE.contains(key) && value instanceof CompoundTag ct) {
                out.addProperty(key, ct.toString());
                continue;
            }
            JsonElement typed = null;
            ResourceLocation rl = ResourceLocation.tryParse(key);
            DataComponentType<?> type = rl == null ? null : BuiltInRegistries.DATA_COMPONENT_TYPE.get(rl);
            if (type != null && type.codec() != null) {
                Codec codec = type.codec();
                DataResult<?> parsed = codec.parse(NbtOps.INSTANCE, value);
                if (parsed.result().isPresent()) {
                    DataResult<JsonElement> enc = codec.encodeStart(JsonOps.INSTANCE, parsed.result().get());
                    typed = enc.result().orElse(null);
                }
            }
            out.add(key, typed != null ? typed : heuristic(key, value));
        }
        return out;
    }

    private static JsonElement heuristic(String key, Tag value) {
        if (value instanceof ByteTag b && BOOLEAN_KEYS.contains(key)) return new JsonPrimitive(b.getAsByte() != 0);
        if (value instanceof CompoundTag c) {
            JsonObject o = new JsonObject();
            for (String k : c.getAllKeys()) o.add(k, heuristic(k, c.get(k)));
            return o;
        }
        if (value instanceof ListTag l) {
            JsonArray a = new JsonArray();
            for (Tag t : l) a.add(heuristic(key, t));
            return a;
        }
        return NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, value);
    }
}
