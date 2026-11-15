package dev.alphasucks.aea.compat.sync;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.compat.Codecs;
import dev.overgrown.apoli.action.BiEntityAction;
import dev.overgrown.apoli.action.EntityAction;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.condition.DamageCondition;
import dev.overgrown.apoli.data.AttributeModifier;
import dev.overgrown.apoli.data.AttributeModifierHelper;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** sync:body_part_damage_modifier and sync:action_on_sending_message (Sync's field layout). */
public final class SyncPowers2 {
    private SyncPowers2() {}

    // ---------------------------------------------------------------- body parts
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

    static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
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

    public record BodyCfg(List<Entry> entries, Optional<DamageCondition> damageCondition, boolean requireHitData, boolean showHitLocation) {}

    public static final class BodyPartDamage extends PowerType<BodyCfg> {
        @Override
        public MapCodec<BodyCfg> configCodec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                Codecs.singleOrList(ENTRY_CODEC).fieldOf("modifiers").forGetter(BodyCfg::entries),
                DamageCondition.CODEC.optionalFieldOf("damage_condition").forGetter(BodyCfg::damageCondition),
                Codec.BOOL.optionalFieldOf("require_hit_data", false).forGetter(BodyCfg::requireHitData),
                Codec.BOOL.optionalFieldOf("show_hit_location", false).forGetter(BodyCfg::showHitLocation)
            ).apply(i, BodyCfg::new));
        }
    }

    // ---------------------------------------------------------------- chat
    public record Consumer(String filter, Optional<EntityAction> before, Optional<EntityAction> after,
                           Optional<String> replacement, boolean prevent) {
        Pattern pattern() {
            try {
                return Pattern.compile(filter);
            } catch (PatternSyntaxException e) {
                return Pattern.compile(Pattern.quote(filter));
            }
        }

        public boolean matches(String msg) {
            return filter.equals(msg) || pattern().matcher(msg).find();
        }

        public String apply(String msg) {
            return replacement.map(r -> pattern().matcher(msg).replaceAll(r)).orElse(msg);
        }
    }

    static final Codec<Consumer> CONSUMER = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("filter").forGetter(Consumer::filter),
        EntityAction.CODEC.optionalFieldOf("before_action").forGetter(Consumer::before),
        EntityAction.CODEC.optionalFieldOf("after_action").forGetter(Consumer::after),
        Codec.STRING.optionalFieldOf("replacement").forGetter(Consumer::replacement),
        Codec.BOOL.optionalFieldOf("prevent", false).forGetter(Consumer::prevent)
    ).apply(i, Consumer::new));

    public record MessageCfg(Optional<ResourceLocation> messageType, List<Consumer> consumers, int priority) {}

    public static final class SendingMessage extends PowerType<MessageCfg> {
        @Override
        public MapCodec<MessageCfg> configCodec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                IdCodecs.ID.optionalFieldOf("message_type").forGetter(MessageCfg::messageType),
                CONSUMER.optionalFieldOf("filter").forGetter(c -> Optional.empty()),
                CONSUMER.listOf().optionalFieldOf("filters", List.of()).forGetter(MessageCfg::consumers),
                Codec.INT.optionalFieldOf("priority", 0).forGetter(MessageCfg::priority)
            ).apply(i, (t, single, list, p) -> {
                List<Consumer> all = new ArrayList<>();
                single.ifPresent(all::add);
                all.addAll(list);
                return new MessageCfg(t, all, p);
            }));
        }
    }
}
