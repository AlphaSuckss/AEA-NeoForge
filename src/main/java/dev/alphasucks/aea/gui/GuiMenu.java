package dev.alphasucks.aea.gui;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * The menu behind an {@code aea:gui}: the GUI's own slots first, then (with {@code show_inventory}) the 36 player slots.
 * The client builds the same layout from the synced power definition; a mismatch makes it a "broken" menu that closes.
 */
public final class GuiMenu extends AbstractContainerMenu {
    public final ResourceLocation powerId;
    public final ResourceLocation guiId;
    @Nullable public final GuiConfig config;
    @Nullable public final GuiModel model;
    public final Player player;
    public final SimpleContainer container;
    public final int guiSlotCount;
    public final int playerSlotStart;
    public final BitSet visible = new BitSet();
    public final float[] progress;
    public final double[] sliders;
    public final int[][] entityIds;
    public final boolean broken;
    @Nullable GuiSession session;
    private boolean closed;
    private boolean closing;
    int contextSlider = -1;

    private GuiMenu(int syncId, Inventory inventory, ResourceLocation powerId, ResourceLocation guiId, @Nullable GuiConfig config, int expectedSlots) {
        super(AeaGui.MENU.get(), syncId);
        this.powerId = powerId;
        this.guiId = guiId;
        this.player = inventory.player;
        GuiModel model = config == null ? null : config.model();
        int guiSlots = model == null ? 0 : model.slotCount();
        int total = config == null ? 0 : guiSlots + (config.showInventory() ? 36 : 0);
        boolean ok = model != null && (expectedSlots < 0 || expectedSlots == total);
        this.broken = !ok;
        if (!ok) {
            int dead = Math.max(0, Math.min(expectedSlots, 4096));
            this.config = null;
            this.model = null;
            this.container = new SimpleContainer(Math.max(1, dead));
            for (int i = 0; i < dead; i++) addSlot(new GuiSlot.Dead(container, i));
            this.guiSlotCount = dead;
            this.playerSlotStart = -1;
            this.progress = new float[0];
            this.sliders = new double[0];
            this.entityIds = new int[0][];
            return;
        }
        this.config = config;
        this.model = model;
        this.container = new SimpleContainer(Math.max(1, guiSlots));
        for (GuiModel.SlotDef def : model.slots()) {
            addSlot(new GuiSlot(this, def, container));
            if (def.isDisplay() && def.displayItem() != null) container.setItem(def.index(), def.displayItem().copy());
        }
        this.guiSlotCount = guiSlots;
        if (config.showInventory()) {
            this.playerSlotStart = guiSlots;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 0, 0));
            }
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 0, 0));
        } else {
            this.playerSlotStart = -1;
        }
        int n = model.elementCount();
        this.progress = new float[n];
        this.sliders = new double[n];
        this.entityIds = new int[n][];
        Arrays.fill(sliders, Double.NaN);
        for (int i = 0; i < n; i++) {
            entityIds[i] = new int[0];
            if (model.element(i) instanceof GuiElement.Slider slider) sliders[i] = slider.start();
        }
        visible.set(0, n);
    }

    public static GuiMenu createServer(int syncId, Inventory inventory, ServerPlayer player, ResourceLocation powerId, ResourceLocation guiId, GuiConfig config) {
        GuiMenu menu = new GuiMenu(syncId, inventory, powerId, guiId, config, -1);
        menu.session = new GuiSession(menu, player);
        GuiPowers.beginOpening(menu);
        try {
            menu.session.onOpen();
        } finally {
            GuiPowers.endOpening();
        }
        return menu;
    }

    public static GuiMenu createClient(int syncId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        ResourceLocation powerId = buf.readResourceLocation();
        ResourceLocation guiId = buf.readResourceLocation();
        int slots = buf.readVarInt();
        GuiConfig config = GuiPowers.config(inventory.player, powerId);
        GuiMenu menu = new GuiMenu(syncId, inventory, powerId, guiId, config, slots);
        GuiNetwork.State state = GuiNetwork.State.read(buf);
        if (!menu.broken) state.applyTo(menu);
        return menu;
    }

    public int contextSlider() { return contextSlider; }

    public boolean isClient() { return session == null; }

    public boolean isClosed() { return closed; }

    public boolean isVisible(int element) { return visible.get(element); }

    @Nullable
    public GuiSession session() { return session; }

    @Nullable
    public GuiSlot guiSlot(int index) {
        return index >= 0 && index < guiSlotCount && slots.get(index) instanceof GuiSlot s ? s : null;
    }

    public List<GuiSlot> resolveSlots(String id) {
        if (model == null) return List.of();
        List<Integer> indices = model.resolveSlots(id);
        GuiSlot[] out = new GuiSlot[indices.size()];
        int n = 0;
        for (int i : indices) {
            GuiSlot s = guiSlot(i);
            if (s != null) out[n++] = s;
        }
        return List.of(Arrays.copyOf(out, n));
    }

    public double sliderValue(String id) {
        if (model == null) return Double.NaN;
        int e = model.sliderElement(id);
        return e < 0 ? Double.NaN : sliders[e];
    }

    @Override
    public boolean stillValid(Player player) {
        return !closed && player.isAlive();
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (closed || broken) return;
        if (slotId >= 0 && slotId < guiSlotCount) {
            GuiSlot slot = guiSlot(slotId);
            if (slot == null || !slot.visible() || slot.def.isDisplay()) return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        if (slot instanceof GuiSlot gui && (gui.def.isDisplay() || !gui.visible())) return false;
        return super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (closed || broken || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < guiSlotCount) {
            GuiSlot gui = guiSlot(index);
            if (gui == null || !gui.mayPickup(player)) return ItemStack.EMPTY;
            if (playerSlotStart >= 0) {
                if (!moveItemStackTo(stack, playerSlotStart, playerSlotStart + 36, true)) return ItemStack.EMPTY;
            } else {
                player.getInventory().add(stack);
            }
        } else if (!insertIntoGui(stack)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    /** Shift-click into the GUI: first stacks onto matching items, then empty slots. */
    private boolean insertIntoGui(ItemStack stack) {
        int before = stack.getCount();
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int i = 0; i < guiSlotCount && !stack.isEmpty(); i++) {
                GuiSlot slot = guiSlot(i);
                if (slot == null || !slot.visible() || !slot.def.acceptsInsert()) continue;
                boolean empty = !slot.hasItem();
                if ((pass == 0) == empty) continue;
                if (!empty && !ItemStack.isSameItemSameComponents(slot.getItem(), stack)) continue;
                if (!slot.mayPlace(stack)) continue;
                slot.safeInsert(stack);
            }
        }
        return stack.getCount() != before;
    }

    @Override
    public void removed(Player player) {
        if (closed || closing) return;
        closing = true;
        try {
            if (session != null) session.releaseAll();
        } finally {
            closed = true;
            super.removed(player);
            if (session != null) session.finishClose();
        }
    }
}
