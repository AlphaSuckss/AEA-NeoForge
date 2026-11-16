package dev.alphasucks.aea.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alphasucks.aea.client.AEAClient;
import dev.alphasucks.aea.compat.sync.power.EntityTextureOverlayPower;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skipping a void render needs a cancellable inject; it runs once per drawn player, not per entity tick. */
@OnlyIn(Dist.CLIENT)
@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
        at = @At("HEAD"), cancellable = true)
    private void aea$hideCape(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                              float a, float b, float c, float d, float e, float f, CallbackInfo ci) {
        EntityTextureOverlayPower.Config o = AEAClient.overlayOrNull(player);
        if (o != null && o.hideCape()) ci.cancel();
    }
}
