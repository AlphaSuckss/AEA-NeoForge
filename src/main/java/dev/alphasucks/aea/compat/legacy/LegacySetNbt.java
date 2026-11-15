package dev.alphasucks.aea.compat.legacy;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.compat.AEALoot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;

/** {@code aea:legacy_set_nbt}: the 1.20.1 {@code set_nbt} loot function, merging through {@link LegacyItemMerge}. */
public final class LegacySetNbt extends LootItemConditionalFunction {
    public static final MapCodec<LegacySetNbt> CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i)
        .and(LegacyItemMerge.codec("tag").forGetter(f -> f.patch))
        .apply(i, LegacySetNbt::new));

    private final LegacyItemMerge.Patch patch;

    private LegacySetNbt(List<LootItemCondition> conditions, LegacyItemMerge.Patch patch) {
        super(conditions);
        this.patch = patch;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        patch.apply(stack, context.getLevel().registryAccess());
        return stack;
    }

    @Override
    public LootItemFunctionType<LegacySetNbt> getType() {
        return AEALoot.LEGACY_SET_NBT.get();
    }
}
