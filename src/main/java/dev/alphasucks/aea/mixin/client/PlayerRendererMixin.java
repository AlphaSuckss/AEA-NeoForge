package dev.alphasucks.aea.mixin.client;

import dev.alphasucks.aea.client.AEAClient;
import dev.alphasucks.aea.compat.sync.power.EntityTextureOverlayPower;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;",
        at = @At("RETURN"), cancellable = true)
    private void aea$replaceSkin(AbstractClientPlayer player, CallbackInfoReturnable<ResourceLocation> cir) {
        Optional<EntityTextureOverlayPower.Config> o = AEAClient.overlay(player);
        if (o.isEmpty() || o.get().asOverlay()) return;
        o.get().texture(player.getSkin().model() == PlayerSkin.Model.SLIM).ifPresent(cir::setReturnValue);
    }
}
