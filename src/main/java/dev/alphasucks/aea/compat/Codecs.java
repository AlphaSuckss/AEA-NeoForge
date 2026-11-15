package dev.alphasucks.aea.compat;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

public final class Codecs {
    private Codecs() {}

    /** Calio-style {"x":..,"y":..,"z":..} or a [x, y, z] list. */
    public static final Codec<Vec3> VEC3 = Codec.either(
        RecordCodecBuilder.<Vec3>create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("x", 0d).forGetter(v -> v.x),
            Codec.DOUBLE.optionalFieldOf("y", 0d).forGetter(v -> v.y),
            Codec.DOUBLE.optionalFieldOf("z", 0d).forGetter(v -> v.z)
        ).apply(i, Vec3::new)),
        Vec3.CODEC
    ).xmap(e -> e.map(a -> a, b -> b), Either::left);

    public static final Codec<Pose> POSE = Codec.STRING.xmap(
        s -> {
            String n = s.toUpperCase(Locale.ROOT);
            if (n.equals("SNEAKING")) n = "CROUCHING";
            if (n.equals("SWIMMING") || n.equals("FALL_FLYING") || n.equals("SLEEPING") || n.equals("STANDING")) return Pose.valueOf(n);
            try { return Pose.valueOf(n); } catch (IllegalArgumentException e) { return Pose.STANDING; }
        },
        p -> p.name().toLowerCase(Locale.ROOT)
    );

    public static <T> Codec<List<T>> singleOrList(Codec<T> c) {
        return Codec.either(c.listOf(), c).xmap(e -> e.map(l -> l, List::of), Either::left);
    }
}
