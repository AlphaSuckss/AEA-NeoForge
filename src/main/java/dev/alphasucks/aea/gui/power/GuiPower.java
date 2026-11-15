package dev.alphasucks.aea.gui.power;

import com.mojang.serialization.MapCodec;
import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.gui.GuiConfig;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.resources.ResourceLocation;

/** {@code aea:gui}: a data-driven screen the holder can open with {@code aea:open_gui}. */
public final class GuiPower extends PowerType<GuiConfig> {
    public static final ResourceLocation ID = AEA.id("gui");

    @Override
    public MapCodec<GuiConfig> configCodec() {
        return GuiConfig.CODEC;
    }
}
