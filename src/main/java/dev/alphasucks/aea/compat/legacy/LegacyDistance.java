package dev.alphasucks.aea.compat.legacy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.compat.Codecs;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;

/**
 * Apoli 2.9.2's {@code distance_from_coordinates} / {@code distance_from_spawn}, shared by the entity and block forms.
 * Overgrown's versions measure differently, so ApoliJsonUpgrader points legacy packs here.
 */
public final class LegacyDistance {
    private LegacyDistance() {}

    public record Cfg(String reference, Vec3 offset, Vec3 coordinates, boolean ignoreX, boolean ignoreY, boolean ignoreZ,
                      String shape, boolean scaleReferenceToDimension, boolean scaleDistanceToDimension,
                      String comparison, double compareTo, Optional<Boolean> resultOnWrongDimension,
                      Optional<Integer> roundToDigit) {}

    // Apoli 2.9 comparisons are the symbols ("==", ">=", ...), not Overgrown's names.
    private static final Codec<String> COMPARISON = Codec.STRING.xmap(s -> s.trim(), s -> s);

    public static MapCodec<Cfg> codec(String defaultReference) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("reference", defaultReference).forGetter(Cfg::reference),
            Codecs.VEC3.optionalFieldOf("offset", Vec3.ZERO).forGetter(Cfg::offset),
            Codecs.VEC3.optionalFieldOf("coordinates", Vec3.ZERO).forGetter(Cfg::coordinates),
            Codec.BOOL.optionalFieldOf("ignore_x", false).forGetter(Cfg::ignoreX),
            Codec.BOOL.optionalFieldOf("ignore_y", false).forGetter(Cfg::ignoreY),
            Codec.BOOL.optionalFieldOf("ignore_z", false).forGetter(Cfg::ignoreZ),
            Codec.STRING.optionalFieldOf("shape", "cube").forGetter(Cfg::shape),
            Codec.BOOL.optionalFieldOf("scale_reference_to_dimension", true).forGetter(Cfg::scaleReferenceToDimension),
            Codec.BOOL.optionalFieldOf("scale_distance_to_dimension", false).forGetter(Cfg::scaleDistanceToDimension),
            COMPARISON.fieldOf("comparison").forGetter(Cfg::comparison),
            Codec.DOUBLE.fieldOf("compare_to").forGetter(Cfg::compareTo),
            Codec.BOOL.optionalFieldOf("result_on_wrong_dimension").forGetter(Cfg::resultOnWrongDimension),
            Codec.INT.optionalFieldOf("round_to_digit").forGetter(Cfg::roundToDigit)
        ).apply(i, Cfg::new));
    }

    /** Port of Apoli 2.9.2's DistanceFromCoordinatesConditionRegistry.testCondition. */
    public static boolean test(Cfg c, Vec3 pos, Level level) {
        if (level == null) return outOfBounds(c.comparison());
        double scale = level.dimensionType().coordinateScale();
        double x = 0, y = 0, z = 0;
        switch (c.reference()) {
            case "world_spawn", "player_spawn", "player_natural_spawn" -> {
                if (c.resultOnWrongDimension().isPresent() && level.dimension() != Level.OVERWORLD) return c.resultOnWrongDimension().get();
                BlockPos spawn = level.getSharedSpawnPos();
                x = spawn.getX(); y = spawn.getY(); z = spawn.getZ();
            }
            default -> {
            }
        }
        x += c.coordinates().x + c.offset().x;
        y += c.coordinates().y + c.offset().y;
        z += c.coordinates().z + c.offset().z;
        if (c.scaleReferenceToDimension() && (x != 0 || z != 0)) {
            if (scale == 0) return outOfBounds(c.comparison());
            x /= scale;
            z /= scale;
        }
        double dx = c.ignoreX() ? 0 : Math.abs(pos.x - x);
        double dy = c.ignoreY() ? 0 : Math.abs(pos.y - y);
        double dz = c.ignoreZ() ? 0 : Math.abs(pos.z - z);
        if (c.scaleDistanceToDimension()) {
            dx *= scale;
            dz *= scale;
        }
        double distance = shapeDistance(c.shape(), dx, dy, dz);
        if (c.roundToDigit().isPresent()) {
            distance = new BigDecimal(distance).setScale(c.roundToDigit().get(), RoundingMode.HALF_UP).doubleValue();
        }
        return compare(c.comparison(), distance, c.compareTo());
    }

    // What 2.9 answered when the distance can't be measured: only "farther than" comparisons pass.
    private static boolean outOfBounds(String comparison) {
        return comparison.equals("!=") || comparison.equals(">") || comparison.equals(">=");
    }

    private static double shapeDistance(String shape, double x, double y, double z) {
        return switch (shape.toLowerCase(Locale.ROOT)) {
            case "star", "manhattan" -> x + y + z;
            case "sphere", "euclidean" -> Math.sqrt(x * x + y * y + z * z);
            default -> Math.max(Math.max(x, y), z);
        };
    }

    private static boolean compare(String op, double a, double b) {
        return switch (op) {
            case "==" -> a == b;
            case "<" -> a < b;
            case ">" -> a > b;
            case "<=" -> a <= b;
            case ">=" -> a >= b;
            case "!=" -> a != b;
            default -> false;
        };
    }
}
