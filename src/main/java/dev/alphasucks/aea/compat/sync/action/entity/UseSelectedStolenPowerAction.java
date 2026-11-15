package dev.alphasucks.aea.compat.sync.action.entity;

import com.mojang.serialization.MapCodec;
import dev.alphasucks.aea.compat.sync.StolenPowers;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.Power;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerTypeRegistry;
import dev.overgrown.apoli.power.PowerTypes;
import dev.overgrown.apoli.power.builtin.ActionOnKeyPressPower;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** {@code sync:use_selected_stolen_power}: fires the key-press powers of the selected stolen package. */
public final class UseSelectedStolenPowerAction implements ActionType<EntityCtx, Object> {
    @Override
    public MapCodec<Object> codec() {
        return MapCodec.unit(Boolean.TRUE);
    }

    @Override
    public void run(Object cfg, EntityCtx ctx) {
        if (ctx.level().isClientSide()) return;
        PowerContainer comp = PowerContainer.of(ctx.raw());
        if (comp == null) return;
        ResourceLocation sel = StolenPowers.selected(ctx.raw());
        if (sel == null) return;
        ResourceLocation keyPress = PowerTypeRegistry.resolveId(ResourceLocation.fromNamespaceAndPath("apoli", "action_on_key_press"));
        // The stolen powers and their sub-powers.
        List<ResourceLocation> targets = new ArrayList<>();
        for (ResourceLocation p : StolenPowers.powersFor(ctx.raw(), sel)) {
            targets.add(p);
            for (ResourceLocation sub : comp.allPowers()) if (comp.sourcesOf(sub).contains(p)) targets.add(sub);
        }
        for (ResourceLocation p : targets) {
            Power power = ApoliPowers.get(p);
            if (power == null || !keyPress.equals(PowerTypeRegistry.resolveId(power.typeId()))) continue;
            if (power.config() instanceof ActionOnKeyPressPower.Config c) PowerTypes.ACTION_ON_KEY_PRESS.tryActivate(p, c, comp);
        }
    }
}
