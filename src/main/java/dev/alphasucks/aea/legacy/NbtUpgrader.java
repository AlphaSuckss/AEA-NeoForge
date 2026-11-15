package dev.alphasucks.aea.legacy;

import com.mojang.serialization.Dynamic;
import dev.alphasucks.aea.AEA;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Runs pre-1.20.5 NBT through Mojang's own DataFixerUpper so legacy item stacks, entities and
 * particles come out in the 1.21.1 shape (item components, renamed fields, new particle options).
 */
public final class NbtUpgrader {
    private NbtUpgrader() {}

    public static int currentVersion() {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }

    /** Pack format → data version of the game release that format shipped with. */
    public static int dataVersionForPackFormat(int format) {
        if (format <= 4) return 1631;
        if (format == 5) return 2230;
        if (format == 6) return 2586;
        if (format == 7) return 2730;
        if (format == 8) return 2860;
        if (format <= 11) return format == 9 ? 2975 : 3120;
        if (format <= 14) return 3337;
        if (format <= 17) return 3465;
        if (format <= 25) return 3578;
        if (format <= 40) return 3700;
        if (format <= 47) return 3839;
        return currentVersion();
    }

    @Nullable
    public static CompoundTag parse(String snbt) {
        try {
            return TagParser.parseTag(snbt);
        } catch (Exception e) {
            // Legacy Apoli/command NBT was sometimes written with stray closing braces.
            String trimmed = snbt.trim();
            while (trimmed.endsWith("}") && countUnbalanced(trimmed) < 0) {
                trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
                try {
                    return TagParser.parseTag(trimmed);
                } catch (Exception ignored) {
                }
            }
            return null;
        }
    }

