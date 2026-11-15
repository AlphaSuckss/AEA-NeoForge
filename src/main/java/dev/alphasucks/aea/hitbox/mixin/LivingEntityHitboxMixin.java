package dev.alphasucks.aea.hitbox.mixin;

import dev.alphasucks.aea.hitbox.HitboxParts;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Holds an entity's {@code aea:hitbox_part} boxes on the entity itself, so they go away with it. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHitboxMixin implements HitboxParts.Holder {
    @Unique
    private HitboxParts aea$hitboxParts;

    @Override
    public HitboxParts aea$hitboxParts() {
        HitboxParts state = aea$hitboxParts;
        if (state == null) {
            state = new HitboxParts();
            aea$hitboxParts = state;
        }
        return state;
    }
}
