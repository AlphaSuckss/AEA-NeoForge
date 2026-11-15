package dev.alphasucks.aea.compat.sync.condition.bientity;

import com.mojang.serialization.MapCodec;
import dev.alphasucks.aea.compat.Codecs;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** {@code sync:colliding}: the actor's bounding box (moved by {@code offset}) overlaps the target's. */
public final class CollidingCondition implements ConditionType<BiEntityCtx, Optional<Vec3>> {
    @Override
    public MapCodec<Optional<Vec3>> codec() {
        return Codecs.VEC3.optionalFieldOf("offset");
    }

    @Override
    public boolean test(Optional<Vec3> offset, BiEntityCtx ctx) {
        if (ctx.actor() == null || ctx.target() == null) return false;
        AABB box = ctx.actor().getBoundingBox();
        if (offset.isPresent()) box = box.move(offset.get());
        return box.intersects(ctx.target().getBoundingBox());
    }
}
