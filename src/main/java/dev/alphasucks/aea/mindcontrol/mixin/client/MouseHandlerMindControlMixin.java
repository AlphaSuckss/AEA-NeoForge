package dev.alphasucks.aea.mindcontrol.mixin.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.alphasucks.aea.mindcontrol.client.MindControlKeys;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMindControlMixin {
    /** A controlled player's mouse doesn't turn them when the controller has their look. */
    @WrapWithCondition(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private boolean aea$keepLook(LocalPlayer player, double yaw, double pitch) {
        return MindControlKeys.mayTurn();
    }

    /** The wheel picks the victim's hotbar slot while controlling, and does nothing for a victim whose hotbar is taken. */
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void aea$scroll(long window, double x, double y, CallbackInfo ci) {
        if (MindControlKeys.scroll(y)) ci.cancel();
    }
}
