package dev.alphasucks.aea.mobai.goal;

import dev.overgrown.apoli.condition.BiEntityCondition;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.Optional;

/** Targets whatever last hurt the mob (if bientity_condition passes), optionally alerting same-type mobs nearby. */
public final class AeaRetaliateGoal extends Goal {
    private final Mob mob;
    private final Optional<BiEntityCondition> condition;
    private final boolean alertOthers;
    private final double alertRange;
    private int lastHurt;
    private LivingEntity target;

    public AeaRetaliateGoal(Mob mob, Optional<BiEntityCondition> condition, boolean alertOthers, double alertRange) {
        this.mob = mob;
        this.condition = condition;
        this.alertOthers = alertOthers;
        this.alertRange = alertRange;
        setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        int stamp = mob.getLastHurtByMobTimestamp();
        LivingEntity attacker = mob.getLastHurtByMob();
        if (stamp == lastHurt || attacker == null || !attacker.isAlive() || !attacker.canBeSeenByAnyone()
            || !mob.canAttack(attacker) || !Goals.test(condition, mob, attacker)) return false;
        target = attacker;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = mob.getTarget();
        return t != null && t == target && t.isAlive() && t.canBeSeenByAnyone() && mob.canAttack(t) && mob.distanceToSqr(t) <= 1024.0D;
    }

    @Override
    public void start() {
        lastHurt = mob.getLastHurtByMobTimestamp();
        mob.setTarget(target);
        if (alertOthers && target != null) {
            for (Mob other : mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(alertRange),
                    m -> m != mob && m.getType() == mob.getType() && m.getTarget() == null && m.isAlive())) {
                if (other.canAttack(target)) other.setTarget(target);
            }
        }
    }

    @Override
    public void stop() {
        if (mob.getTarget() == target) mob.setTarget(null);
        target = null;
    }
}
