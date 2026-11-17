package dev.alphasucks.aea.mindcontrol.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.alphasucks.aea.mindcontrol.client.MindControlKeys;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Keeps a mind-controlled player's own key and mouse presses away from the parts the controller has taken over.
 * Runs on key events only, not every tick.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(KeyMapping.class)
public abstract class KeyMappingMindControlMixin {
    @ModifyExpressionValue(method = "click", at = @At(value = "INVOKE",
        target = "Lnet/neoforged/neoforge/client/settings/KeyMappingLookup;getAll(Lcom/mojang/blaze3d/platform/InputConstants$Key;)Ljava/util/List;"))
    private static List<KeyMapping> aea$filterClick(List<KeyMapping> mappings) {
        return MindControlKeys.filter(mappings, true);
    }

    @ModifyExpressionValue(method = "set", at = @At(value = "INVOKE",
        target = "Lnet/neoforged/neoforge/client/settings/KeyMappingLookup;getAll(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)Ljava/util/List;"))
    private static List<KeyMapping> aea$filterSet(List<KeyMapping> mappings) {
        return MindControlKeys.filter(mappings, false);
    }
}
