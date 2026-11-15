package dev.alphasucks.aea.compat.legacy.action.item;

import com.mojang.serialization.MapCodec;
import dev.alphasucks.aea.compat.legacy.LegacyItemMerge;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.condition.context.ItemCtx;

/** {@code aea:legacy_merge_nbt}: what {@code origins:merge_nbt} did on 1.20.1; ApoliJsonUpgrader rewrites old packs to it. */
public final class LegacyMergeNbtAction implements ActionType<ItemCtx, LegacyItemMerge.Patch> {
    @Override
    public MapCodec<LegacyItemMerge.Patch> codec() {
        return LegacyItemMerge.codec("nbt");
    }

    @Override
    public void run(LegacyItemMerge.Patch patch, ItemCtx ctx) {
        if (ctx.level() == null) return;
        patch.apply(ctx.stack(), ctx.level().registryAccess());
    }
}
