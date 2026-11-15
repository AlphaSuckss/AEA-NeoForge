package dev.alphasucks.aea.legacy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.AEAConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.fml.ModList;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Makes data packs written for 1.20.4-and-older load on 1.21.1:
 * <ul>
 *   <li>plural folder names (functions/, tags/items/, loot_tables/ ...) are served under the 1.21 singular names;</li>
 *   <li>file contents are upgraded on read (commands, item NBT → components, Apoli/Origins JSON, recipes, ...).</li>
 * </ul>
 * Only packs whose pack.mcmeta declares an older pack_format are touched.
 */
public final class LegacyResources {
    private LegacyResources() {}

    public static final int CURRENT_FORMAT = 48;
    /** Sentinel: "not handled, run vanilla". */
    @SuppressWarnings("unchecked")
    public static final IoSupplier<InputStream> PASS = (IoSupplier<InputStream>) (IoSupplier<?>) () -> null;

    public record Info(boolean legacy, int format, int dataVersion) {
        boolean renameFolders() { return legacy && format < 45; }
        boolean upgradeContent() { return legacy && format < 41; }
    }

    private static final Info NOT_LEGACY = new Info(false, CURRENT_FORMAT, NbtUpgrader.currentVersion());
    private static final Map<PackResources, Info> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<int[]> GUARD = ThreadLocal.withInitial(() -> new int[1]);

    /** new 1.21 folder → pre-1.21 folder. */
    private static final String[][] FOLDERS = {
        {"function", "functions"},
        {"tags/function", "tags/functions"},
        {"tags/item", "tags/items"},
        {"tags/block", "tags/blocks"},
        {"tags/entity_type", "tags/entity_types"},
        {"tags/fluid", "tags/fluids"},
        {"tags/game_event", "tags/game_events"},
        {"loot_table", "loot_tables"},
        {"recipe", "recipes"},
        {"advancement", "advancements"},
        {"predicate", "predicates"},
        {"item_modifier", "item_modifiers"},
        {"structure", "structures"},
    };

    public static Info info(PackResources pack) {
        Info cached = CACHE.get(pack);
        if (cached != null) return cached;
        Info info = NOT_LEGACY;
        try {
            String id = pack.packId();
            boolean candidate = !(id.startsWith("mod:") || id.startsWith("mod/") || id.equals("vanilla")
                || id.startsWith("neoforge") || id.startsWith("builtin/") || id.startsWith("fabric"));
            if (candidate && AEAConfig.legacyPacks()) {
                PackMetadataSection meta = pack.getMetadataSection(PackMetadataSection.TYPE);
                if (meta != null && meta.packFormat() < CURRENT_FORMAT) {
                    int fmt = meta.packFormat();
                    // A pack that explicitly supports 1.21.x is not legacy even with a low primary format.
                    boolean supportsCurrent = meta.supportedFormats().map(r -> r.isValueInRange(CURRENT_FORMAT)).orElse(false);
                    if (!supportsCurrent) {
                        info = new Info(true, fmt, NbtUpgrader.dataVersionForPackFormat(fmt));
                        AEA.LOGGER.info("[AEA] Upgrading legacy data pack '{}' (pack_format {}) for 1.21.1", id, fmt);
                    }
                }
            }
        } catch (Throwable t) {
            AEA.LOGGER.debug("[AEA] Could not read pack metadata: {}", t.toString());
        }
        CACHE.put(pack, info);
        return info;
    }

    private static String legacyDir(String path) {
        for (String[] f : FOLDERS) {
            if (path.equals(f[0])) return f[1];
            if (path.startsWith(f[0] + "/")) return f[1] + path.substring(f[0].length());
        }
        return null;
    }

    private static boolean guarded() {
        return GUARD.get()[0] > 0;
    }

    // Entry points used by the mixins

    public static IoSupplier<InputStream> getResource(PackResources pack, PackType type, ResourceLocation loc) {
        if (type != PackType.SERVER_DATA || guarded()) return PASS;
        Info info = info(pack);
        if (!info.legacy()) return PASS;
        int[] g = GUARD.get();
        g[0]++;
        try {
            IoSupplier<InputStream> sup = pack.getResource(type, loc);
            if (sup == null && info.renameFolders()) {
                String legacy = legacyDir(loc.getPath());
                if (legacy != null) sup = pack.getResource(type, ResourceLocation.fromNamespaceAndPath(loc.getNamespace(), legacy));
            }
            if (sup == null) return null;
            if (!conditionsPass(info, loc, sup)) return null;
            return wrap(info, loc, sup);
        } finally {
            g[0]--;
        }
    }

