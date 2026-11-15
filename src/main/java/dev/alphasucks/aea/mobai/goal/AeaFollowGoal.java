package dev.alphasucks.aea.mobai.goal;

import dev.overgrown.apoli.condition.BiEntityCondition;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.Optional;

/** Follows the mob's owner ("target": "owner", default) or the nearest matching entity ("target": "nearest"). */
public final class AeaFollowGoal extends Goal {
    private final Mob mob;
    private final boolean owner;
    private final Optional<BiEntityCondition> condition;
    private final double speed;
    private final double startDistance;
    private final double stopDistance;
    private final double range;
    private final double teleportDistance;
    private LivingEntity leader;
    private int repath;

    public AeaFollowGoal(Mob mob, boolean owner, Optional<BiEntityCondition> condition, double speed, double startDistance,
                         double stopDistance, double range, double teleportDistance) {
        this.mob = mob;
        this.owner = owner;
        this.condition = condition;
        this.speed = speed;
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        this.range = range;
        this.teleportDistance = teleportDistance;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    private LivingEntity findLeader() {
        if (owner) {
            Entity o = mob instanceof OwnableEntity t ? t.getOwner() : null;
            if (o instanceof LivingEntity l && l.isAlive() && l.level() == mob.level() && Goals.test(condition, mob, l)) return l;
            return null;
        }
        return Goals.nearest(mob, range, e -> Goals.test(condition, mob, e));
    }

    @Override
    public boolean canUse() {
        LivingEntity l = findLeader();
        if (l == null || mob.distanceToSqr(l) < startDistance * startDistance) return false;
        leader = l;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (leader == null || !leader.isAlive() || leader.level() != mob.level()) return false;
        double d = mob.distanceToSqr(leader);
        return d > stopDistance * stopDistance && (owner || d <= range * range * 4.0D);
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        leader = null;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (leader == null) return;
        mob.getLookControl().setLookAt(leader, 10.0F, (float) mob.getMaxHeadXRot());
        if (--repath > 0) return;
        repath = 10;
        if (teleportDistance > 0.0D && mob.distanceToSqr(leader) >= teleportDistance * teleportDistance && !mob.isPassenger()) {
            mob.moveTo(leader.getX(), leader.getY(), leader.getZ(), mob.getYRot(), mob.getXRot());
            mob.getNavigation().stop();
            return;
        }
        mob.getNavigation().moveTo(leader, speed);
    }
}
