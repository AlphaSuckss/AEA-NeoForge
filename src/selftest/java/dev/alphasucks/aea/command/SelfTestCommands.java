package dev.alphasucks.aea.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.alphasucks.aea.gui.GuiSelfTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;

/**
 * {@code /aea selftest ...}: the smoke tests used while porting (some summon mobs and grant them every power). Only in
 * the selftest jar, and only registered with {@code -Daea.selftest=true}; AEACommands finds this class by name.
 */
public final class SelfTestCommands {
    private SelfTestCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("aea").requires(s -> s.hasPermission(4))
            .then(Commands.literal("selftest")
                .then(Commands.literal("grant").executes(c -> AEACommands.guard(() -> SelfTest.grant(c.getSource()))))
                .then(Commands.literal("functions").executes(c -> AEACommands.guard(() -> SelfTest.functions(c.getSource()))))
                .then(Commands.literal("player").executes(c -> AEACommands.guard(() -> SelfTest.player(c.getSource()))))
                .then(Commands.literal("items").executes(c -> AEACommands.guard(() -> SelfTest.legacyItems(c.getSource()))))
                .then(Commands.literal("audit").executes(c -> AEACommands.guard(() -> ApoliAudit.run(c.getSource()))))
                .then(Commands.literal("legacy").executes(c -> AEACommands.guard(() -> LegacyCommandTest.run(c.getSource()))))
                .then(Commands.literal("dump").then(Commands.argument("namespace", StringArgumentType.word())
                    .executes(c -> AEACommands.guard(() -> SelfTest.dump(c.getSource(), StringArgumentType.getString(c, "namespace"))))))
                .then(Commands.literal("probe").then(Commands.argument("power", ResourceLocationArgument.id())
                    .executes(c -> AEACommands.guard(() -> SelfTest.probe(c.getSource(), ResourceLocationArgument.getId(c, "power"))))))
                .then(Commands.literal("seeinvis").then(Commands.argument("power", ResourceLocationArgument.id())
                    .executes(c -> AEACommands.guard(() -> SeeInvisibleSelfTest.run(c.getSource(), ResourceLocationArgument.getId(c, "power"))))))
                .then(MindControlSelfTest.node())
                .then(Commands.literal("spawn").executes(c -> AEACommands.guard(() -> SpawnPointSelfTest.run(c.getSource(), null)))
                    .then(Commands.argument("power", ResourceLocationArgument.id())
                        .executes(c -> AEACommands.guard(() -> SpawnPointSelfTest.run(c.getSource(), ResourceLocationArgument.getId(c, "power"))))))
                .then(Commands.literal("milk").executes(c -> AEACommands.guard(() -> EffectsSelfTest.milk(c.getSource()))))
                .then(Commands.literal("effects").executes(c -> AEACommands.guard(() -> EffectsSelfTest.effects(c.getSource()))))
                .then(Commands.literal("cond").then(Commands.argument("json", StringArgumentType.greedyString())
                    .executes(c -> AEACommands.guard(() -> EffectsSelfTest.condition(c.getSource(), StringArgumentType.getString(c, "json"))))))
                .then(Commands.literal("expr").then(Commands.argument("expression", StringArgumentType.greedyString())
                    .executes(c -> AEACommands.guard(() -> EffectsSelfTest.expression(c.getSource(), StringArgumentType.getString(c, "expression"))))))
                .then(Commands.literal("gui").executes(c -> AEACommands.guard(() -> GuiSelfTest.schedule(c.getSource()))))));
    }
}
