package dev.alphasucks.aea.gui;

import com.mojang.brigadier.StringReader;
import dev.alphasucks.aea.AEA;
import dev.overgrown.apoli.action.EntityAction;
import dev.overgrown.apoli.attribution.PowerCause;
import dev.overgrown.apoli.condition.EntityCondition;
import dev.overgrown.apoli.condition.context.EntityCtx;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;

/** Server half of an open {@code aea:gui}: runs element actions, evaluates conditions and progress, syncs state. */
public final class GuiSession {
    private static final int REFRESH_INTERVAL = 5;
    private static final int MEMORY_SAFETY_SAVE = 100;
    private static final int MAX_ENTITY_IDS = 16;

    private final GuiMenu h;
    private final ServerPlayer player;
    private final GuiConfig config;
    private final GuiModel model;
    private final int n;
    private final BitSet sentVisible = new BitSet();
    private final float[] sentProgress;
    private final double[] sentSliders;
    private final int[][] sentEntities;
    private final double[] pendingSlider;
    private final boolean[] pendingRelease;
    private final boolean[] held;
    private final int[] cooldown;
    private final long[] lastPress;
    private int ticks;
    private int sinceSave;
    private boolean loading;
    private boolean closing;

    GuiSession(GuiMenu h, ServerPlayer player) {
        this.h = h;
        this.player = player;
        this.config = h.config;
        this.model = h.model;
        this.n = model.elementCount();
        this.sentProgress = new float[n];
        this.sentSliders = new double[n];
        this.sentEntities = new int[n][];
        this.pendingSlider = new double[n];
        this.pendingRelease = new boolean[n];
        this.held = new boolean[n];
        this.cooldown = new int[n];
        this.lastPress = new long[n];
        Arrays.fill(pendingSlider, Double.NaN);
        Arrays.fill(lastPress, Long.MIN_VALUE);
        if (config.memory()) {
            h.container.addListener(c -> {
                if (!loading && !h.isClosed()) {
                    GuiMemory.save(player, h);
                    sinceSave = 0;
                }
            });
        }
    }

    public ServerPlayer player() { return player; }

    public GuiMenu h() { return h; }

    public boolean closing() { return closing; }

    void onOpen() {
        if (config.memory()) loadMemory();
        evaluateVisibility();
        evaluateProgress();
        resolveEntities();
        markSent();
    }

    private void loadMemory() {
        CompoundTag saved = GuiMemory.get(player, h.guiId);
        if (saved == null) return;
        boolean returned = false;
        loading = true;
        try {
            CompoundTag slots = saved.getCompound("slots");
            for (String id : slots.getAllKeys()) {
                ItemStack stack = GuiMemory.load(player, slots.getCompound(id));
                if (stack.isEmpty()) continue;
                List<Integer> indices = model.resolveSlots(id);
                GuiModel.SlotDef def = indices.size() == 1 ? model.slots().get(indices.get(0)) : null;
                if (def != null && def.id().equals(id) && !def.isDisplay()) {
                    h.container.setItem(def.index(), stack);
                } else {
                    // The GUI changed since the items were stored: hand them back instead of losing them.
                    player.getInventory().placeItemBackInInventory(stack);
                    returned = true;
                }
            }
            CompoundTag sliders = saved.getCompound("sliders");
            for (String id : sliders.getAllKeys()) {
                int e = model.sliderElement(id);
                if (e >= 0 && model.element(e) instanceof GuiElement.Slider slider && sliders.contains(id, Tag.TAG_ANY_NUMERIC)) {
                    h.sliders[e] = slider.clamp(sliders.getDouble(id));
                }
            }
        } finally {
            loading = false;
        }
        if (returned) GuiMemory.save(player, h);
    }

    void releaseAll() {
        closing = true;
        for (int i = 0; i < n; i++) if (held[i]) release(i);
    }

    void finishClose() {
        closing = true;
        if (config.memory()) GuiMemory.save(player, h);
        else returnItems();
    }

    private void returnItems() {
        for (GuiModel.SlotDef def : model.slots()) {
            if (def.isDisplay()) continue;
            ItemStack stack = h.container.removeItemNoUpdate(def.index());
            if (stack.isEmpty()) continue;
            if (player.isAlive() && !player.hasDisconnected()) player.getInventory().placeItemBackInInventory(stack);
            else player.drop(stack, false);
        }
    }

    public void tick() {
        if (h.isClosed() || closing) return;
        ticks++;
        if (ticks % REFRESH_INTERVAL == 0) {
            if (!powerStillActive()) {
                player.closeContainer();
                return;
            }
            evaluateVisibility();
            resolveEntities();
        }
        for (int i = 0; i < n; i++) {
            if (!held[i]) continue;
            GuiElement.Button button = (GuiElement.Button) model.element(i);
            if (!h.isVisible(i) || !passes(button.common().condition()) || !passes(button.pressCondition())) release(i);
            else run(button.whilePressed(), -1);
            if (h.isClosed()) return;
        }
        for (int i = 0; i < n; i++) {
            if (!Double.isNaN(pendingSlider[i]) || pendingRelease[i]) {
                applySlider(i);
                if (h.isClosed()) return;
            }
            if (cooldown[i] > 0) cooldown[i]--;
        }
        if (model.hasProgress()) evaluateProgress();
        flush();
        if (config.memory() && ++sinceSave >= MEMORY_SAFETY_SAVE) {
            sinceSave = 0;
            GuiMemory.save(player, h);
        }
    }

