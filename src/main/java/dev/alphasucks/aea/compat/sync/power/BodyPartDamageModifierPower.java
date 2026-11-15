package dev.alphasucks.aea.compat.sync.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.compat.Codecs;
import dev.overgrown.apoli.action.BiEntityAction;
import dev.overgrown.apoli.condition.DamageCondition;
import dev.overgrown.apoli.data.AttributeModifier;
import dev.overgrown.apoli.data.AttributeModifierHelper;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code sync:body_part_damage_modifier}: modifies damage by where on the body it lands. Regions use the normalised hit
 * position AEAHooks computes (x and z in [-1, 1], y in [0, 1]).
 */
public final class BodyPartDamageModifierPower extends PowerType<BodyPartDamageModifierPower.Config> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("sync", "body_part_damage_modifier");

    public record Region(double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
        public boolean contains(double x, double y, double z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        static Region preset(String name) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "any" -> new Region(-1, 1, 0, 1, -1, 1);
                case "head" -> new Region(-1, 1, 0.88, 1.0, -1, 1);
                case "torso" -> new Region(-0.80, 0.80, 0.50, 0.88, -1, 1);
                case "left_arm" -> new Region(0.80, 1.0, 0.60, 0.88, -1, 1);
                case "right_arm" -> new Region(-1.0, -0.80, 0.60, 0.88, -1, 1);
                case "legs" -> new Region(-1, 1, 0.18, 0.50, -1, 1);
                case "feet" -> new Region(-1, 1, 0.0, 0.18, -1, 1);
                case "achilles_heel" -> new Region(-0.35, 0.35, 0.0, 0.12, 0.30, 1.0);
                case "chest" -> new Region(-0.60, 0.60, 0.70, 0.88, -1.0, 0.0);
                case "back" -> new Region(-0.60, 0.60, 0.50, 0.88, 0.0, 1.0);
                default -> null;
            };
        }
    }

    public record Entry(Region region, List<AttributeModifier> modifiers, Optional<BiEntityAction> action) {}

    public record Config(List<Entry> entries, Optional<DamageCondition> damageCondition, boolean requireHitData, boolean showHitLocation) {}

    // A named body_part wins over the explicit bounds, as in Sync.
    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.optionalFieldOf("body_part").forGetter(e -> Optional.empty()),
        Codec.DOUBLE.optionalFieldOf("x_min", -1.0).forGetter(e -> e.region.minX()),
        Codec.DOUBLE.optionalFieldOf("x_max", 1.0).forGetter(e -> e.region.maxX()),
        Codec.DOUBLE.optionalFieldOf("y_min", 0.0).forGetter(e -> e.region.minY()),
        Codec.DOUBLE.optionalFieldOf("y_max", 1.0).forGetter(e -> e.region.maxY()),
        Codec.DOUBLE.optionalFieldOf("z_min", -1.0).forGetter(e -> e.region.minZ()),
        Codec.DOUBLE.optionalFieldOf("z_max", 1.0).forGetter(e -> e.region.maxZ()),
        AttributeModifier.CODEC.optionalFieldOf("modifier").forGetter(e -> Optional.empty()),
        AttributeModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(Entry::modifiers),
        BiEntityAction.CODEC.optionalFieldOf("bientity_action").forGetter(Entry::action)
    ).apply(i, (part, x0, x1, y0, y1, z0, z1, single, list, action) -> {
        Region r = part.map(Region::preset).orElse(null);
        if (r == null) r = new Region(x0, x1, y0, y1, z0, z1);
        List<AttributeModifier> mods = new ArrayList<>(list);
        single.ifPresent(mods::add);
        return new Entry(r, AttributeModifierHelper.ensureSorted(mods), action);
    }));

    @Override
    public MapCodec<Config> configCodec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            Codecs.singleOrList(ENTRY_CODEC).fieldOf("modifiers").forGetter(Config::entries),
            DamageCondition.CODEC.optionalFieldOf("damage_condition").forGetter(Config::damageCondition),
            Codec.BOOL.optionalFieldOf("require_hit_data", false).forGetter(Config::requireHitData),
            Codec.BOOL.optionalFieldOf("show_hit_location", false).forGetter(Config::showHitLocation)
        ).apply(i, Config::new));
    }
}
