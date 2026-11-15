package dev.alphasucks.aea.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.legacy.ApoliJsonUpgrader;
import dev.overgrown.apoli.action.BiEntityAction;
import dev.overgrown.apoli.action.BlockAction;
import dev.overgrown.apoli.action.EntityAction;
import dev.overgrown.apoli.action.ItemAction;
import dev.overgrown.apoli.condition.BiEntityCondition;
import dev.overgrown.apoli.condition.BiomeCondition;
import dev.overgrown.apoli.condition.BlockCondition;
import dev.overgrown.apoli.condition.DamageCondition;
import dev.overgrown.apoli.condition.EntityCondition;
import dev.overgrown.apoli.condition.FluidCondition;
import dev.overgrown.apoli.condition.ItemCondition;
import dev.overgrown.apoli.loader.ApoliReloadListener;
import dev.overgrown.apoli.power.Power;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Debug-only (-Daea.selftest=true): checks every type Apoli 2.9.2 / Origins 1.10.2 registered against the live
 * registries. Each sample (tools/samples.json, every field filled in, written the 1.20.1 way) goes through AEA's legacy
 * upgrade and Overgrown's own decoding. For every field the audit checks that the decoder actually reads it.
 * Input: -Daea.audit.samples=&lt;path&gt;. Output: the log and -Daea.audit.out (default aea-audit.txt).
 */
final class ApoliAudit {
    private ApoliAudit() {}

    private static RegistryOps<JsonElement> ops;

    static int run(CommandSourceStack src) {
        ops = RegistryOps.create(JsonOps.INSTANCE, src.getServer().registryAccess());
        Path in = Path.of(System.getProperty("aea.audit.samples", "aea-audit-samples.json"));
        Path out = Path.of(System.getProperty("aea.audit.out", "aea-audit.txt"));
        JsonArray samples;
        try {
            samples = JsonParser.parseString(Files.readString(in)).getAsJsonArray();
        } catch (Exception e) {
            src.sendFailure(Component.literal("[AEA audit] can't read " + in.toAbsolutePath() + ": " + e));
            return 0;
        }
        List<String> lines = new ArrayList<>();
        int ok = 0, missing = 0, failed = 0, ignored = 0, unsure = 0, defaults = 0;
        for (JsonElement el : samples) {
            JsonObject s = el.getAsJsonObject();
            String ctx = s.get("context").getAsString();
            String id = s.get("id").getAsString();
            JsonObject sample = s.getAsJsonObject("sample");
            Function<JsonObject, DataResult<String>> dec = decoder(ctx);
            if (dec == null) { lines.add("SKIP " + ctx + " " + id); continue; }
            DataResult<String> base = dec.apply(sample.deepCopy());
            if (base.error().isPresent()) {
                String msg = base.error().get().message();
                boolean isMissing = msg.contains("Unknown") || msg.contains("unknown") || msg.contains("not a known") || msg.contains("No key")
                    && msg.contains("type");
                if (isMissing && (msg.toLowerCase().contains("type") || msg.toLowerCase().contains("registry"))) {
                    missing++;
                    lines.add("MISSING " + ctx + " " + id + " :: " + oneLine(msg));
                } else {
                    failed++;
                    lines.add("FAIL " + ctx + " " + id + " :: " + oneLine(msg));
                }
                continue;
            }
            String s0 = base.result().get();
            // Apoli 2.9 only required these; the rest were optional.
            JsonObject minimal = new JsonObject();
            minimal.add("type", sample.get("type"));
            for (JsonElement r : s.getAsJsonArray("required")) if (sample.has(r.getAsString())) minimal.add(r.getAsString(), sample.get(r.getAsString()));
            DataResult<String> rmin = dec.apply(minimal);
            String minimalNote = rmin.error().map(e -> "MINIMAL-FAILS (" + oneLine(e.message()) + ")").orElse(null);
            String s0b = dec.apply(sample.deepCopy()).result().orElse("");
            boolean deterministic = s0.equals(s0b);
            List<String> fieldNotes = new ArrayList<>();
            if (minimalNote != null) { failed++; fieldNotes.add(minimalNote); }
            for (JsonElement f : s.getAsJsonArray("fields")) {
                String field = f.getAsString();
                if (!sample.has(field)) continue;
                JsonObject absent = sample.deepCopy();
                absent.remove(field);
                DataResult<String> ra = dec.apply(absent);
                boolean read;
                if (ra.error().isPresent()) {
                    read = true; // required and parsed
                } else if (deterministic && !ra.result().get().equals(s0)) {
                    read = true;
                } else {
                    // Same as absent: either ignored, or the sample equals the default. Try another value, then garbage.
                    read = false;
                    JsonElement variant = variant(sample.get(field));
                    if (variant != null && deterministic) {
                        JsonObject v = sample.deepCopy();
                        v.add(field, variant);
                        DataResult<String> rv = dec.apply(v);
                        if (rv.error().isPresent() || !rv.result().get().equals(s0)) read = true;
                    }
                    if (!read) {
                        JsonObject g = sample.deepCopy();
                        JsonObject garbage = new JsonObject();
                        garbage.add("aea_invalid", new JsonPrimitive(true));
                        g.add(field, garbage);
                        DataResult<String> rg = dec.apply(g);
                        if (rg.error().isPresent()) read = true;
                        else if (deterministic && !rg.result().get().equals(s0) && !rg.result().get().equals(ra.result().get())) read = true;
                    }
                    if (!read) {
                        if (deterministic) { ignored++; fieldNotes.add("IGNORED " + field); }
                        else { unsure++; fieldNotes.add("UNSURE " + field); }
                    }
                }
            }
            if (deterministic && s.has("default_tests")) {
                for (Map.Entry<String, JsonElement> d : s.getAsJsonObject("default_tests").entrySet()) {
                    JsonObject withDefault = sample.deepCopy();
                    withDefault.add(d.getKey(), d.getValue());
                    JsonObject without = sample.deepCopy();
                    without.remove(d.getKey());
                    DataResult<String> a = dec.apply(withDefault), b = dec.apply(without);
                    if (a.result().isPresent() && b.result().isPresent() && !a.result().get().equals(b.result().get())) {
                        defaults++;
                        fieldNotes.add("DEFAULT " + d.getKey() + "=" + d.getValue() + " {" + diff(a.result().get(), b.result().get()) + "}");
                    }
                }
            }
            if (fieldNotes.isEmpty()) { ok++; lines.add("OK " + ctx + " " + id); }
            else lines.add("FIELDS " + ctx + " " + id + " :: " + String.join(", ", fieldNotes));
        }
        String summary = "[AEA audit] " + samples.size() + " types: " + ok + " ok, " + missing + " missing, " + failed + " failed, "
            + ignored + " ignored fields, " + unsure + " unsure fields, " + defaults + " default differences";
        lines.add(0, summary);
        try {
            Files.write(out, lines);
        } catch (Exception e) {
            AEA.LOGGER.error("[AEA audit] couldn't write {}", out, e);
        }
        for (String l : lines) if (!l.startsWith("OK ")) AEA.LOGGER.info("[AEA audit] {}", l);
        src.sendSuccess(() -> Component.literal(summary + " -> " + out.toAbsolutePath()), false);
        return 1;
    }

