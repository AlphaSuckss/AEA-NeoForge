package dev.alphasucks.aea.compat.legacy.condition.bientity;

import com.mojang.serialization.MapCodec;
import dev.overgrown.apoli.condition.BiEntityCondition;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.BiEntityCtx;

/**
 * {@code aea:legacy_undirected}: Apoli 2.9's {@code undirected}, a bi-entity condition tested both ways round (Overgrown's
 * takes an entity condition instead).
 */
public final class UndirectedCondition implements ConditionType<BiEntityCtx, BiEntityCondition> {
    @Override
    public MapCodec<BiEntityCondition> codec() {
        return BiEntityCondition.CODEC.fieldOf("condition");
    }

    @Override
    public boolean test(BiEntityCondition cond, BiEntityCtx ctx) {
        return cond.test(ctx) || cond.test(ctx.swap());
    }
}
