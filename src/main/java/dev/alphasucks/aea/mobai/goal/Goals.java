package dev.alphasucks.aea.mobai.goal;

import dev.overgrown.apoli.condition.BiEntityCondition;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

final class Goals {
    private Goals() {}

    static boolean test(Optional<BiEntityCondition> condition, Mob mob, LivingEntity other) {
        if (condition.isEmpty()) return true;
        try {
            return condition.get().test(BiEntityCtx.of(mob, other, mob.level()));
        } catch (RuntimeException e) {
            return false;
        }
    }

    static LivingEntity nearest(Mob mob, double range, Predicate<LivingEntity> filter) {
        List<LivingEntity> list = mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(range),
            e -> e != mob && e.isAlive() && e.canBeSeenByAnyone());
        LivingEntity best = null;
        double bestDist = range * range;
        for (LivingEntity e : list) {
            double d = mob.distanceToSqr(e);
            if (d <= bestDist && filter.test(e)) {
                best = e;
                bestDist = d;
            }
        }
        return best;
    }
}