    private static String oneLine(String s) {
        s = s.replace('\n', ' ');
        return s.length() > 400 ? s.substring(0, 400) + "..." : s;
    }

    /** "with default" vs "absent": the parts that differ. */
    private static String diff(String a, String b) {
        int p = 0;
        while (p < a.length() && p < b.length() && a.charAt(p) == b.charAt(p)) p++;
        int sa = a.length(), sb = b.length();
        while (sa > p && sb > p && a.charAt(sa - 1) == b.charAt(sb - 1)) { sa--; sb--; }
        int from = Math.max(0, p - 30);
        return "default:" + a.substring(from, Math.min(a.length(), sa + 10)) + " | absent:" + b.substring(from, Math.min(b.length(), sb + 10));
    }

    private static JsonElement variant(JsonElement v) {
        if (v == null || !v.isJsonPrimitive()) return null;
        JsonPrimitive p = v.getAsJsonPrimitive();
        if (p.isBoolean()) return new JsonPrimitive(!p.getAsBoolean());
        if (p.isNumber()) {
            double d = p.getAsDouble();
            return d == Math.rint(d) ? new JsonPrimitive((long) d * 3 + 1) : new JsonPrimitive(d * 3 + 0.25);
        }
        return null;
    }

    private static Function<JsonObject, DataResult<String>> decoder(String ctx) {
        return switch (ctx) {
            case "power" -> ApoliAudit::decodePower;
            case "entity_condition" -> j -> decode(EntityCondition.CODEC, j);
            case "bientity_condition" -> j -> decode(BiEntityCondition.CODEC, j);
            case "item_condition" -> j -> decode(ItemCondition.CODEC, j);
            case "block_condition" -> j -> decode(BlockCondition.CODEC, j);
            case "damage_condition" -> j -> decode(DamageCondition.CODEC, j);
            case "fluid_condition" -> j -> decode(FluidCondition.CODEC, j);
            case "biome_condition" -> j -> decode(BiomeCondition.CODEC, j);
            case "entity_action" -> j -> decode(EntityAction.CODEC, j);
            case "bientity_action" -> j -> decode(BiEntityAction.CODEC, j);
            case "block_action" -> j -> decode(BlockAction.CODEC, j);
            case "item_action" -> j -> decode(ItemAction.CODEC, j);
            default -> null;
        };
    }

    private static JsonElement legacy(JsonObject j) {
        return ApoliJsonUpgrader.upgrade(j, 3465);
    }

    private static <T> DataResult<String> decode(Codec<T> codec, JsonObject j) {
        try {
            return codec.parse(ops, legacy(j)).map(String::valueOf);
        } catch (Throwable t) {
            return DataResult.error(() -> "threw " + t);
        }
    }

    private static DataResult<String> decodePower(JsonObject j) {
        try {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aea_audit", "sample");
            boolean wasMultiple = j.get("type").getAsString().endsWith("multiple");
            JsonElement up = legacy(j);
            if (!wasMultiple && up.isJsonObject() && up.getAsJsonObject().get("type").getAsString().endsWith("multiple")) {
                // AEA turned this power into several (e.g. toggle_night_vision): decode each part.
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, JsonElement> e : up.getAsJsonObject().entrySet()) {
                    if (!(e.getValue() instanceof JsonObject sub) || !sub.has("type")) continue;
                    DataResult<String> r = Power.CODEC.parse(
                        ApoliReloadListener.prepare(new Dynamic<>(ops, (JsonElement) sub), id)).map(String::valueOf);
                    if (r.error().isPresent()) return r;
                    sb.append(e.getKey()).append('=').append(r.result().get()).append(';');
                }
                return DataResult.success(sb.toString());
            }
            Dynamic<JsonElement> prepared = ApoliReloadListener.prepare(new Dynamic<>(ops, up), id);
            return Power.CODEC.parse(prepared).map(String::valueOf);
        } catch (Throwable t) {
            return DataResult.error(() -> "threw " + t);
        }
    }

    static Map<String, String> unused() { return new LinkedHashMap<>(); }
}
