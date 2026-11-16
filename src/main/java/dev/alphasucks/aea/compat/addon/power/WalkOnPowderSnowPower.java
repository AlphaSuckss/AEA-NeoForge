package dev.alphasucks.aea.compat.addon.power;

import com.mojang.serialization.MapCodec;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.resources.ResourceLocation;

/** {@code moborigins:walk_on_powder_snow}: the holder walks on powder snow as if wearing leather boots; see PowderSnowBlockMixin. */
public final class WalkOnPowderSnowPower extends PowerType<Boolean> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("moborigins", "walk_on_powder_snow");
    public static final dev.overgrown.apoli.power.PowerTypeUsage.Handle HELD = dev.overgrown.apoli.power.PowerTypeUsage.handle(ID);

    @Override
    public MapCodec<Boolean> configCodec() {
        return MapCodec.unit(Boolean.TRUE);
    }
}
