package dev.alphasucks.aea.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.alphasucks.aea.client.AEAClient;
import dev.alphasucks.aea.compat.sync.power.EntityTextureOverlayPower;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@OnlyIn(Dist.CLIENT)
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @ModifyReturnValue(method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;",
        at = @At("RETURN"))
    private ResourceLocation aea$replaceSkin(ResourceLocation original, @Local(argsOnly = true) AbstractClientPlayer player) {
        EntityTextureOverlayPower.Config o = AEAClient.overlayOrNull(player);
        if (o == null || o.asOverlay()) return original;
        return o.texture(player.getSkin().model() == PlayerSkin.Model.SLIM).orElse(original);
    }
}
