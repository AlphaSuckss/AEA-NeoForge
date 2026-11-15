package dev.alphasucks.aea.gui.action.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.gui.GuiMenu;
import dev.alphasucks.aea.gui.GuiPlaceholders;
import dev.alphasucks.aea.gui.GuiPowers;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.condition.context.EntityCtx;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** {@code aea:gui_command}: runs a command with the GUI placeholders (slider values, slot contents, ...) filled in. */
public final class GuiCommandAction implements ActionType<EntityCtx, GuiCommandAction.Cfg> {
    public record Cfg(String command) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(Codec.STRING.fieldOf("command").forGetter(Cfg::command)).apply(i, Cfg::new));
    }

    @Override
    public void run(Cfg cfg, EntityCtx ctx) {
        Entity entity = ctx.entity();
        if (entity == null) return;
        MinecraftServer server = entity.level().getServer();
        if (server == null) return;
        GuiMenu menu = GuiPowers.serverGui(entity);
        int slider = menu == null ? -1 : menu.contextSlider();
        String command = GuiPlaceholders.resolver(menu, entity instanceof Player p ? p : null, slider,
            i -> menu.sliders[i]).apply(cfg.command());
        // AEA 1.5.4 ran these at Apoli's execute_command level (2) with output hidden by default.
        CommandSourceStack source = entity.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, command);
    }
}
