package dev.alphasucks.aea.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.MapCodec;
import dev.alphasucks.aea.AEA;
import dev.alphasucks.aea.compat.legacy.LegacySetNbt;
import dev.overgrown.apoli.condition.EntityCondition;
import dev.overgrown.apoli.condition.context.EntityCtx;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Loot conditions Origins 1.10 / Apoli 2.9 registered: {@code origins:origin} and {@code apoli:power}.
 * They delegate to the matching entity conditions of Overgrown's Origins/Apoli.
 */
public final class AEALoot {
    private AEALoot() {}

    private static final DeferredRegister<LootItemConditionType> ORIGINS = DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, "origins");
    private static final DeferredRegister<LootItemConditionType> APOLI = DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, "apoli");

    public static final Supplier<LootItemConditionType> ORIGIN = ORIGINS.register("origin",
        () -> new LootItemConditionType(codec("origins:origin", AEALoot.ORIGIN)));
    public static final Supplier<LootItemConditionType> POWER = APOLI.register("power",
        () -> new LootItemConditionType(codec("apoli:power", AEALoot.POWER)));

    private static final DeferredRegister<LootItemFunctionType<?>> AEA_FUNCTIONS =
        DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, "aea");
    public static final Supplier<LootItemFunctionType<LegacySetNbt>> LEGACY_SET_NBT =
        AEA_FUNCTIONS.register("legacy_set_nbt", () -> new LootItemFunctionType<>(LegacySetNbt.CODEC));

    public static void register(IEventBus bus) {
        ORIGINS.register(bus);
        APOLI.register(bus);
        AEA_FUNCTIONS.register(bus);
    }

    private static MapCodec<Delegating> codec(String conditionType, Supplier<LootItemConditionType> self) {
        return MapCodec.assumeMapUnsafe(Codec.PASSTHROUGH).xmap(
            raw -> new Delegating(conditionType, raw, self),
            d -> d.raw);
    }

    public static final class Delegating implements LootItemCondition {
        private final String type;
        private final Dynamic<?> raw;
        private final Supplier<LootItemConditionType> self;
        private volatile EntityCondition parsed;
        private volatile boolean failed;

        Delegating(String type, Dynamic<?> raw, Supplier<LootItemConditionType> self) {
            this.type = type;
            this.raw = raw;
            this.self = self;
        }

        @Override
        public LootItemConditionType getType() {
            return self.get();
        }

        @Override
        public boolean test(LootContext ctx) {
            Entity entity = ctx.getParamOrNull(LootContextParams.THIS_ENTITY);
            if (entity == null) return false;
            EntityCondition cond = condition();
            return cond != null && cond.test(EntityCtx.of(entity, entity.level()));
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private EntityCondition condition() {
            if (parsed != null || failed) return parsed;
            Dynamic d = raw.remove("condition").set("type", raw.createString(type));
            parsed = (EntityCondition) EntityCondition.CODEC.parse(d).resultOrPartial(e -> AEA.LOGGER.warn("[AEA] Bad {} loot condition: {}", type, e)).orElse(null);
            failed = parsed == null;
            return parsed;
        }
    }
}
