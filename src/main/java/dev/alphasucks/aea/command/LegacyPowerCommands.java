package dev.alphasucks.aea.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.alphasucks.aea.AEA;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.PowerContainer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Apoli 2.9.2 {@code /power list|sources|clear}, which Overgrown dropped. Legacy commands are routed here by {@code ApoliCommandUpgrader}. */
@EventBusSubscriber(modid = AEA.MOD_ID)
public final class LegacyPowerCommands {
    private LegacyPowerCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("aea:power").requires(s -> s.hasPermission(2))
            .then(Commands.literal("list")
                .then(Commands.argument("target", EntityArgument.entity())
                    .executes(c -> list(c, false))
                    .then(Commands.argument("subpowers", BoolArgumentType.bool())
                        .executes(c -> list(c, BoolArgumentType.getBool(c, "subpowers"))))))
            .then(Commands.literal("sources")
                .then(Commands.argument("target", EntityArgument.entity())
                    .then(Commands.argument("power", ResourceLocationArgument.id())
                        .executes(LegacyPowerCommands::sources))))
            .then(Commands.literal("clear")
                .then(Commands.argument("targets", EntityArgument.entities())
                    .executes(LegacyPowerCommands::clear))));
    }

    private static int list(CommandContext<CommandSourceStack> c, boolean subpowers) throws CommandSyntaxException {
        Entity e = EntityArgument.getEntity(c, "target");
        PowerContainer pc = e instanceof LivingEntity ? PowerContainer.of(e) : null;
        List<ResourceLocation> ids = new ArrayList<>();
        if (pc != null) for (ResourceLocation id : pc.allPowers()) if (subpowers || !ApoliPowers.isSubPower(id)) ids.add(id);
        ids.sort(ResourceLocation::compareTo);
        if (ids.isEmpty()) { c.getSource().sendFailure(Component.literal(e.getName().getString() + " has no powers.")); return 0; }
        String names = String.join(", ", ids.stream().map(ResourceLocation::toString).toList());
        c.getSource().sendSuccess(() -> Component.literal(e.getName().getString() + " has " + ids.size() + " power(s): " + names), true);
        return ids.size();
    }

    private static int sources(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Entity e = EntityArgument.getEntity(c, "target");
        ResourceLocation power = ResourceLocationArgument.getId(c, "power");
        PowerContainer pc = e instanceof LivingEntity ? PowerContainer.of(e) : null;
        Set<ResourceLocation> src = pc == null ? Set.of() : pc.sourcesOf(power);
        if (src == null || src.isEmpty()) { c.getSource().sendFailure(Component.literal(e.getName().getString() + " does not have " + power + ".")); return 0; }
        String names = String.join(", ", src.stream().map(ResourceLocation::toString).sorted().toList());
        c.getSource().sendSuccess(() -> Component.literal(e.getName().getString() + " has " + power + " from " + src.size() + " source(s): " + names), true);
        return src.size();
    }

    private static int clear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int cleared = 0;
        for (Entity e : EntityArgument.getEntities(c, "targets")) {
            PowerContainer pc = e instanceof LivingEntity ? PowerContainer.of(e) : null;
            if (pc == null || pc.allPowers().isEmpty()) continue;
            for (ResourceLocation s : List.copyOf(pc.allSources())) pc.removeAllFromSource(s);
            cleared++;
        }
        final int n = cleared;
        if (n == 0) c.getSource().sendFailure(Component.literal("No target had any powers."));
        else c.getSource().sendSuccess(() -> Component.literal("Cleared all powers from " + n + " entit" + (n == 1 ? "y." : "ies.")), true);
        return n;
    }
}