    /** @return true if the listing was fully handled. */
    public static boolean listResources(PackResources pack, PackType type, String namespace, String path, PackResources.ResourceOutput output) {
        if (type != PackType.SERVER_DATA || guarded()) return false;
        Info info = info(pack);
        if (!info.legacy()) return false;
        int[] g = GUARD.get();
        g[0]++;
        try {
            pack.listResources(type, namespace, path, (id, sup) -> emit(info, id, sup, output));
            if (info.renameFolders()) {
                String legacy = legacyDir(path);
                if (legacy != null) {
                    String newPrefix = path;
                    pack.listResources(type, namespace, legacy, (id, sup) -> {
                        String p = id.getPath();
                        String renamed = newPrefix + p.substring(legacy.length());
                        ResourceLocation nid = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), renamed);
                        emit(info, nid, sup, output);
                    });
                }
            }
            return true;
        } finally {
            g[0]--;
        }
    }

    private static void emit(Info info, ResourceLocation id, IoSupplier<InputStream> sup, PackResources.ResourceOutput output) {
        if (!conditionsPass(info, id, sup)) {
            if (AEAConfig.logRewrites()) AEA.LOGGER.info("[AEA] {} skipped: its load conditions are not met", id);
            return;
        }
        ResourceLocation out = renameConventionalTag(id);
        output.accept(out, wrap(info, out, sup));
    }

    /** Fabric 1.20.1 conventional tag files ({@code c:tags/item/iron_ingots.json}) define the 1.21 names instead. */
    private static ResourceLocation renameConventionalTag(ResourceLocation id) {
        if (!id.getNamespace().equals("c") || !id.getPath().startsWith("tags/") || !id.getPath().endsWith(".json")) return id;
        String p = id.getPath();
        int slash = p.indexOf('/', 5);
        // tags/<registry...>/<name>.json; registry folders may contain a slash (worldgen/biome)
        String reg = p.startsWith("tags/worldgen/") ? p.substring(0, p.indexOf('/', 14) + 1) : slash < 0 ? null : p.substring(0, slash + 1);
        if (reg == null) return id;
        String name = p.substring(reg.length(), p.length() - 5);
        if (name.contains("/")) return id;
        String mapped = ConventionalTags.mapPath(name);
        return mapped == null ? id : ResourceLocation.fromNamespaceAndPath("c", reg + mapped + ".json");
    }

    /** Fabric resource conditions / Forge conditions: false → the file is absent. */
    private static boolean conditionsPass(Info info, ResourceLocation id, IoSupplier<InputStream> sup) {
        if (!info.legacy()) return true;
        String path = id.getPath();
        if (!(path.endsWith(".json") || path.endsWith(".json5"))) return true;
        try (InputStream in = sup.get()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (!LoadConditions.mentions(text)) return true;
            JsonReader reader = new JsonReader(new StringReader(text));
            reader.setLenient(true);
            JsonElement el = JsonParser.parseReader(reader);
            return !el.isJsonObject() || LoadConditions.apply(el.getAsJsonObject(), false);
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean neoForgeEvaluatesConditions(String cat) {
        return switch (cat) {
            case "recipe", "recipes", "advancement", "advancements", "loot_table", "loot_tables", "predicate", "predicates",
                 "item_modifier", "item_modifiers", "worldgen", "dimension", "dimension_type", "damage_type", "chat_type",
                 "trim_material", "trim_pattern" -> true;
            default -> false;
        };
    }

    // Content upgrades

    private static String category(String path) {
        int slash = path.indexOf('/');
        String first = slash < 0 ? path : path.substring(0, slash);
        if (first.equals("tags")) return "tags";
        return first;
    }

    private static IoSupplier<InputStream> wrap(Info info, ResourceLocation id, IoSupplier<InputStream> sup) {
        if (!info.upgradeContent() && !info.renameFolders()) return sup;
        String path = id.getPath();
        String cat = category(path);
        boolean function = cat.equals("function") || cat.equals("functions");
        boolean apoli = switch (cat) {
            case "powers", "power", "origins", "origin", "origin_layers", "origin_layer", "global_powers", "badges", "skill_trees" -> true;
            default -> false;
        };
        boolean vanilla = switch (cat) {
            case "recipe", "recipes", "advancement", "advancements", "loot_table", "loot_tables", "item_modifier",
                 "item_modifiers", "predicate", "predicates" -> true;
            default -> false;
        };
        boolean tags = cat.equals("tags");
        boolean worldgen = info.upgradeContent() && (cat.equals("worldgen") || cat.equals("dimension_type") || cat.equals("dimension"));
        boolean json = path.endsWith(".json") || path.endsWith(".json5");
        // Any other JSON (damage types, chat types, trims, other mods' data) still gets text fixes, conditions and c: tags.
        boolean other = json && info.upgradeContent() && !function && !apoli && !vanilla && !tags && !worldgen;
        if (!function && !apoli && !vanilla && !tags && !worldgen && !other) return sup;
        if (!function && !json) return sup;
        return () -> {
            byte[] bytes;
            try (InputStream in = sup.get()) {
                bytes = in.readAllBytes();
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1);
            String out;
            try {
                if (info.upgradeContent()) text = TextFixes.all(text);
                if (function) {
                    out = info.upgradeContent() ? CommandUpgrader.upgradeFunction(text, info.dataVersion()) : text;
                    if (AEAConfig.logRewrites() && !out.equals(text)) AEA.LOGGER.info("[AEA] Rewrote function {}:\n{}", id, out);
                } else if (tags) {
                    JsonReader reader = new JsonReader(new StringReader(text));
                    reader.setLenient(true);
                    JsonElement el = JsonParser.parseReader(reader);
                    if (el.isJsonObject()) LoadConditions.apply(el.getAsJsonObject(), false);
                    if (el.isJsonObject() && !el.getAsJsonObject().has("values")) el.getAsJsonObject().add("values", new JsonArray());
                    if (el.isJsonObject() && !id.getPath().startsWith("tags/function")) tagEntries(el.getAsJsonObject());
                    out = el.toString();
                } else {
                    JsonReader reader = new JsonReader(new StringReader(text));
                    reader.setLenient(true);
                    JsonElement el = JsonParser.parseReader(reader);
                    if (el.isJsonObject()) LoadConditions.apply(el.getAsJsonObject(), neoForgeEvaluatesConditions(cat));
                    ConventionalTags.walk(el);
                    if (other) {
                        // text fixes / conditions / c: tags only
                    } else if (worldgen) {
                        el = WorldgenUpgrader.upgrade(el);
                    } else if (apoli) {
                        if (cat.equals("badges") && el.isJsonObject()) ApoliJsonUpgrader.defaultBadgeType(el.getAsJsonObject());
                        el = ApoliJsonUpgrader.upgrade(el, info.dataVersion());
                        if (cat.equals("powers") && Diagnostics.POWERS.size() < 50000) Diagnostics.POWERS.put(id, el);
                    } else if (info.upgradeContent()) {
                        String c = cat.endsWith("s") ? cat.substring(0, cat.length() - 1) : cat;
                        el = VanillaJsonUpgrader.upgrade(c, el, info.dataVersion());
                    }
                    out = el.toString();
                }
            } catch (Throwable t) {
                AEA.LOGGER.warn("[AEA] Could not upgrade {} — loading it unchanged: {}", id, t.toString());
                out = text;
            }
            return new ByteArrayInputStream(out.getBytes(StandardCharsets.UTF_8));
        };
    }

    /**
     * Tag entries in legacy packs:
     * <ul>
     *   <li>Fabric v1 conventional tags are renamed to the 1.21 {@code c:} names;</li>
     *   <li>tag references ({@code #...}) become optional: vanilla and Fabric renamed or removed several tags since 1.20.1,
     *   and one missing tag would take the whole tag (and every tag including it) down;</li>
     *   <li>entries from mods that aren't installed become optional, so one missing mod item doesn't do the same.</li>
     * </ul>
     */
    private static void tagEntries(JsonObject tag) {
        if (!tag.has("values") || !tag.get("values").isJsonArray()) return;
        JsonArray values = tag.getAsJsonArray("values");
        for (int i = 0; i < values.size(); i++) {
            JsonElement v = values.get(i);
            String entry;
            boolean required = true;
            JsonObject obj = null;
            if (v.isJsonPrimitive()) {
                entry = v.getAsString();
            } else if (v.isJsonObject() && v.getAsJsonObject().has("id")) {
                obj = v.getAsJsonObject();
                entry = obj.get("id").getAsString();
                required = !obj.has("required") || obj.get("required").getAsBoolean();
            } else continue;
            String mapped = ConventionalTags.mapRef(entry);
            boolean optional = !required || mapped.startsWith("#") || ConventionalTags.isConventional(mapped) || !modPresent(mapped);
            if (!optional && mapped.equals(entry)) continue;
            JsonObject opt = new JsonObject();
            opt.addProperty("id", mapped);
            opt.addProperty("required", !optional);
            values.set(i, opt);
        }
    }

    private static boolean modPresent(String entry) {
        String rl = entry.startsWith("#") ? entry.substring(1) : entry;
        int c = rl.indexOf(':');
        if (c <= 0) return true;
        String ns = rl.substring(0, c);
        if (ns.equals("minecraft") || ns.equals("neoforge")) return true;
        return ModList.get() == null || ModList.get().isLoaded(ns);
    }
}
