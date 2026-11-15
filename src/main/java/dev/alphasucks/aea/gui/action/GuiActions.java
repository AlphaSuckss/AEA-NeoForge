package dev.alphasucks.aea.gui.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.compat.Reg;
import dev.alphasucks.aea.gui.AeaGui;
import dev.alphasucks.aea.gui.GuiMenu;
import dev.alphasucks.aea.gui.GuiPlaceholders;
import dev.alphasucks.aea.gui.GuiPowers;
import dev.alphasucks.aea.gui.GuiSlot;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.action.ActionTypes;
import dev.overgrown.apoli.action.ItemAction;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.condition.context.ItemCtx;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Optional;

/** Entity actions of the GUI feature (all in the entity action registry, as in AEA 1.5.4). */
public final class GuiActions {
    private GuiActions() {}

    public static void register() {
        Reg.action(ActionTypes.ENTITY, "aea:open_gui", new OpenGui());
        Reg.action(ActionTypes.ENTITY, "aea:close_gui", new CloseGui());
        Reg.action(ActionTypes.ENTITY, "aea:gui_command", new GuiCommand());
        Reg.action(ActionTypes.ENTITY, "aea:gui_slot_action", new GuiSlotItemAction());
        Reg.action(ActionTypes.ENTITY, "aea:modify_gui_slot", new ModifyGuiSlotAction());
        Reg.action(ActionTypes.ENTITY, "aea:gui_craft", new GuiCraftAction());
    }

    /** The server-side GUI the entity has open, if any. */
    @Nullable
    static GuiMenu serverGui(Entity entity) {
        GuiMenu menu = GuiPowers.open(entity);
        return menu != null && !menu.isClient() && menu.model != null ? menu : null;
    }

    // ------------------------------------------------------------------ aea:open_gui

    public record OpenCfg(ResourceLocation power) {}

    public static final class OpenGui implements ActionType<EntityCtx, OpenCfg> {
        @Override
        public MapCodec<OpenCfg> codec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(IdCodecs.ID.fieldOf("power").forGetter(OpenCfg::power)).apply(i, OpenCfg::new));
        }

        @Override
        public void run(OpenCfg cfg, EntityCtx ctx) {
            if (ctx.entity() instanceof ServerPlayer player) AeaGui.open(player, cfg.power());
        }
    }

    // ------------------------------------------------------------------ aea:close_gui

    public record CloseCfg(Optional<ResourceLocation> gui) {}

    public static final class CloseGui implements ActionType<EntityCtx, CloseCfg> {
        @Override
        public MapCodec<CloseCfg> codec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(IdCodecs.ID.optionalFieldOf("gui").forGetter(CloseCfg::gui)).apply(i, CloseCfg::new));
        }

        @Override
        public void run(CloseCfg cfg, EntityCtx ctx) {
            GuiMenu menu = serverGui(ctx.entity());
            if (menu == null || !(ctx.entity() instanceof ServerPlayer player)) return;
            if (cfg.gui().isPresent() && !cfg.gui().get().equals(menu.guiId)) return;
            if (menu.session() != null && menu.session().closing()) return;
            player.closeContainer();
        }
    }

    // ------------------------------------------------------------------ aea:gui_command

    public record CommandCfg(String command) {}

    public static final class GuiCommand implements ActionType<EntityCtx, CommandCfg> {
        @Override
        public MapCodec<CommandCfg> codec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(Codec.STRING.fieldOf("command").forGetter(CommandCfg::command)).apply(i, CommandCfg::new));
        }

        @Override
        public void run(CommandCfg cfg, EntityCtx ctx) {
            Entity entity = ctx.entity();
            if (entity == null) return;
            MinecraftServer server = entity.level().getServer();
            if (server == null) return;
            GuiMenu menu = serverGui(entity);
            int slider = menu == null ? -1 : menu.contextSlider();
            String command = GuiPlaceholders.resolver(menu, entity instanceof Player p ? p : null, slider,
                i -> menu.sliders[i]).apply(cfg.command());
            // AEA 1.5.4 ran these at Apoli's execute_command level (2) with output hidden by default.
            CommandSourceStack source = entity.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            server.getCommands().performPrefixedCommand(source, command);
        }
    }

    // ------------------------------------------------------------------ aea:gui_slot_action

    public record SlotActionCfg(String slot, ItemAction itemAction) {}

    public static final class GuiSlotItemAction implements ActionType<EntityCtx, SlotActionCfg> {
        @Override
        public MapCodec<SlotActionCfg> codec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("slot").forGetter(SlotActionCfg::slot),
                ItemAction.CODEC.fieldOf("item_action").forGetter(SlotActionCfg::itemAction)
            ).apply(i, SlotActionCfg::new));
        }

        @Override
        public void run(SlotActionCfg cfg, EntityCtx ctx) {
            GuiMenu menu = serverGui(ctx.entity());
            if (menu == null) return;
            Player player = (Player) ctx.entity();
            for (GuiSlot slot : menu.resolveSlots(cfg.slot())) {
                ItemStack stack = slot.getItem();
                cfg.itemAction().run(new ItemCtx(stack, player.level(), player, slot::set));
                if (slot.getItem() == stack && stack.isEmpty()) slot.set(ItemStack.EMPTY);
                else slot.setChanged();
            }
        }
    }
}
