package dev.alphasucks.aea.compat.legacy.power;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.data.AttributeModifier;
import dev.overgrown.apoli.data.AttributeModifierHelper;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Apoli 2.9's plain modifier powers that Overgrown dropped: {@code apoli:modify_lava_speed} and
 * {@code apoli:modify_insomnia_ticks}. The config is the sorted modifier list; the mixins apply it.
 */
public final class ModifierPower extends PowerType<List<AttributeModifier>> {
    public static final ResourceLocation MODIFY_LAVA_SPEED = ResourceLocation.fromNamespaceAndPath("apoli", "modify_lava_speed");
    public static final ResourceLocation MODIFY_INSOMNIA_TICKS = ResourceLocation.fromNamespaceAndPath("apoli", "modify_insomnia_ticks");

    @Override
    public MapCodec<List<AttributeModifier>> configCodec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            AttributeModifier.CODEC.optionalFieldOf("modifier").forGetter(l -> Optional.empty()),
            AttributeModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(l -> l)
        ).apply(i, (one, many) -> {
            List<AttributeModifier> all = new ArrayList<>(many);
            one.ifPresent(all::add);
            return AttributeModifierHelper.ensureSorted(all);
        }));
    }
}
