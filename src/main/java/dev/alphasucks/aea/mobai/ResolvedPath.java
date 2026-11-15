package dev.alphasucks.aea.mobai;

import dev.overgrown.apoli.condition.BlockCondition;
import dev.overgrown.apoli.condition.context.BlockCtx;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.List;

/** aea:mob_pathing config resolved for one pathfinding run, with a per-run block-condition cache (AEA 1.5.4). */
public final class ResolvedPath {
    private static final int RULE_BLOCKED = 0;
    private static final int RULE_WALKABLE = 1;
    private static final int RULE_AVOID = 2;
    public static final int MAX_JUMP = 8;
    public static final int MAX_DROP = 256;

    public final Mob mob;
    public final MobPathingPower.Config config;
    public final int jump;
    public final int drop;
    private final Long2LongOpenHashMap cache = new Long2LongOpenHashMap();

    private ResolvedPath(Mob mob, MobPathingPower.Config config) {
        this.mob = mob;
        this.config = config;
        this.jump = config.jumpHeight().map(e -> Math.min(MAX_JUMP, Math.max(0, Mth.floor(e.eval(mob))))).orElse(-1);
        this.drop = config.dropHeight().map(e -> Math.min(MAX_DROP, Math.max(0, Mth.floor(e.eval(mob))))).orElse(-1);
    }

    public static ResolvedPath of(Mob mob) {
        MobPathingPower.Config cfg = MobPathingPower.config(mob);
        return cfg == null ? null : new ResolvedPath(mob, cfg);
    }

    public boolean hasBlockRules() {
        return config.hasBlockRules();
    }

    public boolean isBlocked(int x, int y, int z) {
        return config.blocked().isPresent() && test(RULE_BLOCKED, config.blocked().get(), x, y, z);
    }

    public boolean isWalkableFloor(int x, int y, int z) {
        if (config.walkable().isEmpty()) return true;
        BlockCondition c = config.walkable().get();
        return test(RULE_WALKABLE, c, x, y - 1, z) || test(RULE_WALKABLE, c, x, y, z);
    }

    /** Highest matching avoid penalty, or -1 when a matching rule has a negative penalty (impassable). */
    public float avoidPenalty(int x, int y, int z) {
        List<MobPathingPower.Avoid> avoid = config.avoid();
        float best = 0.0F;
        for (int i = 0; i < avoid.size() && i < 30; i++) {
            MobPathingPower.Avoid a = avoid.get(i);
            if (test(RULE_AVOID + i, a.blockCondition(), x, y - 1, z) || test(RULE_AVOID + i, a.blockCondition(), x, y, z)) {
                if (a.penalty() < 0.0F) return -1.0F;
                best = Math.max(best, a.penalty());
            }
        }
        return best;
    }

    private boolean test(int rule, BlockCondition condition, int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        long bits = cache.get(key);
        long known = 1L << (rule * 2);
        long value = 1L << (rule * 2 + 1);
        if ((bits & known) != 0L) return (bits & value) != 0L;
        boolean result = evaluate(condition, x, y, z);
        cache.put(key, bits | known | (result ? value : 0L));
        return result;
    }

    private boolean evaluate(BlockCondition condition, int x, int y, int z) {
        Level level = mob.level();
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.isLoaded(pos)) return false;
        try {
            return condition.test(new BlockCtx(pos, level.getBlockState(pos), level, mob, null));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public interface Holder {
        ResolvedPath aea$resolvedPath();
    }
}
