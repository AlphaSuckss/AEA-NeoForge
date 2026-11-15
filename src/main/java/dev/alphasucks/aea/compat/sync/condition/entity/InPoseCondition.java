package dev.alphasucks.aea.compat.sync.condition.entity;

import com.mojang.serialization.MapCodec;
import dev.alphasucks.aea.compat.Codecs;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.EntityCtx;
import net.minecraft.world.entity.Pose;

/** {@code sync:in_pose}: the entity's current pose (Fabric-era names like {@code sneaking} are accepted). */
public final class InPoseCondition implements ConditionType<EntityCtx, Pose> {
    @Override
    public MapCodec<Pose> codec() {
        return Codecs.POSE.fieldOf("pose");
    }

    @Override
    public boolean test(Pose pose, EntityCtx ctx) {
        return ctx.raw().getPose() == pose;
    }
}
