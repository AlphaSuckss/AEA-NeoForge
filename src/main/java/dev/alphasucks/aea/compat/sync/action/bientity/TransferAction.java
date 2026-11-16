package dev.alphasucks.aea.compat.sync.action.bientity;

import dev.overgrown.apoli.codec.LoggedOptionalField;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.compat.sync.StolenPowers;
import dev.overgrown.apoli.PowerContainerAttachment;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.action.EntityAction;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.PowerContainer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@code sync:transfer}: steals the target's powers (grouped by the source that granted them) into the actor's stolen
 * slots, or gives the selected stolen package to the target.
 */
public final class TransferAction implements ActionType<BiEntityCtx, TransferAction.Cfg> {
    public record Cfg(String mode, Optional<ResourceLocation> source, ResourceLocation transferSource, boolean strip,
                      Optional<EntityAction> actorAction, Optional<EntityAction> targetAction) {}

    @Override
    public MapCodec<Cfg> codec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("mode", "steal").forGetter(Cfg::mode),
            IdCodecs.ID.optionalFieldOf("source").forGetter(Cfg::source),
            IdCodecs.ID.optionalFieldOf("transfer_source", StolenPowers.DEFAULT_SOURCE).forGetter(Cfg::transferSource),
            Codec.BOOL.optionalFieldOf("strip_from_donor", true).forGetter(Cfg::strip),
            LoggedOptionalField.of("actor_action", EntityAction.CODEC).forGetter(Cfg::actorAction),
            LoggedOptionalField.of("target_action", EntityAction.CODEC).forGetter(Cfg::targetAction)
        ).apply(i, Cfg::new));
    }

    @Override
    public void run(Cfg cfg, BiEntityCtx ctx) {
        Entity actor = ctx.actor(), target = ctx.target();
        if (actor == null || target == null || ctx.level().isClientSide()) return;
        PowerContainer a = PowerContainerAttachment.getOrCreate(actor);
        PowerContainer t = PowerContainerAttachment.getOrCreate(target);
        if (a == null || t == null) return;
        boolean stealing = cfg.mode.equalsIgnoreCase("auto") ? StolenPowers.isStealing(actor) : !cfg.mode.equalsIgnoreCase("give");
        if (stealing) steal(cfg, actor, a, t);
        else if (!give(cfg, actor, a, t)) return; // nothing selected: Sync skips the follow-up actions too
        cfg.actorAction.ifPresent(x -> x.run(EntityCtx.of(actor, actor.level())));
        cfg.targetAction.ifPresent(x -> x.run(EntityCtx.of(target, target.level())));
    }

    private static void steal(Cfg cfg, Entity actor, PowerContainer a, PowerContainer t) {
        Map<ResourceLocation, List<ResourceLocation>> toSteal = new LinkedHashMap<>();
        if (cfg.source.isPresent()) {
            List<ResourceLocation> ps = StolenPowers.topLevel(StolenPowers.powersFromSource(t, cfg.source.get()));
            if (!ps.isEmpty()) toSteal.put(cfg.source.get(), ps);
        } else {
            Set<ResourceLocation> seen = new LinkedHashSet<>();
            for (ResourceLocation p : t.allPowers()) {
                for (ResourceLocation s : t.sourcesOf(p)) if (!s.equals(cfg.transferSource)) seen.add(s);
            }
            for (ResourceLocation s : seen) {
                if (ApoliPowers.get(s) != null) continue; // skip sub-power "sources" (parent powers)
                List<ResourceLocation> ps = StolenPowers.topLevel(StolenPowers.powersFromSource(t, s));
                if (!ps.isEmpty()) toSteal.put(s, ps);
            }
        }
        for (Map.Entry<ResourceLocation, List<ResourceLocation>> e : toSteal.entrySet()) {
            for (ResourceLocation p : e.getValue()) {
                if (cfg.strip) t.removePower(p, e.getKey());
                a.addPower(p, cfg.transferSource);
            }
            StolenPowers.registerSteal(actor.getUUID(), e.getKey(), e.getValue());
        }
    }

    private static boolean give(Cfg cfg, Entity actor, PowerContainer a, PowerContainer t) {
        ResourceLocation sel = StolenPowers.selected(actor);
        if (sel == null) return false;
        List<ResourceLocation> pkg = StolenPowers.powersFor(actor, sel);
        if (pkg.isEmpty()) return false;
        for (ResourceLocation p : pkg) {
            if (cfg.strip) a.removePower(p, cfg.transferSource);
            t.addPower(p, sel);
        }
        if (cfg.strip) StolenPowers.deregister(actor.getUUID(), sel);
        return true;
    }
}
