package dev.alphasucks.aea.compat.sync.action.bientity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.AEA;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/** {@code sync:convert_entity}: converts the target mob into another type, like a zombie villager cure. */
public final class ConvertEntityAction implements ActionType<BiEntityCtx, ConvertEntityAction.Cfg> {
    public record Cfg(ResourceLocation convertTo, boolean ignoreDifficulty) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            IdCodecs.ID.fieldOf("convert_to").forGetter(Cfg::convertTo),
            Codec.BOOL.optionalFieldOf("ignore_difficulty", true).forGetter(Cfg::ignoreDifficulty)
        ).apply(i, Cfg::new));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run(Cfg cfg, BiEntityCtx ctx) {
        if (!(ctx.target() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;
        if (!cfg.ignoreDifficulty) {
            // Vanilla's zombie-villager odds: never on easy, half the time on normal.
            Difficulty d = level.getDifficulty();
            if (d == Difficulty.EASY || d == Difficulty.PEACEFUL) return;
            if (d == Difficulty.NORMAL && level.getRandom().nextBoolean()) return;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(cfg.convertTo);
        if (type == null) return;
        try {
            Mob converted = mob.convertTo((EntityType<? extends Mob>) type, true);
            if (converted != null) converted.playAmbientSound();
        } catch (ClassCastException e) {
            AEA.LOGGER.warn("[AEA] sync:convert_entity: {} is not a mob type", cfg.convertTo);
        }
    }
}
