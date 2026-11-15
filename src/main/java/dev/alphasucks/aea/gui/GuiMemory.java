package dev.alphasucks.aea.gui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.AttachmentType;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Per-player storage for GUIs with {@code "memory": true}: {@code {"<gui id>": {slots: {id: stack}, sliders: {id: value}}}}.
 * Kept in the {@code aea:gui_memory} attachment, saved with the player and copied on death.
 */
public final class GuiMemory {
    private GuiMemory() {}

    public static Supplier<AttachmentType<CompoundTag>> TYPE;

    @Nullable
    public static CompoundTag get(Player player, ResourceLocation guiId) {
        if (!player.hasData(TYPE.get())) return null;
        CompoundTag all = player.getData(TYPE.get());
        if (!all.contains(guiId.toString(), Tag.TAG_COMPOUND)) return null;
        return all.getCompound(guiId.toString());
    }

    public static double slider(Player player, ResourceLocation guiId, String slider) {
        CompoundTag tag = get(player, guiId);
        if (tag == null) return Double.NaN;
        CompoundTag sliders = tag.getCompound("sliders");
        return sliders.contains(slider, Tag.TAG_ANY_NUMERIC) ? sliders.getDouble(slider) : Double.NaN;
    }

    public static void save(Player player, GuiMenu menu) {
        if (menu.model == null) return;
        CompoundTag entry = new CompoundTag();
        CompoundTag slots = new CompoundTag();
        for (GuiModel.SlotDef def : menu.model.slots()) {
            if (def.isDisplay()) continue;
            ItemStack stack = menu.container.getItem(def.index());
            if (!stack.isEmpty()) slots.put(def.id(), stack.save(player.registryAccess()));
        }
        CompoundTag sliders = new CompoundTag();
        menu.model.sliders().forEach((id, element) -> {
            if (!Double.isNaN(menu.sliders[element])) sliders.putDouble(id, menu.sliders[element]);
        });
        entry.put("slots", slots);
        entry.put("sliders", sliders);
        CompoundTag all = player.hasData(TYPE.get()) ? player.getData(TYPE.get()).copy() : new CompoundTag();
        all.put(menu.guiId.toString(), entry);
        player.setData(TYPE.get(), all);
    }

    static ItemStack load(Player player, CompoundTag stack) {
        return ItemStack.parseOptional(player.registryAccess(), stack);
    }
}
