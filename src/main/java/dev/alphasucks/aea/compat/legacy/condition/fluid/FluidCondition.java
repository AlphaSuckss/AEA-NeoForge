package dev.alphasucks.aea.compat.legacy.condition.fluid;

import net.minecraft.world.level.material.Fluid;
import com.mojang.serialization.MapCodec;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.FluidCtx;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** {@code apoli:fluid} (Apoli 2.9): the fluid is exactly {@code fluid}. */
public final class FluidCondition implements ConditionType<FluidCtx, FluidCondition.Cfg> {
    /** The fluid is resolved once at load (fluids are registered before data packs); null when unknown. */
    public record Cfg(ResourceLocation id, Fluid fluid) {}

    private static final MapCodec<Cfg> CODEC = IdCodecs.ID.xmap(
        id -> new Cfg(id, BuiltInRegistries.FLUID.getOptional(id).orElse(null)), Cfg::id).fieldOf("fluid");

    @Override
    public MapCodec<Cfg> codec() {
        return CODEC;
    }

    @Override
    public boolean test(Cfg cfg, FluidCtx ctx) {
        return cfg.fluid() != null && ctx.state().getType() == cfg.fluid();
    }
}
