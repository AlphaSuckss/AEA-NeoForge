package dev.alphasucks.aea.mixin.common;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The raw shared entity flags, which Apoli's invisibility power does not change. */
@Mixin(Entity.class)
public interface EntityFlagsAccessor {
    @Invoker("getSharedFlag")
    boolean aea$sharedFlag(int flag);
}
