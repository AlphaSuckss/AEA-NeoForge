package dev.alphasucks.aea.compat.sync.action.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.alphasucks.aea.compat.sync.StolenPowers;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.condition.context.EntityCtx;

/** {@code sync:cycle_stolen_power}: moves the stolen-power selection by {@code delta}. */
public final class CycleStolenPowerAction implements ActionType<EntityCtx, Integer> {
    @Override
    public MapCodec<Integer> codec() {
        return Codec.INT.optionalFieldOf("delta", 1);
    }

    @Override
    public void run(Integer delta, EntityCtx ctx) {
        if (!ctx.level().isClientSide()) StolenPowers.cycle(ctx.raw(), delta);
    }
}
