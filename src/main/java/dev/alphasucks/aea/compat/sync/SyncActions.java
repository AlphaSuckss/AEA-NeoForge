package dev.alphasucks.aea.compat.sync;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.AEA;
import dev.overgrown.apoli.action.ActionType;
import dev.overgrown.apoli.action.BiEntityAction;
import dev.overgrown.apoli.action.EntityAction;
import dev.overgrown.apoli.codec.IdCodecs;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.entity.SavedLocations;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.Power;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.PowerContainerAttachment;
import dev.overgrown.apoli.power.PowerTypeRegistry;
import dev.overgrown.apoli.power.PowerTypes;
import dev.overgrown.apoli.power.builtin.ActionOnKeyPressPower;
import dev.overgrown.apoli.rope.Rope;
import dev.overgrown.apoli.rope.RopeAnchor;
import dev.overgrown.apoli.rope.RopeEndpointSource;
import dev.overgrown.apoli.rope.RopeManager;
import dev.overgrown.apoli.rope.RopeParams;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Sync 1.11.8 actions that Overgrown's Apoli does not ship with Sync's fields. */
public final class SyncActions {
    private SyncActions() {}

    static final ResourceLocation ROPE_TEXTURE = ResourceLocation.fromNamespaceAndPath("apoli", "textures/rope/rope.png");

    static ResourceLocation ropeTexture(ResourceLocation tex) {
        // Sync's own rope texture isn't shipped on NeoForge; Overgrown's Apoli has the same art under apoli:.
        return tex.getNamespace().equals("sync") ? ResourceLocation.fromNamespaceAndPath("apoli", tex.getPath()) : tex;
    }

    static RopeParams params(float maxLength) {
        RopeParams d = RopeParams.DEFAULT;
        return new RopeParams(d.minLength(), maxLength, d.stiffness(), d.radialDamping(), d.springScaling(), d.swingBoost(),
            d.maxSwingSpeed(), d.controlAccel(), d.reelStep(), d.slackPullRate(), d.constrainFrom(), d.constrainTo(),
            d.controllable(), d.mode(), d.breakBeyond(), d.startLength());
    }

    public record RopeCfg(float maxLength, ResourceLocation texture, String mode) {}

