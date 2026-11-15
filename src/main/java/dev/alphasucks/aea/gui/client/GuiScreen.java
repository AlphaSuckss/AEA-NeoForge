package dev.alphasucks.aea.gui.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.gui.GuiConfig;
import dev.alphasucks.aea.gui.GuiElement;
import dev.alphasucks.aea.gui.GuiMenu;
import dev.alphasucks.aea.gui.GuiModel;
import dev.alphasucks.aea.gui.GuiNetwork;
import dev.alphasucks.aea.gui.GuiPlaceholders;
import dev.alphasucks.aea.gui.GuiSlot;
import dev.alphasucks.aea.gui.mixin.SlotAccessor;
import dev.overgrown.apoli.client.ClientEntitySets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Client screen of an {@code aea:gui}. */
public final class GuiScreen extends AbstractContainerScreen<GuiMenu> {
    private static final ResourceLocation PANEL = ResourceLocation.withDefaultNamespace("textures/gui/demo_background.png");
    private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation BUTTON_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/button_highlighted");
    private static final ResourceLocation SLIDER = ResourceLocation.withDefaultNamespace("widget/slider");
    private static final ResourceLocation SLIDER_HANDLE = ResourceLocation.withDefaultNamespace("widget/slider_handle");
    private static final ResourceLocation SLIDER_HANDLE_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/slider_handle_highlighted");

    private final GuiMenu gui;
    private final GuiConfig config;
    private final GuiModel model;
    private GuiLayout layout;
    private final boolean[] dynamicText;
    private final LivingEntity[] dummies;
    private final LivingEntity[] setMembers;
    private boolean dummiesBuilt;
    private int ticks;
    private int heldButton = -1;
    private int draggingSlider = -1;
    private double dragValue;
    private double lastSentValue = Double.NaN;
    private static boolean entityErrorLogged;

    public GuiScreen(GuiMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.gui = menu;
        this.config = menu.config;
        this.model = menu.model;
        int n = model == null ? 0 : model.elementCount();
        this.dynamicText = new boolean[n];
        this.dummies = new LivingEntity[n];
        this.setMembers = new LivingEntity[n];
        for (int i = 0; i < n; i++) {
            GuiElement e = model.element(i);
            if (e instanceof GuiElement.Text text) dynamicText[i] = GuiPlaceholders.has(text.text());
            else if (e instanceof GuiElement.Button button) dynamicText[i] = button.text().isPresent() && GuiPlaceholders.has(button.text().get());
            else if (e instanceof GuiElement.Slider slider) dynamicText[i] = slider.text().isPresent() && GuiPlaceholders.has(slider.text().get());
        }
    }

    @Override
    protected void init() {
        if (config == null || model == null) {
            imageWidth = 0;
            imageHeight = 0;
            super.init();
            return;
        }
        Window window = minecraft.getWindow();
        double current = window.getGuiScale();
        layout = GuiLayout.compute(config,
            e -> e instanceof GuiElement.Text t ? font.width(resolve(t.text(), -1)) : 0,
            lock -> (float) (window.calculateScale(lock, false) / current));
        imageWidth = layout.width;
        imageHeight = layout.height;
        super.init();
        placeSlots();
        if (!dummiesBuilt) {
            dummiesBuilt = true;
            buildDummies();
        }
    }

    private void placeSlots() {
        for (GuiModel.SlotDef def : model.slots()) {
            GuiSlot slot = gui.guiSlot(def.index());
            if (slot == null) continue;
            GuiLayout.Box box = layout.boxes[def.element()];
            float lx = box.x + def.localX();
            float ly = box.y + def.localY();
            move(slot, Math.round(box.px + (lx - box.px) * box.scale), Math.round(box.py + (ly - box.py) * box.scale));
            slot.drawScale = box.scale;
        }
        if (gui.playerSlotStart >= 0) {
            for (int i = 0; i < 36; i++) {
                Slot slot = gui.slots.get(gui.playerSlotStart + i);
                int row = i / 9;
                int col = i % 9;
                int y = row < 3 ? layout.inventoryY + 1 + row * 18 : layout.inventoryY + 59;
                move(slot, layout.inventoryX + 1 + col * 18, y);
            }
        }
    }

