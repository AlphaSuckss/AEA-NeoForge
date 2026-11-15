package dev.alphasucks.aea.compat.sync.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.action.EntityAction;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** {@code sync:action_on_sending_message}: runs actions on, rewrites or cancels the holder's chat messages; see AEAHooks. */
public final class ActionOnSendingMessagePower extends PowerType<ActionOnSendingMessagePower.Config> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("sync", "action_on_sending_message");

    public record Filter(String filter, Optional<EntityAction> before, Optional<EntityAction> after,
                         Optional<String> replacement, boolean prevent) {
        private Pattern pattern() {
            // Sync's filters are regexes, but packs also use plain text with regex characters in it.
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

    public record Config(Optional<ResourceLocation> messageType, List<Filter> filters, int priority) {}

    private static final Codec<Filter> FILTER_CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("filter").forGetter(Filter::filter),
        EntityAction.CODEC.optionalFieldOf("before_action").forGetter(Filter::before),
        EntityAction.CODEC.optionalFieldOf("after_action").forGetter(Filter::after),
        Codec.STRING.optionalFieldOf("replacement").forGetter(Filter::replacement),
        Codec.BOOL.optionalFieldOf("prevent", false).forGetter(Filter::prevent)
    ).apply(i, Filter::new));

    @Override
    public MapCodec<Config> configCodec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            IdCodecs.ID.optionalFieldOf("message_type").forGetter(Config::messageType),
            FILTER_CODEC.optionalFieldOf("filter").forGetter(c -> Optional.empty()),
            FILTER_CODEC.listOf().optionalFieldOf("filters", List.of()).forGetter(Config::filters),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(Config::priority)
        ).apply(i, (t, single, list, p) -> {
            List<Filter> all = new ArrayList<>();
            single.ifPresent(all::add);
            all.addAll(list);
            return new Config(t, all, p);
        }));
    }
}
