package dev.alphasucks.aea.compat.legacy.condition.block;

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
public final class MaterialCondition implements ConditionType<BlockCtx, List<String>> {
    @Override
    public MapCodec<List<String>> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("material").forGetter(l -> Optional.empty()),
            Codec.STRING.listOf().optionalFieldOf("materials", List.of()).forGetter(l -> l)
        ).apply(i, (one, many) -> {
            List<String> all = new ArrayList<>(many);
            one.ifPresent(all::add);
            return all;
        }));
    }

    @Override
    public boolean test(List<String> materials, BlockCtx ctx) {
        for (String m : materials) {
            if (ctx.state().is(TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("apoli", "material/" + m.toLowerCase(Locale.ROOT))))) return true;
        }
        return false;
    }
}
