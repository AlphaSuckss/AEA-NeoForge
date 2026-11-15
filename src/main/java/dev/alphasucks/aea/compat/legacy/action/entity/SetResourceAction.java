package dev.alphasucks.aea.compat.legacy.action.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerResources;
import net.minecraft.resources.ResourceLocation;

/** {@code apoli:set_resource} (Apoli 2.9): sets a resource power to a value. */
public final class SetResourceAction implements ActionType<EntityCtx, SetResourceAction.Cfg> {
    public record Cfg(ResourceLocation resource, int value) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            IdCodecs.ID.fieldOf("resource").forGetter(Cfg::resource),
            Codec.INT.fieldOf("value").forGetter(Cfg::value)
        ).apply(i, Cfg::new));
    }

    @Override
    public void run(Cfg cfg, EntityCtx ctx) {
        if (ctx.level().isClientSide()) return;
        PowerResources.write(PowerContainer.of(ctx.raw()), cfg.resource, cfg.value);
    }
}
