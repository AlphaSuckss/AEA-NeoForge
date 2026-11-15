package dev.alphasucks.aea.gui.action.entity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.gui.AeaGui;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.condition.context.EntityCtx;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** {@code aea:open_gui}: opens the GUI of one of the player's {@code aea:gui} powers. */
public final class OpenGuiAction implements ActionType<EntityCtx, OpenGuiAction.Cfg> {
    public record Cfg(ResourceLocation power) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(IdCodecs.ID.fieldOf("power").forGetter(Cfg::power)).apply(i, Cfg::new));
    }

    @Override
    public void run(Cfg cfg, EntityCtx ctx) {
        if (ctx.entity() instanceof ServerPlayer player) AeaGui.open(player, cfg.power());
    }
}
