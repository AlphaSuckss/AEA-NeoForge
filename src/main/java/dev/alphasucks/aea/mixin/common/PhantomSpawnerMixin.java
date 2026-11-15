package dev.alphasucks.aea.mixin.common;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.alphasucks.aea.compat.legacy.LegacyApoli;
import dev.overgrown.apoli.data.AttributeModifier;
import dev.overgrown.apoli.data.AttributeModifierHelper;
import dev.overgrown.apoli.power.PowerLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

@Mixin(PhantomSpawner.class)
public abstract class PhantomSpawnerMixin {
    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/stats/ServerStatsCounter;getValue(Lnet/minecraft/stats/Stat;)I"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private int aea$insomnia(int original, @Local ServerPlayer player) {
        List<List> cfgs = (List) PowerLookup.active(player, LegacyApoli.MODIFY_INSOMNIA, List.class);
        if (cfgs.isEmpty()) return original;
        List<AttributeModifier> all = new ArrayList<>();
        for (List l : cfgs) for (Object o : l) if (o instanceof AttributeModifier m) all.add(m);
        return (int) AttributeModifierHelper.apply((double) original, AttributeModifierHelper.ensureSorted(all), player);
    }
}
