package dev.alphasucks.aea.compat;

import com.mojang.serialization.MapCodec;
import dev.alphasucks.aea.AEA;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.action.TypedActionRegistry;
import dev.overgrown.apoli.alias.AliasingMapCodec;
import dev.overgrown.apoli.condition.ConditionType;
import dev.overgrown.apoli.condition.TypedConditionRegistry;
import dev.overgrown.apoli.power.PowerType;
import dev.overgrown.apoli.power.PowerTypeRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Small helpers over Overgrown's registries that tolerate ids Overgrown later ships himself. */
public final class Reg {
    private Reg() {}

    public static ResourceLocation id(String s) {
        return ResourceLocation.parse(s);
    }

    public static void power(String id, PowerType<?> type) {
        ResourceLocation rl = id(id);
        if (PowerTypeRegistry.view().containsKey(rl)) {
            AEA.LOGGER.debug("[AEA] Power type {} already provided, keeping Overgrown's", rl);
            return;
        }
        PowerTypeRegistry.register(rl, type);
    }

    public static void powerAlias(String alias, String target) {
        try {
            PowerTypeRegistry.aliases().registerTypeAlias(id(alias), id(target));
        } catch (IllegalStateException e) {
            AEA.LOGGER.debug("[AEA] Power alias {} skipped: {}", alias, e.getMessage());
        }
    }

    public static <CTX> void action(TypedActionRegistry<CTX> reg, String id, ActionType<CTX, ?> type) {
        ResourceLocation rl = id(id);
        if (reg.view().containsKey(rl)) return;
        reg.register(rl, type);
    }

    public static <CTX> void condition(TypedConditionRegistry<CTX> reg, String id, ConditionType<CTX, ?> type) {
        ResourceLocation rl = id(id);
        if (reg.view().containsKey(rl)) return;
        reg.register(rl, type);
    }

    public static <CTX> void actionAlias(TypedActionRegistry<CTX> reg, String alias, String target) {
        try {
            reg.aliases().registerTypeAlias(id(alias), id(target));
        } catch (IllegalStateException e) {
            AEA.LOGGER.debug("[AEA] Action alias {} skipped: {}", alias, e.getMessage());
        }
    }

    public static <CTX> void conditionAlias(TypedConditionRegistry<CTX> reg, String alias, String target) {
        try {
            reg.aliases().registerTypeAlias(id(alias), id(target));
        } catch (IllegalStateException e) {
            AEA.LOGGER.debug("[AEA] Condition alias {} skipped: {}", alias, e.getMessage());
        }
    }

    /** Registers {@code id} as {@code target} with renamed fields (old → new). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <CTX> void conditionRenamed(TypedConditionRegistry<CTX> reg, String id, String target, Map<String, String> renames) {
        ConditionType<CTX, ?> t = reg.get(id(target));
        if (t == null) { AEA.LOGGER.warn("[AEA] Missing condition {} for {}", target, id); return; }
        ConditionType raw = t;
        condition(reg, id, new ConditionType<CTX, Object>() {
            @Override public MapCodec<Object> codec() { return AliasingMapCodec.wrap((MapCodec) raw.codec(), renames); }
            @Override public boolean test(Object config, CTX ctx) { return raw.test(config, ctx); }
        });
    }
}
