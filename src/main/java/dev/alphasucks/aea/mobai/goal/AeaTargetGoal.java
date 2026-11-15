package dev.alphasucks.aea.mobai.goal;

import dev.overgrown.apoli.condition.BiEntityCondition;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.Optional;

/** Targets the nearest entity passing bientity_condition (actor = mob), like a configurable nearest-attackable goal. */
public final class AeaTargetGoal extends Goal {
    private static final int UNSEEN_MEMORY = 60;
    private final Mob mob;
    private final Optional<Double> range;
    private final boolean mustSee;
    private final int interval;
    private final Optional<BiEntityCondition> condition;
    private LivingEntity target;
    private int unseen;
    private int recheck;

    public AeaTargetGoal(Mob mob, Optional<Double> range, boolean mustSee, int interval, Optional<BiEntityCondition> condition) {
        this.mob = mob;
        this.range = range;
        this.mustSee = mustSee;
        this.interval = Math.max(1, interval);
        this.condition = condition;
        setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    private double range() {
        if (range.isPresent()) return range.get();
        try {
            return mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        } catch (RuntimeException e) {
            return 16.0D;
        }
    }

    private boolean valid(LivingEntity e) {
        return mob.canAttack(e) && Goals.test(condition, mob, e) && (!mustSee || mob.getSensing().hasLineOfSight(e));
    }

    @Override
    public boolean canUse() {
        if (interval > 1 && mob.getRandom().nextInt(interval) != 0) return false;
        target = Goals.nearest(mob, range(), this::valid);
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = mob.getTarget();
        if (t == null || t != target || !t.isAlive() || !t.canBeSeenByAnyone() || !mob.canAttack(t)) return false;
        double r = range();
        if (mob.distanceToSqr(t) > r * r) return false;
        if (mustSee) {
            if (mob.getSensing().hasLineOfSight(t)) unseen = 0;
            else if (++unseen > UNSEEN_MEMORY) return false;
        }
        if (--recheck <= 0) {
            recheck = 20;
            return Goals.test(condition, mob, t);
        }
        return true;
    }

    @Override
    public void start() {
        mob.setTarget(target);
        unseen = 0;
        recheck = 20;
    }

    @Override
    public void stop() {
        if (mob.getTarget() == target) mob.setTarget(null);
        target = null;
    }
}
