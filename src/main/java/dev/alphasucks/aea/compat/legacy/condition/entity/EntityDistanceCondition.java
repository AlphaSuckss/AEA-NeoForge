package dev.alphasucks.aea.compat.legacy.condition.entity;

import com.mojang.serialization.MapCodec;
import dev.alphasucks.aea.compat.legacy.LegacyDistance;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.EntityCtx;
import net.minecraft.world.entity.Entity;

/** Apoli 2.9.2's entity {@code distance_from_coordinates} / {@code distance_from_spawn}; see {@link LegacyDistance}. */
public final class EntityDistanceCondition implements ConditionType<EntityCtx, LegacyDistance.Cfg> {
    private final MapCodec<LegacyDistance.Cfg> codec;

    public EntityDistanceCondition(String defaultReference) {
        this.codec = LegacyDistance.codec(defaultReference);
    }

    @Override
    public MapCodec<LegacyDistance.Cfg> codec() {
        return codec;
    }

    @Override
    public boolean test(LegacyDistance.Cfg cfg, EntityCtx ctx) {
        Entity e = ctx.entity();
        if (e == null) return false;
        return LegacyDistance.test(cfg, e.position(), e.level());
    }
}