    static final MapCodec<RopeCfg> ROPE_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.FLOAT.optionalFieldOf("max_length", 30f).forGetter(RopeCfg::maxLength),
        IdCodecs.ID.optionalFieldOf("texture", ROPE_TEXTURE).forGetter(RopeCfg::texture),
        Codec.STRING.optionalFieldOf("mode", "toggle").forGetter(RopeCfg::mode)
    ).apply(i, RopeCfg::new));

    private static boolean detachNear(UUID owner, Vec3 anchor, ServerLevel level) {
        boolean any = false;
        for (Rope r : new ArrayList<>(RopeManager.forOwner(owner))) {
            Vec3 a = r.to.position(level);
            Vec3 b = r.from.position(level);
            if ((a != null && a.distanceToSqr(anchor) < 2.25) || (b != null && b.distanceToSqr(anchor) < 2.25)) {
                RopeManager.remove(r.id);
                any = true;
            }
        }
        return any;
    }

    private static void rope(RopeCfg c, Entity actor, boolean entityTarget) {
        if (!(actor instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        String mode = c.mode.toLowerCase(Locale.ROOT);
        if (mode.equals("detach_all")) {
            RopeManager.removeMatching(player.getUUID(), null, null);
            return;
        }
        RopeAnchor to = new RopeEndpointSource(RopeEndpointSource.Type.RAYCAST, c.maxLength, entityTarget, !entityTarget, Optional.empty())
            .resolve(player, null, level);
        if (to == null) return;
        if (entityTarget && !(to instanceof RopeAnchor.OfEntity)) return;
        Vec3 anchor = to.position(level);
        RopeAnchor from = RopeEndpointSource.self().resolve(player, null, level);
        if (from == null || anchor == null) return;
        switch (mode) {
            case "detach" -> detachNear(player.getUUID(), anchor, level);
            case "attach" -> RopeManager.create(level, from, to, null, player.getUUID(), params(c.maxLength), ropeTexture(c.texture));
            default -> {
                if (!detachNear(player.getUUID(), anchor, level)) {
                    RopeManager.create(level, from, to, null, player.getUUID(), params(c.maxLength), ropeTexture(c.texture));
                }
            }
        }
    }

    // ---------------------------------------------------------------- sync:attach_rope / attach_rope_entity
    public static final class AttachRope implements ActionType<EntityCtx, RopeCfg> {
        private final boolean entity;
        public AttachRope(boolean entity) { this.entity = entity; }
        @Override public MapCodec<RopeCfg> codec() { return ROPE_CODEC; }
        @Override public void run(RopeCfg c, EntityCtx ctx) { rope(c, ctx.raw(), entity); }
    }

    // ---------------------------------------------------------------- sync:rope_leash (bi-entity)
    public static final class RopeLeash implements ActionType<BiEntityCtx, RopeCfg> {
        @Override public MapCodec<RopeCfg> codec() { return ROPE_CODEC; }

        @Override
        public void run(RopeCfg c, BiEntityCtx ctx) {
            Entity actor = ctx.actor(), target = ctx.target();
            if (actor == null || target == null || !(actor.level() instanceof ServerLevel level)) return;
            String mode = c.mode.toLowerCase(Locale.ROOT);
            boolean exists = RopeManager.connected(actor.getUUID(), target.getUUID());
            if (mode.equals("detach_all")) { RopeManager.removeMatching(actor.getUUID(), null, null); return; }
            if (exists && !mode.equals("attach")) {
                for (Rope r : new ArrayList<>(RopeManager.forEntity(actor.getUUID()))) {
                    if (r.touches(target.getUUID())) RopeManager.remove(r.id);
                }
                return;
            }
            if (mode.equals("detach")) return;
            RopeManager.create(level, new RopeAnchor.OfEntity(actor.getId(), Vec3.ZERO), new RopeAnchor.OfEntity(target.getId(), Vec3.ZERO),
                null, actor.getUUID(), params(c.maxLength), ropeTexture(c.texture));
        }
    }

    // ---------------------------------------------------------------- sync:print
    public record PrintCfg(String message, boolean showInChat, String logger) {}

    public static final class Print implements ActionType<EntityCtx, PrintCfg> {
        @Override
        public MapCodec<PrintCfg> codec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("message").forGetter(PrintCfg::message),
                Codec.BOOL.optionalFieldOf("show_in_chat", false).forGetter(PrintCfg::showInChat),
                Codec.STRING.optionalFieldOf("logger_id", "Sync/PrintAction").forGetter(PrintCfg::logger)
            ).apply(i, PrintCfg::new));
        }

        @Override
        public void run(PrintCfg c, EntityCtx ctx) {
            LoggerFactory.getLogger(c.logger).info(c.message);
            if (c.showInChat && ctx.raw() instanceof ServerPlayer p) p.sendSystemMessage(Component.literal(c.message));
        }
    }

    // ---------------------------------------------------------------- sync:item_cooldown
    public record ItemCooldownCfg(List<ResourceLocation> items, List<ResourceLocation> tags, int ticks) {}

    public static final class ItemCooldown implements ActionType<EntityCtx, ItemCooldownCfg> {
        @Override
        public MapCodec<ItemCooldownCfg> codec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                IdCodecs.ID.listOf().optionalFieldOf("items", List.of()).forGetter(ItemCooldownCfg::items),
                IdCodecs.ID.listOf().optionalFieldOf("item_tags", List.of()).forGetter(ItemCooldownCfg::tags),
                Codec.INT.optionalFieldOf("ticks", 20).forGetter(ItemCooldownCfg::ticks)
            ).apply(i, ItemCooldownCfg::new));
        }

        @Override
        public void run(ItemCooldownCfg c, EntityCtx ctx) {
            if (ctx.level().isClientSide() || !(ctx.raw() instanceof Player p)) return;
            for (ResourceLocation id : c.items) {
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item != null) p.getCooldowns().addCooldown(item, c.ticks);
            }
            for (ResourceLocation tag : c.tags) {
                BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tag))
                    .ifPresent(set -> set.forEach(h -> p.getCooldowns().addCooldown(h.value(), c.ticks)));
            }
        }
    }

    // ---------------------------------------------------------------- sync:convert_entity (bi-entity)
    public record ConvertCfg(ResourceLocation convertTo, boolean ignoreDifficulty) {}

    public static final class ConvertEntity implements ActionType<BiEntityCtx, ConvertCfg> {
        @Override
        public MapCodec<ConvertCfg> codec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                IdCodecs.ID.fieldOf("convert_to").forGetter(ConvertCfg::convertTo),
                Codec.BOOL.optionalFieldOf("ignore_difficulty", true).forGetter(ConvertCfg::ignoreDifficulty)
            ).apply(i, ConvertCfg::new));
        }

        @Override
        @SuppressWarnings("unchecked")
        public void run(ConvertCfg c, BiEntityCtx ctx) {
            if (!(ctx.target() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;
            if (!c.ignoreDifficulty) {
                Difficulty d = level.getDifficulty();
                if (d == Difficulty.EASY || d == Difficulty.PEACEFUL) return;
                if (d == Difficulty.NORMAL && level.getRandom().nextBoolean()) return;
            }
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(c.convertTo);
            if (type == null) return;
            try {
                Mob converted = mob.convertTo((EntityType<? extends Mob>) type, true);
                if (converted != null) converted.playAmbientSound();
            } catch (ClassCastException e) {
                AEA.LOGGER.warn("[AEA] sync:convert_entity: {} is not a mob type", c.convertTo);
            }
        }
    }

    // ---------------------------------------------------------------- sync:save_location (bi-entity)
    public record SaveLocCfg(String id, boolean overwrite, boolean track) {}

    public static final class SaveLocationBi implements ActionType<BiEntityCtx, SaveLocCfg> {
        @Override
        public MapCodec<SaveLocCfg> codec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(SaveLocCfg::id),
                Codec.BOOL.optionalFieldOf("overwrite", true).forGetter(SaveLocCfg::overwrite),
                Codec.BOOL.optionalFieldOf("track_entity", false).forGetter(SaveLocCfg::track)
            ).apply(i, SaveLocCfg::new));
        }

        @Override
        public void run(SaveLocCfg c, BiEntityCtx ctx) {
            Entity actor = ctx.actor(), target = ctx.target();
            if (actor == null || target == null || actor.level().isClientSide()) return;
            SavedLocations locs = SavedLocations.of(actor);
            if (locs == null) return;
            locs.put(actor.getUUID(), c.id, new SavedLocations.Location(target.getX(), target.getY(), target.getZ(),
                target.level().dimension(), target.getYRot(), target.getXRot()), c.overwrite);
        }
    }

    // ---------------------------------------------------------------- sync:suppress_power / sync:liberate_power (bi-entity)
    public record PowerFilterCfg(Optional<ResourceLocation> power, List<ResourceLocation> powers, List<ResourceLocation> types,
                                 List<ResourceLocation> sources, List<ResourceLocation> ignored, Optional<BiEntityAction> action) {}

    static final MapCodec<PowerFilterCfg> FILTER_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        IdCodecs.ID.optionalFieldOf("power").forGetter(PowerFilterCfg::power),
        IdCodecs.ID.listOf().optionalFieldOf("powers", List.of()).forGetter(PowerFilterCfg::powers),
        IdCodecs.ID.listOf().optionalFieldOf("power_types", List.of()).forGetter(PowerFilterCfg::types),
        IdCodecs.ID.listOf().optionalFieldOf("power_sources", List.of()).forGetter(PowerFilterCfg::sources),
        IdCodecs.ID.listOf().optionalFieldOf("ignored_powers", List.of()).forGetter(PowerFilterCfg::ignored),
        BiEntityAction.CODEC.optionalFieldOf("bientity_action").forGetter(PowerFilterCfg::action)
    ).apply(i, PowerFilterCfg::new));

    public static final ResourceLocation SUPPRESS_SOURCE = ResourceLocation.fromNamespaceAndPath("sync", "suppress_power");

    static List<ResourceLocation> matching(PowerFilterCfg c, PowerContainer comp, Set<ResourceLocation> universe) {
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
                for (ResourceLocation s : comp.sourcesOf(pid)) if (c.sources.contains(s)) { match = true; break; }
            }
            if (match) out.add(pid);
        }
        return out;
    }

    public static final class SuppressPower implements ActionType<BiEntityCtx, PowerFilterCfg> {
        private final boolean liberate;
        public SuppressPower(boolean liberate) { this.liberate = liberate; }
        @Override public MapCodec<PowerFilterCfg> codec() { return FILTER_CODEC; }

        @Override
        public void run(PowerFilterCfg c, BiEntityCtx ctx) {
            if (ctx.target() == null || ctx.level().isClientSide()) return;
            PowerContainer comp = PowerContainer.of(ctx.target());
            if (comp == null) return;
            Set<ResourceLocation> universe = liberate ? new LinkedHashSet<>(comp.suppressedPowers()) : new LinkedHashSet<>(comp.allPowers());
            for (ResourceLocation pid : matching(c, comp, universe)) {
                boolean changed = liberate ? comp.unsuppressPower(pid, SUPPRESS_SOURCE) : comp.suppressPower(pid, SUPPRESS_SOURCE);
                if (changed) c.action.ifPresent(a -> a.run(ctx));
            }
        }
    }

    // ---------------------------------------------------------------- sync:transfer (bi-entity) and the stolen-power actions
    public record TransferCfg(String mode, Optional<ResourceLocation> source, ResourceLocation transferSource, boolean strip,
                              Optional<EntityAction> actorAction, Optional<EntityAction> targetAction) {}

    public static final class Transfer implements ActionType<BiEntityCtx, TransferCfg> {
        @Override
        public MapCodec<TransferCfg> codec() {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("mode", "steal").forGetter(TransferCfg::mode),
                IdCodecs.ID.optionalFieldOf("source").forGetter(TransferCfg::source),
                IdCodecs.ID.optionalFieldOf("transfer_source", StolenPowers.DEFAULT_SOURCE).forGetter(TransferCfg::transferSource),
                Codec.BOOL.optionalFieldOf("strip_from_donor", true).forGetter(TransferCfg::strip),
                EntityAction.CODEC.optionalFieldOf("actor_action").forGetter(TransferCfg::actorAction),
                EntityAction.CODEC.optionalFieldOf("target_action").forGetter(TransferCfg::targetAction)
            ).apply(i, TransferCfg::new));
        }

        @Override
        public void run(TransferCfg c, BiEntityCtx ctx) {
            Entity actor = ctx.actor(), target = ctx.target();
            if (actor == null || target == null || ctx.level().isClientSide()) return;
            PowerContainer a = PowerContainerAttachment.getOrCreate(actor);
            PowerContainer t = PowerContainerAttachment.getOrCreate(target);
            if (a == null || t == null) return;
            boolean stealing = c.mode.equalsIgnoreCase("auto") ? StolenPowers.isStealing(actor) : !c.mode.equalsIgnoreCase("give");
            if (stealing) {
                Map<ResourceLocation, List<ResourceLocation>> toSteal = new LinkedHashMap<>();
                if (c.source.isPresent()) {
                    List<ResourceLocation> ps = StolenPowers.topLevel(StolenPowers.powersFromSource(t, c.source.get()));
                    if (!ps.isEmpty()) toSteal.put(c.source.get(), ps);
                } else {
                    Set<ResourceLocation> seen = new LinkedHashSet<>();
                    for (ResourceLocation p : t.allPowers()) for (ResourceLocation s : t.sourcesOf(p)) if (!s.equals(c.transferSource)) seen.add(s);
                    for (ResourceLocation s : seen) {
                        if (ApoliPowers.get(s) != null) continue; // skip sub-power "sources" (parent powers)
                        List<ResourceLocation> ps = StolenPowers.topLevel(StolenPowers.powersFromSource(t, s));
                        if (!ps.isEmpty()) toSteal.put(s, ps);
                    }
                }
                for (Map.Entry<ResourceLocation, List<ResourceLocation>> e : toSteal.entrySet()) {
                    for (ResourceLocation p : e.getValue()) {
                        if (c.strip) t.removePower(p, e.getKey());
                        a.addPower(p, c.transferSource);
                    }
                    StolenPowers.registerSteal(actor.getUUID(), e.getKey(), e.getValue());
                }
            } else {
                ResourceLocation sel = StolenPowers.selected(actor);
                if (sel == null) return;
                List<ResourceLocation> pkg = StolenPowers.powersFor(actor, sel);
                if (pkg.isEmpty()) return;
                for (ResourceLocation p : pkg) {
                    if (c.strip) a.removePower(p, c.transferSource);
                    t.addPower(p, sel);
                }
                if (c.strip) StolenPowers.deregister(actor.getUUID(), sel);
            }
            c.actorAction.ifPresent(x -> x.run(EntityCtx.of(actor, actor.level())));
            c.targetAction.ifPresent(x -> x.run(EntityCtx.of(target, target.level())));
        }
    }

    public static final class CycleStolen implements ActionType<EntityCtx, Integer> {
        @Override public MapCodec<Integer> codec() { return Codec.INT.optionalFieldOf("delta", 1); }
        @Override public void run(Integer delta, EntityCtx ctx) { if (!ctx.level().isClientSide()) StolenPowers.cycle(ctx.raw(), delta); }
    }

    public static final class ToggleTransferMode implements ActionType<EntityCtx, Object> {
        @Override public MapCodec<Object> codec() { return MapCodec.unit(Boolean.TRUE); }
        @Override public void run(Object c, EntityCtx ctx) { if (!ctx.level().isClientSide()) StolenPowers.toggleMode(ctx.raw()); }
    }

    public static final class UseSelectedStolen implements ActionType<EntityCtx, Object> {
        @Override public MapCodec<Object> codec() { return MapCodec.unit(Boolean.TRUE); }

        @Override
        public void run(Object c, EntityCtx ctx) {
            if (ctx.level().isClientSide()) return;
            PowerContainer comp = PowerContainer.of(ctx.raw());
            if (comp == null) return;
            ResourceLocation sel = StolenPowers.selected(ctx.raw());
            if (sel == null) return;
            ResourceLocation keyPress = PowerTypeRegistry.resolveId(ResourceLocation.fromNamespaceAndPath("apoli", "action_on_key_press"));
            List<ResourceLocation> targets = new ArrayList<>();
            for (ResourceLocation p : StolenPowers.powersFor(ctx.raw(), sel)) {
                targets.add(p);
                for (ResourceLocation sub : comp.allPowers()) if (comp.sourcesOf(sub).contains(p)) targets.add(sub);
            }
            for (ResourceLocation p : targets) {
                Power power = ApoliPowers.get(p);
                if (power == null || !keyPress.equals(PowerTypeRegistry.resolveId(power.typeId()))) continue;
                if (power.config() instanceof ActionOnKeyPressPower.Config cfg) PowerTypes.ACTION_ON_KEY_PRESS.tryActivate(p, cfg, comp);
            }
        }
    }
}
