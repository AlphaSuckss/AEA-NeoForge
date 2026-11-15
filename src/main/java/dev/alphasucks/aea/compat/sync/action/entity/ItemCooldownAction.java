package dev.alphasucks.aea.compat.sync.action.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.condition.context.EntityCtx;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import java.util.List;

/** {@code sync:item_cooldown}: puts items and item tags on cooldown for the player. */
public final class ItemCooldownAction implements ActionType<EntityCtx, ItemCooldownAction.Cfg> {
    public record Cfg(List<ResourceLocation> items, List<ResourceLocation> tags, int ticks) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            IdCodecs.ID.listOf().optionalFieldOf("items", List.of()).forGetter(Cfg::items),
            IdCodecs.ID.listOf().optionalFieldOf("item_tags", List.of()).forGetter(Cfg::tags),
            Codec.INT.optionalFieldOf("ticks", 20).forGetter(Cfg::ticks)
        ).apply(i, Cfg::new));
    }

    @Override
    public void run(Cfg cfg, EntityCtx ctx) {
        if (ctx.level().isClientSide() || !(ctx.raw() instanceof Player p)) return;
        for (ResourceLocation id : cfg.items) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != null) p.getCooldowns().addCooldown(item, cfg.ticks);
        }
        for (ResourceLocation tag : cfg.tags) {
            BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tag))
                .ifPresent(set -> set.forEach(h -> p.getCooldowns().addCooldown(h.value(), cfg.ticks)));
        }
    }
}
