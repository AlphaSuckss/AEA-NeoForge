package dev.alphasucks.aea.gui;

import com.mojang.serialization.MapCodec;
import dev.overgrown.apoli.power.PowerType;

/** {@code aea:gui}: a data-driven screen the holder can open with {@code aea:open_gui}. */
public final class GuiPowerType extends PowerType<GuiConfig> {
    @Override
    public MapCodec<GuiConfig> configCodec() {
        return GuiConfig.CODEC;
    }
}
