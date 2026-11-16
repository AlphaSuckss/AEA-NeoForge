package dev.alphasucks.aea.compat.addon.power;

import dev.overgrown.apoli.codec.LoggedOptionalField;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.action.ItemAction;
import dev.overgrown.apoli.condition.BlockCondition;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * {@code apoli:modify_drops}: runs an item action on a copy of the tool before a broken block's drops are rolled, then
 * rolls them with that copy. See BlockMixin.
 */
public final class ModifyDropsPower extends PowerType<ModifyDropsPower.Config> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("apoli", "modify_drops");

    public record Config(Optional<ItemAction> itemAction, Optional<BlockCondition> blockCondition) {}

    @Override
    public MapCodec<Config> configCodec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            LoggedOptionalField.of("item_action", ItemAction.CODEC).forGetter(Config::itemAction),
            LoggedOptionalField.strict("block_condition", BlockCondition.CODEC).forGetter(Config::blockCondition)
        ).apply(i, Config::new));
    }
}
