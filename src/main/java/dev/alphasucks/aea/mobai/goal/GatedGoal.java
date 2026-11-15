package dev.alphasucks.aea.mobai.goal;

import dev.overgrown.apoli.condition.EntityCondition;
import dev.overgrown.apoli.condition.context.EntityCtx;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Runs a goal only while the spec's entity "condition" holds for the mob. */
public final class GatedGoal extends Goal {
    private final Goal delegate;
    private final EntityCondition condition;
    private final Mob mob;

    public GatedGoal(Goal delegate, EntityCondition condition, Mob mob) {
        this.delegate = delegate;
        this.condition = condition;
        this.mob = mob;
    }

    public Goal delegate() {
        return delegate;
    }

    private boolean allowed() {
        try {
            return condition.test(EntityCtx.of(mob, mob.level()));
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override public boolean canUse() { return allowed() && delegate.canUse(); }
    @Override public boolean canContinueToUse() { return allowed() && delegate.canContinueToUse(); }
    @Override public boolean isInterruptable() { return delegate.isInterruptable(); }
    @Override public void start() { delegate.start(); }
    @Override public void stop() { delegate.stop(); }
    @Override public boolean requiresUpdateEveryTick() { return delegate.requiresUpdateEveryTick(); }
    @Override public void tick() { delegate.tick(); }
    @Override public void setFlags(EnumSet<Goal.Flag> flags) { delegate.setFlags(flags); }
    @Override public EnumSet<Goal.Flag> getFlags() { return delegate.getFlags(); }
    @Override public String toString() { return "aea:gated(" + delegate + ")"; }
}
