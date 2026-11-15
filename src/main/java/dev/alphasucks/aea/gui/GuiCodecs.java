package dev.alphasucks.aea.gui;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.legacy.NbtUpgrader;
import dev.overgrown.apoli.alias.AliasingMapCodec;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.data.Nbt;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Codecs shared by the GUI elements. Item specs are AEA 1.5.4's: an item id, or
 * {@code {"item"|"id", "count"|"amount", "nbt"|"tag"}}. On 1.21 the object may also carry {@code components};
 * a 1.20.1 {@code nbt} tag is run through Mojang's data fixer into components.
 */
public final class GuiCodecs {
    private GuiCodecs() {}

    /** Pack format 15 (1.20.1): the data version AEA 1.5.4 item NBT was written for. */
    private static final int LEGACY_DATA_VERSION = NbtUpgrader.dataVersionForPackFormat(15);

    record StackSpec(ResourceLocation item, int count, Optional<Nbt> nbt, Optional<Dynamic<?>> components) {}

    private static final MapCodec<StackSpec> STACK_OBJECT = AliasingMapCodec.wrap(RecordCodecBuilder.mapCodec(i -> i.group(
        IdCodecs.ID.fieldOf("item").forGetter(StackSpec::item),
        Codec.intRange(1, 127).optionalFieldOf("count", 1).forGetter(StackSpec::count),
        Nbt.CODEC.optionalFieldOf("nbt").forGetter(StackSpec::nbt),
        Codec.PASSTHROUGH.optionalFieldOf("components").forGetter(StackSpec::components)
    ).apply(i, StackSpec::new)), Map.of("id", "item", "amount", "count", "tag", "nbt"));

    private static final Codec<StackSpec> STACK_SPEC = Codec.either(IdCodecs.ID, STACK_OBJECT.codec()).xmap(
        e -> e.map(id -> new StackSpec(id, 1, Optional.empty(), Optional.empty()), s -> s),
        s -> s.count() == 1 && s.nbt().isEmpty() && s.components().isEmpty() ? Either.left(s.item()) : Either.right(s));

