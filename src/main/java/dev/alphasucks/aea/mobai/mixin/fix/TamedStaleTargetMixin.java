package dev.alphasucks.aea.mobai.mixin.fix;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** AEA fix: tamed mobs drop a dead target / anger at a dead or missing entity instead of chasing it forever. */
@Mixin(Mob.class)
public abstract class TamedStaleTargetMixin {
    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void aea$dropStaleTarget(CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        if (!(mob instanceof TamableAnimal tame) || !tame.isTame() || !(mob.level() instanceof ServerLevel level)) return;
        boolean changed = false;
        LivingEntity target = mob.getTarget();
        if (target != null && !target.isAlive()) {
            mob.setTarget(null);
            changed = true;
        }
        if (mob instanceof NeutralMob angry && angry.getRemainingPersistentAngerTime() > 0) {
            UUID at = angry.getPersistentAngerTarget();
            if (at != null) {
                Entity e = level.getEntity(at);
                if (e == null || !e.isAlive()) {
                    angry.stopBeingAngry();
                    changed = true;
                }
            } else if (target != null && !target.isAlive()) {
                angry.stopBeingAngry();
            }
        }
        if (changed) mob.getNavigation().stop();
    }
}
