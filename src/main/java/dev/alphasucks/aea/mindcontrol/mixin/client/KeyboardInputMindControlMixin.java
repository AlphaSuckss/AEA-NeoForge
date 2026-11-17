package dev.alphasucks.aea.mindcontrol.mixin.client;

import dev.alphasucks.aea.mindcontrol.client.MindControlKeys;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While controlling, your keys drive the victim, not your spectating self (and sneak must not stop spectating). */
@OnlyIn(Dist.CLIENT)
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMindControlMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void aea$stillWhileControlling(boolean slow, float slowFactor, CallbackInfo ci) {
        if (!MindControlKeys.controlling()) return;
        up = down = left = right = jumping = shiftKeyDown = false;
        forwardImpulse = leftImpulse = 0;
    }
}
