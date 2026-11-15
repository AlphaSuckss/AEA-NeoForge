package dev.alphasucks.aea.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.gui.power.GuiPower;
import dev.alphasucks.aea.legacy.Diagnostics;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.action.ActionTypes;
import dev.overgrown.apoli.action.TypedActionRegistry;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.ConditionTypes;
import dev.overgrown.apoli.condition.TypedConditionRegistry;
import dev.overgrown.apoli.power.PowerType;
import dev.overgrown.apoli.power.PowerTypeRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Reports fields in legacy power JSON that no codec for that type reads (they would be silently ignored). */
final class FieldAudit {
    private FieldAudit() {}

    private static final Set<String> POWER_COMMON = Set.of("type", "name", "description", "hidden", "condition", "loading_priority",
        "badges", "tags", "skill", "load_condition");
    private static final List<TypedActionRegistry<?>> ACTIONS = List.of(ActionTypes.ENTITY, ActionTypes.BI_ENTITY, ActionTypes.BLOCK, ActionTypes.ITEM);
    private static final List<TypedConditionRegistry<?>> CONDITIONS = List.of(ConditionTypes.ENTITY, ConditionTypes.BI_ENTITY,
        ConditionTypes.BLOCK, ConditionTypes.ITEM, ConditionTypes.DAMAGE, ConditionTypes.FLUID, ConditionTypes.BIOME);

    static int run(CommandSourceStack src) {
        Map<String, Integer> unknown = new TreeMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> e : Diagnostics.POWERS.entrySet()) {
            if (e.getValue() instanceof JsonObject root) walk(root, true, unknown);
        }
        unknown.forEach((k, n) -> AEA.LOGGER.warn("[AEA] fields: {} ignored x{}", k, n));
        int total = unknown.size();
        src.sendSuccess(() -> Component.literal("[AEA] " + total + " distinct legacy field(s) are not read by any codec (see log)."), false);
        return total;
    }

    private static void walk(JsonElement el, boolean powerRoot, Map<String, Integer> out) {
        if (el instanceof JsonArray a) {
            for (JsonElement c : a) walk(c, false, out);
            return;
        }
        if (!(el instanceof JsonObject o)) return;
        String typeStr = o.has("type") && o.get("type").isJsonPrimitive() ? o.get("type").getAsString() : null;
        ResourceLocation id = typeStr == null ? null : ResourceLocation.tryParse(typeStr);
        boolean multiple = id != null && powerRoot && PowerTypeRegistry.resolveId(id).getPath().equals("multiple");
        if (id != null && !multiple) {
            Set<String> keys = new HashSet<>();
            boolean known = false;
            if (powerRoot) {
                PowerType<?> pt = PowerTypeRegistry.get(id);
                if (pt != null) { known = true; keys.addAll(keys(pt.configCodec())); keys.addAll(POWER_COMMON); }
            }
            for (TypedActionRegistry<?> r : ACTIONS) {
                ActionType<?, ?> t = r.get(id);
                if (t != null) { known = true; keys.addAll(keys(t.codec())); }
            }
            for (TypedConditionRegistry<?> r : CONDITIONS) {
                ConditionType<?, ?> t = r.get(id);
                if (t != null) { known = true; keys.addAll(keys(t.codec())); keys.add("inverted"); }
            }
            if (!powerRoot) {
                PowerType<?> pt = PowerTypeRegistry.get(id);
                if (pt != null && !known) { known = true; keys.addAll(keys(pt.configCodec())); keys.addAll(POWER_COMMON); }
            }
            if (known && keys.size() > 1) {
                for (String k : o.keySet()) {
                    if (k.equals("type") || keys.contains(k)) continue;
                    out.merge(PowerTypeRegistry.resolveId(id) + " ." + k + "   (as " + typeStr + ")", 1, Integer::sum);
                }
            }
        }
        boolean guiPower = id != null && PowerTypeRegistry.resolveId(id).equals(GuiPower.ID);
        for (Map.Entry<String, JsonElement> c : o.entrySet()) {
            if (c.getKey().equals("badges")) continue;
            // aea:gui elements carry their own "type" (aea:text, aea:slot, ...): audit what is inside them, not the element itself.
            if (guiPower && c.getKey().equals("elements") && c.getValue() instanceof JsonArray elements) {
                for (JsonElement e : elements) {
                    if (e instanceof JsonObject eo) for (Map.Entry<String, JsonElement> f : eo.entrySet()) walk(f.getValue(), false, out);
                }
                continue;
            }
            boolean childIsPower = multiple && c.getValue().isJsonObject() && c.getValue().getAsJsonObject().has("type");
            walk(c.getValue(), childIsPower, out);
        }
    }

    private static Set<String> keys(MapCodec<?> codec) {
        Set<String> out = new HashSet<>();
        try {
            codec.keys(JsonOps.INSTANCE).map(j -> j.isJsonPrimitive() ? j.getAsString() : j.toString()).forEach(out::add);
        } catch (Throwable ignored) {
        }
        aliasKeys(codec, out, 0);
        return out;
    }

    /** Old field names accepted through Overgrown's AliasingMapCodec / field-alias wrappers. */
    @SuppressWarnings("unchecked")
    private static void aliasKeys(Object codec, Set<String> out, int depth) {
        if (codec == null || depth > 4) return;
        for (Class<?> c = codec.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(codec);
                    if (v instanceof Map<?, ?> m && f.getName().toLowerCase(Locale.ROOT).contains("old")) {
                        for (Object k : m.keySet()) if (k instanceof String s) out.add(s);
                    } else if (v instanceof Map<?, ?> m && f.getName().toLowerCase(Locale.ROOT).contains("default")) {
                        for (Object k : m.keySet()) if (k instanceof String s) out.add(s);
                    } else if (v instanceof MapCodec<?> inner && inner != codec) {
                        try {
                            inner.keys(JsonOps.INSTANCE).map(j -> j.isJsonPrimitive() ? j.getAsString() : j.toString()).forEach(out::add);
                        } catch (Throwable ignored) {
                        }
                        aliasKeys(inner, out, depth + 1);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