    private boolean powerStillActive() {
        try {
            return GuiPowers.find(player, h.powerId) != null && GuiPowers.isActive(player, h.powerId);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void pressButton(int element) {
        if (h.isClosed() || closing || !(model.element(element) instanceof GuiElement.Button button) || held[element]) return;
        long now = player.level().getGameTime();
        if (lastPress[element] == now || cooldown[element] > 0) return;
        if (!h.isVisible(element) || !passes(button.common().condition()) || !passes(button.pressCondition())) return;
        lastPress[element] = now;
        cooldown[element] = button.cooldown();
        held[element] = true;
        run(button.onPress(), -1);
    }

    public void releaseButton(int element) {
        if (h.isClosed() || element < 0 || element >= n || !held[element]) return;
        release(element);
    }

    private void release(int element) {
        held[element] = false;
        if (model.element(element) instanceof GuiElement.Button button) run(button.onRelease(), -1);
    }

    public void moveSlider(int element, double value, boolean release) {
        if (h.isClosed() || closing || !(model.element(element) instanceof GuiElement.Slider slider)) return;
        if (!Double.isNaN(value) && !Double.isInfinite(value)) pendingSlider[element] = slider.clamp(value);
        if (release) pendingRelease[element] = true;
    }

    private void applySlider(int element) {
        GuiElement.Slider slider = (GuiElement.Slider) model.element(element);
        double value = pendingSlider[element];
        boolean release = pendingRelease[element];
        pendingSlider[element] = Double.NaN;
        pendingRelease[element] = false;
        boolean allowed = h.isVisible(element) && passes(slider.common().condition()) && passes(slider.slideCondition());
        if (!Double.isNaN(value)) {
            if (allowed && value != h.sliders[element]) {
                h.sliders[element] = value;
                run(slider.onSlide(), element);
                if (h.isClosed()) return;
                if (config.memory()) GuiMemory.save(player, h);
            } else if (!allowed) {
                // Snap the client back to the server value.
                sentSliders[element] = Double.NaN;
            }
        }
        if (release && allowed) run(slider.onRelease(), element);
    }

    private boolean passes(Optional<EntityCondition> condition) {
        if (condition.isEmpty()) return true;
        try {
            return condition.get().test(EntityCtx.of(player, player.level()));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void evaluateVisibility() {
        if (!model.hasConditions()) return;
        for (int i = 0; i < n; i++) h.visible.set(i, passes(model.element(i).common().condition()));
    }

    private void evaluateProgress() {
        for (int i = 0; i < n; i++) {
            if (model.element(i) instanceof GuiElement.Image image && image.progress().isPresent()) {
                double v;
                try {
                    v = image.progress().get().eval(player);
                } catch (RuntimeException e) {
                    v = 0.0;
                }
                h.progress[i] = (float) Math.max(0.0, Math.min(1.0, v));
            }
        }
    }

    private void resolveEntities() {
        if (!model.hasServerEntities()) return;
        for (int i = 0; i < n; i++) {
            if (!(model.element(i) instanceof GuiElement.EntityView view) || !view.resolvedOnServer() || !h.isVisible(i)) continue;
            int[] ids;
            try {
                EntitySelector selector = new EntitySelectorParser(new StringReader(view.selector().get()), true).parse();
                List<? extends Entity> found = selector.findEntities(player.createCommandSourceStack().withPermission(2).withSuppressedOutput());
                ids = found.stream().limit(MAX_ENTITY_IDS).mapToInt(Entity::getId).toArray();
            } catch (Exception e) {
                ids = new int[0];
            }
            h.entityIds[i] = ids;
        }
    }

    private void markSent() {
        sentVisible.clear();
        sentVisible.or(h.visible);
        System.arraycopy(h.progress, 0, sentProgress, 0, n);
        System.arraycopy(h.sliders, 0, sentSliders, 0, n);
        for (int i = 0; i < n; i++) sentEntities[i] = h.entityIds[i];
    }

    private void flush() {
        GuiNetwork.Builder b = new GuiNetwork.Builder();
        boolean any = false;
        if (!sentVisible.equals(h.visible)) {
            b.visible(h.visible);
            sentVisible.clear();
            sentVisible.or(h.visible);
            any = true;
        }
        for (int i = 0; i < n; i++) {
            float p = h.progress[i];
            if (Math.abs(p - sentProgress[i]) > 1.0E-4F || (p != sentProgress[i] && (p == 0.0F || p == 1.0F))) {
                b.progress(i, p);
                sentProgress[i] = p;
                any = true;
            }
            if (!Double.isNaN(h.sliders[i]) && Double.compare(h.sliders[i], sentSliders[i]) != 0) {
                b.slider(i, h.sliders[i]);
                sentSliders[i] = h.sliders[i];
                any = true;
            }
            if (!Arrays.equals(h.entityIds[i], sentEntities[i])) {
                b.entities(i, h.entityIds[i]);
                sentEntities[i] = h.entityIds[i];
                any = true;
            }
        }
        if (any) GuiNetwork.sendState(player, h.containerId, b.build());
    }

    private void run(Optional<EntityAction> action, int slider) {
        if (action.isEmpty()) return;
        int previous = h.contextSlider;
        h.contextSlider = slider;
        boolean attributed = PowerCause.push(player, h.powerId);
        try {
            action.get().run(EntityCtx.of(player, player.level()));
        } catch (RuntimeException e) {
            AEA.LOGGER.error("[AEA] An action of aea:gui {} failed", h.powerId, e);
        } finally {
            if (attributed) PowerCause.pop();
            h.contextSlider = previous;
        }
    }
}
