package dev.alphasucks.aea.compat.legacy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.legacy.NbtUpgrader;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.20.1 item tag merge ({@code merge_nbt} item action, {@code set_nbt} loot function) with 1.20.1 semantics: the legacy
 * tag is converted for the actual item through the DataFixer, then each resulting component is merged into the stack.
 * Custom keys go to custom_data as before; vanilla keys (display, Enchantments, Potion, CustomPotionEffects, book pages,
 * ...) become the real components instead of being buried in custom_data.
 */
public final class LegacyItemMerge {
    private LegacyItemMerge() {}

    /** Components whose 1.20.1 source was a list that a merge replaced wholesale. */
    private static final Set<String> REPLACE = Set.of("minecraft:enchantments", "minecraft:stored_enchantments",
        "minecraft:attribute_modifiers", "minecraft:lore", "minecraft:can_break", "minecraft:can_place_on");

    public static final class Patch {
        public final CompoundTag legacy;
        public final int dataVersion;
        private final Map<Item, CompoundTag> converted = new ConcurrentHashMap<>();

        public Patch(CompoundTag legacy, int dataVersion) {
            this.legacy = legacy;
            this.dataVersion = dataVersion;
        }

        CompoundTag componentsFor(Item item) {
            return converted.computeIfAbsent(item, i -> {
                CompoundTag stack = new CompoundTag();
                stack.putString("id", BuiltInRegistries.ITEM.getKey(i).toString());
                stack.putByte("Count", (byte) 1);
                stack.put("tag", legacy.copy());
                CompoundTag fixed = NbtUpgrader.fixItemStack(stack, dataVersion);
                return fixed.contains("components", Tag.TAG_COMPOUND) ? fixed.getCompound("components") : new CompoundTag();
            });
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public void apply(ItemStack stack, HolderLookup.Provider registries) {
            if (stack.isEmpty() || legacy.isEmpty()) return;
            CompoundTag comps = componentsFor(stack.getItem());
            RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
            for (String key : comps.getAllKeys()) {
                Tag incoming = comps.get(key);
                if (key.equals("minecraft:custom_data")) {
                    if (incoming instanceof CompoundTag c) CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.merge(c));
                    continue;
                }
                ResourceLocation rl = ResourceLocation.tryParse(key);
                DataComponentType type = rl == null ? null : BuiltInRegistries.DATA_COMPONENT_TYPE.get(rl);
                if (type == null || type.codec() == null) continue;
                Tag result = incoming;
                if (!REPLACE.contains(key) && incoming instanceof CompoundTag in) {
                    Object current = stack.get(type);
                    if (current != null) {
                        Tag have = (Tag) ((Codec) type.codec()).encodeStart(ops, current).result().orElse(null);
                        if (have instanceof CompoundTag hc) result = hc.copy().merge(in);
                    }
                }
                DataResult<?> parsed = ((Codec) type.codec()).parse(ops, result);
                Object value = parsed.result().orElse(null);
                if (value != null) stack.set(type, value);
                else AEA.LOGGER.debug("[AEA] Couldn't merge legacy {} into {}: {}", key, stack, parsed.error().map(Object::toString).orElse("?"));
            }
        }
    }

    /** The patch read from the SNBT string in {@code field}; the upgraders record the source data version in {@code aea_data_version}. */
    public static MapCodec<Patch> codec(String field) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.comapFlatMap(s -> {
                CompoundTag t = NbtUpgrader.parse(s);
                return t == null ? DataResult.error(() -> "Invalid SNBT: " + s) : DataResult.success(t);
            }, Tag::toString).fieldOf(field).forGetter(p -> p.legacy),
            Codec.INT.optionalFieldOf("aea_data_version", 3465).forGetter(p -> p.dataVersion)
        ).apply(i, Patch::new));
    }
}
