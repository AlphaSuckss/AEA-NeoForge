package dev.alphasucks.aea.mixin.common;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.alphasucks.aea.compat.addon.power.PreventSpawnPointPower;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.commands.SetSpawnCommand;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Collection;

/** Marks spawn points set by {@code /spawnpoint}, so {@code aea:prevent_spawn_point} can tell them apart. */
@Mixin(SetSpawnCommand.class)
public abstract class SetSpawnCommandMixin {
    @WrapMethod(method = "setSpawn")
    private static int aea$markCommand(CommandSourceStack source, Collection<ServerPlayer> targets, BlockPos pos, float angle, Operation<Integer> original) {
        boolean outer = PreventSpawnPointPower.inCommand;
        PreventSpawnPointPower.inCommand = true;
        try {
            return original.call(source, targets, pos, angle);
        } finally {
            PreventSpawnPointPower.inCommand = outer;
        }
    }
}
