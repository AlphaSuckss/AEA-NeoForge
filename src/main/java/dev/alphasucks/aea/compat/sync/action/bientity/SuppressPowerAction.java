package dev.alphasucks.aea.compat.sync.action.bientity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.action.BiEntityAction;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.Power;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerTypeRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@code sync:suppress_power} and {@code sync:liberate_power}: suppresses (or lifts the suppression of) the target's
 * powers that match the filter. With no filter, every power matches.
 */
public final class SuppressPowerAction implements ActionType<BiEntityCtx, SuppressPowerAction.Cfg> {
    // The suppression source, so liberate_power only lifts what suppress_power did.
    private static final ResourceLocation SUPPRESS_SOURCE = ResourceLocation.fromNamespaceAndPath("sync", "suppress_power");

    public record Cfg(Optional<ResourceLocation> power, List<ResourceLocation> powers, List<ResourceLocation> types,
                      List<ResourceLocation> sources, List<ResourceLocation> ignored, Optional<BiEntityAction> action) {}

    private static final MapCodec<Cfg> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        IdCodecs.ID.optionalFieldOf("power").forGetter(Cfg::power),
        IdCodecs.ID.listOf().optionalFieldOf("powers", List.of()).forGetter(Cfg::powers),
        IdCodecs.ID.listOf().optionalFieldOf("power_types", List.of()).forGetter(Cfg::types),
        IdCodecs.ID.listOf().optionalFieldOf("power_sources", List.of()).forGetter(Cfg::sources),
        IdCodecs.ID.listOf().optionalFieldOf("ignored_powers", List.of()).forGetter(Cfg::ignored),
        BiEntityAction.CODEC.optionalFieldOf("bientity_action").forGetter(Cfg::action)
    ).apply(i, Cfg::new));

    private final boolean liberate;

    public SuppressPowerAction(boolean liberate) {
        this.liberate = liberate;
    }

    @Override
    public MapCodec<Cfg> codec() {
        return CODEC;
    }

    @Override
    public void run(Cfg cfg, BiEntityCtx ctx) {
        if (ctx.target() == null || ctx.level().isClientSide()) return;
        PowerContainer comp = PowerContainer.of(ctx.target());
        if (comp == null) return;
        Set<ResourceLocation> universe = liberate ? new LinkedHashSet<>(comp.suppressedPowers()) : new LinkedHashSet<>(comp.allPowers());
        for (ResourceLocation pid : matching(cfg, comp, universe)) {
            boolean changed = liberate ? comp.unsuppressPower(pid, SUPPRESS_SOURCE) : comp.suppressPower(pid, SUPPRESS_SOURCE);
            if (changed) cfg.action.ifPresent(a -> a.run(ctx));
        }
    }

    private static List<ResourceLocation> matching(Cfg c, PowerContainer comp, Set<ResourceLocation> universe) {
        Set<ResourceLocation> ids = new HashSet<>(c.powers);
        c.power.ifPresent(ids::add);
        Set<ResourceLocation> types = new HashSet<>();
        for (ResourceLocation t : c.types) types.add(PowerTypeRegistry.resolveId(t));
        boolean filtered = !ids.isEmpty() || !types.isEmpty() || !c.sources.isEmpty();
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation pid : universe) {
            if (c.ignored.contains(pid)) continue;
            boolean match = !filtered || ids.contains(pid);
            if (!match && !types.isEmpty()) {
                Power p = ApoliPowers.get(pid);
                if (p != null && types.contains(PowerTypeRegistry.resolveId(p.typeId()))) match = true;
            }
            if (!match && !c.sources.isEmpty()) {
                for (ResourceLocation s : comp.sourcesOf(pid)) {
                    if (c.sources.contains(s)) {
                        match = true;
                        break;
                    }
                }
            }
            if (match) out.add(pid);
        }
        return out;
    }
}
