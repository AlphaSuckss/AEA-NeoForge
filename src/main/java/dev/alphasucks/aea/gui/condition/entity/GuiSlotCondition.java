package dev.alphasucks.aea.gui.condition.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.gui.GuiMenu;
import dev.alphasucks.aea.gui.GuiPowers;
import dev.alphasucks.aea.gui.GuiSlot;
import dev.overgrown.apoli.codec.LoggedOptionalField;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.ItemCondition;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.condition.context.ItemCtx;
import dev.overgrown.apoli.data.Comparison;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * {@code aea:gui_slot}: whether any matching GUI slot holds an item (passing {@code item_condition}, when given). With a
 * comparison, the total count of matching items is compared instead.
 */
public final class GuiSlotCondition implements ConditionType<EntityCtx, GuiSlotCondition.Cfg> {
    public record Cfg(String slot, Optional<ItemCondition> itemCondition, Optional<Comparison> comparison, int compareTo) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("slot").forGetter(Cfg::slot),
            LoggedOptionalField.strict("item_condition", ItemCondition.CODEC).forGetter(Cfg::itemCondition),
            Comparison.CODEC.optionalFieldOf("comparison").forGetter(Cfg::comparison),
            Codec.INT.optionalFieldOf("compare_to", 1).forGetter(Cfg::compareTo)
        ).apply(i, Cfg::new));
    }

    @Override
    public boolean test(Cfg cfg, EntityCtx ctx) {
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
