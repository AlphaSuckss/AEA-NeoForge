package dev.alphasucks.aea.gui.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.gui.GuiCodecs;
import dev.alphasucks.aea.gui.GuiMenu;
import dev.alphasucks.aea.gui.GuiSlot;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.condition.context.EntityCtx;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** {@code aea:modify_gui_slot}: set / add / consume / clear items in GUI slots. */
public final class ModifyGuiSlotAction implements ActionType<EntityCtx, ModifyGuiSlotAction.Cfg> {
    public enum Operation implements StringRepresentable {
        SET("set"), ADD("add"), CONSUME("consume"), CLEAR("clear");

        public static final Codec<Operation> CODEC = StringRepresentable.fromEnum(Operation::values);
        private final String name;

        Operation(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public record Cfg(String slot, Operation operation, Optional<ItemStack> item, Optional<Integer> count) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.<Cfg>mapCodec(i -> i.group(
            Codec.STRING.fieldOf("slot").forGetter(Cfg::slot),
            Operation.CODEC.fieldOf("operation").forGetter(Cfg::operation),
            GuiCodecs.ITEM_STACK.optionalFieldOf("item").forGetter(Cfg::item),
            Codec.intRange(0, 4096).optionalFieldOf("count").forGetter(Cfg::count)
        ).apply(i, Cfg::new)).flatXmap(c -> (c.operation == Operation.SET || c.operation == Operation.ADD) && c.item.isEmpty()
            ? DataResult.error(() -> "aea:modify_gui_slot: \"" + c.operation.getSerializedName() + "\" needs an \"item\"")
            : DataResult.success(c), DataResult::success);
    }

    @Override
    public void run(Cfg cfg, EntityCtx ctx) {
        GuiMenu menu = GuiActions.serverGui(ctx.entity());
        if (menu == null) return;
        List<GuiSlot> slots = menu.resolveSlots(cfg.slot());
        if (slots.isEmpty()) return;
        switch (cfg.operation) {
            case CLEAR -> slots.forEach(s -> s.set(ItemStack.EMPTY));
            case SET -> {
                ItemStack item = cfg.item.get();
                int count = cfg.count.orElse(item.getCount());
                for (GuiSlot s : slots) {
                    if (count <= 0) s.set(ItemStack.EMPTY);
                    else s.set(item.copyWithCount(Math.min(count, capacity(s, item))));
                }
            }
            case ADD -> add(slots, cfg.item.get(), cfg.count.orElse(cfg.item.get().getCount()));
            case CONSUME -> consume(slots, cfg.item.orElse(null), cfg.count.orElse(1));
        }
    }

    static int capacity(GuiSlot slot, ItemStack stack) {
        return Math.max(1, Math.min(slot.getMaxStackSize(), stack.getMaxStackSize()));
    }

    /** Adds {@code count} of {@code item} only if all of it fits: tops up matching stacks first, then fills empty slots. */
    static boolean add(List<GuiSlot> slots, ItemStack item, int count) {
        if (count <= 0 || item.isEmpty()) return false;
        int room = 0;
        for (GuiSlot s : slots) {
            ItemStack in = s.getItem();
            int cap = capacity(s, item);
            if (in.isEmpty()) room += cap;
            else if (ItemStack.isSameItemSameComponents(in, item)) room += Math.max(0, cap - in.getCount());
            if (room >= count) break;
        }
        if (room < count) return false;
        int left = count;
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (GuiSlot s : slots) {
                if (left <= 0) break;
                ItemStack in = s.getItem();
                int cap = capacity(s, item);
                if (pass == 0 && !in.isEmpty() && ItemStack.isSameItemSameComponents(in, item)) {
                    int add = Math.min(left, cap - in.getCount());
                    if (add > 0) {
                        in.grow(add);
                        s.setChanged();
                        left -= add;
                    }
                } else if (pass == 1 && in.isEmpty()) {
                    int put = Math.min(left, cap);
                    s.set(item.copyWithCount(put));
                    left -= put;
                }
            }
        }
        return true;
    }

    /** Removes {@code count} matching items only if that many are present. A null template matches anything. */
    static boolean consume(List<GuiSlot> slots, @Nullable ItemStack template, int count) {
        if (count <= 0) return false;
        int have = 0;
        for (GuiSlot s : slots) if (matches(s.getItem(), template)) have += s.getItem().getCount();
        if (have < count) return false;
        int left = count;
        for (GuiSlot s : slots) {
            if (left <= 0) break;
            ItemStack in = s.getItem();
            if (!matches(in, template)) continue;
            int take = Math.min(left, in.getCount());
            in.shrink(take);
            left -= take;
            if (in.isEmpty()) s.set(ItemStack.EMPTY);
            else s.setChanged();
        }
        return true;
    }

    private static boolean matches(ItemStack stack, @Nullable ItemStack template) {
        if (stack.isEmpty()) return false;
        if (template == null) return true;
        // 1.20.1: a template with NBT matched exactly, one without matched by item. Components are 1.21's NBT.
        return template.getComponentsPatch().isEmpty() ? stack.is(template.getItem()) : ItemStack.isSameItemSameComponents(stack, template);
    }
}
