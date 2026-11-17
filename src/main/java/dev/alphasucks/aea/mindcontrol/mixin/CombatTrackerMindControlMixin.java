package dev.alphasucks.aea.mindcontrol.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.alphasucks.aea.mindcontrol.MindControl;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** "X was slain by Y (controlled by Z)" when Y was mind controlled with {@code kill_credit} on. Death only, so cold. */
@Mixin(CombatTracker.class)
public abstract class CombatTrackerMindControlMixin {
    @Shadow @Final private LivingEntity mob;

    @ModifyReturnValue(method = "getDeathMessage", at = @At("RETURN"))
    private Component aea$controlledBy(Component original) {
        LivingEntity killer = mob.getKillCredit();
        ServerPlayer controller = MindControl.creditedController(killer);
        if (controller == null || controller == killer) return original;
        return original.copy().append(Component.translatable("death.aea.mind_control.controlled_by", controller.getDisplayName()));
    }
}
