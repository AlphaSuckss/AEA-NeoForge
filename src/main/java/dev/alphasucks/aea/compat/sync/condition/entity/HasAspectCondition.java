package dev.alphasucks.aea.compat.sync.condition.entity;

import com.mojang.serialization.MapCodec;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.EntityCtx;

/** {@code sync:has_aspect}: AspectsLib is Fabric-only, so nothing ever has an aspect here. */
public final class HasAspectCondition implements ConditionType<EntityCtx, Object> {
    @Override
    public MapCodec<Object> codec() {
        return MapCodec.unit(Boolean.FALSE);
    }

    @Override
    public boolean test(Object cfg, EntityCtx ctx) {
        return false;
    }
}
