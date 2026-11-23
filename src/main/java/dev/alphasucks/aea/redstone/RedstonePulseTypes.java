package dev.alphasucks.aea.redstone;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.compat.Codecs;
import dev.alphasucks.aea.compat.Reg;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.action.ActionTypes;
import dev.overgrown.apoli.condition.context.BlockCtx;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/** {@code aea:redstone_pulse} (block action) and {@code /aea redstone pulse}. See {@link RedstonePulses}. */
public final class RedstonePulseTypes {
    private RedstonePulseTypes() {}

    public record Cfg(int strength, int duration, RedstonePulses.Mode mode, boolean strong, List<Direction> sides) {
        int sideMask() {
            if (sides.isEmpty()) return RedstonePulses.ALL_SIDES;
            int mask = 0;
            for (Direction d : sides) mask |= 1 << d.get3DDataValue();
            return mask;
        }
    }

    private static final Codec<RedstonePulses.Mode> MODE = Codec.STRING.comapFlatMap(s -> switch (s) {
        case "receive" -> DataResult.success(RedstonePulses.Mode.RECEIVE);
        case "emit" -> DataResult.success(RedstonePulses.Mode.EMIT);
        default -> DataResult.error(() -> "Unknown pulse mode \"" + s + "\"; use receive or emit");
    }, m -> m == RedstonePulses.Mode.EMIT ? "emit" : "receive");

    private static final MapCodec<Cfg> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.intRange(1, 15).optionalFieldOf("strength", 15).forGetter(Cfg::strength),
        Codec.intRange(1, 72000).optionalFieldOf("duration", 4).forGetter(Cfg::duration),
        MODE.optionalFieldOf("mode", RedstonePulses.Mode.RECEIVE).forGetter(Cfg::mode),
        Codec.BOOL.optionalFieldOf("strong", false).forGetter(Cfg::strong),
        Codecs.singleOrList(Direction.CODEC).optionalFieldOf("sides", List.of()).forGetter(Cfg::sides)
    ).apply(i, Cfg::new));

    public static void register() {
        RedstonePulses.register();
        Reg.action(ActionTypes.BLOCK, "aea:redstone_pulse", new ActionType<BlockCtx, Cfg>() {
            @Override public MapCodec<Cfg> codec() { return CODEC; }

            @Override
            public void run(Cfg cfg, BlockCtx ctx) {
                if (ctx.level() instanceof ServerLevel level) {
                    RedstonePulses.pulse(level, ctx.pos(), cfg.mode(), cfg.strength(), cfg.duration(), cfg.sideMask(), cfg.strong());
                }
            }
        });
    }

    /**
     * {@code /aea redstone pulse <pos> [receive|emit] [strength] [duration] [strong]}, defaulting to receive, 15, 4 ticks,
     * weak. For packs that keep a position elsewhere, such as at a marker entity:
     * {@code execute at @e[tag=linked] run aea redstone pulse ~ ~ ~}.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        var pos = Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> run(c, RedstonePulses.Mode.RECEIVE));
        for (RedstonePulses.Mode mode : RedstonePulses.Mode.values()) {
            pos.then(Commands.literal(mode == RedstonePulses.Mode.EMIT ? "emit" : "receive").executes(c -> run(c, mode))
                .then(Commands.argument("strength", IntegerArgumentType.integer(1, 15)).executes(c -> run(c, mode))
                    .then(Commands.argument("duration", IntegerArgumentType.integer(1, 72000)).executes(c -> run(c, mode))
                        .then(Commands.argument("strong", BoolArgumentType.bool()).executes(c -> run(c, mode))))));
        }
        return Commands.literal("redstone").then(Commands.literal("pulse").then(pos));
    }

    private static int run(CommandContext<CommandSourceStack> c, RedstonePulses.Mode mode) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        int strength = arg(c, "strength", 15);
        int duration = arg(c, "duration", 4);
        boolean strong = hasArg(c, "strong") && BoolArgumentType.getBool(c, "strong");
        RedstonePulses.pulse(c.getSource().getLevel(), pos, mode, strength, duration, RedstonePulses.ALL_SIDES, strong);
        c.getSource().sendSuccess(() -> Component.literal("Redstone pulse (" + mode.name().toLowerCase(java.util.Locale.ROOT) + ", "
            + strength + ", " + duration + " ticks" + (strong ? ", strong" : "") + ") at " + pos.toShortString()), false);
        return strength;
    }

    private static int arg(CommandContext<CommandSourceStack> c, String name, int fallback) {
        return hasArg(c, name) ? IntegerArgumentType.getInteger(c, name) : fallback;
    }

    private static boolean hasArg(CommandContext<CommandSourceStack> c, String name) {
        try {
            c.getArgument(name, Object.class);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