    private static int countUnbalanced(String s) {
        int depth = 0;
        boolean inStr = false;
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\\') { i++; continue; }
                if (c == quote) inStr = false;
                continue;
            }
            if (c == '"' || c == '\'') { inStr = true; quote = c; continue; }
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') depth--;
        }
        return depth;
    }

    /** Full legacy stack {id, Count, tag} → {id, count, components}. */
    public static CompoundTag fixItemStack(CompoundTag legacy, int fromVersion) {
        if (fromVersion >= currentVersion()) return legacy;
        try {
            Dynamic<Tag> out = DataFixers.getDataFixer().update(References.ITEM_STACK,
                new Dynamic<>(NbtOps.INSTANCE, legacy.copy()), fromVersion, currentVersion());
            return out.getValue() instanceof CompoundTag c ? c : legacy;
        } catch (Throwable t) {
            AEA.LOGGER.debug("[AEA] Item stack upgrade failed for {}: {}", legacy, t.toString());
            return legacy;
        }
    }

    /** The DataFixer only converts some tags for matching item ids (potions, books, heads, ...). */
    static String dummyIdFor(Tag tag) {
        if (!(tag instanceof CompoundTag t)) return "minecraft:stone";
        if (t.contains("Potion") || t.contains("CustomPotionEffects") || t.contains("CustomPotionColor")) return "minecraft:potion";
        if (t.contains("StoredEnchantments")) return "minecraft:enchanted_book";
        if (t.contains("SkullOwner")) return "minecraft:player_head";
        if (t.contains("pages") || t.contains("author") || t.contains("title")) return "minecraft:written_book";
        if (t.contains("Fireworks")) return "minecraft:firework_rocket";
        if (t.contains("Explosion")) return "minecraft:firework_star";
        if (t.contains("BlockEntityTag")) return "minecraft:chest";
        return "minecraft:stone";
    }

    /** Upgrades an item compound that may be partial (no id / Count), as used in NBT matching. */
    public static CompoundTag fixPartialItem(CompoundTag legacy, int fromVersion) {
        if (fromVersion >= currentVersion()) return legacy;
        boolean hasId = legacy.contains("id", Tag.TAG_STRING);
        boolean hasCount = legacy.contains("Count");
        if (!legacy.contains("tag") && !hasCount) return legacy;
        CompoundTag work = legacy.copy();
        if (!hasId) work.putString("id", dummyIdFor(work.get("tag")));
        if (!hasCount) work.putByte("Count", (byte) 1);
        CompoundTag fixed = fixItemStack(work, fromVersion);
        if (fixed == work || fixed.contains("tag")) {
            // DFU refused; fall back to the custom_data mapping.
            CompoundTag manual = legacy.copy();
            Tag tag = manual.get("tag");
            manual.remove("tag");
            if (tag instanceof CompoundTag ct) {
                CompoundTag comps = new CompoundTag();
                comps.put("minecraft:custom_data", ct);
                manual.put("components", comps);
            }
            if (manual.contains("Count")) {
                manual.putInt("count", manual.getInt("Count"));
                manual.remove("Count");
            }
            return manual;
        }
        if (!hasId) fixed.remove("id");
        if (!hasCount) fixed.remove("count");
        // Keep only the keys that were asked about, so partial NBT matches stay partial.
        if (fixed.contains("components", Tag.TAG_COMPOUND)) {
            CompoundTag comps = fixed.getCompound("components");
            for (String key : Set.copyOf(comps.getAllKeys())) {
                Tag t = comps.get(key);
                if (key.equals("minecraft:custom_data") && t instanceof CompoundTag ct && ct.isEmpty()) comps.remove(key);
            }
            if (comps.isEmpty()) fixed.remove("components");
        }
        return fixed;
    }

    /** Full entity NBT (needs an id) through the entity fixers. */
    public static CompoundTag fixEntity(CompoundTag legacy, String entityId, int fromVersion) {
        if (fromVersion >= currentVersion()) return legacy;
        CompoundTag work = legacy.copy();
        boolean hadId = work.contains("id", Tag.TAG_STRING);
        if (!hadId) work.putString("id", entityId.contains(":") ? entityId : "minecraft:" + entityId);
        convertCardinalPowers(work);
        try {
            Dynamic<Tag> out = DataFixers.getDataFixer().update(References.ENTITY,
                new Dynamic<>(NbtOps.INSTANCE, work), fromVersion, currentVersion());
            if (out.getValue() instanceof CompoundTag c) {
                if (!hadId) c.remove("id");
                dropSuperseded(c);
                return c;
            }
        } catch (Throwable t) {
            AEA.LOGGER.debug("[AEA] Entity upgrade failed for {}: {}", legacy, t.toString());
        }
        CompoundTag manual = legacy.copy();
        fixEntityLike(manual, fromVersion);
        return manual;
    }

    private static final String[][] SUPERSEDED = {{"ActiveEffects", "active_effects"}, {"Attributes", "attributes"},
        {"CustomPotionEffects", "custom_potion_effects"}};

    /** The entity fixer sometimes leaves the pre-1.20.2 key next to its replacement. */
    private static void dropSuperseded(CompoundTag c) {
        for (String[] p : SUPERSEDED) if (c.contains(p[0]) && c.contains(p[1])) c.remove(p[0]);
        if (c.contains("Passengers", Tag.TAG_LIST)) for (Tag t : c.getList("Passengers", Tag.TAG_COMPOUND)) dropSuperseded((CompoundTag) t);
    }

    private static final Set<String> SINGLE_ITEM_KEYS = Set.of("Item", "SelectedItem", "ArmorItem", "SaddleItem",
        "DecorItem", "Book", "RecordItem", "FireworksItem", "Trident", "item", "Offhand");
    private static final Set<String> ITEM_LIST_KEYS = Set.of("Inventory", "HandItems", "ArmorItems", "Items",
        "EnderItems", "item_list", "Equipment");

    /**
     * Recursive manual fix for partial entity NBT (selector nbt=, data merge): items, effects, attributes, powers.
     */
    public static void fixEntityLike(CompoundTag tag, int fromVersion) {
        if (fromVersion >= currentVersion()) return;
        convertCardinalPowers(tag);
        for (String key : Set.copyOf(tag.getAllKeys())) {
            Tag value = tag.get(key);
            if (SINGLE_ITEM_KEYS.contains(key) && value instanceof CompoundTag item) {
                tag.put(key, fixPartialItem(item, fromVersion));
            } else if (ITEM_LIST_KEYS.contains(key) && value instanceof ListTag list) {
                ListTag out = new ListTag();
                for (Tag t : list) out.add(t instanceof CompoundTag c ? fixPartialItem(c, fromVersion) : t);
                tag.put(key, out);
            } else if (key.equals("ActiveEffects") && value instanceof ListTag list) {
                tag.remove(key);
                tag.put("active_effects", fixEffects(list));
            } else if ((key.equals("CustomPotionEffects")) && value instanceof ListTag list) {
                tag.remove(key);
                tag.put("custom_potion_effects", fixEffects(list));
            } else if (key.equals("Attributes") && value instanceof ListTag list) {
                tag.remove(key);
                tag.put("attributes", fixAttributes(list));
            } else if (key.equals("Passengers") && value instanceof ListTag list) {
                for (Tag t : list) if (t instanceof CompoundTag c) fixEntityLike(c, fromVersion);
            }
        }
    }

    public static ListTag fixEffects(ListTag legacy) {
        ListTag out = new ListTag();
        for (Tag t : legacy) {
            if (!(t instanceof CompoundTag c)) continue;
            CompoundTag e = new CompoundTag();
            for (String k : c.getAllKeys()) {
                Tag v = c.get(k);
                switch (k) {
                    case "Id" -> {
                        String name = LegacyIds.effectName(c.getInt("Id"));
                        if (name != null) e.putString("id", name);
                    }
                    case "Amplifier" -> e.putByte("amplifier", c.getByte("Amplifier"));
                    case "Duration" -> e.putInt("duration", c.getInt("Duration"));
                    case "Ambient" -> e.putBoolean("ambient", c.getBoolean("Ambient"));
                    case "ShowParticles" -> e.putBoolean("show_particles", c.getBoolean("ShowParticles"));
                    case "ShowIcon" -> e.putBoolean("show_icon", c.getBoolean("ShowIcon"));
                    case "HiddenEffect" -> { if (v instanceof CompoundTag h) e.put("hidden_effect", fixEffects(listOf(h)).getCompound(0)); }
                    default -> e.put(k, v.copy());
                }
            }
            out.add(e);
        }
        return out;
    }

    private static ListTag listOf(CompoundTag c) {
        ListTag l = new ListTag();
        l.add(c);
        return l;
    }

    public static ListTag fixAttributes(ListTag legacy) {
        ListTag out = new ListTag();
        for (Tag t : legacy) {
            if (!(t instanceof CompoundTag c)) continue;
            CompoundTag a = new CompoundTag();
            if (c.contains("Name")) {
                String name = c.getString("Name");
                a.putString("id", name.contains(":") ? name : "minecraft:" + name);
            }
            if (c.contains("Base")) a.putDouble("base", c.getDouble("Base"));
            if (c.contains("Modifiers", Tag.TAG_LIST)) {
                ListTag mods = new ListTag();
                for (Tag mt : c.getList("Modifiers", Tag.TAG_COMPOUND)) {
                    CompoundTag m = (CompoundTag) mt;
                    CompoundTag nm = new CompoundTag();
                    String n = m.contains("Name") ? m.getString("Name") : "legacy";
                    nm.putString("id", "aea:" + n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_"));
                    if (m.contains("Amount")) nm.putDouble("amount", m.getDouble("Amount"));
                    int op = m.getInt("Operation");
                    nm.putString("operation", op == 0 ? "add_value" : op == 1 ? "add_multiplied_base" : "add_multiplied_total");
                    mods.add(nm);
                }
                a.put("modifiers", mods);
            }
            for (String k : c.getAllKeys()) {
                if (!Set.of("Name", "Base", "Modifiers").contains(k)) a.put(k, c.get(k).copy());
            }
            out.add(a);
        }
        return out;
    }

    /** Fabric Cardinal Components power data → Overgrown's Apoli NeoForge attachment. */
    public static void convertCardinalPowers(CompoundTag entity) {
        if (!entity.contains("cardinal_components", Tag.TAG_COMPOUND)) return;
        CompoundTag cc = entity.getCompound("cardinal_components");
        if (!cc.contains("apoli:powers", Tag.TAG_COMPOUND)) return;
        CompoundTag legacy = cc.getCompound("apoli:powers");
        CompoundTag powers = new CompoundTag();
        for (Tag t : legacy.getList("Powers", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) t;
            String type = p.getString("Type");
            if (type.isEmpty()) continue;
            ListTag sources = new ListTag();
            if (p.contains("Sources", Tag.TAG_LIST)) {
                for (Tag s : p.getList("Sources", Tag.TAG_STRING)) sources.add(s.copy());
            } else if (p.contains("Source", Tag.TAG_STRING)) {
                sources.add(StringTag.valueOf(p.getString("Source")));
            }
            if (sources.isEmpty()) sources.add(StringTag.valueOf("apoli:command"));
            powers.put(type, sources);
        }
        cc.remove("apoli:powers");
        if (cc.isEmpty()) entity.remove("cardinal_components");
        CompoundTag container = new CompoundTag();
        container.put("powers", powers);
        CompoundTag attachments = entity.contains("neoforge:attachments", Tag.TAG_COMPOUND)
            ? entity.getCompound("neoforge:attachments") : new CompoundTag();
        attachments.put("apoli:power_container", container);
        entity.put("neoforge:attachments", attachments);
    }

    /** Legacy particle string ("minecraft:dust 1 0 0 1") → 1.21.1 particle options compound. */
    @Nullable
    public static CompoundTag fixParticle(String legacy, int fromVersion) {
        try {
            Dynamic<Tag> out = DataFixers.getDataFixer().update(References.PARTICLE,
                new Dynamic<>(NbtOps.INSTANCE, StringTag.valueOf(legacy)), Math.min(fromVersion, 3700), currentVersion());
            Tag v = out.getValue();
            if (v instanceof CompoundTag c) return c;
            if (v instanceof StringTag s) {
                CompoundTag c = new CompoundTag();
                c.putString("type", s.getAsString());
                return c;
            }
        } catch (Throwable t) {
            AEA.LOGGER.debug("[AEA] Particle upgrade failed for {}: {}", legacy, t.toString());
        }
        return null;
    }

    /** Converts a components compound to the [k=v,...] item-argument syntax. */
    /** Components an item predicate can match exactly without over-constraining a legacy partial NBT match. */
    private static final Set<String> EXACT_PREDICATE = Set.of("minecraft:custom_model_data",
        "minecraft:custom_name", "minecraft:damage", "minecraft:unbreakable", "minecraft:dyed_color", "minecraft:potion_contents");

    public static String componentsToArgument(CompoundTag components, boolean predicate) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String key : components.getAllKeys()) {
            if (predicate && !key.equals("minecraft:custom_data") && !EXACT_PREDICATE.contains(key)) continue;
            Tag v = components.get(key);
            if (!first) sb.append(',');
            first = false;
            if (key.startsWith("!")) {
                sb.append(key);
                continue;
            }
            if (predicate && key.equals("minecraft:custom_data")) {
                sb.append("minecraft:custom_data~").append(v.toString());
            } else {
                sb.append(key).append('=').append(v.toString());
            }
        }
        sb.append(']');
        return sb.length() == 2 ? "" : sb.toString();
    }

    public static boolean isEmpty(Map<?, ?> m) {
        return m == null || m.isEmpty();
    }
}
