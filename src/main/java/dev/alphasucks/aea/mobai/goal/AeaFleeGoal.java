package dev.alphasucks.aea.mobai.goal;

import dev.overgrown.apoli.condition.BiEntityCondition;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;

public final class AeaFleeGoal extends Goal {
    private final PathfinderMob mob;
    private final double range;
    private final double speed;
    private final double fastSpeed;
    private final Optional<BiEntityCondition> condition;
    private LivingEntity threat;
    private Path path;

    public AeaFleeGoal(PathfinderMob mob, double range, double speed, double fastSpeed, Optional<BiEntityCondition> condition) {
        this.mob = mob;
        this.range = range;
        this.speed = speed;
        this.fastSpeed = fastSpeed;
        this.condition = condition;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        threat = Goals.nearest(mob, range, e -> Goals.test(condition, mob, e));
        if (threat == null) return false;
        Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, threat.position());
        if (away == null || threat.distanceToSqr(away.x, away.y, away.z) < threat.distanceToSqr(mob)) return false;
        path = mob.getNavigation().createPath(away.x, away.y, away.z, 0);
        return path != null;
    }

    @Override
    public boolean canContinueToUse() {
        return threat != null && threat.isAlive() && !mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        mob.getNavigation().moveTo(path, speed);
    }

    @Override
    public void stop() {
        threat = null;
        path = null;
    }

    @Override
    public void tick() {
        if (threat != null) mob.getNavigation().setSpeedModifier(mob.distanceToSqr(threat) < 49.0D ? fastSpeed : speed);
    }
}
