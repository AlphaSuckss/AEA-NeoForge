package dev.alphasucks.aea.compat.sync.condition.bientity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.data.Comparison;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/**
 * {@code sync:command}: runs a command as the actor, with the actor and target selectors replaced by their UUIDs, and
 * compares the command's result.
 */
public final class CommandCondition implements ConditionType<BiEntityCtx, CommandCondition.Cfg> {
    public record Cfg(String command, Optional<String> actorSelector, Optional<String> targetSelector,
                      Comparison comparison, int compareTo) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("command").forGetter(Cfg::command),
            Codec.STRING.optionalFieldOf("actor_selector").forGetter(Cfg::actorSelector),
            Codec.STRING.optionalFieldOf("target_selector").forGetter(Cfg::targetSelector),
            Comparison.CODEC.optionalFieldOf("comparison", Comparison.GREATER_EQUAL).forGetter(Cfg::comparison),
            Codec.INT.optionalFieldOf("compare_to", 1).forGetter(Cfg::compareTo)
        ).apply(i, Cfg::new));
    }

    @Override
    public boolean test(Cfg c, BiEntityCtx ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        if (actor == null || target == null || actor.getServer() == null) return false;
        String cmd = c.command;
        String actorSel = c.actorSelector.orElse("%actor%");
        String targetSel = c.targetSelector.orElse("%target%");
        cmd = cmd.replace(actorSel, actor.getStringUUID()).replace(targetSel, target.getStringUUID());
        return c.comparison.compare(run(actor, cmd), c.compareTo);
    }

    private static int run(Entity as, String command) {
        MinecraftServer server = as.getServer();
        if (server == null) return 0;
        int[] result = {0};
        CommandSourceStack source = as.createCommandSourceStack().withPermission(4).withSuppressedOutput()
            .withCallback((success, value) -> result[0] = value);
        server.getCommands().performPrefixedCommand(source, command);
        return result[0];
    }
}
