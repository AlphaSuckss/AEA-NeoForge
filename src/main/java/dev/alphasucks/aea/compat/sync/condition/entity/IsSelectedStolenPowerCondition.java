package dev.alphasucks.aea.compat.sync.condition.entity;

import com.mojang.serialization.MapCodec;
import dev.alphasucks.aea.compat.sync.StolenPowers;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.EntityCtx;
import net.minecraft.resources.ResourceLocation;

/** {@code sync:is_selected_stolen_power}: whether the stolen package from {@code source} is the selected one. */
public final class IsSelectedStolenPowerCondition implements ConditionType<EntityCtx, ResourceLocation> {
    @Override
    public MapCodec<ResourceLocation> codec() {
        return IdCodecs.ID.fieldOf("source");
    }

    @Override
    public boolean test(ResourceLocation source, EntityCtx ctx) {
        return StolenPowers.isSourceSelected(ctx.raw(), source);
    }
}
