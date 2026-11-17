package dev.alphasucks.aea.compat.origins.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.alphasucks.aea.AEAClientConfig;
import dev.overgrown.apoli.client.IconRenderer;
import dev.overgrown.origins.client.screen.ChooseOriginScreen;
import dev.overgrown.origins.client.screen.OriginDisplayScreen;
import dev.overgrown.origins.origin.Origin;
import dev.overgrown.origins.origin.OriginLayer;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * The origin grid, ported from Alternate Origin GUI 1.1.5 by UltrusBot (MIT, see {@code LICENSE_altorigingui}).
 * The choose-origin screen shows every origin of the layer as an icon in a grid of 7 by 5 per page, with the origin's
 * window to its right, instead of stepping through them one at a time with arrows.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(ChooseOriginScreen.class)
public abstract class ChooseOriginScreenMixin extends OriginDisplayScreen {
    @Unique private static final ResourceLocation AEA$CHOICES = ResourceLocation.fromNamespaceAndPath("aea", "textures/gui/origin_choices.png");
    @Unique private static final int AEA$WIDTH = 219, AEA$HEIGHT = 182, AEA$GAP = 10, AEA$ICON = 26;
    @Unique private static final int AEA$COLUMNS = 7, AEA$PER_PAGE = 35;

    @Shadow @Final private ArrayList<OriginLayer> layerList;
    @Shadow @Final private int currentLayerIndex;
    @Shadow @Final private List<Origin> originSelection;
    @Shadow private int currentOrigin;
    @Shadow private int maxSelection;
    @Shadow private Origin randomOrigin;

    @Shadow private Origin getCurrentOriginInternal() { throw new AssertionError(); }

    @Unique private int aea$left, aea$top, aea$page, aea$pages;

    private ChooseOriginScreenMixin(Component title, boolean showDirtBackground) {
        super(title, showDirtBackground);
    }

    /** The grid needs room for itself and the origin window side by side; on a smaller screen the arrows stay. */
    @Unique
    private boolean aea$grid() {
        return AEAClientConfig.originGrid() && this.width >= AEA$WIDTH + AEA$GAP + windowWidth && this.height >= AEA$HEIGHT + 30;
    }

    @WrapOperation(method = "init", at = @At(value = "INVOKE", ordinal = 0,
        target = "Ldev/overgrown/origins/client/screen/ChooseOriginScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;"))
    private GuiEventListener aea$dropPreviousArrow(ChooseOriginScreen screen, GuiEventListener widget, Operation<GuiEventListener> original) {
        return aea$grid() ? widget : original.call(screen, widget);
    }

    @WrapOperation(method = "init", at = @At(value = "INVOKE", ordinal = 1,
        target = "Ldev/overgrown/origins/client/screen/ChooseOriginScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;"))
    private GuiEventListener aea$dropNextArrow(ChooseOriginScreen screen, GuiEventListener widget, Operation<GuiEventListener> original) {
        return aea$grid() ? widget : original.call(screen, widget);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void aea$addGrid(CallbackInfo ci) {
        if (!aea$grid()) return;
        aea$top = (this.height - AEA$HEIGHT) / 2;
        aea$left = (this.width - (AEA$WIDTH + AEA$GAP + windowWidth)) / 2;
        this.guiLeft = aea$left + AEA$WIDTH + AEA$GAP;
        this.guiTop = aea$top;

        for (GuiEventListener child : children()) {
            if (child instanceof Button button && button.getMessage().getContents() instanceof TranslatableContents t
                && t.getKey().equals("origins.gui.select")) {
                button.setPosition(guiLeft + windowWidth / 2 - 50, guiTop + windowHeight + 5);
            }
        }

        aea$pages = Math.max(1, (maxSelection + AEA$PER_PAGE - 1) / AEA$PER_PAGE);
        if (aea$page >= aea$pages) aea$page = 0;
        for (int i = 0; i < Math.min(maxSelection, AEA$PER_PAGE); i++) {
            final int slot = i;
            addRenderableWidget(Button.builder(Component.empty(), b -> aea$select(slot))
                .bounds(aea$slotX(i), aea$slotY(i), AEA$ICON, AEA$ICON).build());
        }
        if (maxSelection > AEA$PER_PAGE) {
            int y = aea$top + AEA$HEIGHT + 5;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> aea$page = (aea$page - 1 + aea$pages) % aea$pages)
                .bounds(aea$left, y, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> aea$page = (aea$page + 1) % aea$pages)
                .bounds(aea$left + AEA$WIDTH - 20, y, 20, 20).build());
        }
        addRenderableOnly(this::aea$renderGrid);
    }

    @Unique
    private int aea$slotX(int slot) {
        return aea$left + 12 + (slot % AEA$COLUMNS) * 28;
    }

    @Unique
    private int aea$slotY(int slot) {
        return aea$top + 10 + (slot / AEA$COLUMNS) * 30;
    }

    @Unique
    private void aea$select(int slot) {
        int index = slot + aea$page * AEA$PER_PAGE;
        if (index >= maxSelection) return;
        currentOrigin = index;
        Origin origin = getCurrentOriginInternal();
        showOrigin(origin, layerList.get(currentLayerIndex), origin == randomOrigin);
    }

    @Unique
    private void aea$renderGrid(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(AEA$CHOICES, aea$left, aea$top, 0, 0, AEA$WIDTH, AEA$HEIGHT);
        int first = aea$page * AEA$PER_PAGE, end = Math.min(first + AEA$PER_PAGE, maxSelection);
        for (int index = first; index < end; index++) {
            int x = aea$slotX(index - first), y = aea$slotY(index - first);
            boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + AEA$ICON && mouseY < y + AEA$ICON;
            boolean lit = hovered || getFocused() instanceof Button b && b.getX() == x && b.getY() == y;
            int frame = (index == currentOrigin ? AEA$ICON : 0) + (lit ? 2 * AEA$ICON : 0);
            graphics.blit(AEA$CHOICES, x, y, 230, frame, AEA$ICON, AEA$ICON);
            if (index >= originSelection.size()) {
                // The random origin: a question mark and a dot that cycles through the impact colours.
                graphics.blit(AEA$CHOICES, x + 6, y + 5, 243, 120, 13, 16);
                int cycle = (int) (Util.getMillis() / 750L % 4L);
                graphics.blit(AEA$CHOICES, x, y, 224 + cycle * 8, lit ? 112 : 104, 8, 8);
                continue;
            }
            Origin origin = originSelection.get(index);
            int level = origin.impact().level();
            if (level >= 0 && level <= 3) graphics.blit(AEA$CHOICES, x, y, 224 + level * 8, lit ? 112 : 104, 8, 8);
            else graphics.blit(AEA$CHOICES, x, y, level == 4 ? 248 : 240, lit ? 144 : 136, 8, 8);
            IconRenderer.render(graphics, origin.icon(), x + 5, y + 5);
            if (hovered) {
                setTooltipForNextRenderPass(layerList.get(currentLayerIndex).name().copy().append(": ").append(origin.name()));
            }
        }
        if (aea$pages > 1) {
            graphics.drawCenteredString(this.font, (aea$page + 1) + "/" + aea$pages, aea$left + AEA$WIDTH / 2,
                aea$top + AEA$HEIGHT + 5 + (20 - this.font.lineHeight) / 2 + 1, 0xFFFFFF);
        }
    }
}
