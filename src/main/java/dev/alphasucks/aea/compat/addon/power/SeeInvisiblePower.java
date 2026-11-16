package dev.alphasucks.aea.compat.addon.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.compat.addon.PowerTags;
import dev.alphasucks.aea.mixin.common.EntityFlagsAccessor;
import dev.overgrown.apoli.condition.BiEntityCondition;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.Power;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerLookup;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;

/**
 * {@code aea:see_invisible}: the holder sees invisible entities, drawn at the configured opacity. Only what the holder
 * sees changes: the entity stays invisible to everyone else, and {@code isInvisible()} (and so {@code apoli:invisible})
 * is untouched. See {@code EntitySeeInvisibleMixin} and {@code LivingEntityRendererSeeInvisibleMixin}.
 */
public final class SeeInvisiblePower extends PowerType<SeeInvisiblePower.Cfg> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("aea", "see_invisible");

    /** Shared entity flag the game sets for the Invisibility effect and the {@code Invisible} data tag. */
    private static final int FLAG_INVISIBLE = 5;

    /** Why an entity is invisible: {@code any}, {@code effect}, a power ID or a {@code #}power tag. */
    public record Source(Kind kind, ResourceLocation id) {
        public enum Kind { ANY, EFFECT, POWER, TAG }

        public static final Codec<Source> CODEC = Codec.STRING.comapFlatMap(Source::read, Source::write);

        private static DataResult<Source> read(String raw) {
            if (raw.equals("any")) return DataResult.success(new Source(Kind.ANY, null));
            if (raw.equals("effect")) return DataResult.success(new Source(Kind.EFFECT, null));
            boolean tag = raw.startsWith("#");
            ResourceLocation id = ResourceLocation.tryParse(tag ? raw.substring(1) : raw);
            if (id == null) return DataResult.error(() -> "Not a source: \"" + raw + "\". Use \"any\", \"effect\", a power ID or a #power tag.");
            return DataResult.success(new Source(tag ? Kind.TAG : Kind.POWER, id));
        }

        private String write() {
            return switch (kind) {
                case ANY -> "any";
                case EFFECT -> "effect";
                case POWER -> id.toString();
                case TAG -> "#" + id;
            };
        }

        boolean matches(Entity target) {
            return switch (kind) {
                case ANY -> true;
                case EFFECT -> ((EntityFlagsAccessor) target).aea$sharedFlag(FLAG_INVISIBLE);
                case POWER -> hasActive(target, id);
                case TAG -> {
                    for (ResourceLocation power : PowerTags.get(id, target.level().isClientSide())) {
                        if (hasActive(target, power)) yield true;
                    }
                    yield false;
                }
            };
        }
    }

    private static final Codec<List<Source>> SOURCES = Codec.withAlternative(Source.CODEC.listOf(), Source.CODEC.xmap(List::of, l -> l.get(0)));

    public record Cfg(float opacity, Optional<BiEntityCondition> bientityCondition, List<Source> sources) {
        boolean reveals(Entity observer, Entity target) {
            if (bientityCondition.isPresent() && !bientityCondition.get().test(BiEntityCtx.of(observer, target, target.level()))) return false;
            for (int i = 0; i < sources.size(); i++) {
                if (sources.get(i).matches(target)) return true;
            }
            return false;
        }
    }

    private static final MapCodec<Cfg> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.floatRange(0.0F, 1.0F).optionalFieldOf("opacity", 0.5F).forGetter(Cfg::opacity),
        BiEntityCondition.CODEC.optionalFieldOf("bientity_condition").forGetter(Cfg::bientityCondition),
        SOURCES.optionalFieldOf("source", List.of(new Source(Source.Kind.ANY, null))).forGetter(Cfg::sources)
    ).apply(i, Cfg::new));

    @Override
    public MapCodec<Cfg> configCodec() {
        return CODEC;
    }

    private static int cachedGeneration = -1;
    private static boolean cachedInUse;

    /** Whether any loaded power uses this type, so the checks cost nothing on packs that never do. */
    private static boolean inUse() {
        int generation = ApoliPowers.generation();
        if (generation != cachedGeneration) {
            cachedInUse = ApoliPowers.anyOfType(ID);
            cachedGeneration = generation;
        }
        return cachedInUse;
    }

    /**
     * How transparent {@code target} looks to {@code observer}: from 0 (solid) to 1 (not drawn), or NaN when none of
     * the observer's powers reveal it. With several matching powers the most visible one wins.
     */
    public static float revealOpacity(Entity observer, Entity target) {
        if (observer == null || !inUse()) return Float.NaN;
        float[] best = {Float.NaN};
        PowerLookup.forEach(observer, ID, Cfg.class, cfg -> {
            if ((Float.isNaN(best[0]) || cfg.opacity() < best[0]) && cfg.reveals(observer, target)) best[0] = cfg.opacity();
        });
        return best[0];
    }

    /** The entity holds this power, it is not suppressed and its condition passes. */
    private static boolean hasActive(Entity entity, ResourceLocation powerId) {
        PowerContainer container = PowerContainer.of(entity);
        if (container == null || !container.hasPower(powerId) || container.isSuppressed(powerId)) return false;
        Power power = ApoliPowers.get(powerId);
        if (power == null || power.condition().isEmpty()) return power != null;
        try {
            return power.condition().get().test(EntityCtx.of(entity, entity.level()));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
