package dev.alphasucks.aea.mobai.goal;

import dev.overgrown.apoli.condition.BiEntityCondition;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.Optional;

public final class AeaLookAtGoal extends Goal {
    private final Mob mob;
    private final double range;
    private final float chance;
    private final Optional<BiEntityCondition> condition;
    private LivingEntity target;
    private int lookTime;

    public AeaLookAtGoal(Mob mob, double range, float chance, Optional<BiEntityCondition> condition) {
        this.mob = mob;
        this.range = range;
        this.chance = chance;
        this.condition = condition;
        setFlags(EnumSet.of(Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (mob.getRandom().nextFloat() >= chance) return false;
        target = Goals.nearest(mob, range, e -> Goals.test(condition, mob, e));
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && target.isAlive() && mob.distanceToSqr(target) <= range * range && lookTime > 0;
    }

    @Override
    public void start() {
        lookTime = 40 + mob.getRandom().nextInt(40);
    }

    @Override
    public void stop() {
        target = null;
    }

    @Override
    public void tick() {
        if (target != null && target.isAlive()) {
            mob.getLookControl().setLookAt(target.getX(), target.getEyeY(), target.getZ());
            lookTime--;
        }
    }
}