    public static final Codec<ItemStack> ITEM_STACK = new Codec<>() {
        @Override
        public <T> DataResult<Pair<ItemStack, T>> decode(DynamicOps<T> ops, T input) {
            return STACK_SPEC.decode(ops, input).flatMap(p -> build(ops, p.getFirst()).map(s -> Pair.of(s, p.getSecond())));
        }

        @Override
        public <T> DataResult<T> encode(ItemStack stack, DynamicOps<T> ops, T prefix) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            DataComponentPatch patch = stack.getComponentsPatch();
            if (stack.getCount() == 1 && patch.isEmpty()) return IdCodecs.ID.encode(id, ops, prefix);
            var map = ops.mapBuilder();
            map.add("item", ops.createString(id.toString()));
            map.add("count", ops.createInt(stack.getCount()));
            if (!patch.isEmpty()) map.add("components", DataComponentPatch.CODEC.encodeStart(ops, patch));
            return map.build(prefix);
        }
    };

    private static <T> DataResult<ItemStack> build(DynamicOps<T> ops, StackSpec spec) {
        Optional<Holder.Reference<Item>> item = BuiltInRegistries.ITEM.getHolder(spec.item());
        if (item.isEmpty()) return DataResult.error(() -> "Unknown item '" + spec.item() + "'");
        DataComponentPatch fromNbt = DataComponentPatch.EMPTY;
        if (spec.nbt().isPresent()) {
            CompoundTag legacy = new CompoundTag();
            legacy.putString("id", spec.item().toString());
            legacy.putByte("Count", (byte) spec.count());
            legacy.put("tag", spec.nbt().get().tag().copy());
            CompoundTag fixed = NbtUpgrader.fixItemStack(legacy, LEGACY_DATA_VERSION);
            if (fixed.contains("components", Tag.TAG_COMPOUND)) {
                T converted = new Dynamic<>(NbtOps.INSTANCE, (Tag) fixed.getCompound("components")).convert(ops).getValue();
                DataResult<DataComponentPatch> r = DataComponentPatch.CODEC.parse(ops, converted);
                if (r.error().isPresent()) return DataResult.error(() -> "Invalid item nbt for " + spec.item() + ": " + r.error().get().message());
                fromNbt = r.getOrThrow();
            }
        }
        DataComponentPatch fromComponents = DataComponentPatch.EMPTY;
        if (spec.components().isPresent()) {
            DataResult<DataComponentPatch> r = DataComponentPatch.CODEC.parse(spec.components().get());
            if (r.error().isPresent()) return DataResult.error(() -> "Invalid item components for " + spec.item() + ": " + r.error().get().message());
            fromComponents = r.getOrThrow();
        }
        ItemStack stack = new ItemStack(item.get(), spec.count(), merge(fromNbt, fromComponents));
        return DataResult.success(stack);
    }

    private static DataComponentPatch merge(DataComponentPatch base, DataComponentPatch over) {
        if (base.isEmpty()) return over;
        if (over.isEmpty()) return base;
        DataComponentPatch.Builder b = DataComponentPatch.builder();
        for (DataComponentPatch p : List.of(base, over)) {
            for (var e : p.entrySet()) {
                if (e.getValue().isPresent()) setUnchecked(b, e.getKey(), e.getValue().get());
                else b.remove(e.getKey());
            }
        }
        return b.build();
    }

    @SuppressWarnings("unchecked")
    private static <V> void setUnchecked(DataComponentPatch.Builder b, net.minecraft.core.component.DataComponentType<V> type, Object value) {
        b.set(type, (V) value);
    }

    public static final Codec<EquipmentSlot> EQUIPMENT_SLOT = Codec.STRING.comapFlatMap(s -> {
        try {
            return DataResult.success(EquipmentSlot.byName(s));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "Unknown equipment slot '" + s + "' (expected mainhand, offhand, head, chest, legs or feet)");
        }
    }, EquipmentSlot::getName);

    public static final Codec<List<String>> STRING_OR_LIST = Codec.either(Codec.STRING, Codec.STRING.listOf()).xmap(
        e -> e.map(List::of, l -> l),
        l -> l.size() == 1 ? Either.left(l.get(0)) : Either.right(l));

    /** Parses {@code "row,col"} (each a number or a range like {@code 1-3}) into {@code [rowFrom, rowTo, colFrom, colTo]}, 1-based. */
    public static DataResult<int[]> parseCellRange(String cell, int rows, int cols) {
        String[] parts = cell.replace(" ", "").split(",");
        if (parts.length != 2) {
            return DataResult.error(() -> "Invalid slot_grid cell '" + cell + "': expected \"row,col\" (ranges like \"1-3,2\" are allowed)");
        }
        int[] r = range(parts[0]);
        int[] c = range(parts[1]);
        if (r == null || c == null) {
            return DataResult.error(() -> "Invalid slot_grid cell '" + cell + "': rows and columns must be numbers or ranges like 1-3");
        }
        if (r[0] < 1 || r[1] > rows || c[0] < 1 || c[1] > cols) {
            return DataResult.error(() -> "slot_grid cell '" + cell + "' is outside the " + rows + "x" + cols + " grid (rows and columns start at 1)");
        }
        return DataResult.success(new int[]{r[0], r[1], c[0], c[1]});
    }

    private static int[] range(String s) {
        try {
            int dash = s.indexOf('-', 1);
            if (dash < 0) {
                int v = Integer.parseInt(s);
                return new int[]{v, v};
            }
            int a = Integer.parseInt(s.substring(0, dash));
            int b = Integer.parseInt(s.substring(dash + 1));
            return new int[]{Math.min(a, b), Math.max(a, b)};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
