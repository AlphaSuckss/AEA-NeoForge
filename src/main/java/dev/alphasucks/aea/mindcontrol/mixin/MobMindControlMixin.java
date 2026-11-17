package dev.alphasucks.aea.mindcontrol.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.alphasucks.aea.mindcontrol.MindControlled;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * While a player controls a mob, its AI step does nothing: no senses, goals, pathing, brain or move/look/jump control.
 * {@code MindControl} sets its movement instead. Each call is skipped on its own, so nothing is allocated per tick.
 */
@Mixin(Mob.class)
public abstract class MobMindControlMixin implements MindControlled {
    @Unique private boolean aea$mindControlled;

    @Override
    public boolean aea$mindControlled() {
        return aea$mindControlled;
    }

    @Override
    public void aea$setMindControlled(boolean controlled) {
        aea$mindControlled = controlled;
    }

    @WrapWithCondition(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/sensing/Sensing;tick()V"))
    private boolean aea$sensing(Sensing sensing) {
        return !aea$mindControlled;
    }

    @WrapWithCondition(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tickRunningGoals(Z)V"))
    private boolean aea$runningGoals(GoalSelector selector, boolean everyTick) {
        return !aea$mindControlled;
    }

    @WrapWithCondition(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V"))
    private boolean aea$goals(GoalSelector selector) {
        return !aea$mindControlled;
    }

    @WrapWithCondition(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;tick()V"))
    private boolean aea$navigation(PathNavigation navigation) {
        return !aea$mindControlled;
    }

    @WrapWithCondition(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;customServerAiStep()V"))
    private boolean aea$customAi(Mob mob) {
        return !aea$mindControlled;
    }

    @WrapWithCondition(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/control/MoveControl;tick()V"))
    private boolean aea$move(MoveControl control) {
        return !aea$mindControlled;
    }

    @WrapWithCondition(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/control/LookControl;tick()V"))
    private boolean aea$look(LookControl control) {
        return !aea$mindControlled;
    }

    @WrapWithCondition(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/control/JumpControl;tick()V"))
    private boolean aea$jump(JumpControl control) {
        return !aea$mindControlled;
    }
}
