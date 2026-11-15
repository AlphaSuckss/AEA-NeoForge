package dev.alphasucks.aea.compat.legacy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import dev.alphasucks.aea.legacy.NbtUpgrader;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.ItemCtx;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Item {@code nbt} condition with Apoli 2.9 semantics: the legacy tag is converted for the actual item through the
 * DataFixer, then matched partially against custom_data and the item's other components.
 */
public final class LegacyItemNbt implements ConditionType<ItemCtx, LegacyItemNbt.Cfg> {

    public static final class Cfg {
        final CompoundTag legacy;
        final Map<Item, CompoundTag> converted = new ConcurrentHashMap<>();

        Cfg(CompoundTag legacy) {
            this.legacy = legacy;
        }

        CompoundTag componentsFor(Item item) {
            return converted.computeIfAbsent(item, i -> {
                CompoundTag stack = new CompoundTag();
                stack.putString("id", BuiltInRegistries.ITEM.getKey(i).toString());
                stack.putByte("Count", (byte) 1);
                stack.put("tag", legacy.copy());
                CompoundTag fixed = NbtUpgrader.fixItemStack(stack, 3465);
                return fixed.contains("components", Tag.TAG_COMPOUND) ? fixed.getCompound("components") : new CompoundTag();
            });
        }
    }

    @Override
    public MapCodec<Cfg> codec() {
        return Codec.STRING.comapFlatMap(s -> {
            CompoundTag t = NbtUpgrader.parse(s);
            return t == null ? DataResult.error(() -> "Invalid SNBT: " + s) : DataResult.success(new Cfg(t));
        }, c -> c.legacy.toString()).fieldOf("nbt");
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean test(Cfg cfg, ItemCtx ctx) {
        ItemStack stack = ctx.stack();
        if (stack.isEmpty()) return cfg.legacy.isEmpty();
        CompoundTag expected = cfg.componentsFor(stack.getItem());
        RegistryOps<Tag> ops = ctx.level().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for (String key : expected.getAllKeys()) {
            Tag want = expected.get(key);
            if (key.equals("minecraft:custom_data")) {
                CustomData data = stack.get(DataComponents.CUSTOM_DATA);
                if (data == null || !(want instanceof CompoundTag wc) || !NbtUtils.compareNbt(wc, data.copyTag(), true)) return false;
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(key);
            DataComponentType type = rl == null ? null : BuiltInRegistries.DATA_COMPONENT_TYPE.get(rl);
            if (type == null || type.codec() == null) continue;
            Object actual = stack.get(type);
            if (actual == null) return false;
            Tag have = (Tag) ((Codec) type.codec()).encodeStart(ops, actual).result().orElse(null);
            if (have == null) continue;
            if (!matches(want, have, ctx)) return false;
        }
        return true;
    }

    private static boolean matches(Tag want, Tag have, ItemCtx ctx) {
        if (NbtUtils.compareNbt(want, have, true)) return true;
        Tag w = canonical(want, ctx), h = canonical(have, ctx);
        return NbtUtils.compareNbt(w, h, true);
    }

    /** Re-serialises JSON text strings so key order and defaults don't matter. */
    private static Tag canonical(Tag t, ItemCtx ctx) {
        if (t instanceof StringTag s) {
            String v = s.getAsString();
            if (v.startsWith("{") || v.startsWith("[") || v.startsWith("\"")) {
                try {
                    Component c = Component.Serializer.fromJson(v, ctx.level().registryAccess());
                    if (c != null) return StringTag.valueOf(Component.Serializer.toJson(c, ctx.level().registryAccess()));
                } catch (Exception ignored) {
                }
            }
            return t;
        }
        if (t instanceof ListTag l) {
            ListTag out = new ListTag();
            for (Tag e : l) out.add(canonical(e, ctx));
            return out;
        }
        if (t instanceof CompoundTag c) {
            CompoundTag out = new CompoundTag();
            for (String k : c.getAllKeys()) out.put(k, canonical(c.get(k), ctx));
            return out;
        }
        return t;
    }

    /** Test hook: does {@code stack} match the 1.20.1 tag {@code snbt}? */
    public static boolean matchesLegacy(String snbt, ItemStack stack, net.minecraft.world.level.Level level) {
        CompoundTag t = NbtUpgrader.parse(snbt);
        return t != null && new LegacyItemNbt().test(new Cfg(t), new ItemCtx(stack, level, null));
    }
}
