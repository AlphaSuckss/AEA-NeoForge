package dev.alphasucks.aea.compat.sync.condition.item;

import com.mojang.serialization.MapCodec;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.ItemCtx;
import net.minecraft.world.entity.player.Player;

/** {@code sync:on_cooldown}: the item is on cooldown for the player holding it. */
public final class OnCooldownCondition implements ConditionType<ItemCtx, Object> {
    @Override
    public MapCodec<Object> codec() {
        return MapCodec.unit(Boolean.TRUE);
    }

    @Override
    public boolean test(Object cfg, ItemCtx ctx) {
        if (ctx.stack().isEmpty()) return false;
        return ctx.holder() instanceof Player p && p.getCooldowns().isOnCooldown(ctx.stack().getItem());
    }
}
