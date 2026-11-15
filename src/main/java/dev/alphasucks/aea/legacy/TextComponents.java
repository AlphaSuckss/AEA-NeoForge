package dev.alphasucks.aea.legacy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.io.StringReader;

/**
 * JSON text components: 1.20.3 replaced the lenient Gson parser with strict codecs. Things 1.20.1 accepted and 1.21.1
 * rejects (the whole component fails):
 * <ul>
 *   <li>{@code "extra": []} (must be non-empty);</li>
 *   <li>non-string {@code "text"} ({@code "text": 5}) and bare numbers/booleans as components;</li>
 *   <li>string booleans for style flags, unknown colour names (also handled at text level by {@link TextFixes});</li>
 *   <li>{@code show_item} hover events carrying 1.20.1 item NBT ({@code "tag"} / legacy {@code "value"}).</li>
 * </ul>
 */
public final class TextComponents {
    private TextComponents() {}

    private static final String[] FLAGS = {"bold", "italic", "underlined", "strikethrough", "obfuscated", "interpret"};

    public static JsonElement fix(JsonElement c, int dv) {
        if (c == null || c.isJsonNull()) return c;
        if (c.isJsonPrimitive()) {
            JsonPrimitive p = c.getAsJsonPrimitive();
            return p.isString() ? p : new JsonPrimitive(p.getAsString());
        }
        if (c.isJsonArray()) {
            JsonArray a = c.getAsJsonArray();
            if (a.isEmpty()) return new JsonPrimitive("");
            JsonArray out = new JsonArray();
            for (JsonElement e : a) out.add(fix(e, dv));
            return out;
        }
        JsonObject o = c.getAsJsonObject();
        if (o.has("text") && o.get("text").isJsonPrimitive() && !o.getAsJsonPrimitive("text").isString()) {
            o.addProperty("text", o.get("text").getAsString());
        }
        if (o.has("extra")) {
            JsonElement ex = o.get("extra");
            if (ex.isJsonArray() && ex.getAsJsonArray().isEmpty()) o.remove("extra");
            else if (ex.isJsonArray()) {
                JsonArray out = new JsonArray();
                for (JsonElement e : ex.getAsJsonArray()) out.add(fix(e, dv));
                o.add("extra", out);
            } else o.add("extra", wrap(fix(ex, dv)));
        }
        if (o.has("with") && o.get("with").isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement e : o.getAsJsonArray("with")) out.add(e.isJsonPrimitive() ? e : fix(e, dv));
            o.add("with", out);
        }
        if (o.has("separator")) o.add("separator", fix(o.get("separator"), dv));
        for (String f : FLAGS) {
            if (o.has(f) && o.get(f).isJsonPrimitive() && o.getAsJsonPrimitive(f).isString()) {
                String v = o.get(f).getAsString();
                if (v.equals("true") || v.equals("false")) o.addProperty(f, Boolean.parseBoolean(v));
                else o.remove(f);
            }
        }
        if (o.has("hoverEvent") && o.get("hoverEvent").isJsonObject()) hover(o.getAsJsonObject("hoverEvent"), dv);
        if (o.has("clickEvent") && o.get("clickEvent").isJsonObject()) {
            JsonObject ce = o.getAsJsonObject("clickEvent");
            if (ce.has("value") && ce.get("value").isJsonPrimitive() && !ce.getAsJsonPrimitive("value").isString()) {
                ce.addProperty("value", ce.get("value").getAsString());
            }
        }
        return o;
    }

    private static JsonArray wrap(JsonElement e) {
        JsonArray a = new JsonArray();
        a.add(e);
        return a;
    }

    private static void hover(JsonObject h, int dv) {
        String action = h.has("action") ? h.get("action").getAsString() : "";
        switch (action) {
            case "show_text" -> {
                if (h.has("contents")) h.add("contents", fix(h.get("contents"), dv));
                if (h.has("value")) h.add("value", fix(h.get("value"), dv));
            }
            case "show_item" -> {
                JsonObject contents = null;
                if (h.has("contents") && h.get("contents").isJsonObject()) contents = h.getAsJsonObject("contents");
                else if (h.has("contents") && h.get("contents").isJsonPrimitive()) {
                    contents = new JsonObject();
                    contents.add("id", h.get("contents"));
                } else if (h.has("value")) {
                    // Legacy: the item as SNBT text {id:"x",Count:1b,tag:{...}}.
                    JsonElement v = h.get("value");
                    String snbt = v.isJsonPrimitive() ? v.getAsString() : v.isJsonObject() && v.getAsJsonObject().has("text") ? v.getAsJsonObject().get("text").getAsString() : null;
                    CompoundTag tag = snbt == null ? null : NbtUpgrader.parse(snbt);
                    if (tag != null) {
                        CompoundTag fixed = NbtUpgrader.fixItemStack(tag, dv);
                        contents = new JsonObject();
                        contents.addProperty("id", fixed.getString("id"));
                        if (fixed.contains("count")) contents.addProperty("count", fixed.getInt("count"));
                        if (fixed.contains("components", Tag.TAG_COMPOUND)) contents.add("components", TextFixes.componentsToJson(fixed.getCompound("components")));
                        h.remove("value");
                    }
                }
                if (contents != null) {
                    if (contents.has("Count") && !contents.has("count")) { contents.add("count", contents.get("Count")); contents.remove("Count"); }
                    if (contents.has("tag") && contents.has("id")) {
                        CompoundTag tag = ApoliJsonUpgrader.nbtOf(contents.get("tag"));
                        if (tag != null) {
                            CompoundTag legacy = new CompoundTag();
                            legacy.putString("id", contents.get("id").getAsString());
                            legacy.putByte("Count", (byte) 1);
                            legacy.put("tag", tag);
                            CompoundTag fixed = NbtUpgrader.fixItemStack(legacy, dv);
                            if (fixed.contains("components", Tag.TAG_COMPOUND)) contents.add("components", TextFixes.componentsToJson(fixed.getCompound("components")));
                        }
                        contents.remove("tag");
                    }
                    h.add("contents", contents);
                }
            }
            case "show_entity" -> {
                if (h.has("contents") && h.get("contents").isJsonObject()) {
                    JsonObject c = h.getAsJsonObject("contents");
                    if (c.has("name")) c.add("name", fix(c.get("name"), dv));
                }
            }
            default -> {}
        }
    }

    /** A text component given as a JSON string (command argument, NBT value): parse, fix, re-serialise. */
    public static String fixString(String json, int dv) {
        String t = json.trim();
        if (t.isEmpty() || !(t.startsWith("{") || t.startsWith("[") || t.startsWith("\""))) return json;
        try {
            JsonReader r = new JsonReader(new StringReader(t));
            r.setLenient(true);
            JsonElement el = JsonParser.parseReader(r);
            String before = el.toString();
            String out = fix(el, dv).toString();
            return out.equals(before) ? json : out;
        } catch (Throwable e) {
            return json;
        }
    }

    /** Fix the text components under the given keys of {@code o}. */
    public static void fixFields(JsonObject o, int dv, String... keys) {
        for (String k : keys) {
            if (!o.has(k)) continue;
            JsonElement v = o.get(k);
            if (v.isJsonArray() && k.equals("lore")) {
                JsonArray out = new JsonArray();
                for (JsonElement e : v.getAsJsonArray()) out.add(fix(e, dv));
                o.add(k, out);
            } else {
                o.add(k, fix(v, dv));
            }
        }
    }
}
