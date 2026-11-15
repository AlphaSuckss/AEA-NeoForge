package dev.alphasucks.aea.compat.sync.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.resources.ResourceLocation;

/** {@code sync:flip_model}: renders the holder upside down (Dinnerbone style); see AEAClient. */
public final class FlipModelPower extends PowerType<FlipModelPower.Config> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("sync", "flip_model");

    public record Config(boolean flipView) {}

    @Override
    public MapCodec<Config> configCodec() {
        return Codec.BOOL.optionalFieldOf("flip_view", false).xmap(Config::new, Config::flipView);
    }

    @Override
    public boolean resolvesForClient() {
        return true;
    }
}
