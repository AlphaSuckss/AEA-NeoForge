package dev.alphasucks.aea.compat.legacy.condition.fluid;

import com.mojang.serialization.MapCodec;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.FluidCtx;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** {@code apoli:fluid} (Apoli 2.9): the fluid is exactly {@code fluid}. */
public final class FluidCondition implements ConditionType<FluidCtx, ResourceLocation> {
    @Override
    public MapCodec<ResourceLocation> codec() {
        return IdCodecs.ID.fieldOf("fluid");
    }

    @Override
    public boolean test(ResourceLocation fluid, FluidCtx ctx) {
        return BuiltInRegistries.FLUID.getKey(ctx.state().getType()).equals(fluid);
    }
}
