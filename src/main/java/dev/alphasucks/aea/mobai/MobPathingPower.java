package dev.alphasucks.aea.mobai;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.codec.LoggedOptionalField;
import dev.overgrown.apoli.condition.BlockCondition;
import dev.overgrown.apoli.data.Expression;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerLookup;
import dev.overgrown.apoli.power.PowerType;
import dev.overgrown.apoli.power.PowerTypeGate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.PathType;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * aea:mob_pathing (AEA 1.5.4): changes how the holding mob pathfinds — jump/drop heights, mid-air path
 * following, block rules (walkable / blocked / avoid) and per-node-type penalties.
 */
public final class MobPathingPower extends PowerType<MobPathingPower.Config> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("aea", "mob_pathing");
    private static final PowerTypeGate GATE = new PowerTypeGate(ID);

    /** Node names as AEA 1.5.4 (1.20.1) spelled them. Every 1.21.1 PathType name is accepted too. */
    private static final String[] NODE_NAMES = {
        "blocked", "open", "walkable", "walkable_door", "trapdoor", "powder_snow", "danger_powder_snow", "fence",
        "lava", "water", "water_border", "rail", "unpassable_rail", "danger_fire", "damage_fire", "danger_other",
        "damage_other", "door_open", "door_wood_closed", "door_iron_closed", "breach", "leaves", "sticky_honey",
        "cocoa", "damage_cautious"
    };

    private static final Codec<PathType> NODE_TYPE = Codec.STRING.comapFlatMap(MobPathingPower::nodeType, MobPathingPower::nodeName);

    private static DataResult<PathType> nodeType(String raw) {
        String s = raw.toLowerCase(Locale.ROOT);
        if (s.startsWith("minecraft:")) s = s.substring("minecraft:".length());
        for (PathType t : PathType.values()) {
            if (t.name().toLowerCase(Locale.ROOT).equals(s)) return DataResult.success(t);
        }
        String bad = s;
        return DataResult.error(() -> "Unknown path node type '" + bad + "'. Known: " + String.join(", ", NODE_NAMES));
    }

    private static String nodeName(PathType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    public record Avoid(BlockCondition blockCondition, float penalty) {
        public static final Codec<Avoid> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockCondition.CODEC.fieldOf("block_condition").forGetter(Avoid::blockCondition),
            Codec.FLOAT.optionalFieldOf("penalty", 8.0F).forGetter(Avoid::penalty)
        ).apply(i, Avoid::new));
    }

    public record Config(Optional<Expression> jumpHeight, Optional<Expression> dropHeight, boolean airbornePathing,
                         double airborneReach, Optional<BlockCondition> walkable, Optional<BlockCondition> blocked,
                         List<Avoid> avoid, Map<PathType, Float> penalties, int repathInterval) {
        public Float penaltyFor(PathType type) {
            return penalties.isEmpty() ? null : penalties.get(type);
        }

        public boolean hasBlockRules() {
            return walkable.isPresent() || blocked.isPresent() || !avoid.isEmpty();
        }
    }

    @Override
    public MapCodec<Config> configCodec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            Expression.DOUBLE_OR_EXPR.optionalFieldOf("jump_height").forGetter(Config::jumpHeight),
            Expression.DOUBLE_OR_EXPR.optionalFieldOf("drop_height").forGetter(Config::dropHeight),
            Codec.BOOL.optionalFieldOf("airborne_pathing", false).forGetter(Config::airbornePathing),
            Codec.DOUBLE.optionalFieldOf("airborne_reach", 4.0D).forGetter(Config::airborneReach),
            LoggedOptionalField.strict("walkable", BlockCondition.CODEC).forGetter(Config::walkable),
            LoggedOptionalField.strict("blocked", BlockCondition.CODEC).forGetter(Config::blocked),
            MobAiPower.strictOr("avoid", Avoid.CODEC.listOf(), List.of()).forGetter(Config::avoid),
            MobAiPower.strictOr("penalties", Codec.unboundedMap(NODE_TYPE, Codec.FLOAT)
                    .<Map<PathType, Float>>xmap(m -> m.isEmpty() ? Map.of() : new EnumMap<>(m), m -> m), Map.<PathType, Float>of())
                .forGetter(Config::penalties),
            Codec.INT.optionalFieldOf("repath_interval", 0).forGetter(Config::repathInterval)
        ).apply(i, Config::new));
    }

    @Override
    public void tick(ResourceLocation powerId, Config cfg, PowerContainer holder) {
        if (cfg.repathInterval() <= 0) return;
        if (!(holder.rawOwner() instanceof Mob mob) || mob.level().isClientSide()) return;
        if (mob.tickCount % cfg.repathInterval() != 0 || !MobAiPower.conditionHolds(powerId, mob)) return;
        PathNavigation nav = mob.getNavigation();
        if (!nav.isDone() && nav instanceof Repathable r) r.aea$repath();
    }

    /** The holding mob's active aea:mob_pathing config, cached per tick and power generation; null when none. */
    public static Config config(Mob mob) {
        if (mob == null || !GATE.inUse() || mob.level().isClientSide()) return null;
        MobAiState state = ((MobAiState.Holder) mob).aea$mobAiState();
        long stamp = stamp(mob);
        if (state.pathStamp != stamp) {
            state.pathStamp = stamp;
            Config found = null;
            try {
                found = PowerLookup.firstActive((Entity) mob, ID, Config.class);
            } catch (RuntimeException ignored) {
            }
            state.path = found;
        }
        return state.path;
    }

    static long stamp(Mob mob) {
        return mob.level().getGameTime() * 1024L + (long) (ApoliPowers.generation() & 1023);
    }
}
