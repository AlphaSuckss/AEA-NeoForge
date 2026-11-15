package dev.alphasucks.aea.compat.legacy.condition.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.BiomeCtx;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.Locale;

/** {@code apoli:category} (Apoli 2.9): biome categories are gone in 1.21, so AEA ships them as {@code apoli:category/*} tags. */
public final class CategoryCondition implements ConditionType<BiomeCtx, String> {
    @Override
    public MapCodec<String> codec() {
        return Codec.STRING.fieldOf("category");
    }

    @Override
    public boolean test(String category, BiomeCtx ctx) {
        return ctx.biome().is(TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("apoli", "category/" + category.toLowerCase(Locale.ROOT))));
    }
}
