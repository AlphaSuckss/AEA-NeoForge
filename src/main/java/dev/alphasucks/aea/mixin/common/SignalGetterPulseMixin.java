package dev.alphasucks.aea.mixin.common;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.alphasucks.aea.redstone.RedstonePulses;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.SignalGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Adds {@link RedstonePulses} to the two signal queries every other one is built on. These run constantly; the handler
 * returns straight away while no pulse exists.
 */
@Mixin(SignalGetter.class)
public interface SignalGetterPulseMixin {
    @ModifyReturnValue(method = "getSignal", at = @At("RETURN"))
    private int aea$pulseSignal(int original, BlockPos pos, Direction direction) {
        return RedstonePulses.signal((SignalGetter) this, pos, direction, original, false);
    }

    @ModifyReturnValue(method = "getDirectSignal", at = @At("RETURN"))
    private int aea$pulseDirectSignal(int original, BlockPos pos, Direction direction) {
        return RedstonePulses.signal((SignalGetter) this, pos, direction, original, true);
    }
}
