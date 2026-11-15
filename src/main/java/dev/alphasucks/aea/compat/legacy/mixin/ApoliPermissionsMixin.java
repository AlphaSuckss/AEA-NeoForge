package dev.alphasucks.aea.compat.legacy.mixin;

import dev.overgrown.apoli.command.ApoliPermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Apoli 2.9 / Origins 1.10.2 gated /power, /resource and /origin on the command source's permission level. Overgrown asks the
 * permission API about the player instead, so a power's execute_command or a function run as a non-op player fails. When the source
 * has been raised above the player's own level (functions, power actions), honor the source level as 2.9 did. Commands a player types
 * keep Overgrown's per-player check.
 */
@Mixin(value = ApoliPermissions.class, remap = false)
public abstract class ApoliPermissionsMixin {
    @Inject(method = "checkNode", at = @At("HEAD"), cancellable = true)
    private static void aea$elevatedSource(CommandSourceStack source, PermissionNode<Boolean> permission, int fallbackLevel,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (source.getEntity() instanceof ServerPlayer player && source.hasPermission(fallbackLevel) && !player.hasPermissions(fallbackLevel)) {
            cir.setReturnValue(true);
        }
    }
}
