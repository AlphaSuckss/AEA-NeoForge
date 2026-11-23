package dev.alphasucks.aea.mindcontrol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alphasucks.aea.AEA;
import dev.overgrown.apoli.action.BiEntityAction;
import dev.overgrown.apoli.codec.LoggedOptionalField;
import dev.overgrown.apoli.condition.BiEntityCondition;
import dev.overgrown.apoli.condition.context.BiEntityCtx;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@code aea:mind_control}: a player takes over another player or a mob.
 *
 * <p>The controller becomes a spectator whose camera is locked to the victim, which is how it works at any distance
 * and in any dimension: the server moves the controller along with the victim and loads what is around it. A
 * {@link ControlBody} stands where the controller was and passes the damage it takes on to them. Where they were and
 * their game mode are written into their player data first, so a crash or disconnect puts them back on their next login.
 *
 * <p>A mob victim is driven here on the server, with its AI paused ({@code MobMindControlMixin}). A player victim moves
 * on their own client, which presses the keys the server relays to it, so the server sees ordinary movement.
 */
public final class MindControl {
    private MindControl() {}

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, AEA.MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<ControlBody>> BODY = ENTITIES.register("mind_control_body",
        () -> EntityType.Builder.<ControlBody>of(ControlBody::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F)
            .clientTrackingRange(10).noSummon().build("aea:mind_control_body"));

    private static final TicketType<ChunkPos> BODY_TICKET = TicketType.create("aea_mind_control", Comparator.comparingLong(ChunkPos::toLong));
    private static final String RETURN_TAG = "aea_mind_control_return";

    // ---- configuration ----

    private static final Codec<Integer> CONTROLS = Codec.withAlternative(Control.CODEC.listOf(), Control.CODEC.xmap(List::of, l -> l.get(0)))
        .xmap(l -> {
            int mask = 0;
            for (Control c : l) mask |= c.bit();
            return mask;
        }, mask -> {
            List<Control> l = new ArrayList<>();
            for (Control c : Control.values()) if (c.in(mask)) l.add(c);
            return l;
        });

    public record Cfg(int controls, Optional<Integer> duration, Optional<BiEntityCondition> releaseCondition, Optional<Integer> struggle,
                      boolean killCredit, boolean closeScreens, boolean bodyDamage, float shareDamage,
                      Optional<BiEntityAction> onStart, Optional<BiEntityAction> onRelease) {}

