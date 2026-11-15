package dev.alphasucks.aea.gui.condition.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.gui.GuiElement;
import dev.alphasucks.aea.gui.GuiMemory;
import dev.alphasucks.aea.gui.GuiMenu;
import dev.alphasucks.aea.gui.GuiPowers;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.data.Comparison;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Optional;

/** {@code aea:gui_value}: compares a GUI slider's value. */
public final class GuiValueCondition implements ConditionType<EntityCtx, GuiValueCondition.Cfg> {
    public record Cfg(Optional<ResourceLocation> gui, String slider, Comparison comparison, double compareTo) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            IdCodecs.ID.optionalFieldOf("gui").forGetter(Cfg::gui),
            Codec.STRING.fieldOf("slider").forGetter(Cfg::slider),
            Comparison.CODEC.fieldOf("comparison").forGetter(Cfg::comparison),
            Codec.DOUBLE.fieldOf("compare_to").forGetter(Cfg::compareTo)
        ).apply(i, Cfg::new));
    }

    @Override
    public boolean test(Cfg cfg, EntityCtx ctx) {
        double v = value(ctx.entity() instanceof Player p ? p : null, cfg.gui(), cfg.slider());
        return !Double.isNaN(v) && cfg.comparison().compare(v, cfg.compareTo());
    }

    /** The open GUI's slider; else (with {@code gui}) the remembered value, else that slider's start value. */
    private static double value(@Nullable Player player, Optional<ResourceLocation> gui, String slider) {
        if (player == null) return Double.NaN;
        GuiMenu menu = GuiPowers.open(player);
        if (menu != null && menu.model != null && (gui.isEmpty() || gui.get().equals(menu.guiId))) {
            double v = menu.sliderValue(slider);
            if (!Double.isNaN(v)) return v;
        }
        if (gui.isEmpty()) return Double.NaN;
        GuiPowers.Held held = GuiPowers.byGuiId(player, gui.get());
        if (!player.level().isClientSide()) {
            double remembered = GuiMemory.slider(player, gui.get(), slider);
            if (!Double.isNaN(remembered)) return remembered;
        }
        if (held != null) {
            int e = held.config().model().sliderElement(slider);
            if (e >= 0 && held.config().model().element(e) instanceof GuiElement.Slider s) return s.start();
        }
        return Double.NaN;
    }
}
