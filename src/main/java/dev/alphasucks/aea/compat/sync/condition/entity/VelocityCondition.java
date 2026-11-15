package dev.alphasucks.aea.compat.sync.condition.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.compat.Codecs;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.data.Comparison;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code sync:velocity}: compares the entity's velocity. Per-axis values (x / y / z) are each compared on their own;
 * otherwise the listed axes are summed and compared to {@code compare_to}.
 */
public final class VelocityCondition implements ConditionType<EntityCtx, VelocityCondition.Cfg> {
    public record Cfg(Optional<Float> x, Optional<Float> y, Optional<Float> z, Optional<List<String>> axes,
                      Comparison comparison, float compareTo) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.optionalFieldOf("x").forGetter(Cfg::x),
            Codec.FLOAT.optionalFieldOf("y").forGetter(Cfg::y),
            Codec.FLOAT.optionalFieldOf("z").forGetter(Cfg::z),
            Codecs.singleOrList(Codec.STRING).optionalFieldOf("axes").forGetter(Cfg::axes),
            Comparison.CODEC.optionalFieldOf("comparison", Comparison.GREATER_EQUAL).forGetter(Cfg::comparison),
            Codec.FLOAT.optionalFieldOf("compare_to", 0f).forGetter(Cfg::compareTo)
        ).apply(i, Cfg::new));
    }

    @Override
    public boolean test(Cfg c, EntityCtx ctx) {
        Vec3 v = ctx.raw().getDeltaMovement();
        if (c.x.isPresent() || c.y.isPresent() || c.z.isPresent()) {
            if (c.x.isPresent() && !c.comparison.compare((float) v.x, c.x.get())) return false;
            if (c.y.isPresent() && !c.comparison.compare((float) v.y, c.y.get())) return false;
            if (c.z.isPresent() && !c.comparison.compare((float) v.z, c.z.get())) return false;
            return true;
        }
        if (c.axes.isPresent()) {
            double combined = 0;
            for (String a : c.axes.get()) {
                combined += switch (a.toLowerCase(Locale.ROOT)) {
                    case "x" -> v.x;
                    case "y" -> v.y;
                    case "z" -> v.z;
                    default -> 0;
                };
            }
            return c.comparison.compare((float) combined, c.compareTo);
        }
        return true;
    }
}
