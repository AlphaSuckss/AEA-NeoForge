package dev.alphasucks.aea.gamerule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.compat.Reg;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.ConditionTypes;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.condition.context.BiomeCtx;
import dev.overgrown.apoli.condition.context.BlockCtx;
import dev.overgrown.apoli.condition.context.DamageCtx;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.condition.context.FluidCtx;
import dev.overgrown.apoli.condition.context.ItemCtx;
import dev.overgrown.apoli.data.Comparison;
import dev.overgrown.apoli.data.expr.ExprParser;
import dev.overgrown.apoli.data.expr.ExprVars;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.function.Function;

/**
 * {@code aea:gamerule}: tests a data pack gamerule ({@code <namespace>:<name>}) or a vanilla one (its plain name).
 * Without {@code comparison} it passes when the rule is true or not zero. Registered for every condition context
 * that has a level.
 */
public final class GameRuleCondition<CTX> implements ConditionType<CTX, GameRuleCondition.Cfg> {
    public record Cfg(String gamerule, Optional<Comparison> comparison, Optional<Double> compareTo) {
        int value(Level level) {
            ResourceLocation id = gamerule.indexOf(':') < 0 ? null : ResourceLocation.tryParse(gamerule);
            return id != null ? DataGameRules.value(id, level) : DataGameRules.vanillaValue(gamerule, level);
        }
    }

    private static final MapCodec<Cfg> CODEC = RecordCodecBuilder.<Cfg>mapCodec(i -> i.group(
        Codec.STRING.fieldOf("gamerule").forGetter(Cfg::gamerule),
        Comparison.CODEC.optionalFieldOf("comparison").forGetter(Cfg::comparison),
        Codec.DOUBLE.optionalFieldOf("compare_to").forGetter(Cfg::compareTo)
    ).apply(i, Cfg::new)).validate(cfg -> cfg.comparison().isPresent() != cfg.compareTo().isPresent()
        ? DataResult.error(() -> "aea:gamerule needs both \"comparison\" and \"compare_to\", or neither")
        : DataResult.success(cfg));

    private final Function<CTX, Level> level;

    private GameRuleCondition(Function<CTX, Level> level) {
        this.level = level;
    }

    @Override
    public MapCodec<Cfg> codec() {
        return CODEC;
    }

    @Override
    public boolean test(Cfg cfg, CTX ctx) {
        int value = cfg.value(level.apply(ctx));
        return cfg.comparison().map(c -> c.compare(value, cfg.compareTo().get())).orElse(value != 0);
    }

    public static void register() {
        String id = "aea:gamerule";
        Reg.condition(ConditionTypes.ENTITY, id, new GameRuleCondition<EntityCtx>(EntityCtx::level));
        Reg.condition(ConditionTypes.BI_ENTITY, id, new GameRuleCondition<BiEntityCtx>(BiEntityCtx::level));
        Reg.condition(ConditionTypes.BLOCK, id, new GameRuleCondition<BlockCtx>(BlockCtx::level));
        Reg.condition(ConditionTypes.ITEM, id, new GameRuleCondition<ItemCtx>(ItemCtx::level));
        Reg.condition(ConditionTypes.DAMAGE, id, new GameRuleCondition<DamageCtx>(DamageCtx::level));
        Reg.condition(ConditionTypes.FLUID, id, new GameRuleCondition<FluidCtx>(FluidCtx::level));
        Reg.condition(ConditionTypes.BIOME, id, new GameRuleCondition<BiomeCtx>(BiomeCtx::level));
        // gamerule(<namespace>:<name>) in expressions: the rule's value, with true and false as 1 and 0.
        ExprParser.registerIdFunction("gamerule", (rule, args, name, at) -> new ExprVars.ResolvedVar(
            (entity, container, level, value) -> DataGameRules.value(rule, level != null ? level : entity != null ? entity.level() : null), false));
    }
}
