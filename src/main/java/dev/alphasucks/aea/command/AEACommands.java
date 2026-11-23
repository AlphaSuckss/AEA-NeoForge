package dev.alphasucks.aea.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.legacy.CommandUpgrader;
import dev.alphasucks.aea.legacy.Diagnostics;
import dev.alphasucks.aea.legacy.TextFixes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/** {@code /aea validate}, {@code /aea upgrade}, and AEA's {@code /scale} when Pehkui isn't installed. */
public final class AEACommands {

    @SubscribeEvent
    public void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        if (!ModList.get().isLoaded("pehkui")) {
            PehkuiScaleCommand.register(d);
            AEA.LOGGER.info("[AEA] Pehkui is not installed: /scale is provided by AEA on top of Apoli's scale system.");
        }
        d.register(Commands.literal("aea").requires(s -> s.hasPermission(2))
            .then(Commands.literal("validate").executes(c -> validate(c.getSource()))
                .then(Commands.literal("fields").executes(c -> guard(() -> FieldAudit.run(c.getSource())))))
            .then(Commands.literal("upgrade").then(Commands.argument("command", StringArgumentType.greedyString())
                .executes(c -> {
                    String in = StringArgumentType.getString(c, "command");
                    String out = CommandUpgrader.upgrade(TextFixes.all(in), 3465);
                    c.getSource().sendSuccess(() -> Component.literal(out), false);
                    AEA.LOGGER.info("[AEA] upgrade: {} => {}", in, out);
                    return 1;
                })))
            .then(dev.alphasucks.aea.redstone.RedstonePulseTypes.command())
        );
    }


    static int guard(IntSupplier body) {
        try {
            return body.getAsInt();
        } catch (Throwable t) {
            AEA.LOGGER.error("[AEA] command crashed", t);
            return 0;
        }
    }

    /** Parse-checks every command string found inside upgraded legacy power JSON. */
    static int validate(CommandSourceStack source) {
        CommandDispatcher<CommandSourceStack> d = source.getServer().getCommands().getDispatcher();
        List<String> bad = new ArrayList<>();
        for (String cmd : Diagnostics.COMMANDS.keySet()) {
            String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
            if (c.contains("$(") || c.contains("%")) continue; // macro / placeholder lines are filled in at run time
            ParseResults<CommandSourceStack> r = d.parse(c, source);
            var last = r.getContext();
            while (last.getChild() != null) last = last.getChild();
            if (r.getReader().canRead() || !r.getExceptions().isEmpty() || last.getCommand() == null) {
                String why = r.getExceptions().isEmpty() ? "unparsed at " + r.getReader().getCursor() : r.getExceptions().values().iterator().next().getMessage();
                bad.add(c + "  <-- " + why);
            }
        }
        for (String b : bad) AEA.LOGGER.warn("[AEA] validate: {}", b);
        int total = Diagnostics.COMMANDS.size();
        source.sendSuccess(() -> Component.literal("[AEA] " + (total - bad.size()) + "/" + total + " embedded commands parse; failures are in the log."), false);
        return bad.size();
    }
}
