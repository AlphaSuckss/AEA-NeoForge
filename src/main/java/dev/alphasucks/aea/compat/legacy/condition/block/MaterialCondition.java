package dev.alphasucks.aea.compat.legacy.condition.block;

import net.minecraft.world.level.block.Block;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.BlockCtx;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** {@code apoli:material} (Apoli 2.9): block materials are gone in 1.20+, so they are matched as {@code apoli:material/*} tags. */
public final class MaterialCondition implements ConditionType<BlockCtx, List<TagKey<Block>>> {
    private static TagKey<Block> tag(String material) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("apoli", "material/" + material.toLowerCase(Locale.ROOT)));
    }

    /** The tags are built once at load. */
    private static final MapCodec<List<TagKey<Block>>> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.STRING.optionalFieldOf("material").forGetter(l -> Optional.empty()),
        Codec.STRING.listOf().optionalFieldOf("materials", List.of()).forGetter(l -> {
            List<String> names = new ArrayList<>();
            for (TagKey<Block> t : l) names.add(t.location().getPath().substring("material/".length()));
            return names;
        })
    ).apply(i, (one, many) -> {
        List<TagKey<Block>> all = new ArrayList<>(many.size() + 1);
        for (String m : many) all.add(tag(m));
        one.ifPresent(m -> all.add(tag(m)));
        return List.copyOf(all);
    }));

    @Override
    public MapCodec<List<TagKey<Block>>> codec() {
        return CODEC;
    }

    @Override
    public boolean test(List<TagKey<Block>> materials, BlockCtx ctx) {
        for (int i = 0; i < materials.size(); i++) {
            if (ctx.state().is(materials.get(i))) return true;
        }
        return false;
    }
}
