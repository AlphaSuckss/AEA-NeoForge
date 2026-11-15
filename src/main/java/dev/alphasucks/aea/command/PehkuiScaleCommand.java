package dev.alphasucks.aea.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.overgrown.apoli.scale.ScaleEasing;
import dev.overgrown.apoli.scale.ScaleType;
import dev.overgrown.apoli.scale.ScaleTypeCodec;
import dev.overgrown.apoli.scale.Scales;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pehkui's {@code /scale} command, backed by Overgrown's Apoli scale system. Only registered when Pehkui
 * itself is not installed, so 1.20.1 packs that call {@code scale set pehkui:height 0.5 @s} keep working.
 */
public final class PehkuiScaleCommand {
    private PehkuiScaleCommand() {}

    private static final int DEFAULT_DELAY = 20;
    private static final Map<UUID, Map<ResourceLocation, Integer>> DELAYS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<ResourceLocation, ScaleEasing>> EASINGS = new ConcurrentHashMap<>();
    private static final ResourceLocation BASE = ResourceLocation.fromNamespaceAndPath("pehkui", "base");

    /** Pehkui types Overgrown's scale system has no equivalent for, mapped onto vanilla attributes. */
    private static List<Holder<Attribute>> attributesFor(String path) {
        return switch (path) {
            case "reach" -> List.of(Attributes.BLOCK_INTERACTION_RANGE, Attributes.ENTITY_INTERACTION_RANGE);
            case "block_reach" -> List.of(Attributes.BLOCK_INTERACTION_RANGE);
            case "entity_reach" -> List.of(Attributes.ENTITY_INTERACTION_RANGE);
            case "health" -> List.of(Attributes.MAX_HEALTH);
            case "attack_speed" -> List.of(Attributes.ATTACK_SPEED);
            default -> List.of();
        };
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("scale").requires(s -> s.hasPermission(2))
            .then(op("set", Op.SET))
            .then(op("add", Op.ADD))
            .then(op("subtract", Op.SUBTRACT))
            .then(op("multiply", Op.MULTIPLY))
            .then(op("divide", Op.DIVIDE))
            .then(op("power", Op.POWER))
            .then(Commands.literal("reset")
                .executes(c -> reset(c, null, List.of(c.getSource().getEntityOrException())))
                .then(Commands.argument("targets", EntityArgument.entities())
                    .executes(c -> reset(c, null, EntityArgument.getEntities(c, "targets"))))
                .then(Commands.argument("scale_type", ResourceLocationArgument.id())
                    .executes(c -> reset(c, ResourceLocationArgument.getId(c, "scale_type"), List.of(c.getSource().getEntityOrException())))
                    .then(Commands.argument("targets", EntityArgument.entities())
                        .executes(c -> reset(c, ResourceLocationArgument.getId(c, "scale_type"), EntityArgument.getEntities(c, "targets"))))))
            .then(Commands.literal("get")
                .executes(c -> get(c, BASE, c.getSource().getEntityOrException(), 1))
                .then(Commands.argument("scale_type", ResourceLocationArgument.id())
                    .executes(c -> get(c, ResourceLocationArgument.getId(c, "scale_type"), c.getSource().getEntityOrException(), 1))
                    .then(Commands.argument("entity", EntityArgument.entity())
                        .executes(c -> get(c, ResourceLocationArgument.getId(c, "scale_type"), EntityArgument.getEntity(c, "entity"), 1))
                        .then(Commands.argument("scalingFactor", DoubleArgumentType.doubleArg())
                            .executes(c -> get(c, ResourceLocationArgument.getId(c, "scale_type"), EntityArgument.getEntity(c, "entity"),
                                DoubleArgumentType.getDouble(c, "scalingFactor")))))))
            .then(Commands.literal("delay")
                .then(Commands.literal("set")
                    .then(Commands.argument("scale_type", ResourceLocationArgument.id())
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                            .executes(c -> delay(c, List.of(c.getSource().getEntityOrException())))
                            .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(c -> delay(c, EntityArgument.getEntities(c, "targets")))))))
                .then(Commands.literal("reset")
                    .executes(c -> { DELAYS.remove(c.getSource().getEntityOrException().getUUID()); return 1; })
                    .then(Commands.argument("targets", EntityArgument.entities())
                        .executes(c -> { for (Entity e : EntityArgument.getEntities(c, "targets")) DELAYS.remove(e.getUUID()); return 1; })))
                .then(Commands.literal("get").then(Commands.argument("scale_type", ResourceLocationArgument.id())
                    .executes(c -> delayOf(c.getSource().getEntityOrException(), ResourceLocationArgument.getId(c, "scale_type"))))))
            .then(Commands.literal("easing")
                .then(Commands.literal("set").then(Commands.argument("scale_type", ResourceLocationArgument.id())
                    .then(Commands.argument("easing", ResourceLocationArgument.id())
                        .executes(c -> easing(c, List.of(c.getSource().getEntityOrException())))
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .executes(c -> easing(c, EntityArgument.getEntities(c, "targets")))))))
                .then(Commands.literal("reset").executes(c -> { EASINGS.remove(c.getSource().getEntityOrException().getUUID()); return 1; })))
            // Overgrown's scale state already persists; accept every Pehkui persist form as a no-op.
            .then(Commands.literal("persist").executes(c -> 1)
                .then(Commands.argument("args", StringArgumentType.greedyString()).executes(c -> 1))));
    }

    enum Op { SET, ADD, SUBTRACT, MULTIPLY, DIVIDE, POWER }

    private static ArgumentBuilder<CommandSourceStack, ?> op(String name, Op op) {
        return Commands.literal(name)
            .then(Commands.argument("value", FloatArgumentType.floatArg())
                .executes(c -> apply(c, op, BASE, List.of(c.getSource().getEntityOrException())))
                .then(Commands.argument("targets", EntityArgument.entities())
                    .executes(c -> apply(c, op, BASE, EntityArgument.getEntities(c, "targets")))))
            .then(Commands.argument("scale_type", ResourceLocationArgument.id())
                .then(Commands.argument("value", FloatArgumentType.floatArg())
                    .executes(c -> apply(c, op, ResourceLocationArgument.getId(c, "scale_type"), List.of(c.getSource().getEntityOrException())))
                    .then(Commands.argument("targets", EntityArgument.entities())
                        .executes(c -> apply(c, op, ResourceLocationArgument.getId(c, "scale_type"), EntityArgument.getEntities(c, "targets"))))));
    }

    private static float combine(Op op, float current, float v) {
        return switch (op) {
            case SET -> v;
            case ADD -> current + v;
            case SUBTRACT -> current - v;
            case MULTIPLY -> current * v;
            case DIVIDE -> v == 0 ? current : current / v;
            case POWER -> (float) Math.pow(current, v);
        };
    }

    private static int apply(CommandContext<CommandSourceStack> c, Op op, ResourceLocation id, Collection<? extends Entity> targets) {
        float v = FloatArgumentType.getFloat(c, "value");
        ScaleType type = ScaleTypeCodec.resolve(id);
        int n = 0;
        for (Entity e : targets) {
            if (type != null) {
                float current = Scales.base(e, type);
                Scales.set(e, type, combine(op, current, v), delayFor(e, id), easingFor(e, id));
                n++;
            } else if (e instanceof LivingEntity le) {
                for (Holder<Attribute> attr : attributesFor(id.getPath())) {
                    AttributeInstance inst = le.getAttribute(attr);
                    if (inst == null) continue;
                    ResourceLocation mid = ResourceLocation.fromNamespaceAndPath("aea", "pehkui_" + id.getPath());
                    AttributeModifier old = inst.getModifier(mid);
                    float current = old == null ? 1f : (float) (1 + old.amount());
                    float next = combine(op, current, v);
                    inst.removeModifier(mid);
                    if (next != 1f) inst.addPermanentModifier(new AttributeModifier(mid, next - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                    if (attr == Attributes.MAX_HEALTH && le.getHealth() > le.getMaxHealth()) le.setHealth(le.getMaxHealth());
                }
                n++;
            }
        }
        return n;
    }

    private static int reset(CommandContext<CommandSourceStack> c, ResourceLocation id, Collection<? extends Entity> targets) {
        for (Entity e : targets) {
            if (id == null) {
                Scales.reset(e);
                if (e instanceof LivingEntity le) {
                    for (String p : List.of("reach", "block_reach", "entity_reach", "health", "attack_speed")) {
                        for (Holder<Attribute> a : attributesFor(p)) {
                            AttributeInstance inst = le.getAttribute(a);
                            if (inst != null) inst.removeModifier(ResourceLocation.fromNamespaceAndPath("aea", "pehkui_" + p));
                        }
                    }
                }
                continue;
            }
            ScaleType type = ScaleTypeCodec.resolve(id);
            if (type != null) Scales.set(e, type, 1f, delayFor(e, id), easingFor(e, id));
            else if (e instanceof LivingEntity le) {
                for (Holder<Attribute> a : attributesFor(id.getPath())) {
                    AttributeInstance inst = le.getAttribute(a);
                    if (inst != null) inst.removeModifier(ResourceLocation.fromNamespaceAndPath("aea", "pehkui_" + id.getPath()));
                }
            }
        }
        return targets.size();
    }

    private static int get(CommandContext<CommandSourceStack> c, ResourceLocation id, Entity e, double factor) {
        ScaleType type = ScaleTypeCodec.resolve(id);
        float value = 1f;
        if (type != null) value = Scales.value(e, type);
        else if (e instanceof LivingEntity le) {
            for (Holder<Attribute> a : attributesFor(id.getPath())) {
                AttributeInstance inst = le.getAttribute(a);
                AttributeModifier m = inst == null ? null : inst.getModifier(ResourceLocation.fromNamespaceAndPath("aea", "pehkui_" + id.getPath()));
                if (m != null) { value = (float) (1 + m.amount()); break; }
            }
        }
        float shown = value;
        c.getSource().sendSuccess(() -> Component.literal(id + " scale of " + e.getName().getString() + " is " + shown), false);
        return (int) (value * factor);
    }

    private static int delay(CommandContext<CommandSourceStack> c, Collection<? extends Entity> targets) {
        ResourceLocation id = ResourceLocationArgument.getId(c, "scale_type");
        int ticks = IntegerArgumentType.getInteger(c, "ticks");
        for (Entity e : targets) DELAYS.computeIfAbsent(e.getUUID(), k -> new ConcurrentHashMap<>()).put(id, ticks);
        return targets.size();
    }

    private static int delayOf(Entity e, ResourceLocation id) {
        return delayFor(e, id);
    }

    private static int easing(CommandContext<CommandSourceStack> c, Collection<? extends Entity> targets) {
        ResourceLocation id = ResourceLocationArgument.getId(c, "scale_type");
        ScaleEasing easing = easingByName(ResourceLocationArgument.getId(c, "easing").getPath());
        for (Entity e : targets) EASINGS.computeIfAbsent(e.getUUID(), k -> new ConcurrentHashMap<>()).put(id, easing);
        return targets.size();
    }

    static ScaleEasing easingByName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        // Pehkui names: quad_in, quad_out, quad_in_out, sine_in, ... → ease_in_quad, ...
        String[] parts = n.split("_");
        if (parts.length >= 2) {
            String kind = parts[0];
            String dir = n.substring(kind.length() + 1);
            n = "ease_" + dir + "_" + kind;
        }
        for (ScaleEasing e : ScaleEasing.values()) if (e.getSerializedName().equals(n)) return e;
        return ScaleEasing.LINEAR;
    }

    private static int delayFor(Entity e, ResourceLocation id) {
        Map<ResourceLocation, Integer> m = DELAYS.get(e.getUUID());
        if (m == null) return DEFAULT_DELAY;
        Integer v = m.get(id);
        if (v == null) v = m.get(BASE);
        return v == null ? DEFAULT_DELAY : v;
    }

    private static ScaleEasing easingFor(Entity e, ResourceLocation id) {
        Map<ResourceLocation, ScaleEasing> m = EASINGS.get(e.getUUID());
        if (m == null) return ScaleEasing.LINEAR;
        ScaleEasing v = m.get(id);
        return v == null ? ScaleEasing.LINEAR : v;
    }
}
