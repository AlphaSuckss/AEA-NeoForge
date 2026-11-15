package dev.alphasucks.aea.gui.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.compat.Reg;
import dev.alphasucks.aea.gui.GuiElement;
import dev.alphasucks.aea.gui.GuiMemory;
import dev.alphasucks.aea.gui.GuiMenu;
import dev.alphasucks.aea.gui.GuiPowers;
import dev.alphasucks.aea.gui.GuiSlot;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.codec.LoggedOptionalField;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.ConditionTypes;
import dev.overgrown.apoli.condition.ItemCondition;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.condition.context.ItemCtx;
import dev.overgrown.apoli.data.Comparison;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Entity conditions of the GUI feature. */
public final class GuiConditions {
    private GuiConditions() {}

    public static void register() {
        Reg.condition(ConditionTypes.ENTITY, "aea:gui_open", new GuiOpen());
        Reg.condition(ConditionTypes.ENTITY, "aea:gui_slot", new GuiSlotCondition());
        Reg.condition(ConditionTypes.ENTITY, "aea:gui_value", new GuiValue());
    }

    // ------------------------------------------------------------------ aea:gui_open

    public record OpenCfg(Optional<ResourceLocation> gui) {}

    public static final class GuiOpen implements ConditionType<EntityCtx, OpenCfg> {
        @Override
        public MapCodec<OpenCfg> codec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(IdCodecs.ID.optionalFieldOf("gui").forGetter(OpenCfg::gui)).apply(i, OpenCfg::new));
        }

        @Override
        public boolean test(OpenCfg cfg, EntityCtx ctx) {
            GuiMenu menu = GuiPowers.open(ctx.entity());
            return menu != null && !menu.broken && (cfg.gui().isEmpty() || cfg.gui().get().equals(menu.guiId));
        }
    }

    // ------------------------------------------------------------------ aea:gui_slot

    public record SlotCfg(String slot, Optional<ItemCondition> itemCondition, Optional<Comparison> comparison, int compareTo) {}

    public static final class GuiSlotCondition implements ConditionType<EntityCtx, SlotCfg> {
        @Override
        public MapCodec<SlotCfg> codec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("slot").forGetter(SlotCfg::slot),
                LoggedOptionalField.strict("item_condition", ItemCondition.CODEC).forGetter(SlotCfg::itemCondition),
                Comparison.CODEC.optionalFieldOf("comparison").forGetter(SlotCfg::comparison),
                Codec.INT.optionalFieldOf("compare_to", 1).forGetter(SlotCfg::compareTo)
            ).apply(i, SlotCfg::new));
        }

        @Override
        public boolean test(SlotCfg cfg, EntityCtx ctx) {
            GuiMenu menu = GuiPowers.open(ctx.entity());
            if (menu == null || menu.model == null) return false;
            Player player = (Player) ctx.entity();
            List<GuiSlot> slots = menu.resolveSlots(cfg.slot());
            boolean any = false;
            int count = 0;
            for (GuiSlot slot : slots) {
                ItemStack stack = slot.getItem();
                boolean match;
                if (cfg.itemCondition().isPresent()) {
                    try {
                        match = cfg.itemCondition().get().test(new ItemCtx(stack, player.level(), player));
                    } catch (RuntimeException e) {
                        match = false;
                    }
                } else {
                    match = !stack.isEmpty();
                }
                if (match) {
                    any = true;
                    count += stack.getCount();
                }
            }
            return cfg.comparison().isPresent() ? cfg.comparison().get().compare(count, cfg.compareTo()) : any;
        }
    }

    // ------------------------------------------------------------------ aea:gui_value

    public record ValueCfg(Optional<ResourceLocation> gui, String slider, Comparison comparison, double compareTo) {}

    public static final class GuiValue implements ConditionType<EntityCtx, ValueCfg> {
        @Override
        public MapCodec<ValueCfg> codec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                IdCodecs.ID.optionalFieldOf("gui").forGetter(ValueCfg::gui),
                Codec.STRING.fieldOf("slider").forGetter(ValueCfg::slider),
                Comparison.CODEC.fieldOf("comparison").forGetter(ValueCfg::comparison),
                Codec.DOUBLE.fieldOf("compare_to").forGetter(ValueCfg::compareTo)
            ).apply(i, ValueCfg::new));
        }

        @Override
        public boolean test(ValueCfg cfg, EntityCtx ctx) {
            double v = value(ctx.entity() instanceof Player p ? p : null, cfg.gui(), cfg.slider());
            return !Double.isNaN(v) && cfg.comparison().compare(v, cfg.compareTo());
        }

        /** The open GUI's slider; else (with {@code gui}) the remembered value, else that slider's start value. */
        static double value(@Nullable Player player, Optional<ResourceLocation> gui, String slider) {
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
}
