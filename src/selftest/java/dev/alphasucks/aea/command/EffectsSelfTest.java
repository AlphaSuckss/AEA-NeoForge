package dev.alphasucks.aea.command;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.overgrown.apoli.condition.EntityCondition;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.data.Expression;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.EffectCures;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Smoke tests for effect and gamerule features, run on entities tagged {@code t}:
 * {@code milk} cures them as drinking milk does, {@code cond <json>} tests an entity condition on them, and
 * {@code expr <expression>} evaluates an expression for them.
 */
final class EffectsSelfTest {
    private EffectsSelfTest() {}

    private static List<Entity> targets(ServerLevel level) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : level.getAllEntities()) if (e.getTags().contains("t")) out.add(e);
        out.sort((a, b) -> label(a).compareTo(label(b)));
        return out;
    }

    private static String label(Entity e) {
        TreeSet<String> tags = new TreeSet<>(e.getTags());
        tags.remove("t");
        return String.join(",", tags);
    }

    private static String effects(LivingEntity living) {
        TreeSet<String> names = new TreeSet<>();
        for (MobEffectInstance i : living.getActiveEffects()) names.add(i.getEffect().unwrapKey().get().location().getPath());
        return names.isEmpty() ? "none" : String.join(" ", names);
    }

    private static void say(CommandSourceStack src, String line) {
        src.sendSuccess(() -> Component.literal("[AEA selftest] " + line), false);
    }

    static int milk(CommandSourceStack src) {
        int n = 0;
        for (Entity e : targets(src.getLevel())) {
            if (!(e instanceof LivingEntity living)) continue;
            String before = effects(living);
            living.removeEffectsCuredBy(EffectCures.MILK);
            say(src, "milk | " + label(e) + ": before=" + before + " after=" + effects(living));
            n++;
        }
        return n;
    }

    static int effects(CommandSourceStack src) {
        int n = 0;
        for (Entity e : targets(src.getLevel())) {
            if (e instanceof LivingEntity living) say(src, "effects | " + label(e) + ": " + effects(living));
            n++;
        }
        return n;
    }

    static int condition(CommandSourceStack src, String json) {
        EntityCondition condition = EntityCondition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        int n = 0;
        for (Entity e : targets(src.getLevel())) {
            say(src, "cond " + json + " | " + label(e) + ": " + condition.test(EntityCtx.of(e, e.level())));
            n++;
        }
        return n;
    }

    static int expression(CommandSourceStack src, String text) {
        Expression expression = Expression.of(text);
        List<Entity> all = targets(src.getLevel());
        if (all.isEmpty()) say(src, "expr " + text + " = " + expression.eval(src.getLevel()));
        for (Entity e : all) say(src, "expr " + text + " | " + label(e) + ": " + expression.eval(e));
        return 1;
    }
}