    public static final MapCodec<Cfg> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        CONTROLS.optionalFieldOf("controls", Control.ALL).forGetter(Cfg::controls),
        Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("duration").forGetter(Cfg::duration),
        LoggedOptionalField.strict("release_condition", BiEntityCondition.CODEC).forGetter(Cfg::releaseCondition),
        Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("struggle").forGetter(Cfg::struggle),
        Codec.BOOL.optionalFieldOf("kill_credit", false).forGetter(Cfg::killCredit),
        Codec.BOOL.optionalFieldOf("close_screens", false).forGetter(Cfg::closeScreens),
        Codec.BOOL.optionalFieldOf("body_damage", true).forGetter(Cfg::bodyDamage),
        Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("share_damage", 0.0F).forGetter(Cfg::shareDamage),
        LoggedOptionalField.of("on_start", BiEntityAction.CODEC).forGetter(Cfg::onStart),
        LoggedOptionalField.of("on_release", BiEntityAction.CODEC).forGetter(Cfg::onRelease)
    ).apply(i, Cfg::new));

    // ---- sessions ----

    private record Return(ResourceKey<Level> dimension, double x, double y, double z, float yRot, float xRot, GameType gameMode) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("dimension", dimension.location().toString());
            t.putDouble("x", x);
            t.putDouble("y", y);
            t.putDouble("z", z);
            t.putFloat("yRot", yRot);
            t.putFloat("xRot", xRot);
            t.putString("gameMode", gameMode.getName());
            return t;
        }

        static Return load(CompoundTag t) {
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("dimension"));
            return new Return(ResourceKey.create(Registries.DIMENSION, dim == null ? Level.OVERWORLD.location() : dim),
                t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getFloat("yRot"), t.getFloat("xRot"),
                GameType.byName(t.getString("gameMode"), GameType.SURVIVAL));
        }
    }

    static final class Session {
        final UUID controllerId, victimId;
        final boolean victimIsPlayer;
        final Cfg cfg;
        final Return back;
        ServerPlayer controller;
        LivingEntity victim;
        ControlBody body;
        ServerLevel bodyLevel;
        ChunkPos bodyChunk;
        ControlInput input = ControlInput.NONE;
        int ticks, struggle;
        boolean ended;

        Session(ServerPlayer controller, LivingEntity victim, Cfg cfg, Return back) {
            this.controller = controller;
            this.victim = victim;
            this.controllerId = controller.getUUID();
            this.victimId = victim.getUUID();
            this.victimIsPlayer = victim instanceof ServerPlayer;
            this.cfg = cfg;
            this.back = back;
        }

        String victimName() {
            return victim.getDisplayName().getString();
        }
    }

    private static final Map<UUID, Session> BY_CONTROLLER = new HashMap<>();
    private static final Map<UUID, Session> BY_VICTIM = new HashMap<>();

    public static boolean isControlled(Entity entity) {
        return !BY_VICTIM.isEmpty() && BY_VICTIM.containsKey(entity.getUUID());
    }

    public static boolean isControlling(Entity entity) {
        return !BY_CONTROLLER.isEmpty() && BY_CONTROLLER.containsKey(entity.getUUID());
    }

    public static boolean controls(Entity controller, Entity victim) {
        Session s = BY_CONTROLLER.isEmpty() ? null : BY_CONTROLLER.get(controller.getUUID());
        return s != null && s.victimId.equals(victim.getUUID());
    }

    /** The player controlling this entity, if its session gives them the kill credit. */
    public static ServerPlayer creditedController(Entity entity) {
        if (entity == null || BY_VICTIM.isEmpty()) return null;
        Session s = BY_VICTIM.get(entity.getUUID());
        return s != null && s.cfg.killCredit() ? s.controller : null;
    }

    static boolean bodyInUse(ControlBody body) {
        Optional<UUID> owner = body.owner();
        if (owner.isEmpty()) return false;
        Session s = BY_CONTROLLER.get(owner.get());
        return s != null && s.body == body;
    }

    // ---- start and end ----

    public static void start(Cfg cfg, Entity actor, Entity target) {
        if (!(actor instanceof ServerPlayer controller) || !(target instanceof LivingEntity victim) || actor == target) return;
        if (victim instanceof ControlBody || !victim.isAlive() || !controller.isAlive()) return;
        if (isControlling(controller) || isControlled(controller) || isControlled(victim) || isControlling(victim)) return;

        Return back = new Return(controller.level().dimension(), controller.getX(), controller.getY(), controller.getZ(),
            controller.getYRot(), controller.getXRot(), controller.gameMode.getGameModeForPlayer());
        controller.getPersistentData().put(RETURN_TAG, back.save());

        Session s = new Session(controller, victim, cfg, back);
        BY_CONTROLLER.put(s.controllerId, s);
        BY_VICTIM.put(s.victimId, s);

        ServerLevel level = controller.serverLevel();
        s.body = ControlBody.of(controller);
        s.bodyLevel = level;
        s.bodyChunk = new ChunkPos(controller.blockPosition());
        level.getChunkSource().addRegionTicket(BODY_TICKET, s.bodyChunk, 2, s.bodyChunk);
        level.addFreshEntity(s.body);

        controller.stopRiding();
        controller.closeContainer();
        controller.setGameMode(GameType.SPECTATOR);
        controller.setCamera(victim);
        if (victim instanceof Mob mob) {
            ((MindControlled) mob).aea$setMindControlled(true);
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
        sendStates(s);
        cfg.onStart().ifPresent(a -> a.run(BiEntityCtx.of(controller, victim, victim.level())));
    }

    /** Ends the session the entity is in, from either side. */
    public static void release(Entity entity) {
        Session s = BY_CONTROLLER.get(entity.getUUID());
        if (s == null) s = BY_VICTIM.get(entity.getUUID());
        if (s != null) end(s, true);
    }

    private static void end(Session s, boolean restoreGameMode) {
        if (s.ended) return;
        s.ended = true;
        BY_CONTROLLER.remove(s.controllerId);
        BY_VICTIM.remove(s.victimId);

        if (s.body != null) s.body.discard();
        if (s.bodyLevel != null) s.bodyLevel.getChunkSource().removeRegionTicket(BODY_TICKET, s.bodyChunk, 2, s.bodyChunk);

        if (s.victim instanceof Mob mob) {
            ((MindControlled) mob).aea$setMindControlled(false);
            mob.xxa = mob.yya = mob.zza = 0;
            mob.setJumping(false);
            mob.setShiftKeyDown(false);
        }
        ServerPlayer controller = online(s.controllerId);
        if (controller != null) {
            restore(controller, s.back, restoreGameMode);
            send(controller, MindControlNetwork.StatePayload.NONE);
        }
        if (s.victimIsPlayer) {
            ServerPlayer victim = online(s.victimId);
            if (victim != null) send(victim, MindControlNetwork.StatePayload.NONE);
        }
        if (controller != null && s.victim != null && s.victim.isAlive()) {
            LivingEntity victim = s.victim;
            s.cfg.onRelease().ifPresent(a -> a.run(BiEntityCtx.of(controller, victim, victim.level())));
        }
    }

    /** Puts the controller back where their body stood. */
    private static void restore(ServerPlayer player, Return back, boolean restoreGameMode) {
        player.getPersistentData().remove(RETURN_TAG);
        player.setCamera(player);
        ServerLevel level = player.server.getLevel(back.dimension());
        if (level == null) level = player.server.overworld();
        player.teleportTo(level, back.x(), back.y(), back.z(), back.yRot(), back.xRot());
        player.fallDistance = 0;
        if (restoreGameMode) player.setGameMode(back.gameMode());
    }

    private static ServerPlayer online(UUID id) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }

    private static void sendStates(Session s) {
        int needed = s.cfg.struggle().orElse(0);
        send(s.controller, new MindControlNetwork.StatePayload(MindControlNetwork.ROLE_CONTROLLER, s.victim.getId(), s.victimName(),
            s.cfg.controls(), needed, s.struggle));
        if (s.victim instanceof ServerPlayer victim) {
            send(victim, new MindControlNetwork.StatePayload(MindControlNetwork.ROLE_VICTIM, s.controller.getId(),
                s.controller.getDisplayName().getString(), s.cfg.controls(), needed, s.struggle));
        }
    }

    private static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload.type())) PacketDistributor.sendToPlayer(player, payload);
    }

    // ---- each tick ----

    private static void tick(MinecraftServer server) {
        if (BY_CONTROLLER.isEmpty()) return;
        for (Session s : new ArrayList<>(BY_CONTROLLER.values())) {
            if (!s.ended) tick(server, s);
        }
    }

    private static void tick(MinecraftServer server, Session s) {
        ServerPlayer controller = server.getPlayerList().getPlayer(s.controllerId);
        if (controller == null) {
            end(s, true);
            return;
        }
        s.controller = controller;
        if (controller.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            // Someone changed their game mode: the session is over, and the new mode stays.
            end(s, false);
            return;
        }

        LivingEntity victim = resolveVictim(server, s);
        if (victim == null) {
            if (!s.victimIsPlayer) {
                end(s, true);
            }
            // A player victim who is dead or between dimensions: wait for them.
            return;
        }
        if (victim != s.victim) {
            s.victim = victim;
            sendStates(s);
        }
        if (controller.getCamera() != victim && victim.isAlive()) {
            controller.setCamera(victim);
            sendStates(s);
        }

        s.ticks++;
        if (s.cfg.duration().isPresent() && s.ticks >= s.cfg.duration().get()) {
            end(s, true);
            return;
        }
        if (s.cfg.releaseCondition().isPresent() && s.cfg.releaseCondition().get().test(BiEntityCtx.of(controller, victim, victim.level()))) {
            end(s, true);
            return;
        }
        if (s.cfg.closeScreens() && victim instanceof ServerPlayer p && p.containerMenu != p.inventoryMenu) p.closeContainer();
        if (s.body != null) {
            if (s.body.isRemoved()) {
                end(s, true);
                return;
            }
            s.body.setHealth(Math.min(controller.getHealth(), s.body.getMaxHealth()));
        }
        if (victim instanceof Mob mob) drive(mob, s.input, s.cfg.controls());
    }

    private static LivingEntity resolveVictim(MinecraftServer server, Session s) {
        LivingEntity v = s.victim;
        if (v != null && !v.isRemoved() && v.isAlive()) return v;
        if (s.victimIsPlayer) {
            ServerPlayer p = server.getPlayerList().getPlayer(s.victimId);
            return p != null && p.isAlive() ? p : null;
        }
        if (v != null && v.getRemovalReason() != Entity.RemovalReason.CHANGED_DIMENSION) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(s.victimId) instanceof LivingEntity found && found.isAlive()) {
                if (found instanceof Mob mob) ((MindControlled) mob).aea$setMindControlled(true);
                return found;
            }
        }
        return null;
    }

    // ---- input ----

    private static void receiveInput(ServerPlayer sender, ControlInput raw) {
        Session s = BY_CONTROLLER.get(sender.getUUID());
        if (s == null) return;
        ControlInput input = raw.masked(s.cfg.controls());
        s.input = input;
        if (s.victim instanceof ServerPlayer victim) {
            send(victim, new MindControlNetwork.RelayPayload(input));
        } else if (s.victim instanceof Mob mob && mob.isAlive()) {
            for (int i = 0; i < input.attacks(); i++) mobAttack(mob, s);
            for (int i = 0; i < input.uses(); i++) mobUse(mob);
        }
    }

    private static void receiveStruggle(ServerPlayer sender, int presses) {
        Session s = BY_VICTIM.get(sender.getUUID());
        if (s == null || s.cfg.struggle().isEmpty() || presses <= 0) return;
        s.struggle += Math.min(presses, 10);
        if (s.struggle >= s.cfg.struggle().get()) end(s, true);
        else sendStates(s);
    }

    /** Moves a mob from the controller's held keys. Runs before the mob ticks, so its own movement uses it. */
    private static void drive(Mob mob, ControlInput in, int controls) {
        if (Control.LOOK.in(controls)) {
            mob.setYRot(in.yRot());
            mob.setYHeadRot(in.yRot());
            mob.setXRot(in.xRot());
        }
        float forward = (in.has(ControlInput.FORWARD) ? 1 : 0) - (in.has(ControlInput.BACK) ? 1 : 0);
        float strafe = (in.has(ControlInput.LEFT) ? 1 : 0) - (in.has(ControlInput.RIGHT) ? 1 : 0);
        float vertical = (in.has(ControlInput.JUMP) ? 1 : 0) - (in.has(ControlInput.SNEAK) ? 1 : 0);
        if (mob instanceof Ghast) {
            // Ghasts steer by pushing themselves, as their own move control does.
            Vec3 dir = mob.getLookAngle().scale(forward).add(Vec3.directionFromRotation(0, mob.getYRot() - 90).scale(strafe)).add(0, vertical, 0);
            if (dir.lengthSqr() > 1.0E-4) mob.setDeltaMovement(mob.getDeltaMovement().add(dir.normalize().scale(0.1)));
            return;
        }
        boolean swims = mob.isNoGravity() || mob.isInWater() || mob.isInLava();
        var flying = mob.getAttribute(Attributes.FLYING_SPEED);
        double speed = mob.isNoGravity() && flying != null ? flying.getValue() : mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (in.has(ControlInput.SPRINT)) speed *= 1.3;
        // Like a mob's own move control: the speed goes in both the input and the mob's speed, as its AI would set them.
        mob.setSpeed((float) speed);
        mob.zza = (float) (forward * speed);
        mob.xxa = (float) (strafe * speed);
        mob.yya = swims ? (float) (vertical * speed) : 0;
        mob.setJumping(!swims && in.has(ControlInput.JUMP));
        mob.setShiftKeyDown(!swims && in.has(ControlInput.SNEAK));
        mob.setSprinting(in.has(ControlInput.SPRINT) && forward > 0);
        if (mob instanceof Blaze && in.has(ControlInput.JUMP)) mob.setDeltaMovement(mob.getDeltaMovement().add(0, 0.08, 0));
    }

    private static EntityHitResult lookedAt(Mob mob, double reach) {
        Vec3 eye = mob.getEyePosition();
        Vec3 end = eye.add(mob.getLookAngle().scale(reach));
        AABB box = mob.getBoundingBox().expandTowards(mob.getLookAngle().scale(reach)).inflate(1.0);
        return ProjectileUtil.getEntityHitResult(mob.level(), mob, eye, end, box, e -> e.isPickable() && !e.isSpectator() && e != mob, 0.3F);
    }

    private static void mobAttack(Mob mob, Session s) {
        mob.swing(InteractionHand.MAIN_HAND);
        EntityHitResult hit = lookedAt(mob, 2.5 + mob.getBbWidth());
        if (hit != null && hit.getEntity() != s.controller && !(hit.getEntity() instanceof ControlBody body && body == s.body)) {
            mob.doHurtTarget(hit.getEntity());
        }
    }

    private static void mobUse(Mob mob) {
        Level level = mob.level();
        Vec3 look = mob.getLookAngle();
        if (mob instanceof Creeper creeper) {
            creeper.ignite();
        } else if (mob instanceof Ghast ghast) {
            LargeFireball ball = new LargeFireball(level, ghast, look, ghast.getExplosionPower());
            ball.setPos(ghast.getX() + look.x * 4, ghast.getY(0.5) + 0.5, ghast.getZ() + look.z * 4);
            level.addFreshEntity(ball);
        } else if (mob instanceof Blaze blaze) {
            SmallFireball ball = new SmallFireball(level, blaze, look.normalize());
            ball.setPos(ball.getX(), blaze.getY(0.5) + 0.5, ball.getZ());
            level.addFreshEntity(ball);
        } else if (mob instanceof RangedAttackMob ranged) {
            EntityHitResult hit = lookedAt(mob, 64);
            if (hit != null && hit.getEntity() instanceof LivingEntity target) ranged.performRangedAttack(target, 1.0F);
        }
    }

    // ---- damage, chat, commands ----

    /** Damage to a body goes to its controller. A blow that could kill them ends the session first, so they die at home. */
    static boolean hurtBody(ControlBody body, DamageSource source, float amount) {
        Optional<UUID> owner = body.owner();
        Session s = owner.isEmpty() ? null : BY_CONTROLLER.get(owner.get());
        if (s == null || s.body != body) {
            body.discard();
            return false;
        }
        // With body_damage off the body can't be hurt, except by what nothing can resist (the void, /kill).
        if (!s.cfg.bodyDamage() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return hurtController(s, source, amount);
    }

    /** Hurts a controlling player, who is a spectator. A blow that could kill them ends the session first, so they die at home. */
    private static boolean hurtController(Session s, DamageSource source, float amount) {
        ServerPlayer controller = online(s.controllerId);
        if (controller == null || amount <= 0 || source.getEntity() == controller) return false;
        if (amount >= controller.getHealth() + controller.getAbsorptionAmount()) {
            end(s, true);
            return controller.hurt(source, amount);
        }
        boolean invulnerable = controller.getAbilities().invulnerable;
        controller.getAbilities().invulnerable = false;
        try {
            return controller.hurt(source, amount);
        } finally {
            controller.getAbilities().invulnerable = invulnerable;
        }
    }

    /** share_damage: part of what the controlled target takes (before its armour) also hits the controller. */
    private static void onDamageTaken(LivingDamageEvent.Post event) {
        if (BY_VICTIM.isEmpty()) return;
        Session s = BY_VICTIM.get(event.getEntity().getUUID());
        if (s == null || s.ended || s.cfg.shareDamage() <= 0) return;
        hurtController(s, event.getSource(), event.getOriginalDamage() * s.cfg.shareDamage());
    }

    private static void onDamage(LivingIncomingDamageEvent event) {
        if (BY_VICTIM.isEmpty()) return;
        LivingEntity victim = event.getEntity();
        Session s = BY_VICTIM.get(victim.getUUID());
        if (s == null || !s.cfg.killCredit()) return;
        Entity attacker = event.getSource().getEntity();
        if (attacker == null || attacker == victim) victim.setLastHurtByPlayer(s.controller);
    }

    private static void onChat(ServerChatEvent event) {
        if (BY_CONTROLLER.isEmpty()) return;
        ServerPlayer sender = event.getPlayer();
        Session asController = BY_CONTROLLER.get(sender.getUUID());
        if (asController != null && Control.CHAT.in(asController.cfg.controls())) {
            event.setCanceled(true);
            LivingEntity victim = asController.victim;
            if (victim != null) {
                sender.server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("chat.type.text", victim.getDisplayName(), event.getMessage()), false);
            }
            return;
        }
        Session asVictim = BY_VICTIM.get(sender.getUUID());
        if (asVictim != null && Control.CHAT.in(asVictim.cfg.controls())) event.setCanceled(true);
    }

    /** True while a controller's command runs as their victim, so the victim's own block doesn't stop it. */
    private static boolean redirecting;

    private static void onCommand(CommandEvent event) {
        if (BY_CONTROLLER.isEmpty()) return;
        var source = event.getParseResults().getContext().getSource();
        if (redirecting || !(source.getEntity() instanceof ServerPlayer player) || source.getEntity() != source.getPlayer()) return;
        Session asController = BY_CONTROLLER.get(player.getUUID());
        if (asController != null && Control.COMMANDS.in(asController.cfg.controls())) {
            event.setCanceled(true);
            LivingEntity victim = asController.victim;
            String command = event.getParseResults().getReader().getString();
            if (victim != null) {
                player.server.execute(() -> {
                    redirecting = true;
                    try {
                        player.server.getCommands().performPrefixedCommand(victim.createCommandSourceStack(), command);
                    } finally {
                        redirecting = false;
                    }
                });
            }
            return;
        }
        Session asVictim = BY_VICTIM.get(player.getUUID());
        if (asVictim != null && !redirecting && Control.COMMANDS.in(asVictim.cfg.controls())) event.setCanceled(true);
    }

    // ---- lifecycle ----

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag tag = player.getPersistentData().getCompound(RETURN_TAG);
        if (!tag.isEmpty() && !isControlling(player)) restore(player, Return.load(tag), true);
        Session asVictim = BY_VICTIM.get(player.getUUID());
        if (asVictim != null) {
            asVictim.victim = player;
            sendStates(asVictim);
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (BY_CONTROLLER.isEmpty()) return;
        Session s = BY_CONTROLLER.get(event.getEntity().getUUID());
        if (s != null) {
            // Saved as a spectator where the victim is; their next login puts them back.
            s.ended = true;
            BY_CONTROLLER.remove(s.controllerId);
            BY_VICTIM.remove(s.victimId);
            if (s.body != null) s.body.discard();
            if (s.bodyLevel != null) s.bodyLevel.getChunkSource().removeRegionTicket(BODY_TICKET, s.bodyChunk, 2, s.bodyChunk);
            if (s.victim instanceof Mob mob) ((MindControlled) mob).aea$setMindControlled(false);
            if (s.victimIsPlayer && online(s.victimId) instanceof ServerPlayer victim) send(victim, MindControlNetwork.StatePayload.NONE);
            return;
        }
        Session v = BY_VICTIM.get(event.getEntity().getUUID());
        if (v != null) end(v, true);
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (BY_VICTIM.isEmpty() || !(event.getEntity() instanceof ServerPlayer player)) return;
        Session s = BY_VICTIM.get(player.getUUID());
        if (s != null) {
            s.victim = player;
            sendStates(s);
        }
    }

    /** A controller killed some other way than through their body ends the session before dying. */
    private static void onDeath(LivingDeathEvent event) {
        if (BY_CONTROLLER.isEmpty()) return;
        Session s = BY_CONTROLLER.get(event.getEntity().getUUID());
        if (s != null) end(s, true);
    }

    private static void onStopping(ServerStoppingEvent event) {
        for (Session s : new ArrayList<>(BY_CONTROLLER.values())) end(s, true);
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, e -> e.put(BODY.get(), LivingEntity.createLivingAttributes().build()));
        modBus.addListener(RegisterPayloadHandlersEvent.class, MindControl::registerPayloads);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Pre.class, e -> tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, MindControl::onDamage);
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, MindControl::onDamageTaken);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ServerChatEvent.class, MindControl::onChat);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, CommandEvent.class, MindControl::onCommand);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, MindControl::onLogin);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, MindControl::onLogout);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, MindControl::onRespawn);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingDeathEvent.class, MindControl::onDeath);
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, MindControl::onStopping);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1").optional();
        r.playToServer(MindControlNetwork.InputPayload.TYPE, MindControlNetwork.InputPayload.CODEC,
            (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) receiveInput(sp, p.input()); }));
        r.playToServer(MindControlNetwork.StrugglePayload.TYPE, MindControlNetwork.StrugglePayload.CODEC,
            (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) receiveStruggle(sp, p.presses()); }));
        r.playToClient(MindControlNetwork.StatePayload.TYPE, MindControlNetwork.StatePayload.CODEC, (p, ctx) -> clientHandle(p, ctx));
        r.playToClient(MindControlNetwork.RelayPayload.TYPE, MindControlNetwork.RelayPayload.CODEC, (p, ctx) -> clientHandle(p, ctx));
    }

    private static void clientHandle(net.minecraft.network.protocol.common.custom.CustomPacketPayload p, IPayloadContext ctx) {
        if (FMLEnvironment.dist.isClient()) ctx.enqueueWork(() -> dev.alphasucks.aea.mindcontrol.client.MindControlClient.receive(p));
    }

    // ---- input from code ----

    /** Feeds input as if the controller's client had sent it. */
    public static void testInput(ServerPlayer controller, ControlInput input) {
        receiveInput(controller, input);
    }

    public static Set<UUID> controllers() {
        return BY_CONTROLLER.keySet();
    }
}
