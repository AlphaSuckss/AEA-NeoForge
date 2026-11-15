package dev.alphasucks.aea.mobai.goal;

import dev.overgrown.apoli.action.EntityAction;
import dev.overgrown.apoli.condition.EntityCondition;
import dev.overgrown.apoli.condition.context.EntityCtx;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.Optional;

/** A data-driven goal: start/continue conditions, start/tick/stop actions, tick interval, max duration and cooldown. */
public final class AeaCustomGoal extends Goal {
    private final Mob mob;
    private final Optional<EntityCondition> startCondition;
    private final Optional<EntityCondition> continueCondition;
    private final Optional<EntityAction> startAction;
    private final Optional<EntityAction> tickAction;
    private final Optional<EntityAction> stopAction;
    private final int interval;
    private final int duration;
    private final int cooldown;
    private int ticks;
    private long readyAt;

    public AeaCustomGoal(Mob mob, EnumSet<Goal.Flag> controls, Optional<EntityCondition> startCondition, Optional<EntityCondition> continueCondition,
                         Optional<EntityAction> startAction, Optional<EntityAction> tickAction, Optional<EntityAction> stopAction,
                         int interval, int duration, int cooldown) {
        this.mob = mob;
        this.startCondition = startCondition;
        this.continueCondition = continueCondition;
        this.startAction = startAction;
        this.tickAction = tickAction;
        this.stopAction = stopAction;
        this.interval = Math.max(1, interval);
        this.duration = duration;
        this.cooldown = Math.max(0, cooldown);
        setFlags(controls);
    }

    private EntityCtx ctx() {
        return EntityCtx.of(mob, mob.level());
    }

    private boolean test(Optional<EntityCondition> c) {
        if (c.isEmpty()) return true;
        try {
            return c.get().test(ctx());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void run(Optional<EntityAction> a) {
        if (a.isPresent()) {
            try {
                a.get().run(ctx());
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Override
    public boolean canUse() {
        return mob.level().getGameTime() >= readyAt && test(startCondition);
    }

    @Override
    public boolean canContinueToUse() {
        if (duration > 0 && ticks >= duration) return false;
        return test(continueCondition.isPresent() ? continueCondition : startCondition);
    }

    @Override
    public void start() {
        ticks = 0;
        run(startAction);
    }

    @Override
    public void stop() {
        run(stopAction);
        readyAt = mob.level().getGameTime() + cooldown;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (ticks++ % interval == 0) run(tickAction);
    }
}
