package dev.alphasucks.aea.redstone;

import dev.alphasucks.aea.AEA;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Temporary, invisible redstone power ({@code aea:redstone_pulse}, {@code /aea redstone pulse}).
 *
 * <p>No block state changes. Every redstone block, vanilla or modded, asks the world for power through
 * {@link SignalGetter#getSignal} and {@link SignalGetter#getDirectSignal}; {@code SignalGetterPulseMixin} adds the
 * pulses to their answers, and blocks are told to recheck when a pulse starts and ends, the way a lever or button tells
 * them.
 *
 * <ul>
 *   <li>{@link Mode#EMIT}: the position gives out power, like a redstone block put there for a moment (air works).</li>
 *   <li>{@link Mode#RECEIVE}: the block at the position is powered, as if a power source sat on each chosen side.</li>
 * </ul>
 * With {@code strong}, the power also goes on through a solid block, like a lever's power through the block it is on.
 *
 * <p>Server only. {@link #active} is checked first on every signal query, so nothing is looked up while there are no
 * pulses anywhere.
 */
public final class RedstonePulses {
    private RedstonePulses() {}

    public enum Mode { EMIT, RECEIVE }

    /** Every side; bit {@code d.get3DDataValue()} per side. */
    public static final int ALL_SIDES = 0b111111;

    static final class Pulse {
        int strength, ticks, sides;
        boolean strong;

        Pulse(int strength, int ticks, int sides, boolean strong) {
            this.strength = strength;
            this.ticks = ticks;
            this.sides = sides;
            this.strong = strong;
        }

        boolean side(Direction d) {
            return (sides & (1 << d.get3DDataValue())) != 0;
        }
    }

    static final class Pulses {
        final Long2ObjectMap<Pulse> emit = new Long2ObjectOpenHashMap<>(), receive = new Long2ObjectOpenHashMap<>();

        int size() {
            return emit.size() + receive.size();
        }
    }

    /** Pulses in all levels; the hot-path gate. */
    private static int active;
    private static final Map<ServerLevel, Pulses> LEVELS = new IdentityHashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, e -> {
            if (active > 0 && e.getLevel() instanceof ServerLevel level) tick(level);
        });
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, e -> {
            if (e.getLevel() instanceof ServerLevel level) {
                Pulses p = LEVELS.remove(level);
                if (p != null) active -= p.size();
            }
        });
    }

    // ---- starting pulses ----

    /**
     * Starts a pulse at {@code pos}, or extends one already there in the same mode: the strength and time left become the
     * higher of the two, and sides and {@code strong} add up.
     *
     * @param strength 1 to 15
     * @param ticks    game ticks, at least 1
     * @param sides    sides of {@code pos} the power leaves (emit) or comes in through (receive); {@link #ALL_SIDES} for all
     */
    public static void pulse(ServerLevel level, BlockPos pos, Mode mode, int strength, int ticks, int sides, boolean strong) {
        strength = Math.max(1, Math.min(15, strength));
        ticks = Math.max(1, ticks);
        sides &= ALL_SIDES;
        if (sides == 0) return;
        Pulses pulses = LEVELS.computeIfAbsent(level, l -> new Pulses());
        Long2ObjectMap<Pulse> map = mode == Mode.EMIT ? pulses.emit : pulses.receive;
        long key = pos.asLong();
        Pulse p = map.get(key);
        if (p == null) {
            map.put(key, new Pulse(strength, ticks, sides, strong));
            active++;
        } else {
            boolean stronger = strength > p.strength || (sides | p.sides) != p.sides || (strong && !p.strong);
            p.ticks = Math.max(p.ticks, ticks);
            if (!stronger) return;
            p.strength = Math.max(p.strength, strength);
            p.sides |= sides;
            p.strong |= strong;
        }
        notify(level, pos.immutable(), mode, strong || (p != null && p.strong));
    }

    private static void tick(ServerLevel level) {
        Pulses pulses = LEVELS.get(level);
        if (pulses == null || pulses.size() == 0) return;
        List<long[]> ended = null;
        for (int m = 0; m < 2; m++) {
            Long2ObjectMap<Pulse> map = m == 0 ? pulses.emit : pulses.receive;
            var it = map.long2ObjectEntrySet().iterator();
            while (it.hasNext()) {
                Long2ObjectMap.Entry<Pulse> e = it.next();
                if (--e.getValue().ticks > 0) continue;
                if (ended == null) ended = new ArrayList<>();
                ended.add(new long[] {e.getLongKey(), m, e.getValue().strong ? 1 : 0});
                it.remove();
                active--;
            }
        }
        if (ended == null) return;
        if (pulses.size() == 0) LEVELS.remove(level);
        // Updates can start new pulses, so they run after the maps are done with.
        for (long[] e : ended) notify(level, BlockPos.of(e[0]), e[1] == 0 ? Mode.EMIT : Mode.RECEIVE, e[2] == 1);
    }

    /** Tells the blocks that can see this pulse to recheck their power, as a lever or button would. */
    private static void notify(ServerLevel level, BlockPos pos, Mode mode, boolean strong) {
        if (!level.isLoaded(pos)) return;
        try {
            // Updates come "from" a redstone block. Using the block itself would make some ignore it: a door skips updates
            // from another door, so it would never notice a pulse sent into it.
            Block source = Blocks.REDSTONE_BLOCK;
            if (mode == Mode.EMIT) {
                level.updateNeighborsAt(pos, source);
                // Strong power reaches through the blocks next to it, so their neighbours need telling too.
                if (strong) for (Direction d : Direction.values()) level.updateNeighborsAt(pos.relative(d), source);
            } else {
                level.neighborChanged(pos, source, pos.above());
                // A strongly powered block passes it on to everything around it.
                if (strong) level.updateNeighborsAt(pos, source);
            }
        } catch (RuntimeException e) {
            AEA.LOGGER.warn("[AEA] Redstone pulse at {} failed to update its neighbours: {}", pos, e.toString());
        }
    }

    // ---- signal queries (SignalGetterPulseMixin) ----

    /** Adds pulses to {@code getSignal(pos, dir)} (or {@code getDirectSignal} when {@code direct}). */
    public static int signal(SignalGetter getter, BlockPos pos, Direction dir, int original, boolean direct) {
        if (active == 0 || original >= 15 || !(getter instanceof ServerLevel level)) return original;
        Pulses pulses = LEVELS.get(level);
        if (pulses == null) return original;
        int best = original;
        long at = pos.asLong();
        // A pulse emitting from pos, out of the side facing whoever asks (dir points from them to pos).
        Pulse e = pulses.emit.isEmpty() ? null : pulses.emit.get(at);
        if (e != null && (!direct || e.strong) && e.side(dir.getOpposite())) best = Math.max(best, e.strength);
        // A pulse powering the block that asks, which sits one step back along dir, coming in through its side toward pos.
        if (!pulses.receive.isEmpty()) {
            Pulse r = pulses.receive.get(BlockPos.offset(at, dir.getOpposite()));
            if (r != null && (!direct || r.strong) && r.side(dir)) best = Math.max(best, r.strength);
        }
        return best;
    }

    /** For tests and {@code /aea redstone}: how many pulses are running in a level. */
    public static int count(ServerLevel level) {
        Pulses p = LEVELS.get(level);
        return p == null ? 0 : p.size();
    }
}
