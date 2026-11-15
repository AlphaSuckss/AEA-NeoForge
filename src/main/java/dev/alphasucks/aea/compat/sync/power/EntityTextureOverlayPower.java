package dev.alphasucks.aea.compat.sync.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** {@code sync:entity_texture_overlay}: replaces or tints the holder's player skin; drawn by AEAClient. */
public final class EntityTextureOverlayPower extends PowerType<EntityTextureOverlayPower.Config> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("sync", "entity_texture_overlay");

    public record Config(Optional<ResourceLocation> wide, Optional<ResourceLocation> slim, boolean firstPerson, boolean asOverlay,
                         float red, float green, float blue, float alpha, boolean hideCape) {
        public Optional<ResourceLocation> texture(boolean slimModel) {
            return slimModel ? slim.or(() -> wide) : wide.or(() -> slim);
        }
    }

    @Override
    public MapCodec<Config> configCodec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            IdCodecs.ID.optionalFieldOf("wide_texture_location").forGetter(Config::wide),
            IdCodecs.ID.optionalFieldOf("slim_texture_location").forGetter(Config::slim),
            Codec.BOOL.optionalFieldOf("show_first_person", false).forGetter(Config::firstPerson),
            Codec.BOOL.optionalFieldOf("render_as_overlay", false).forGetter(Config::asOverlay),
            Codec.FLOAT.optionalFieldOf("red", 1f).forGetter(Config::red),
            Codec.FLOAT.optionalFieldOf("green", 1f).forGetter(Config::green),
            Codec.FLOAT.optionalFieldOf("blue", 1f).forGetter(Config::blue),
            Codec.FLOAT.optionalFieldOf("alpha", 1f).forGetter(Config::alpha),
            Codec.BOOL.optionalFieldOf("hide_cape", false).forGetter(Config::hideCape)
        ).apply(i, Config::new));
    }

    @Override
    public boolean resolvesForClient() {
        return true;
    }
}
