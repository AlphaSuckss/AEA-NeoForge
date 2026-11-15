package dev.alphasucks.aea.gui.action.entity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.gui.GuiMenu;
import dev.alphasucks.aea.gui.GuiPowers;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.condition.context.EntityCtx;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/** {@code aea:close_gui}: closes the open GUI (only if it is {@code gui}, when given). */
public final class CloseGuiAction implements ActionType<EntityCtx, CloseGuiAction.Cfg> {
    public record Cfg(Optional<ResourceLocation> gui) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(IdCodecs.ID.optionalFieldOf("gui").forGetter(Cfg::gui)).apply(i, Cfg::new));
    }

    @Override
    public void run(Cfg cfg, EntityCtx ctx) {
        GuiMenu menu = GuiPowers.serverGui(ctx.entity());
        if (menu == null || !(ctx.entity() instanceof ServerPlayer player)) return;
        if (cfg.gui().isPresent() && !cfg.gui().get().equals(menu.guiId)) return;
        if (menu.session() != null && menu.session().closing()) return;
        player.closeContainer();
    }
}
