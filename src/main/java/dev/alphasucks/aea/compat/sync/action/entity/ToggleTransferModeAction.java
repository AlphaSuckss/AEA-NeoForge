package dev.alphasucks.aea.compat.sync.action.entity;

import com.mojang.serialization.MapCodec;
import dev.alphasucks.aea.compat.sync.StolenPowers;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.condition.context.EntityCtx;

/** {@code sync:toggle_transfer_mode}: switches {@code sync:transfer} in auto mode between stealing and giving. */
public final class ToggleTransferModeAction implements ActionType<EntityCtx, Object> {
    @Override
    public MapCodec<Object> codec() {
        return MapCodec.unit(Boolean.TRUE);
    }

    @Override
    public void run(Object cfg, EntityCtx ctx) {
        if (!ctx.level().isClientSide()) StolenPowers.toggleMode(ctx.raw());
    }
}
