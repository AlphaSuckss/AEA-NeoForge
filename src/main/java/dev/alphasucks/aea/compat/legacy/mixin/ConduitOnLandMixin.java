package dev.alphasucks.aea.compat.legacy.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.alphasucks.aea.compat.legacy.LegacyOriginsMarkers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Origins 1.10.2's origins:conduit_power_on_land: conduit power reaches players with that power on land too. */
@Mixin(ConduitBlockEntity.class)
public abstract class ConduitOnLandMixin {
    @ModifyExpressionValue(method = "applyEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isInWaterOrRain()Z"))
    private static boolean aea$conduitOnLand(boolean original, @Local Player player) {
        return original || LegacyOriginsMarkers.conduitPowerOnLand(player);
    }
}
