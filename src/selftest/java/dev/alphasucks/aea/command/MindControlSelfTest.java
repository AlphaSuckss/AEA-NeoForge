package dev.alphasucks.aea.command;

import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.serialization.JsonOps;
import dev.alphasucks.aea.mindcontrol.ControlInput;
import dev.alphasucks.aea.mindcontrol.MindControl;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * {@code /aea selftest mc start <controller> <target> [json]}, {@code mc release <entity>}, and
 * {@code mc input <controller> <flags> <yRot> <xRot> [attacks] [uses]} to drive a session without a controller client.
 */
final class MindControlSelfTest {
    private MindControlSelfTest() {}

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        var start = Commands.argument("target", EntityArgument.entity())
            .executes(c -> start(c.getSource(), EntityArgument.getPlayer(c, "controller"), EntityArgument.getEntity(c, "target"), ""))
            .then(Commands.argument("json", StringArgumentType.greedyString())
                .executes(c -> start(c.getSource(), EntityArgument.getPlayer(c, "controller"), EntityArgument.getEntity(c, "target"), StringArgumentType.getString(c, "json"))));
        var input = Commands.argument("pitch", FloatArgumentType.floatArg())
            .executes(c -> input(c.getSource(), EntityArgument.getPlayer(c, "controller"), IntegerArgumentType.getInteger(c, "flags"),
                FloatArgumentType.getFloat(c, "yaw"), FloatArgumentType.getFloat(c, "pitch"), 0, 0))
            .then(Commands.argument("attacks", IntegerArgumentType.integer(0)).then(Commands.argument("uses", IntegerArgumentType.integer(0))
                .executes(c -> input(c.getSource(), EntityArgument.getPlayer(c, "controller"), IntegerArgumentType.getInteger(c, "flags"),
                    FloatArgumentType.getFloat(c, "yaw"), FloatArgumentType.getFloat(c, "pitch"), IntegerArgumentType.getInteger(c, "attacks"), IntegerArgumentType.getInteger(c, "uses")))));
        return Commands.literal("mc")
            .then(Commands.literal("start").then(Commands.argument("controller", EntityArgument.player()).then(start)))
            .then(Commands.literal("release").then(Commands.argument("entity", EntityArgument.entity())
                .executes(c -> release(c.getSource(), EntityArgument.getEntity(c, "entity")))))
            .then(Commands.literal("input").then(Commands.argument("controller", EntityArgument.player())
                .then(Commands.argument("flags", IntegerArgumentType.integer()).then(Commands.argument("yaw", FloatArgumentType.floatArg()).then(input)))));
    }

    static int start(CommandSourceStack src, ServerPlayer controller, Entity target, String json) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, src.getServer().registryAccess());
        var result = MindControl.CODEC.codec().parse(ops, JsonParser.parseString(json.isBlank() ? "{}" : json));
        if (result.error().isPresent()) {
            src.sendFailure(Component.literal("[AEA selftest] mc bad config: " + result.error().get().message()));
            return 0;
        }
        MindControl.start(result.getOrThrow(), controller, target);
        boolean ok = MindControl.controls(controller, target);
        src.sendSuccess(() -> Component.literal("[AEA selftest] mc start " + controller.getScoreboardName() + " -> " + target.getName().getString()
            + " ok=" + ok + " mode=" + controller.gameMode.getGameModeForPlayer().getName() + " camera=" + controller.getCamera().getName().getString()), false);
        return ok ? 1 : 0;
    }

    static int release(CommandSourceStack src, Entity entity) {
        MindControl.release(entity);
        src.sendSuccess(() -> Component.literal("[AEA selftest] mc release " + entity.getName().getString()
            + " controlling=" + MindControl.isControlling(entity) + " controlled=" + MindControl.isControlled(entity)), false);
        return 1;
    }

    static int input(CommandSourceStack src, ServerPlayer controller, int flags, float yRot, float xRot, int attacks, int uses) {
        MindControl.testInput(controller, new ControlInput(flags, yRot, xRot, attacks, uses, 0, 0, -1, 0));
        return 1;
    }
}