    private static void move(Slot slot, int x, int y) {
        ((SlotAccessor) slot).aea$setX(x);
        ((SlotAccessor) slot).aea$setY(y);
    }

    private void buildDummies() {
        ClientLevel level = minecraft.level;
        if (level == null) return;
        for (int i = 0; i < model.elementCount(); i++) {
            if (!(model.element(i) instanceof GuiElement.EntityView view) || view.entityType().isEmpty()) continue;
            try {
                ResourceLocation type = view.entityType().get();
                Entity entity;
                if (view.nbt().isPresent()) {
                    CompoundTag tag = view.nbt().get().tag().copy();
                    tag.putString("id", type.toString());
                    entity = EntityType.create(tag, level).orElse(null);
                } else {
                    entity = BuiltInRegistries.ENTITY_TYPE.get(type).create(level);
                }
                if (entity instanceof LivingEntity living) {
                    for (GuiElement.Equipment eq : view.equipment()) living.setItemSlot(eq.slot(), eq.item().copy());
                    dummies[i] = living;
                }
            } catch (RuntimeException e) {
                dummies[i] = null;
            }
        }
    }

    private boolean shown(int i) {
        return gui.isVisible(i);
    }

    private double sliderValue(int i) {
        return i == draggingSlider ? dragValue : gui.sliders[i];
    }

    private Function<String, String> resolver(int slider) {
        return GuiPlaceholders.resolver(gui, minecraft.player, slider, this::sliderValue);
    }

