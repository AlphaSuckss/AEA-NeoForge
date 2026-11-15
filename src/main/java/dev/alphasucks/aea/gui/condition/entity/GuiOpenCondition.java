package dev.alphasucks.aea.gui.condition.entity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.gui.GuiMenu;
import dev.alphasucks.aea.gui.GuiPowers;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.EntityCtx;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** {@code aea:gui_open}: the entity has a GUI open ({@code gui}, when given). */
public final class GuiOpenCondition implements ConditionType<EntityCtx, GuiOpenCondition.Cfg> {
    public record Cfg(Optional<ResourceLocation> gui) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(IdCodecs.ID.optionalFieldOf("gui").forGetter(Cfg::gui)).apply(i, Cfg::new));
    }

    @Override
    public boolean test(Cfg cfg, EntityCtx ctx) {
        GuiMenu menu = GuiPowers.open(ctx.entity());
        return menu != null && !menu.broken && (cfg.gui().isEmpty() || cfg.gui().get().equals(menu.guiId));
    }
}
