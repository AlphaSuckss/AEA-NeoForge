package dev.alphasucks.aea.mindcontrol.mixin.client;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Raw access, so relayed keys are held as-is even when the player uses toggle sneak or toggle sprint. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("isDown")
    void aea$setDown(boolean down);

    @Accessor("clickCount")
    int aea$clickCount();

    @Accessor("clickCount")
    void aea$setClickCount(int count);
}
