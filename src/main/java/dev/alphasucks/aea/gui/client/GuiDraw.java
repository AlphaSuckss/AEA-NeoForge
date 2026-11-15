package dev.alphasucks.aea.gui.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Drawing helpers for the GUI screen (1.20.1 look, drawn with 1.21 calls). */
final class GuiDraw {
    private GuiDraw() {}

    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_FILL = 0xFF8B8B8B;

    /** A vanilla-style 18x18 slot frame around the 16x16 slot at (x, y). */
    static void slotFrame(GuiGraphics g, int x, int y, float scale) {
        PoseStack pose = g.pose();
        boolean scaled = scale != 1.0F;
        if (scaled) {
            pose.pushPose();
            pose.translate(x, y, 0.0F);
            pose.scale(scale, scale, 1.0F);
            pose.translate(-x, -y, 0.0F);
        }
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_FILL);
        g.fill(x - 1, y - 1, x + 16, y, SLOT_DARK);
        g.fill(x - 1, y, x, y + 16, SLOT_DARK);
        g.fill(x, y + 16, x + 17, y + 17, SLOT_LIGHT);
        g.fill(x + 16, y, x + 17, y + 16, SLOT_LIGHT);
        if (scaled) pose.popPose();
    }

    /** The whole texture stretched over the box. */
    static void stretched(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(texture, x, y, w, h, 0.0F, 0.0F, w, h, w, h);
        RenderSystem.disableBlend();
    }

    /** A GUI sprite (nine-sliced by its own metadata), e.g. the vanilla button and slider sprites. */
    static void sprite(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        g.blitSprite(sprite, x, y, w, h);
        RenderSystem.disableBlend();
    }

    /** An image cut to {@code progress} (0..1) from the left, or from the bottom when vertical. */
    static void image(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h, int u, int v, int texW, int texH, float progress, boolean vertical) {
        float p = Math.max(0.0F, Math.min(1.0F, progress));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (!vertical) {
            int dw = Math.round(w * p);
            if (dw > 0) g.blit(texture, x, y, dw, h, (float) u, (float) v, dw, h, texW, texH);
        } else {
            int dh = Math.round(h * p);
            if (dh > 0) g.blit(texture, x, y + h - dh, w, dh, (float) u, (float) (v + h - dh), w, dh, texW, texH);
        }
        RenderSystem.disableBlend();
    }

    /** 1.20.1's {@code GuiGraphics.blitNineSliced} (removed in 1.20.2) for 256x256 textures. */
    static void nineSliced(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int cornerW, int cornerH,
                           int edgeW, int edgeH, int srcW, int srcH, int srcX, int srcY) {
        cornerW = Math.min(cornerW, w / 2);
        edgeW = Math.min(edgeW, w / 2);
        cornerH = Math.min(cornerH, h / 2);
        edgeH = Math.min(edgeH, h / 2);
        if (w == srcW && h == srcH) {
            g.blit(tex, x, y, srcX, srcY, w, h);
        } else if (h == srcH) {
            g.blit(tex, x, y, srcX, srcY, cornerW, h);
            repeating(g, tex, x + cornerW, y, w - edgeW - cornerW, h, srcX + cornerW, srcY, srcW - edgeW - cornerW, srcH);
            g.blit(tex, x + w - edgeW, y, srcX + srcW - edgeW, srcY, edgeW, h);
        } else if (w == srcW) {
            g.blit(tex, x, y, srcX, srcY, w, cornerH);
            repeating(g, tex, x, y + cornerH, w, h - edgeH - cornerH, srcX, srcY + cornerH, srcW, srcH - edgeH - cornerH);
            g.blit(tex, x, y + h - edgeH, srcX, srcY + srcH - edgeH, w, edgeH);
        } else {
            g.blit(tex, x, y, srcX, srcY, cornerW, cornerH);
            repeating(g, tex, x + cornerW, y, w - edgeW - cornerW, cornerH, srcX + cornerW, srcY, srcW - edgeW - cornerW, cornerH);
            g.blit(tex, x + w - edgeW, y, srcX + srcW - edgeW, srcY, edgeW, cornerH);
            g.blit(tex, x, y + h - edgeH, srcX, srcY + srcH - edgeH, cornerW, edgeH);
            repeating(g, tex, x + cornerW, y + h - edgeH, w - edgeW - cornerW, edgeH, srcX + cornerW, srcY + srcH - edgeH, srcW - edgeW - cornerW, edgeH);
            g.blit(tex, x + w - edgeW, y + h - edgeH, srcX + srcW - edgeW, srcY + srcH - edgeH, edgeW, edgeH);
            repeating(g, tex, x, y + cornerH, cornerW, h - edgeH - cornerH, srcX, srcY + cornerH, cornerW, srcH - edgeH - cornerH);
            repeating(g, tex, x + cornerW, y + cornerH, w - edgeW - cornerW, h - edgeH - cornerH, srcX + cornerW, srcY + cornerH, srcW - edgeW - cornerW, srcH - edgeH - cornerH);
            repeating(g, tex, x + w - edgeW, y + cornerH, cornerW, h - edgeH - cornerH, srcX + srcW - edgeW, srcY + cornerH, edgeW, srcH - edgeH - cornerH);
        }
    }

    private static void repeating(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int srcX, int srcY, int srcW, int srcH) {
        if (w <= 0 || h <= 0 || srcW <= 0 || srcH <= 0) return;
        int cx = x;
        for (int sw : slices(w, srcW)) {
            int offX = (srcW - sw) / 2;
            int cy = y;
            for (int sh : slices(h, srcH)) {
                int offY = (srcH - sh) / 2;
                g.blit(tex, cx, cy, srcX + offX, srcY + offY, sw, sh);
                cy += sh;
            }
            cx += sw;
        }
    }

    /** Splits {@code target} into ceil(target / max) near-equal parts (1.20.1's Divisor). */
    private static int[] slices(int target, int max) {
        int parts = (target + max - 1) / max;
        int[] out = new int[parts];
        int quotient = target / parts;
        int remainder = target % parts;
        int mod = 0;
        for (int i = 0; i < parts; i++) {
            int v = quotient;
            mod += remainder;
            if (mod >= parts) {
                mod -= parts;
                v++;
            }
            out[i] = v;
        }
        return out;
    }
}
