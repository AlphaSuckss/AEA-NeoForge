package dev.alphasucks.aea.compat.legacy.condition.biome;

import net.minecraft.world.level.biome.Biome;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.BiomeCtx;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.Locale;

/** {@code apoli:category} (Apoli 2.9): biome categories are gone in 1.21, so AEA ships them as {@code apoli:category/*} tags. */
public final class CategoryCondition implements ConditionType<BiomeCtx, TagKey<Biome>> {
    /** The tag is built once at load. */
    private static final MapCodec<TagKey<Biome>> CODEC = Codec.STRING.xmap(
        category -> TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("apoli", "category/" + category.toLowerCase(Locale.ROOT))),
        tag -> tag.location().getPath().substring("category/".length())).fieldOf("category");

    @Override
    public MapCodec<TagKey<Biome>> codec() {
        return CODEC;
    }

    @Override
    public boolean test(TagKey<Biome> category, BiomeCtx ctx) {
        return ctx.biome().is(category);
    }
}
