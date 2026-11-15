package dev.alphasucks.aea.compat.sync.power;

import com.mojang.serialization.MapCodec;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.resources.ResourceLocation;

/** {@code sync:prevent_sprinting_particles}: no running particles under the holder; see EntityMixin. */
public final class PreventSprintingParticlesPower extends PowerType<PreventSprintingParticlesPower.Config> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("sync", "prevent_sprinting_particles");

    public record Config() {}

    @Override
    public MapCodec<Config> configCodec() {
        return MapCodec.unit(new Config());
    }
}
