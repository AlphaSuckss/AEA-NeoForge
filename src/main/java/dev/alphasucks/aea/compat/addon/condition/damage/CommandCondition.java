package dev.alphasucks.aea.compat.addon.condition.damage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.DamageCtx;
import dev.overgrown.apoli.data.Comparison;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

/** {@code apoli:command} (damage): runs a command as the damaged entity and compares its result. */
public final class CommandCondition implements ConditionType<DamageCtx, CommandCondition.Cfg> {
    public record Cfg(String command, Comparison comparison, int compareTo) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("command").forGetter(Cfg::command),
            Comparison.CODEC.optionalFieldOf("comparison", Comparison.GREATER_EQUAL).forGetter(Cfg::comparison),
            Codec.INT.optionalFieldOf("compare_to", 1).forGetter(Cfg::compareTo)
        ).apply(i, Cfg::new));
    }

    @Override
    public boolean test(Cfg c, DamageCtx ctx) {
        Entity target = ctx.target();
        if (target == null) return false;
        MinecraftServer server = target.getServer();
        if (server == null) return false;
        int[] result = {0};
        CommandSourceStack src = target.createCommandSourceStack().withPermission(4).withSuppressedOutput()
            .withCallback((ok, v) -> result[0] = v);
        server.getCommands().performPrefixedCommand(src, c.command);
        return c.comparison.compare(result[0], c.compareTo);
    }
}
