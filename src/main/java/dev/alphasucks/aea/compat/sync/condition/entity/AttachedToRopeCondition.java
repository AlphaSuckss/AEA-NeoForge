package dev.alphasucks.aea.compat.sync.condition.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.data.Comparison;
import dev.overgrown.apoli.rope.Rope;
import dev.overgrown.apoli.rope.RopeAnchor;
import dev.overgrown.apoli.rope.RopeManager;

import java.util.List;
import java.util.Locale;

/** {@code sync:attached_to_rope}: counts the ropes on the entity, optionally only those ending at a block or an entity. */
public final class AttachedToRopeCondition implements ConditionType<EntityCtx, AttachedToRopeCondition.Cfg> {
    public record Cfg(String anchorType, Comparison comparison, int compareTo) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("anchor_type", "any").forGetter(Cfg::anchorType),
            Comparison.CODEC.optionalFieldOf("comparison", Comparison.GREATER_EQUAL).forGetter(Cfg::comparison),
            Codec.INT.optionalFieldOf("compare_to", 1).forGetter(Cfg::compareTo)
        ).apply(i, Cfg::new));
    }

    @Override
    public boolean test(Cfg c, EntityCtx ctx) {
        List<Rope> ropes = RopeManager.forEntity(ctx.raw().getUUID());
        int count = 0;
        for (Rope r : ropes) {
            boolean fromSelf = ctx.raw().getUUID().equals(r.fromEntity);
            RopeAnchor other = fromSelf ? r.to : r.from;
            boolean block = other instanceof RopeAnchor.Position;
            switch (c.anchorType.toLowerCase(Locale.ROOT)) {
                case "block" -> { if (block) count++; }
                case "entity" -> { if (!block) count++; }
                default -> count++;
            }
        }
        return c.comparison.compare(count, c.compareTo);
    }
}