    private Component resolve(Component text, int slider) {
        return GuiPlaceholders.has(text) ? GuiPlaceholders.apply(text, resolver(slider)) : text;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 1.20.1 AEA only dimmed the world behind GUIs that use the vanilla background.
        if (config != null && config.showVanillaBackground()) super.renderBackground(g, mouseX, mouseY, partialTick);
        else renderBg(g, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        if (layout == null) return;
        if (config.showVanillaBackground()) {
            RenderSystem.enableBlend();
            GuiDraw.nineSliced(g, PANEL, leftPos, topPos, Math.max(8, layout.width), Math.max(8, layout.height), 4, 4, 4, 4, 248, 166, 0, 0);
            RenderSystem.disableBlend();
        }
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(leftPos, topPos, 0.0F);
        double mx = mouseX - leftPos;
        double my = mouseY - topPos;
        for (int i = 0; i < model.elementCount(); i++) {
            if (!shown(i)) continue;
            GuiElement e = model.element(i);
            if (e instanceof GuiElement.Slot || e instanceof GuiElement.SlotGrid) {
                drawSlotFrames(g, i);
                continue;
            }
            GuiLayout.Box box = layout.boxes[i];
            if (box.scaled()) {
                pose.pushPose();
                pose.translate(box.px, box.py, 0.0F);
                pose.scale(box.scale, box.scale, 1.0F);
                pose.translate(-box.px, -box.py, 0.0F);
            }
            try {
                drawElement(g, i, e, box, mx, my);
            } finally {
                if (box.scaled()) pose.popPose();
            }
        }
        if (gui.playerSlotStart >= 0) {
            for (int i = 0; i < 36; i++) {
                Slot slot = gui.slots.get(gui.playerSlotStart + i);
                GuiDraw.slotFrame(g, slot.x, slot.y, 1.0F);
            }
        }
        pose.popPose();
    }

    private void drawSlotFrames(GuiGraphics g, int element) {
        int first = model.firstSlotOf(element);
        int count = model.slotCountOf(element);
        for (int s = first; s < first + count; s++) {
            GuiSlot slot = gui.guiSlot(s);
            if (slot != null && slot.def.background()) GuiDraw.slotFrame(g, slot.x, slot.y, slot.drawScale);
        }
    }

    private void drawElement(GuiGraphics g, int i, GuiElement element, GuiLayout.Box box, double mx, double my) {
        if (element instanceof GuiElement.Image image) {
            float progress = image.progress().isPresent() ? gui.progress[i] : 1.0F;
            if (image.progress().isPresent() && image.invert()) progress = 1.0F - progress;
            GuiDraw.image(g, image.texture(), box.x, box.y, box.w, box.h, image.u(), image.v(), image.texWidth(), image.texHeight(),
                progress, image.direction() == GuiElement.ProgressDirection.VERTICAL);
        } else if (element instanceof GuiElement.Text text) {
            Component c = dynamicText[i] ? resolve(text.text(), -1) : text.text();
            int width = font.width(c);
            int x = text.centered() ? box.x + (box.w - width) / 2 : box.x;
            int y = box.y + Math.max(0, (box.h - 8) / 2);
            g.drawString(font, c, x, y, text.color(), text.shadow());
        } else if (element instanceof GuiElement.Button button) {
            boolean pressed = heldButton == i;
            boolean hovered = box.contains(mx, my);
            ResourceLocation texture = button.texture().orElse(null);
            if (pressed && button.pressedTexture().isPresent()) texture = button.pressedTexture().get();
            else if ((hovered || pressed) && button.hoverTexture().isPresent()) texture = button.hoverTexture().get();
            if (texture != null) GuiDraw.stretched(g, texture, box.x, box.y, box.w, box.h);
            else GuiDraw.sprite(g, hovered || pressed ? BUTTON_HIGHLIGHTED : BUTTON, box.x, box.y, box.w, box.h);
            if (button.text().isPresent()) {
                Component c = dynamicText[i] ? resolve(button.text().get(), -1) : button.text().get();
                g.drawCenteredString(font, c, box.x + box.w / 2, box.y + (box.h - 8) / 2 + (pressed ? 1 : 0), button.textColor());
            }
        } else if (element instanceof GuiElement.Slider slider) {
            double value = sliderValue(i);
            double frac = Math.max(0.0, Math.min(1.0, (value - slider.min()) / (slider.max() - slider.min())));
            boolean hot = draggingSlider == i || box.contains(mx, my);
            int handle = Math.min(slider.handleWidth(), box.w);
            int hx = box.x + (int) (frac * (box.w - handle));
            if (slider.backgroundTexture().isPresent()) GuiDraw.stretched(g, slider.backgroundTexture().get(), box.x, box.y, box.w, box.h);
            else GuiDraw.sprite(g, SLIDER, box.x, box.y, box.w, box.h);
            if (slider.handleTexture().isPresent()) GuiDraw.stretched(g, slider.handleTexture().get(), hx, box.y, handle, box.h);
            else GuiDraw.sprite(g, hot ? SLIDER_HANDLE_HIGHLIGHTED : SLIDER_HANDLE, hx, box.y, handle, box.h);
            if (slider.text().isPresent()) {
                Component c = dynamicText[i] ? resolve(slider.text().get(), i) : slider.text().get();
                g.drawCenteredString(font, c, box.x + box.w / 2, box.y + (box.h - 8) / 2, slider.textColor());
            }
        } else if (element instanceof GuiElement.EntityView view) {
            LivingEntity entity = entityFor(i, view);
            if (entity == null) return;
            EntityDimensions dims = entity.getType().getDimensions();
            float baseH = Math.max(dims.height(), 0.3F);
            float baseW = Math.max(dims.width(), 0.3F);
            float size = Math.min(box.h / baseH, box.w / baseW) * 0.85F * view.scale();
            if (view.showScale()) {
                float actual = entity.getBbWidth() / baseW;
                if (actual > 0.0F && Float.isFinite(actual)) size *= Math.max(0.01F, Math.min(100.0F, actual)) / view.fitScale();
            }
            int scale = (int) Math.max(1.0F, size);
            int cx = box.x + box.w / 2;
            int cy = box.y + box.h - 2;
            float lookX = 0.0F;
            float lookY = 0.0F;
            if (view.followMouse()) {
                lookX = (float) (cx - box.ux(mx));
                lookY = (float) ((cy - baseH * 0.92F * scale) - box.uy(my));
            }
            try {
                drawEntity(g, cx, cy, scale, lookX, lookY, entity);
            } catch (RuntimeException ex) {
                if (!entityErrorLogged) {
                    entityErrorLogged = true;
                    AEA.LOGGER.warn("[AEA] aea:gui could not draw {} in an aea:entity element; it is skipped", entity, ex);
                }
            }
        }
    }

    /** 1.20.1's {@code InventoryScreen.drawEntity(context, x, y, size, mouseX, mouseY, entity)}. */
    private static void drawEntity(GuiGraphics g, int x, int y, int size, float mouseX, float mouseY, LivingEntity entity) {
        float f = (float) Math.atan(mouseX / 40.0F);
        float h = (float) Math.atan(mouseY / 40.0F);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(h * 20.0F * ((float) Math.PI / 180.0F));
        pose.mul(camera);
        float bodyRot = entity.yBodyRot;
        float yRot = entity.getYRot();
        float xRot = entity.getXRot();
        float headRotO = entity.yHeadRotO;
        float headRot = entity.yHeadRot;
        entity.yBodyRot = 180.0F + f * 20.0F;
        entity.setYRot(180.0F + f * 40.0F);
        entity.setXRot(-h * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        try {
            InventoryScreen.renderEntityInInventory(g, x, y, size, new Vector3f(), pose, camera, entity);
        } finally {
            entity.yBodyRot = bodyRot;
            entity.setYRot(yRot);
            entity.setXRot(xRot);
            entity.yHeadRotO = headRotO;
            entity.yHeadRot = headRot;
        }
    }

    private LivingEntity entityFor(int i, GuiElement.EntityView view) {
        if (view.entityType().isPresent()) return dummies[i];
        if (view.isSelf()) return minecraft.player;
        if (view.set().isPresent()) return setMembers[i];
        if (minecraft.level == null) return null;
        for (int id : gui.entityIds[i]) {
            if (minecraft.level.getEntity(id) instanceof LivingEntity living) return living;
        }
        return null;
    }

    private void refreshSets() {
        ClientLevel level = minecraft.level;
        if (level == null) return;
        Map<UUID, LivingEntity> byUuid = null;
        for (int i = 0; i < model.elementCount(); i++) {
            if (!(model.element(i) instanceof GuiElement.EntityView view) || view.set().isEmpty()) continue;
            List<UUID> members = ClientEntitySets.members(view.set().get());
            setMembers[i] = null;
            if (members.isEmpty()) continue;
            if (byUuid == null) {
                byUuid = new HashMap<>();
                Set<UUID> wanted = new HashSet<>();
                for (int j = 0; j < model.elementCount(); j++) {
                    if (model.element(j) instanceof GuiElement.EntityView v && v.set().isPresent()) wanted.addAll(ClientEntitySets.members(v.set().get()));
                }
                for (Entity e : level.entitiesForRendering()) {
                    if (e instanceof LivingEntity living && wanted.contains(e.getUUID())) byUuid.put(e.getUUID(), living);
                }
            }
            for (UUID uuid : members) {
                LivingEntity living = byUuid.get(uuid);
                if (living != null) {
                    setMembers[i] = living;
                    break;
                }
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (config == null) return;
        boolean vanilla = config.showVanillaBackground();
        if (config.title().isPresent()) g.drawString(font, title, 8, 6, vanilla ? 0x404040 : 0xFFFFFF, !vanilla);
        if (vanilla && gui.playerSlotStart >= 0) {
            g.drawString(font, playerInventoryTitle, layout.inventoryX + 1, layout.inventoryY - 11, 0x404040, false);
        }
    }

    @Override
    protected void renderSlot(GuiGraphics g, Slot slot) {
        float scale = slot instanceof GuiSlot gs ? gs.drawScale : 1.0F;
        if (scale == 1.0F) {
            super.renderSlot(g, slot);
            return;
        }
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(slot.x, slot.y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        pose.translate(-slot.x, -slot.y, 0.0F);
        try {
            super.renderSlot(g, slot);
        } finally {
            pose.popPose();
        }
    }

    @Override
    protected void renderSlotHighlight(GuiGraphics g, Slot slot, int mouseX, int mouseY, float partialTick) {
        float scale = slot instanceof GuiSlot gs ? gs.drawScale : 1.0F;
        if (scale == 1.0F) {
            super.renderSlotHighlight(g, slot, mouseX, mouseY, partialTick);
            return;
        }
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(slot.x, slot.y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        pose.translate(-slot.x, -slot.y, 0.0F);
        try {
            super.renderSlotHighlight(g, slot, mouseX, mouseY, partialTick);
        } finally {
            pose.popPose();
        }
    }

    private int interactiveAt(double mouseX, double mouseY) {
        if (layout == null) return -1;
        double mx = mouseX - leftPos;
        double my = mouseY - topPos;
        for (int i = model.elementCount() - 1; i >= 0; i--) {
            GuiElement e = model.element(i);
            if ((e instanceof GuiElement.Button || e instanceof GuiElement.Slider) && shown(i) && layout.boxes[i].contains(mx, my)) return i;
        }
        return -1;
    }

    private double sliderAt(int i, double mouseX) {
        GuiElement.Slider slider = (GuiElement.Slider) model.element(i);
        GuiLayout.Box box = layout.boxes[i];
        int handle = Math.min(slider.handleWidth(), box.w);
        double pos = box.ux(mouseX - leftPos) - (box.x + handle / 2.0);
        double frac = box.w - handle <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, pos / (box.w - handle)));
        return slider.clamp(slider.min() + frac * (slider.max() - slider.min()));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && model != null) {
            int i = interactiveAt(mouseX, mouseY);
            if (i >= 0) {
                if (model.element(i) instanceof GuiElement.Button) {
                    heldButton = i;
                    sendButton(i, true);
                } else {
                    draggingSlider = i;
                    dragValue = sliderAt(i, mouseX);
                    lastSentValue = Double.NaN;
                }
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingSlider >= 0) {
            dragValue = sliderAt(draggingSlider, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && heldButton >= 0) {
            sendButton(heldButton, false);
            heldButton = -1;
            return true;
        }
        if (button == 0 && draggingSlider >= 0) {
            int i = draggingSlider;
            dragValue = sliderAt(i, mouseX);
            sendSlider(i, dragValue, true);
            gui.sliders[i] = dragValue;
            draggingSlider = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (gui.broken) {
            onClose();
            return;
        }
        ticks++;
        if (draggingSlider >= 0) {
            if (!shown(draggingSlider)) {
                draggingSlider = -1;
            } else if (Double.compare(dragValue, lastSentValue) != 0) {
                sendSlider(draggingSlider, dragValue, false);
                lastSentValue = dragValue;
            }
        }
        if (heldButton >= 0 && !shown(heldButton)) heldButton = -1;
        if (ticks % 10 == 1 && model != null) refreshSets();
    }

    @Override
    public boolean isPauseScreen() {
        return config != null && config.pausesGame();
    }

    private void sendButton(int element, boolean pressed) {
        PacketDistributor.sendToServer(new GuiNetwork.ButtonPayload(gui.containerId, element, pressed));
    }

    private void sendSlider(int element, double value, boolean release) {
        PacketDistributor.sendToServer(new GuiNetwork.SliderPayload(gui.containerId, element, value, release));
    }
}
